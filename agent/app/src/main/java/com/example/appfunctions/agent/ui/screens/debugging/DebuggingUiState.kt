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

import androidx.appfunctions.metadata.AppFunctionMetadata
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionResult
import com.example.appfunctions.agent.domain.troubleshoot.TroubleshootReport

/** UI state for the Debugging Screen. */
data class DebuggingUiState(
    val filteredApps: AppsGroupState = AppsGroupState(),
    val selectedApp: AppInfo? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val searchAppResultState: SearchAppResultState = SearchAppResultState.Idle,
)

data class AppsGroupState(
    val sections: List<AppSection> = emptyList(),
)

data class AppSection(val titleRes: Int, val apps: List<AppInfo>, val showPin: Boolean)

sealed class SearchAppResultState {
    object Idle : SearchAppResultState()

    data class FunctionsFoundState(
        val functions: List<AppFunctionMetadata> = emptyList(),
        val functionInputs: Map<String, Map<String, Any>> = emptyMap(),
        val executionResult: ExecuteAppFunctionResult? = null,
        val expandedFunctions: Set<String> = emptySet(),
        val executedFunction: AppFunctionMetadata? = null,
        val executedInputs: Map<String, Any> = emptyMap(),
    ) : SearchAppResultState()

    data class TroubleshootUiState(
        val report: TroubleshootReport? = null,
        val isLoading: Boolean = false,
    ) : SearchAppResultState()
}
