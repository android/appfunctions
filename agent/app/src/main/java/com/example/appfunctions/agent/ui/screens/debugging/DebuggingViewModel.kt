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

import android.app.PendingIntent
import android.content.res.Resources
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.domain.appfunction.ConvertInputToAppFunctionDataUseCase
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionResult
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.appfunction.GetInstalledAppsUseCase
import com.example.appfunctions.agent.domain.pendingintent.LaunchPendingIntentUseCase
import com.example.appfunctions.agent.domain.troubleshoot.TroubleshootAppUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for the Debugging Screen.
 *
 * Manages state for searching and displaying AppFunctions.
 */
@HiltViewModel
class DebuggingViewModel
    @Inject
    constructor(
        private val getAppFunctionsUseCase: GetAppFunctionsUseCase,
        private val convertInputToAppFunctionDataUseCase: ConvertInputToAppFunctionDataUseCase,
        private val executeAppFunctionUseCase: ExecuteAppFunctionUseCase,
        private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
        private val troubleshootAppUseCase: TroubleshootAppUseCase,
        private val launchPendingIntentUseCase: LaunchPendingIntentUseCase,
        private val settingsRepository: SettingsRepository,
        @ApplicationContext private val context: android.content.Context,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DebuggingUiState())
        val uiState: StateFlow<DebuggingUiState> = _uiState.asStateFlow()

        private var allInstalledApps: List<AppInfo> = emptyList()
        private var pinnedPackages: Set<String> = emptySet()
        private var allAppFunctions: Map<AppFunctionPackageMetadata, List<AppFunctionMetadata>> =
            emptyMap()

        init {
            loadInstalledApps()
            loadAppFunctions()
            observePinnedApps()
        }

        private fun observePinnedApps() {
            viewModelScope.launch {
                settingsRepository.pinnedApps.collect { pinnedApps ->
                    pinnedPackages = pinnedApps
                    updateAppsGroupState()
                }
            }
        }

        private fun loadAppFunctions() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                getAppFunctionsUseCase().collect { appFunctionsMap ->
                    allAppFunctions = appFunctionsMap
                    updateAppsGroupState()
                }
            }
        }

        private fun loadInstalledApps() {
            viewModelScope.launch {
                val apps = getInstalledAppsUseCase()
                allInstalledApps = apps
                updateAppsGroupState()
            }
        }

        private fun updateAppsGroupState() {
            _uiState.update { state ->
                state.copy(filteredApps = filterApps(state.searchQuery), isLoading = false)
            }
        }

        /** Handles app selection. */
        fun onAppSelected(appInfo: AppInfo) {
            val functions =
                allAppFunctions.entries.find { it.key.packageName == appInfo.packageName }?.value
            if (functions == null) {
                runTroubleshooting(appInfo.packageName)
            } else {
                _uiState.update { state ->
                    state.copy(
                        selectedApp = appInfo,
                        searchAppResultState =
                            SearchAppResultState.FunctionsFoundState(functions = functions),
                    )
                }
            }
        }

        fun onClearSelectedApp() {
            _uiState.update {
                it.copy(
                    searchQuery = "",
                    filteredApps = filterApps(""),
                    selectedApp = null,
                    searchAppResultState = SearchAppResultState.Idle,
                )
            }
        }

        /** Handles search query changes. */
        fun onSearchQueryChanged(query: String) {
            _uiState.update { state ->
                state.copy(
                    searchQuery = query,
                    filteredApps = filterApps(query),
                )
            }
        }

        /** Handles function input changes. */
        fun onFunctionInputsChange(
            functionId: String,
            inputs: Map<String, Any>,
        ) {
            _uiState.update { state ->
                val currentSearchAppResultState = state.searchAppResultState
                if (currentSearchAppResultState !is SearchAppResultState.FunctionsFoundState) return
                state.copy(
                    searchAppResultState =
                        currentSearchAppResultState.copy(
                            functionInputs =
                                currentSearchAppResultState.functionInputs + (functionId to inputs),
                        ),
                )
            }
        }

        /** Handles function expansion state changes. */
        fun onFunctionExpandedChange(
            functionId: String,
            expanded: Boolean,
        ) {
            _uiState.update { state ->
                val currentSearchAppResultState = state.searchAppResultState
                if (currentSearchAppResultState !is SearchAppResultState.FunctionsFoundState) return
                val currentExpanded = currentSearchAppResultState.expandedFunctions
                val newExpanded =
                    if (expanded) {
                        currentExpanded + functionId
                    } else {
                        currentExpanded - functionId
                    }
                state.copy(
                    searchAppResultState =
                        currentSearchAppResultState.copy(expandedFunctions = newExpanded),
                )
            }
        }

        /** Invokes the selected AppFunction. */
        fun invokeFunction(function: AppFunctionMetadata) {
            val currentSearchAppResultState = _uiState.value.searchAppResultState
            if (currentSearchAppResultState !is SearchAppResultState.FunctionsFoundState) return

            val inputs = currentSearchAppResultState.functionInputs[function.id] ?: emptyMap()
            viewModelScope.launch {
                try {
                    val appFunctionDataResult =
                        withContext(Dispatchers.Default) {
                            convertInputToAppFunctionDataUseCase(
                                parameters = function.parameters,
                                components = function.components,
                                inputs = inputs,
                            )
                        }
                    appFunctionDataResult
                        .onSuccess { appFunctionData ->
                            val result =
                                executeAppFunctionUseCase(
                                    function = function,
                                    parameters = appFunctionData,
                                )
                            _uiState.update {
                                it.copy(
                                    searchAppResultState =
                                        currentSearchAppResultState.copy(
                                            executionResult = result,
                                            executedFunction = function,
                                            executedInputs = inputs,
                                        ),
                                )
                            }
                        }
                        .onFailure { error ->
                            _uiState.update {
                                it.copy(
                                    searchAppResultState =
                                        currentSearchAppResultState.copy(
                                            executionResult =
                                                ExecuteAppFunctionResult.Error(
                                                    Exception(
                                                        error.message ?: "Failed to convert input",
                                                    ),
                                                ),
                                            executedFunction = function,
                                            executedInputs = inputs,
                                        ),
                                )
                            }
                        }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            searchAppResultState =
                                currentSearchAppResultState.copy(
                                    executionResult = ExecuteAppFunctionResult.Error(e),
                                    executedFunction = function,
                                    executedInputs = inputs,
                                ),
                        )
                    }
                }
            }
        }

        /** Clears the last execution result or error message. */
        fun clearResult() {
            val currentSearchAppResultState = _uiState.value.searchAppResultState
            if (currentSearchAppResultState !is SearchAppResultState.FunctionsFoundState) return
            _uiState.update {
                it.copy(
                    searchAppResultState =
                        currentSearchAppResultState.copy(
                            executionResult = null,
                            executedFunction = null,
                            executedInputs = emptyMap(),
                        ),
                )
            }
        }

        /** Handles launching a PendingIntent. */
        fun launchPendingIntent(pendingIntent: PendingIntent) {
            val currentSearchAppResultState = _uiState.value.searchAppResultState
            if (currentSearchAppResultState !is SearchAppResultState.FunctionsFoundState) return
            viewModelScope.launch {
                val result = launchPendingIntentUseCase(pendingIntent)
                if (result.isFailure) {
                    _uiState.update { state ->
                        state.copy(
                            searchAppResultState =
                                currentSearchAppResultState.copy(
                                    executionResult =
                                        ExecuteAppFunctionResult.Error(
                                            Exception(
                                                result.exceptionOrNull()?.message
                                                    ?: "Failed to launch action",
                                            ),
                                        ),
                                ),
                        )
                    }
                } else {
                    clearResult()
                }
            }
        }

        /** Toggles the pin status of an app. */
        fun onTogglePin(appInfo: AppInfo) {
            viewModelScope.launch {
                val isPinned = pinnedPackages.contains(appInfo.packageName)
                settingsRepository.setAppPinned(appInfo.packageName, !isPinned)
            }
        }

        private fun runTroubleshooting(packageName: String) {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        searchAppResultState =
                            SearchAppResultState.TroubleshootUiState(isLoading = true),
                    )
                }
                val report = troubleshootAppUseCase(packageName)
                _uiState.update {
                    it.copy(
                        searchAppResultState =
                            SearchAppResultState.TroubleshootUiState(
                                isLoading = false,
                                report = report,
                            ),
                    )
                }
            }
        }

        private fun filterApps(query: String): AppsGroupState {
            val supportedPackageNames = allAppFunctions.keys.map { it.packageName }.toSet()
            val pinnedApps = allInstalledApps.filter { pinnedPackages.contains(it.packageName) }
            val remainingApps = allInstalledApps.filter { !pinnedPackages.contains(it.packageName) }

            val supportedApps = remainingApps.filter { supportedPackageNames.contains(it.packageName) }
            val unsupportedApps =
                remainingApps.filter { !supportedPackageNames.contains(it.packageName) }

            val sections =
                buildList {
                    val filteredPinned = pinnedApps.filter { it.label.contains(query, ignoreCase = true) }
                    if (filteredPinned.isNotEmpty()) {
                        add(AppSection(Resources.ID_NULL, filteredPinned, true))
                    }

                    val filteredSupported =
                        supportedApps.filter { it.label.contains(query, ignoreCase = true) }
                    if (filteredSupported.isNotEmpty()) {
                        add(AppSection(R.string.debugging_supported, filteredSupported, true))
                    }

                    val filteredUnsupported =
                        unsupportedApps.filter { it.label.contains(query, ignoreCase = true) }
                    if (filteredUnsupported.isNotEmpty()) {
                        add(AppSection(R.string.debugging_unsupported, filteredUnsupported, false))
                    }
                }
            return AppsGroupState(sections = sections)
        }
    }
