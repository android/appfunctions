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

import android.os.Build
import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.AppFunctionState
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

    /**
     * The functions of the selected app.
     *
     * @property functionStatuses The runtime status of each function. A function without an entry
     *   has a state that couldn't be read.
     */
    data class FunctionsFoundState(
        val functions: List<AppFunctionMetadata> = emptyList(),
        val functionStatuses: Map<AppFunctionName, AppFunctionStatus> = emptyMap(),
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

/**
 * Runtime status of an app function on the Debugging screen.
 *
 * @property isEnabled Whether the function can be executed right now. It is false when the app
 *   disabled the function, the function is disabled by default, or a dynamic function isn't
 *   registered.
 * @property registeredActivityCount For an activity-scoped (dynamic) function, the number of
 *   activities that register it right now. `null` for a global function.
 */
data class AppFunctionStatus(
    val isEnabled: Boolean,
    val registeredActivityCount: Int? = null,
) {
    /** Whether the function is activity-scoped, i.e. registered at runtime by an activity. */
    val isDynamic: Boolean
        get() = registeredActivityCount != null

    /**
     * Whether the Debugging screen can run the function. An activity-scoped function runs only in
     * an activity that registers it, which this screen can't target.
     */
    val canRun: Boolean
        get() = isEnabled && !isDynamic

    companion object {
        /** Builds the status of [function] from its runtime [state]. */
        fun from(
            function: AppFunctionMetadata,
            state: AppFunctionState,
        ): AppFunctionStatus =
            AppFunctionStatus(
                isEnabled = state.isEnabled,
                registeredActivityCount =
                    if (function.scope == AppFunctionMetadata.SCOPE_ACTIVITY) {
                        // Activity-scoped functions exist only on API 37+.
                        if (Build.VERSION.SDK_INT >= 37) state.activityIds?.size ?: 0 else 0
                    } else {
                        null
                    },
            )
    }
}
