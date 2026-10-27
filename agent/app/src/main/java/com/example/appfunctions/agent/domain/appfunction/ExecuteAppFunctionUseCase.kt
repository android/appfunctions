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
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.ExecuteAppFunctionResponse.Success.Companion.PROPERTY_RETURN_VALUE
import androidx.appfunctions.metadata.AppFunctionAllOfTypeMetadata
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionOneOfTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParcelableTypeMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import androidx.core.net.toUri
import com.example.appfunctions.agent.data.AgentInternalTools
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import javax.inject.Inject

/**
 * Use case to execute an AppFunction (or internal agent tool), performing common pre-execution
 * steps such as recursive content URI read permission grants.
 */
class ExecuteAppFunctionUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val appFunctionManager: AppFunctionManager?,
        private val convertAppFunctionDataToJsonUseCase: ConvertAppFunctionDataToJsonUseCase,
        private val agentInternalTools: AgentInternalTools,
    ) {
        /**
         * Executes the use case.
         *
         * @param function The metadata of the function to execute.
         * @param parameters The input parameters for the function.
         * @param threadId Optional chat thread ID used for interaction attribution on API 37+.
         * @return An [ExecuteAppFunctionResult].
         */
        suspend operator fun invoke(
            function: AppFunctionMetadata,
            parameters: AppFunctionData,
            threadId: String? = null,
        ): ExecuteAppFunctionResult {
            if (function.packageName == AgentInternalTools.INTERNAL_TOOL_PACKAGE) {
                return executeInternalTool(function, parameters)
            }

            if (appFunctionManager == null) {
                return ExecuteAppFunctionResult.Error(
                    IllegalStateException("AppFunctionManager not available on this device"),
                )
            }

            grantContentUriPermissions(function, parameters)

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
                                data.getParcelable(
                                    PROPERTY_RETURN_VALUE,
                                    PendingIntent::class.java,
                                )
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExecuteAppFunctionResult.Error(e)
            }
        }

        private suspend fun executeInternalTool(
            function: AppFunctionMetadata,
            parameters: AppFunctionData,
        ): ExecuteAppFunctionResult {
            return try {
                when (function.id) {
                    "getCurrentLocation" -> {
                        val location = agentInternalTools.getCurrentLocation()
                        buildLatLngResult(function, location)
                    }
                    "geocodeAddress" -> {
                        val address =
                            parameters.getString("address")
                                ?: throw IllegalArgumentException(
                                    "Missing required parameter 'address'",
                                )
                        val location = agentInternalTools.geocodeAddress(address)
                        buildLatLngResult(function, location)
                    }
                    "generateImage" -> {
                        val prompt =
                            parameters.getString("prompt")
                                ?: throw IllegalArgumentException(
                                    "Missing required parameter 'prompt'",
                                )
                        val aspectRatio = parameters.getString("aspectRatio")
                        val result = agentInternalTools.generateImage(prompt, aspectRatio)
                        buildGeneratedImageResult(function, result)
                    }
                    else ->
                        throw IllegalArgumentException(
                            "Unknown internal tool: ${function.id}",
                        )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExecuteAppFunctionResult.Error(e)
            }
        }

        private fun buildLatLngResult(
            function: AppFunctionMetadata,
            location: AgentInternalTools.LatLng?,
        ): ExecuteAppFunctionResult.Data {
            if (location == null) {
                return ExecuteAppFunctionResult.Data(
                    data = AppFunctionData.EMPTY,
                    formattedJson = "null",
                )
            }
            val formattedJson =
                JSONObject()
                    .apply {
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                    }
                    .toString()
            val valueType = function.response.valueType
            val data =
                if (valueType is AppFunctionObjectTypeMetadata) {
                    val locationData =
                        AppFunctionData.Builder(valueType, function.components)
                            .setDouble("latitude", location.latitude)
                            .setDouble("longitude", location.longitude)
                            .build()
                    AppFunctionData.Builder(function.response, function.components)
                        .setAppFunctionData(PROPERTY_RETURN_VALUE, locationData)
                        .build()
                } else {
                    AppFunctionData.EMPTY
                }
            return ExecuteAppFunctionResult.Data(data = data, formattedJson = formattedJson)
        }

        private fun buildGeneratedImageResult(
            function: AppFunctionMetadata,
            result: AgentInternalTools.GeneratedImageResult,
        ): ExecuteAppFunctionResult.Data {
            val formattedJson =
                JSONObject()
                    .apply {
                        put("imageUri", result.imageUri)
                        put("mimeType", result.mimeType)
                        put("prompt", result.prompt)
                    }
                    .toString()
            val valueType = function.response.valueType
            val data =
                if (valueType is AppFunctionObjectTypeMetadata) {
                    val imageData =
                        AppFunctionData.Builder(valueType, function.components)
                            .setString("imageUri", result.imageUri)
                            .setString("mimeType", result.mimeType)
                            .setString("prompt", result.prompt)
                            .build()
                    AppFunctionData.Builder(function.response, function.components)
                        .setAppFunctionData(PROPERTY_RETURN_VALUE, imageData)
                        .build()
                } else {
                    AppFunctionData.EMPTY
                }
            return ExecuteAppFunctionResult.Data(data = data, formattedJson = formattedJson)
        }

        private fun grantContentUriPermissions(
            function: AppFunctionMetadata,
            parameters: AppFunctionData,
        ) {
            for (parameter in function.parameters) {
                grantUriPermissionsForProperty(
                    targetPackageName = function.packageName,
                    data = parameters,
                    name = parameter.name,
                    dataType = parameter.dataType,
                    components = function.components,
                )
            }
        }

        private fun grantUriPermissionsForProperty(
            targetPackageName: String,
            data: AppFunctionData,
            name: String,
            dataType: AppFunctionDataTypeMetadata,
            components: AppFunctionComponentsMetadata,
        ) {
            when (dataType) {
                is AppFunctionStringTypeMetadata -> {
                    runCatching { data.getString(name) }
                        .getOrNull()
                        ?.let { grantIfContentUri(targetPackageName, it) }
                }
                is AppFunctionParcelableTypeMetadata -> {
                    if (dataType.qualifiedName == Uri::class.java.name) {
                        runCatching { data.getParcelable(name, Uri::class.java) }
                            .getOrNull()
                            ?.let { grantIfContentUri(targetPackageName, it) }
                    }
                }
                is AppFunctionObjectTypeMetadata -> {
                    val nested =
                        runCatching { data.getAppFunctionData(name) }.getOrNull() ?: return
                    grantUriPermissionsForObject(targetPackageName, nested, dataType, components)
                }
                is AppFunctionAllOfTypeMetadata,
                is AppFunctionOneOfTypeMetadata,
                -> {
                    val nested =
                        runCatching { data.getAppFunctionData(name) }.getOrNull() ?: return
                    grantUriPermissionsForDataType(targetPackageName, nested, dataType, components)
                }
                is AppFunctionReferenceTypeMetadata -> {
                    val resolved = components.dataTypes[dataType.referenceDataType] ?: return
                    grantUriPermissionsForProperty(
                        targetPackageName = targetPackageName,
                        data = data,
                        name = name,
                        dataType = resolved,
                        components = components,
                    )
                }
                is AppFunctionArrayTypeMetadata -> {
                    grantUriPermissionsForArray(
                        targetPackageName = targetPackageName,
                        data = data,
                        name = name,
                        itemType = dataType.itemType,
                        components = components,
                    )
                }
            }
        }

        private fun grantUriPermissionsForObject(
            targetPackageName: String,
            data: AppFunctionData,
            objectType: AppFunctionObjectTypeMetadata,
            components: AppFunctionComponentsMetadata,
        ) {
            if (objectType.qualifiedName == Uri::class.java.name && objectType.properties.isEmpty()) {
                runCatching { data.getString("uri") }
                    .getOrNull()
                    ?.let { grantIfContentUri(targetPackageName, it) }
                return
            }
            for ((propName, propType) in objectType.properties) {
                grantUriPermissionsForProperty(
                    targetPackageName = targetPackageName,
                    data = data,
                    name = propName,
                    dataType = propType,
                    components = components,
                )
            }
        }

        private fun grantUriPermissionsForDataType(
            targetPackageName: String,
            data: AppFunctionData,
            dataType: AppFunctionDataTypeMetadata,
            components: AppFunctionComponentsMetadata,
        ) {
            when (dataType) {
                is AppFunctionObjectTypeMetadata -> {
                    grantUriPermissionsForObject(targetPackageName, data, dataType, components)
                }
                is AppFunctionAllOfTypeMetadata -> {
                    for (subType in dataType.matchAll) {
                        grantUriPermissionsForDataType(
                            targetPackageName,
                            data,
                            subType,
                            components,
                        )
                    }
                }
                is AppFunctionOneOfTypeMetadata -> {
                    for (candidate in dataType.matchOneOf) {
                        grantUriPermissionsForDataType(
                            targetPackageName,
                            data,
                            candidate,
                            components,
                        )
                    }
                }
                is AppFunctionReferenceTypeMetadata -> {
                    val resolved = components.dataTypes[dataType.referenceDataType] ?: return
                    grantUriPermissionsForDataType(targetPackageName, data, resolved, components)
                }
            }
        }

        private fun grantUriPermissionsForArray(
            targetPackageName: String,
            data: AppFunctionData,
            name: String,
            itemType: AppFunctionDataTypeMetadata,
            components: AppFunctionComponentsMetadata,
        ) {
            when (itemType) {
                is AppFunctionStringTypeMetadata -> {
                    runCatching { data.getStringList(name) }
                        .getOrNull()
                        ?.forEach { grantIfContentUri(targetPackageName, it) }
                }
                is AppFunctionParcelableTypeMetadata -> {
                    if (itemType.qualifiedName == Uri::class.java.name) {
                        runCatching { data.getParcelableList(name, Uri::class.java) }
                            .getOrNull()
                            ?.forEach { grantIfContentUri(targetPackageName, it) }
                    }
                }
                is AppFunctionReferenceTypeMetadata -> {
                    val resolved = components.dataTypes[itemType.referenceDataType] ?: return
                    grantUriPermissionsForArray(
                        targetPackageName = targetPackageName,
                        data = data,
                        name = name,
                        itemType = resolved,
                        components = components,
                    )
                }
                is AppFunctionObjectTypeMetadata,
                is AppFunctionAllOfTypeMetadata,
                is AppFunctionOneOfTypeMetadata,
                -> {
                    runCatching { data.getAppFunctionDataList(name) }
                        .getOrNull()
                        ?.forEach { itemData ->
                            grantUriPermissionsForDataType(
                                targetPackageName,
                                itemData,
                                itemType,
                                components,
                            )
                        }
                }
            }
        }

        private fun grantIfContentUri(
            targetPackageName: String,
            uriString: String,
        ) {
            if (uriString.startsWith("content://")) {
                runCatching {
                    grantIfContentUri(targetPackageName, Uri.parse(uriString))
                }
            }
        }

        private fun grantIfContentUri(
            targetPackageName: String,
            uri: Uri,
        ) {
            if (uri.scheme == "content") {
                runCatching {
                    context.grantUriPermission(
                        targetPackageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
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
