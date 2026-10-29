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
package com.example.appfunctions.agent.domain.appfunction

import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import com.example.appfunctions.agent.data.AgentInternalTools
import com.example.appfunctions.agent.data.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Filter parameters for [GetAppFunctionsUseCase].
 *
 * Nullable filter fields (`null`) indicate that the corresponding filter should not be applied.
 */
data class GetAppFunctionsParams(
    val targetPackageNames: Set<String>? = null,
    val isEnabled: Boolean? = null,
    val isConnected: Boolean? = null,
    val includeInternalTools: Boolean = true,
)

/** Use case to observe available AppFunctions grouped by package metadata with optional filters. */
class GetAppFunctionsUseCase
    @Inject
    constructor(
        private val appFunctionManager: AppFunctionManager?,
        private val getAppFunctionStatesUseCase: GetAppFunctionStatesUseCase,
        private val settingsRepository: SettingsRepository,
        private val agentInternalTools: AgentInternalTools,
    ) {
        /**
         * Executes the use case.
         *
         * @param params Optional [GetAppFunctionsParams] filters to apply.
         * @return A Flow emitting a map of [AppFunctionPackageMetadata] to their matching
         *   [AppFunctionMetadata] list.
         */
        operator fun invoke(
            params: GetAppFunctionsParams = GetAppFunctionsParams(),
        ): Flow<Map<AppFunctionPackageMetadata, List<AppFunctionMetadata>>> {
            val internalFunctions = getInternalFunctions(params)
            return observeExternalFunctions(params)
                .filterByConnection(params.isConnected)
                .filterByEnabledState(params.isEnabled)
                .map { externalFunctions ->
                    (externalFunctions + internalFunctions)
                        .sortedByDescending { it.id.startsWith(it.packageName) }
                        .distinctBy { it.packageName to it.id }
                        .groupBy(AppFunctionMetadata::packageMetadata)
                }
        }

        private fun getInternalFunctions(params: GetAppFunctionsParams): List<AppFunctionMetadata> {
            if (!params.includeInternalTools || params.isConnected == false) {
                return emptyList()
            }
            return agentInternalTools
                .getInternalToolsMetadata()
                .filter { (metadata, state) ->
                    val matchesPackage =
                        params.targetPackageNames == null ||
                            metadata.packageName in params.targetPackageNames
                    val matchesEnabled =
                        params.isEnabled == null || state.isEnabled == params.isEnabled
                    matchesPackage && matchesEnabled
                }
                .map { it.first }
        }

        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        private fun observeExternalFunctions(params: GetAppFunctionsParams): Flow<List<AppFunctionMetadata>> {
            if (appFunctionManager == null) {
                return flowOf(emptyList())
            }
            val searchSpec = AppFunctionSearchSpec(packageNames = params.targetPackageNames)
            return appFunctionManager
                .observeAppFunctions()
                .debounce(500.milliseconds)
                .mapLatest { appFunctionManager.searchAppFunctions(searchSpec) }
                .onStart { emit(appFunctionManager.searchAppFunctions(searchSpec)) }
        }

        private fun Flow<List<AppFunctionMetadata>>.filterByConnection(isConnected: Boolean?): Flow<List<AppFunctionMetadata>> {
            if (isConnected == null) {
                return this
            }
            return combine(this, settingsRepository.disconnectedApps) { functions, disconnectedApps ->
                functions.filter { metadata ->
                    val connected = metadata.packageName !in disconnectedApps
                    connected == isConnected
                }
            }
        }

        private fun Flow<List<AppFunctionMetadata>>.filterByEnabledState(isEnabled: Boolean?): Flow<List<AppFunctionMetadata>> {
            if (isEnabled == null) {
                return this
            }
            return map { functions ->
                if (functions.isEmpty()) {
                    emptyList()
                } else {
                    val states = getAppFunctionStatesUseCase(functions.map { it.name })
                    val stateMap = states.associateBy { it.functionName }
                    functions.filter { metadata ->
                        val enabled = stateMap[metadata.name]?.isEnabled ?: false
                        enabled == isEnabled
                    }
                }
            }
        }
    }
