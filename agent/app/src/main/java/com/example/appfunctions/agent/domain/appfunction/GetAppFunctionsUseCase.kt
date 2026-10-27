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
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        operator fun invoke(
            params: GetAppFunctionsParams = GetAppFunctionsParams(),
        ): Flow<Map<AppFunctionPackageMetadata, List<AppFunctionMetadata>>> {
            val externalTargetPackages =
                params.targetPackageNames?.minus(AgentInternalTools.INTERNAL_TOOL_PACKAGE)
            val externalFunctionsFlow: Flow<List<AppFunctionMetadata>> =
                if (
                    appFunctionManager == null ||
                    (externalTargetPackages != null && externalTargetPackages.isEmpty())
                ) {
                    flowOf(emptyList())
                } else {
                    val searchSpec = AppFunctionSearchSpec(packageNames = externalTargetPackages)
                    appFunctionManager
                        .observeAppFunctions()
                        .debounce(500.milliseconds)
                        .mapLatest { appFunctionManager.searchAppFunctions(searchSpec) }
                        .onStart { emit(appFunctionManager.searchAppFunctions(searchSpec)) }
                }

            val disconnectedAppsFlow: Flow<Set<String>> =
                if (params.isConnected != null) {
                    settingsRepository.disconnectedApps
                } else {
                    flowOf(emptySet())
                }

            return combine(externalFunctionsFlow, disconnectedAppsFlow) {
                    externalFunctions,
                    disconnectedApps,
                ->
                filterAndGroup(
                    externalFunctions = externalFunctions,
                    disconnectedApps = disconnectedApps,
                    params = params,
                )
            }
        }

        private suspend fun filterAndGroup(
            externalFunctions: List<AppFunctionMetadata>,
            disconnectedApps: Set<String>,
            params: GetAppFunctionsParams,
        ): Map<AppFunctionPackageMetadata, List<AppFunctionMetadata>> {
            val internalEntries =
                if (params.includeInternalTools) {
                    agentInternalTools.getInternalToolsMetadata()
                } else {
                    emptyList()
                }
            val allCandidates = externalFunctions + internalEntries.map { it.first }

            val preFiltered =
                allCandidates.filter { metadata ->
                    val matchesPackage =
                        params.targetPackageNames == null ||
                            metadata.packageName in params.targetPackageNames
                    val matchesConnection =
                        if (params.isConnected == null) {
                            true
                        } else {
                            val connected =
                                metadata.packageName == AgentInternalTools.INTERNAL_TOOL_PACKAGE ||
                                    metadata.packageName !in disconnectedApps
                            connected == params.isConnected
                        }
                    matchesPackage && matchesConnection
                }

            val enabledFiltered =
                if (params.isEnabled == null) {
                    preFiltered
                } else {
                    val externalFunctionNames =
                        preFiltered
                            .filter { it.packageName != AgentInternalTools.INTERNAL_TOOL_PACKAGE }
                            .map { it.name }
                    val externalStates =
                        if (externalFunctionNames.isNotEmpty()) {
                            getAppFunctionStatesUseCase(externalFunctionNames)
                        } else {
                            emptyList()
                        }
                    val stateMap =
                        (externalStates + internalEntries.map { it.second })
                            .associateBy { it.functionName }

                    preFiltered.filter { metadata ->
                        val enabled =
                            if (metadata.packageName == AgentInternalTools.INTERNAL_TOOL_PACKAGE) {
                                stateMap[metadata.name]?.isEnabled ?: true
                            } else {
                                stateMap[metadata.name]?.isEnabled ?: false
                            }
                        enabled == params.isEnabled
                    }
                }

            return enabledFiltered
                .sortedByDescending { metadata -> metadata.id.startsWith(metadata.packageName) }
                .distinctBy { metadata -> metadata.packageName to metadata.id }
                .groupBy(AppFunctionMetadata::packageMetadata)
        }
    }
