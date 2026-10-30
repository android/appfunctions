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
package com.example.appfunctions.agent.domain

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionName
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.appfunctions.agent.data.FakeAppFunctionService
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionStatesUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GetAppFunctionStatesUseCaseTest {
    private lateinit var context: Context
    private lateinit var appFunctionManager: AppFunctionManager
    private lateinit var useCase: GetAppFunctionStatesUseCase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        appFunctionManager = AppFunctionManager.getInstance(context)!!
        useCase = GetAppFunctionStatesUseCase(appFunctionManager)

        // Adopt shell permission identity
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.adoptShellPermissionIdentity("android.permission.EXECUTE_APP_FUNCTIONS")

        waitForFakeFunctionIndexed()
    }

    private fun waitForFakeFunctionIndexed() {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val command = "cmd app_function list-app-functions --package ${context.packageName}"
        var output = ""
        val timeoutMs = 10_000L
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            output =
                ParcelFileDescriptor.AutoCloseInputStream(
                    uiAutomation.executeShellCommand(command),
                ).bufferedReader().use { it.readText() }
            if (output.contains("fakeFunction")) {
                return
            }
            Thread.sleep(500)
        }
        fail("Timed out waiting for fakeFunction to be indexed. Output of '$command': $output")
    }

    @Test
    fun invoke_returnsFakeFunctionState() =
        runBlocking {
            val targetFunctionName =
                AppFunctionName(
                    context.packageName,
                    FakeAppFunctionService.FUNCTION_ID_FAKE_FUNCTION,
                )

            val result = useCase(listOf(targetFunctionName))

            val found = result.find { it.functionName == targetFunctionName }
            assertTrue("Should find fakeFunction from $targetFunctionName", found != null)
            assertTrue("The function should be enabled", checkNotNull(found).isEnabled)
        }
}
