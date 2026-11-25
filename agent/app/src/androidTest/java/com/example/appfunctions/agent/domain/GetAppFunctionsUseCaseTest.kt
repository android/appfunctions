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

import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.appfunctions.agent.FakeAppFunctionRule
import com.example.appfunctions.agent.awaitUntil
import com.example.appfunctions.agent.data.AgentInternalTools
import com.example.appfunctions.agent.data.DataStoreSettingsRepository
import com.example.appfunctions.agent.data.ToolDiscoveryTools
import com.example.appfunctions.agent.di.settingsDataStore
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionStatesUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsParams
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class GetAppFunctionsUseCaseTest {
    @get:Rule
    val fakeFunction = FakeAppFunctionRule()

    private lateinit var useCase: GetAppFunctionsUseCase

    @Before
    fun setup() {
        val context = fakeFunction.context
        val appFunctionManager = fakeFunction.appFunctionManager
        val dataStore = context.settingsDataStore
        useCase =
            GetAppFunctionsUseCase(
                appFunctionManager = appFunctionManager,
                getAppFunctionStatesUseCase = GetAppFunctionStatesUseCase(appFunctionManager),
                settingsRepository = DataStoreSettingsRepository(dataStore),
                agentInternalTools =
                    AgentInternalTools(
                        context = context,
                        settingsDataStore = dataStore,
                        toolDiscoveryTools = ToolDiscoveryTools(context),
                    ),
            )
    }

    @Test
    fun invoke_returnsFakeFunctionAsGlobalFunction() =
        runBlocking {
            val result = useCase().first()

            val found = result.findFakeFunction()
            assertNotNull("Should find fakeFunction in ${fakeFunction.context.packageName}", found)
            assertEquals(AppFunctionMetadata.SCOPE_GLOBAL, checkNotNull(found).scope)
        }

    @Test
    fun invoke_progressiveToolDisclosure_returnsDiscoveryToolsWithAppCatalog() =
        runBlocking {
            val tools =
                useCase(GetAppFunctionsParams(progressiveToolDisclosure = true))
                    .first()
                    .values
                    .flatten()

            assertFalse(tools.any { it.name == fakeFunction.functionName })
            val expandTool =
                tools.first { it.id == AgentInternalTools.EXPAND_TOOL_DESCRIPTION }
            assertTrue(expandTool.description.contains(fakeFunction.context.packageName))
        }

    @Test
    fun invoke_isEnabledFilter_followsFunctionDisabledAtRuntime() =
        runBlocking {
            fakeFunction.setEnabled(false)

            awaitUntil("fakeFunction to be returned as disabled") {
                useCase(DISABLED_ONLY).first().findFakeFunction() != null
            }
            assertEquals(null, useCase(ENABLED_ONLY).first().findFakeFunction())

            fakeFunction.setEnabled(true)

            awaitUntil("fakeFunction to be returned as enabled") {
                useCase(ENABLED_ONLY).first().findFakeFunction() != null
            }
            assertEquals(null, useCase(DISABLED_ONLY).first().findFakeFunction())
        }

    @Test
    fun invoke_functionDisabledAtRuntime_emitsWithoutTheFunction() =
        runBlocking {
            val containsFakeFunction = Channel<Boolean>(Channel.UNLIMITED)
            val collector =
                launch {
                    useCase(ENABLED_ONLY).collect { result ->
                        containsFakeFunction.send(result.findFakeFunction() != null)
                    }
                }
            try {
                assertTrue(
                    "The first emission should contain fakeFunction",
                    containsFakeFunction.receive(),
                )

                fakeFunction.setEnabled(false)

                // The state change makes the flow emit again, without fakeFunction.
                withTimeout(30.seconds) {
                    while (containsFakeFunction.receive()) {
                        // Skip emissions from before the change.
                    }
                }
            } finally {
                collector.cancel()
            }
        }

    private fun Map<AppFunctionPackageMetadata, List<AppFunctionMetadata>>.findFakeFunction(): AppFunctionMetadata? =
        values.flatten().find { it.name == fakeFunction.functionName }

    private companion object {
        val ENABLED_ONLY = GetAppFunctionsParams(isEnabled = true, includeInternalTools = false)
        val DISABLED_ONLY = GetAppFunctionsParams(isEnabled = false, includeInternalTools = false)
    }
}
