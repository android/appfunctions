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

import androidx.appfunctions.metadata.AppFunctionMetadata
import com.example.appfunctions.agent.data.AgentInternalTools
import com.example.appfunctions.agent.data.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case to discover all currently enabled AppFunction and internal agent tools, filtered by
 * connected apps and optional [targetPackageName] scoping.
 */
class GetEnabledToolsUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val getAppFunctionsUseCase: GetAppFunctionsUseCase,
        private val getAppFunctionStatesUseCase: GetAppFunctionStatesUseCase,
        private val agentInternalTools: AgentInternalTools,
    ) {
        suspend operator fun invoke(targetPackageName: String? = null): List<AppFunctionMetadata> {
            val disconnectedApps = settingsRepository.disconnectedApps.first()
            val internalTools = agentInternalTools.getInternalToolsMetadata()
            val allTools =
                getAppFunctionsUseCase().first().values.flatten() +
                    internalTools.map { it.first }

            val functionNames = allTools.map { it.name }
            val states =
                getAppFunctionStatesUseCase(functionNames) +
                    internalTools.map { it.second }
            val stateMap = states.associateBy { it.functionName }

            return allTools
                .filter { metadata ->
                    val isEnabled =
                        if (metadata.packageName == AgentInternalTools.INTERNAL_TOOL_PACKAGE) {
                            true
                        } else {
                            stateMap[metadata.name]?.isEnabled ?: false
                        }
                    isEnabled &&
                        metadata.packageName !in disconnectedApps &&
                        (targetPackageName == null || metadata.packageName == targetPackageName)
                }
                .sortedByDescending { metadata -> metadata.id.startsWith(metadata.packageName) }
                .distinctBy { metadata -> metadata.id }
        }
    }
