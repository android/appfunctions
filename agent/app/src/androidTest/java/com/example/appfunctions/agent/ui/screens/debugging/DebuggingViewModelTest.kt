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
package com.example.appfunctions.agent.ui.screens.debugging

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.appfunctions.agent.FakeAppFunctionRule
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.data.AgentInternalTools
import com.example.appfunctions.agent.data.DataStoreSettingsRepository
import com.example.appfunctions.agent.data.ToolDiscoveryTools
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.domain.appfunction.ConvertAppFunctionDataToJsonUseCase
import com.example.appfunctions.agent.domain.appfunction.ConvertInputToAppFunctionDataUseCase
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionStatesUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.appfunction.GetInstalledAppsUseCase
import com.example.appfunctions.agent.domain.appfunction.GrantAppFunctionUriPermissionsUseCase
import com.example.appfunctions.agent.domain.pendingintent.LaunchPendingIntentUseCase
import com.example.appfunctions.agent.domain.troubleshoot.TroubleshootAppUseCase
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * Runs [DebuggingViewModel] against the device's AppFunction service, with the agent's own
 * `fakeFunction` as the function under test.
 */
@RunWith(AndroidJUnit4::class)
class DebuggingViewModelTest {
    @get:Rule
    val fakeFunction = FakeAppFunctionRule()

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val viewModelStore = ViewModelStore()
    private lateinit var dataStoreFile: File
    private lateinit var viewModel: DebuggingViewModel

    @Before
    fun setUp() {
        val context = fakeFunction.context
        val appFunctionManager = fakeFunction.appFunctionManager
        // A separate settings file, so pinned or disconnected apps on the device don't apply.
        dataStoreFile = context.preferencesDataStoreFile("debugging_view_model_test")
        dataStoreFile.delete()
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        val settingsRepository = DataStoreSettingsRepository(dataStore)
        val agentInternalTools = AgentInternalTools(context, dataStore, ToolDiscoveryTools(context))
        val getAppFunctionsUseCase =
            GetAppFunctionsUseCase(
                appFunctionManager = appFunctionManager,
                getAppFunctionStatesUseCase = GetAppFunctionStatesUseCase(appFunctionManager),
                settingsRepository = settingsRepository,
                agentInternalTools = agentInternalTools,
            )
        val factory =
            viewModelFactory {
                initializer {
                    DebuggingViewModel(
                        getAppFunctionsUseCase = getAppFunctionsUseCase,
                        getAppFunctionStatesUseCase = GetAppFunctionStatesUseCase(appFunctionManager),
                        convertInputToAppFunctionDataUseCase = ConvertInputToAppFunctionDataUseCase(),
                        executeAppFunctionUseCase =
                            ExecuteAppFunctionUseCase(
                                appFunctionManager = appFunctionManager,
                                convertAppFunctionDataToJsonUseCase =
                                    ConvertAppFunctionDataToJsonUseCase(),
                                agentInternalTools = agentInternalTools,
                                grantAppFunctionUriPermissionsUseCase =
                                    GrantAppFunctionUriPermissionsUseCase(context),
                                getAppFunctionsUseCase = getAppFunctionsUseCase,
                                startAppInteractionSessionUseCase =
                                    Lazy { error("This test doesn't execute functions") },
                            ),
                        getInstalledAppsUseCase = GetInstalledAppsUseCase(context),
                        troubleshootAppUseCase = TroubleshootAppUseCase(context, getAppFunctionsUseCase),
                        launchPendingIntentUseCase = LaunchPendingIntentUseCase(context),
                        settingsRepository = settingsRepository,
                        context = context,
                    )
                }
            }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = ViewModelProvider(viewModelStore, factory)[DebuggingViewModel::class.java]
        }
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModelStore.clear() }
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun selectedApp_functionDisabledAndReEnabledAtRuntime_updatesStatusWithoutReselecting() =
        runBlocking {
            val agentApp = awaitSupportedApp(fakeFunction.context.packageName)
            withContext(Dispatchers.Main) { viewModel.onAppSelected(agentApp) }
            val initialStatus = awaitFakeFunctionStatus { it != null }
            assertTrue(
                "fakeFunction should start enabled and global: $initialStatus",
                initialStatus.isEnabled && !initialStatus.isDynamic,
            )

            fakeFunction.setEnabled(false)
            val disabledStatus = awaitFakeFunctionStatus { it?.isEnabled == false }
            assertFalse("A disabled function can't run", disabledStatus.canRun)

            fakeFunction.setEnabled(true)
            val enabledStatus = awaitFakeFunctionStatus { it?.isEnabled == true }
            assertTrue("A re-enabled global function can run", enabledStatus.canRun)
        }

    /** Waits until [packageName] is listed as an app with functions, then returns it. */
    private suspend fun awaitSupportedApp(packageName: String): AppInfo =
        withTimeout(TIMEOUT) {
            viewModel.uiState
                .first { state -> state.findSupportedApp(packageName) != null }
                .let { state -> checkNotNull(state.findSupportedApp(packageName)) }
        }

    private fun DebuggingUiState.findSupportedApp(packageName: String): AppInfo? =
        filteredApps.sections
            .find { it.titleRes == R.string.debugging_supported }
            ?.apps
            ?.find { it.packageName == packageName }

    /** Waits until the selected app shows a `fakeFunction` status matching [predicate]. */
    private suspend fun awaitFakeFunctionStatus(predicate: (AppFunctionStatus?) -> Boolean): AppFunctionStatus =
        withTimeout(TIMEOUT) {
            val state =
                viewModel.uiState.first { state ->
                    val functionsState =
                        state.searchAppResultState as? SearchAppResultState.FunctionsFoundState
                    functionsState != null &&
                        predicate(functionsState.functionStatuses[fakeFunction.functionName])
                }
            val functionsState = state.searchAppResultState as SearchAppResultState.FunctionsFoundState
            checkNotNull(functionsState.functionStatuses[fakeFunction.functionName])
        }

    private companion object {
        val TIMEOUT = 30.seconds
    }
}
