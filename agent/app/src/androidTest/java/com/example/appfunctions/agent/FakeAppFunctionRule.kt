/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.appfunctions.agent

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.appfunctions.agent.data.FakeAppFunctionService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.fail
import org.junit.rules.ExternalResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Puts the agent's own [FakeAppFunctionService.FUNCTION_ID_FAKE_FUNCTION] in a known state for an
 * instrumented test.
 *
 * Before each test, it adopts the shell identity that reading and executing app functions needs,
 * waits until the freshly installed app's functions are indexed (through the same APIs the tests
 * call) and resets the function to its default, enabled state. After each test, it resets the
 * state again, so a test that disables the function can't affect the next one, and drops the
 * shell identity.
 */
class FakeAppFunctionRule : ExternalResource() {
    val context: Context = ApplicationProvider.getApplicationContext()
    val appFunctionManager: AppFunctionManager =
        checkNotNull(AppFunctionManager.getInstance(context)) {
            "AppFunctionManager is not available on this device"
        }
    val functionName =
        AppFunctionName(context.packageName, FakeAppFunctionService.FUNCTION_ID_FAKE_FUNCTION)

    private val uiAutomation
        get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    override fun before() {
        uiAutomation.adoptShellPermissionIdentity(EXECUTE_APP_FUNCTIONS)
        try {
            runBlocking {
                awaitUntil("fakeFunction to be indexed", INDEXING_TIMEOUT, ::describeIndexedFunctions) {
                    val functions =
                        appFunctionManager.searchAppFunctions(
                            AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                        )
                    functions.any { it.name == functionName } &&
                        appFunctionManager.getAppFunctionStates(listOf(functionName)).isNotEmpty()
                }
                setEnabledState(AppFunctionManager.APP_FUNCTION_STATE_DEFAULT)
                awaitUntil("fakeFunction to be enabled") {
                    appFunctionManager.getAppFunctionStates(listOf(functionName)).single().isEnabled
                }
            }
        } catch (e: Throwable) {
            // after() doesn't run when before() fails.
            uiAutomation.dropShellPermissionIdentity()
            throw e
        }
    }

    override fun after() {
        try {
            runBlocking { setEnabledState(AppFunctionManager.APP_FUNCTION_STATE_DEFAULT) }
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    /** Enables or disables the fake function at runtime, as an app does with its own functions. */
    suspend fun setEnabled(enabled: Boolean) {
        setEnabledState(
            if (enabled) {
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED
            } else {
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED
            },
        )
    }

    /**
     * Enables or disables the fake function, as [setEnabled] does, and returns the result of
     * [awaitChange], which waits until the code under test shows the change. Fails the test after
     * [timeout].
     *
     * A flow built on [AppFunctionManager.observeAppFunctions] registers its platform observer only
     * when it is collected, and the observer isn't notified of earlier changes.
     * `GetAppFunctionsUseCase` emits a first result before that, so a change made right after that
     * result can be missed. Until [awaitChange] returns, the function is therefore switched to the
     * other state and back every [RETOGGLE_INTERVAL].
     */
    suspend fun <T> setEnabledAndAwait(
        enabled: Boolean,
        timeout: Duration = 30.seconds,
        awaitChange: suspend () -> T,
    ): T =
        coroutineScope {
            setEnabled(enabled)
            val retoggling =
                launch {
                    while (true) {
                        delay(RETOGGLE_INTERVAL)
                        // Not cancellable, so that the function is always left in the requested state.
                        withContext(NonCancellable) {
                            setEnabled(!enabled)
                            setEnabled(enabled)
                        }
                    }
                }
            try {
                withTimeout(timeout) { awaitChange() }
            } finally {
                retoggling.cancelAndJoin()
            }
        }

    private suspend fun setEnabledState(state: Int) {
        appFunctionManager.setAppFunctionEnabled(functionName.functionIdentifier, state)
    }

    private fun describeIndexedFunctions(): String {
        val command = "cmd app_function list-app-functions --package ${context.packageName}"
        val output =
            ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command))
                .bufferedReader()
                .use { it.readText() }
        return "Output of '$command': $output"
    }

    private companion object {
        const val EXECUTE_APP_FUNCTIONS = "android.permission.EXECUTE_APP_FUNCTIONS"

        /** Indexing a freshly installed app can take a while on a slow emulator. */
        val INDEXING_TIMEOUT = 60.seconds

        /**
         * How long a state change may take to show before it is made again. Long enough for the
         * observer's notification, the use case's debounce and its queries; making the change again
         * while it is still on its way does no harm.
         */
        val RETOGGLE_INTERVAL = 5.seconds
    }
}

/**
 * Polls [condition] until it is true. Fails the test after [timeout], with [describeState] and the
 * last error thrown by [condition], if any.
 */
suspend fun awaitUntil(
    description: String,
    timeout: Duration = 30.seconds,
    describeState: () -> String = { "" },
    condition: suspend () -> Boolean,
) {
    val start = TimeSource.Monotonic.markNow()
    var lastError: Exception? = null
    while (true) {
        val isMet =
            try {
                condition()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                false
            }
        if (isMet) return
        if (start.elapsedNow() > timeout) {
            fail("Timed out after $timeout waiting for $description. Last error: $lastError. ${describeState()}")
        }
        delay(500.milliseconds)
    }
}
