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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/** Use case to get all available AppFunctions grouped by package name. */
class GetAppFunctionsUseCase
    @Inject
    constructor(
        private val appFunctionManager: AppFunctionManager?,
    ) {
        /**
         * Executes the use case.
         *
         * @return A Flow emitting a map of package names to their list of AppFunctionMetadata.
         */
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        operator fun invoke(): Flow<Map<AppFunctionPackageMetadata, List<AppFunctionMetadata>>> {
            if (appFunctionManager == null) {
                return flowOf(emptyMap())
            }
            return appFunctionManager.observeAppFunctions()
                .debounce(500.milliseconds)
                .mapLatest { _ ->
                    appFunctionManager.search()
                }
                .onStart {
                    emit(appFunctionManager.search())
                }
        }

        private suspend fun AppFunctionManager.search(): Map<AppFunctionPackageMetadata, List<AppFunctionMetadata>> {
            return searchAppFunctions(AppFunctionSearchSpec()).groupBy(
                AppFunctionMetadata::packageMetadata,
            )
        }
    }
