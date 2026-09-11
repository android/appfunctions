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
import androidx.appfunctions.AppFunctionState
import androidx.appfunctions.metadata.AppFunctionName
import javax.inject.Inject

/** Use case to get [AppFunctionState] with given list of [AppFunctionName]. */
class GetAppFunctionStatesUseCase
    @Inject
    constructor(private val appFunctionManager: AppFunctionManager?) {
        suspend operator fun invoke(functionNames: List<AppFunctionName>): List<AppFunctionState> {
            if (appFunctionManager == null) {
                return emptyList()
            }
            return appFunctionManager.getAppFunctionStates(functionNames)
        }
    }
