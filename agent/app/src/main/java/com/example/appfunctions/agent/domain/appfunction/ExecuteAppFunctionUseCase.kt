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

import android.app.AppInteractionAttribution
import android.app.PendingIntent
import android.os.Build
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.ExecuteAppFunctionResponse.Success.Companion.PROPERTY_RETURN_VALUE
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionParcelableTypeMetadata
import androidx.core.net.toUri
import javax.inject.Inject

/** Use case to execute an AppFunction. */
class ExecuteAppFunctionUseCase
    @Inject
    constructor(
        private val appFunctionManager: AppFunctionManager?,
        private val convertAppFunctionDataToJsonUseCase: ConvertAppFunctionDataToJsonUseCase,
    ) {
        /**
         * Executes the use case.
         *
         * @param function The metadata of the function to execute.
         * @param parameters The input parameters for the function.
         * @return A [ExecuteAppFunctionResult].
         */
        suspend operator fun invoke(
            function: AppFunctionMetadata,
            parameters: AppFunctionData,
            threadId: String? = null,
        ): ExecuteAppFunctionResult {
            if (appFunctionManager == null) {
                return ExecuteAppFunctionResult.Error(
                    IllegalStateException("AppFunctionManager not available on this device"),
                )
            }

            val request =
                if (Build.VERSION.SDK_INT >= 37) {
                    val uri =
                        if (threadId != null) {
                            "appfunctions-agent://chat?threadId=$threadId".toUri()
                        } else {
                            "appfunctions-agent://chat".toUri()
                        }
                    ExecuteAppFunctionRequest(
                        targetPackageName = function.packageName,
                        functionIdentifier = function.id,
                        functionParameters = parameters,
                        attribution =
                            AppInteractionAttribution.Builder(
                                AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY,
                            )
                                .setInteractionUri(uri)
                                .build(),
                    )
                } else {
                    ExecuteAppFunctionRequest(
                        targetPackageName = function.packageName,
                        functionIdentifier = function.id,
                        functionParameters = parameters,
                    )
                }

            return try {
                when (val response = appFunctionManager.executeAppFunction(request)) {
                    is ExecuteAppFunctionResponse.Success -> {
                        val data = response.returnValue
                        val valueType = function.response.valueType
                        if (valueType is AppFunctionParcelableTypeMetadata &&
                            valueType.qualifiedName == PendingIntent::class.java.name
                        ) {
                            val pendingIntent =
                                data.getParcelable(PROPERTY_RETURN_VALUE, PendingIntent::class.java)
                            if (pendingIntent != null) {
                                ExecuteAppFunctionResult.PendingIntentAction(pendingIntent)
                            } else {
                                ExecuteAppFunctionResult.Error(
                                    Exception("Failed to extract PendingIntent from response"),
                                )
                            }
                        } else {
                            val jsonString =
                                convertAppFunctionDataToJsonUseCase(
                                    PROPERTY_RETURN_VALUE,
                                    data,
                                    valueType,
                                    function.components,
                                )
                            ExecuteAppFunctionResult.Data(data, jsonString)
                        }
                    }
                    is ExecuteAppFunctionResponse.Error -> {
                        ExecuteAppFunctionResult.Error(response.error)
                    }
                }
            } catch (e: Exception) {
                ExecuteAppFunctionResult.Error(e)
            }
        }
    }

/** Sealed class representing the result of an AppFunction execution. */
sealed class ExecuteAppFunctionResult {
    data class Data(val data: AppFunctionData, val formattedJson: String) :
        ExecuteAppFunctionResult()

    data class PendingIntentAction(val pendingIntent: PendingIntent) : ExecuteAppFunctionResult()

    data class Error(val exception: Exception) : ExecuteAppFunctionResult()
}
