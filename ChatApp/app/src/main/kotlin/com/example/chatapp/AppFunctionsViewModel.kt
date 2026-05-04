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
package com.example.chatapp

import android.app.Application
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Information about an app function.
 *
 * @property id The unique identifier of the app function.
 * @property isEnabled Whether the app function is currently enabled.
 * @property description A human-readable description of the app function, if available.
 */
data class AppFunctionInfo(
    val id: String,
    val isEnabled: Boolean,
    val description: String?,
)

/**
 * UI state for the App Functions settings screen.
 *
 * @property functions The list of available app functions and their current status.
 */
data class AppFunctionsUiState(
    val functions: List<AppFunctionInfo> = emptyList(),
)

/**
 * ViewModel for the App Functions settings screen.
 *
 * This ViewModel interacts with the [AppFunctionManager] to list and manage the state
 * of app functions within the application.
 */
@HiltViewModel
class AppFunctionsViewModel
    @Inject
    constructor(
        application: Application,
    ) : AndroidViewModel(application) {
        private val appFunctionManager = AppFunctionManager.getInstance(application)

        val uiState: StateFlow<AppFunctionsUiState> =
            appFunctionManager?.observeAppFunctions(
                AppFunctionSearchSpec(packageNames = setOf(application.packageName)),
            )?.map { packageMetadataList ->
                val functions =
                    packageMetadataList.firstOrNull {
                        it.packageName == application.packageName
                    }?.appFunctions ?: emptyList()

                val functionInfos =
                    functions.map {
                        AppFunctionInfo(
                            id = it.id,
                            isEnabled = it.isEnabled,
                            description = it.description,
                        )
                    }.sortedBy { it.id }

                AppFunctionsUiState(functions = functionInfos)
            }?.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AppFunctionsUiState(),
            )
                ?: MutableStateFlow(AppFunctionsUiState())

        /**
         * Toggles the enabled state of an app function.
         *
         * @param id The ID of the function to toggle.
         * @param enabled The new enabled state.
         */
        fun toggleFunction(
            id: String,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                val newState =
                    if (enabled) {
                        AppFunctionManager.APP_FUNCTION_STATE_ENABLED
                    } else {
                        AppFunctionManager.APP_FUNCTION_STATE_DISABLED
                    }
                appFunctionManager?.setAppFunctionEnabled(id, newState)
            }
        }
    }
