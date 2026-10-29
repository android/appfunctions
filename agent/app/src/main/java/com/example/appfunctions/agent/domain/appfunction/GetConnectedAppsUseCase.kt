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

import android.content.Context
import com.example.appfunctions.agent.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Use case to get the list of apps and their connection status for the agent. */
class GetConnectedAppsUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val getAppFunctionsUseCase: GetAppFunctionsUseCase,
        private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
        private val settingsRepository: SettingsRepository,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(): Flow<List<ConnectedAppInfo>> {
            return getAppFunctionsUseCase(
                GetAppFunctionsParams(includeInternalTools = false),
            ).flatMapLatest { appFunctionsMap ->
                val installedApps = getInstalledAppsUseCase()
                val installedAppsMap = installedApps.associateBy { it.packageName }

                settingsRepository.disconnectedApps.map { disconnectedApps ->
                    appFunctionsMap.keys
                        .map { packageMetadata ->
                            val packageName = packageMetadata.packageName
                            val appInfo = installedAppsMap[packageName]
                            val label = appInfo?.label ?: packageName
                            val icon = appInfo?.icon
                            val isConnected = packageName !in disconnectedApps
                            val description =
                                packageMetadata
                                    .resolveAppFunctionAppMetadata(context)
                                    ?.displayDescription

                            ConnectedAppInfo(
                                packageName = packageName,
                                label = label,
                                icon = icon,
                                isConnected = isConnected,
                                description = description,
                            )
                        }
                        .sortedBy { it.label }
                }
            }
        }
    }
