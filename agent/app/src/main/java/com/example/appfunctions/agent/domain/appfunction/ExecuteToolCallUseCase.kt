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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.core.content.FileProvider
import com.example.appfunctions.agent.data.AgentInternalTools
import com.example.appfunctions.agent.data.db.entities.MessageAttachment
import com.example.appfunctions.agent.domain.LlmResponsePart
import com.example.appfunctions.agent.domain.ToolOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject

/** Result of executing a single [LlmResponsePart.ToolCall]. */
sealed class ToolCallExecutionResult {
    data class Success(
        val toolOutput: ToolOutput,
        val attachment: MessageAttachment? = null,
    ) : ToolCallExecutionResult()

    data class PendingIntentRequired(
        val pendingIntentId: String,
        val pendingIntent: PendingIntent,
    ) : ToolCallExecutionResult()

    data class Failure(
        val errorMessage: String,
        val exception: Throwable? = null,
    ) : ToolCallExecutionResult()
}

/**
 * Shared pipeline that executes a single [LlmResponsePart.ToolCall] against internal agent tools or
 * discovered Android AppFunctions, handling remote file reference downloads, recursive content URI
 * permission grants, argument conversion, and [AppFunctionExceptionFormatter] formatting.
 */
class ExecuteToolCallUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val convertInputToAppFunctionDataUseCase: ConvertInputToAppFunctionDataUseCase,
        private val executeAppFunctionUseCase: ExecuteAppFunctionUseCase,
        private val agentInternalTools: AgentInternalTools,
    ) {
        suspend operator fun invoke(
            toolCall: LlmResponsePart.ToolCall,
            tools: List<AppFunctionMetadata>,
            threadId: String,
        ): ToolCallExecutionResult {
            if (toolCall.packageName == AgentInternalTools.INTERNAL_TOOL_PACKAGE) {
                return executeInternalTool(toolCall)
                    .fold(
                        onSuccess = { jsonString ->
                            ToolCallExecutionResult.Success(
                                toolOutput =
                                    ToolOutput(
                                        functionId = toolCall.functionId,
                                        callId = toolCall.callId,
                                        result = jsonString,
                                    ),
                                attachment = extractAttachment(jsonString),
                            )
                        },
                        onFailure = { exception ->
                            if (exception is CancellationException) {
                                throw exception
                            }
                            ToolCallExecutionResult.Failure(
                                errorMessage =
                                    "Internal tool execution failed: ${exception.message}",
                                exception = exception,
                            )
                        },
                    )
            }

            val matchingTool =
                tools.find {
                    it.packageName == toolCall.packageName && it.id == toolCall.functionId
                }
                    ?: return ToolCallExecutionResult.Failure(
                        errorMessage = "Tool not found: ${toolCall.functionId}",
                    )

            val rawConvertedInputs =
                buildMap<String, Any> {
                    for ((key, value) in toolCall.arguments) {
                        if (value != null) {
                            put(key, value)
                        }
                    }
                }

            val convertedInputs =
                try {
                    withContext(Dispatchers.IO) {
                        resolveRemoteFileReferencesRecursively(
                            context = context,
                            parametersMetadata = matchingTool.parameters,
                            inputs = rawConvertedInputs,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return ToolCallExecutionResult.Failure(
                        errorMessage = "Failed to download remote file reference: ${e.message}",
                        exception = e,
                    )
                }

            grantContentUriPermissionsRecursively(
                context = context,
                targetPackageName = toolCall.packageName,
                value = convertedInputs,
            )

            val appFunctionDataResult =
                withContext(Dispatchers.Default) {
                    convertInputToAppFunctionDataUseCase(
                        parameters = matchingTool.parameters,
                        components = matchingTool.components,
                        inputs = convertedInputs,
                    )
                }
            if (appFunctionDataResult.isFailure) {
                val exception = appFunctionDataResult.exceptionOrNull()
                if (exception is CancellationException) {
                    throw exception
                }
                return ToolCallExecutionResult.Failure(
                    errorMessage =
                        "Failed to convert arguments for ${toolCall.functionId}\n $exception",
                    exception = exception,
                )
            }

            return when (
                val executionResult =
                    executeAppFunctionUseCase(
                        function = matchingTool,
                        parameters = appFunctionDataResult.getOrThrow(),
                        threadId = threadId,
                    )
            ) {
                is ExecuteAppFunctionResult.Data -> {
                    ToolCallExecutionResult.Success(
                        toolOutput =
                            ToolOutput(
                                functionId = toolCall.functionId,
                                callId = toolCall.callId,
                                result = executionResult.formattedJson,
                            ),
                        attachment = extractAttachment(executionResult.formattedJson),
                    )
                }

                is ExecuteAppFunctionResult.PendingIntentAction -> {
                    ToolCallExecutionResult.PendingIntentRequired(
                        pendingIntentId = UUID.randomUUID().toString(),
                        pendingIntent = executionResult.pendingIntent,
                    )
                }

                is ExecuteAppFunctionResult.Error -> {
                    val exception = executionResult.exception
                    if (exception is CancellationException) {
                        throw exception
                    }
                    val appFunctionException =
                        AppFunctionExceptionFormatter.getAppFunctionException(exception)
                    if (appFunctionException != null) {
                        ToolCallExecutionResult.Success(
                            toolOutput =
                                ToolOutput(
                                    functionId = toolCall.functionId,
                                    callId = toolCall.callId,
                                    result =
                                        AppFunctionExceptionFormatter.format(
                                            appFunctionException,
                                            toolCall.functionId,
                                        ),
                                ),
                        )
                    } else {
                        ToolCallExecutionResult.Failure(
                            errorMessage =
                                "Tool execution failed for ${toolCall.functionId}: ${exception.message}",
                            exception = exception,
                        )
                    }
                }
            }
        }

        private fun extractAttachment(jsonString: String): MessageAttachment? {
            return runCatching {
                val json = JSONObject(jsonString)
                val uri = json.optString("imageUri")
                if (uri.isNotBlank()) {
                    val mimeType =
                        json.optString("mimeType").takeIf { it.isNotBlank() } ?: "image/png"
                    MessageAttachment(uri = uri, mimeType = mimeType)
                } else {
                    null
                }
            }.getOrNull()
        }

        private fun resolveRemoteFileReferencesRecursively(
            context: Context,
            parametersMetadata: List<AppFunctionParameterMetadata>,
            inputs: Map<String, Any>,
        ): Map<String, Any> {
            val paramMap = parametersMetadata.associateBy { it.name }
            return inputs.mapValues { (key, value) ->
                val paramMeta = paramMap[key]
                resolveValueRecursively(context, paramMeta?.dataType, value, key)
            }
        }

        private fun resolveValueRecursively(
            context: Context,
            dataType: AppFunctionDataTypeMetadata?,
            value: Any,
            paramName: String?,
        ): Any {
            return when (value) {
                is String -> {
                    val shouldResolve =
                        isFileReferenceParameter(paramName) || isUriMetadata(dataType)
                    if (
                        shouldResolve &&
                        (value.startsWith("http://") || value.startsWith("https://"))
                    ) {
                        downloadRemoteFileToContentUri(context, value)
                    } else {
                        value
                    }
                }
                is Map<*, *> -> {
                    value.entries.associate { entry ->
                        val k = entry.key as String
                        val propType =
                            (dataType as? AppFunctionObjectTypeMetadata)?.properties?.get(k)
                        k to (
                            entry.value?.let {
                                resolveValueRecursively(
                                    context,
                                    propType,
                                    it,
                                    k,
                                )
                            } ?: ""
                        )
                    }
                }
                is List<*> -> {
                    val itemType = (dataType as? AppFunctionArrayTypeMetadata)?.itemType
                    value.mapNotNull { item ->
                        if (item != null) {
                            resolveValueRecursively(context, itemType, item, paramName)
                        } else {
                            null
                        }
                    }
                }
                else -> value
            }
        }

        private fun downloadRemoteFileToContentUri(
            context: Context,
            urlString: String,
        ): String {
            val url = URL(urlString)
            val connection =
                (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                }
            try {
                connection.connect()
                val contentType = connection.contentType ?: ""
                val ext =
                    when {
                        contentType.contains("png", ignoreCase = true) -> "png"
                        contentType.contains("jpeg", ignoreCase = true) ||
                            contentType.contains("jpg", ignoreCase = true) -> "jpg"
                        contentType.contains("gif", ignoreCase = true) -> "gif"
                        contentType.contains("webp", ignoreCase = true) -> "webp"
                        urlString.substringAfterLast("/", "").contains(".") ->
                            urlString.substringAfterLast("/").substringAfterLast(".")
                        else -> "jpg"
                    }
                val cacheDir = File(context.cacheDir, "file_references").apply { mkdirs() }
                val file = File(cacheDir, "generated_${UUID.randomUUID()}.$ext")
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val contentUri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                return contentUri.toString()
            } finally {
                connection.disconnect()
            }
        }

        private fun grantContentUriPermissionsRecursively(
            context: Context,
            targetPackageName: String,
            value: Any?,
        ) {
            when (value) {
                is String -> {
                    if (value.startsWith("content://")) {
                        runCatching {
                            val uri = Uri.parse(value)
                            context.grantUriPermission(
                                targetPackageName,
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                    }
                }
                is Map<*, *> ->
                    value.values.forEach {
                        grantContentUriPermissionsRecursively(context, targetPackageName, it)
                    }
                is List<*> ->
                    value.forEach {
                        grantContentUriPermissionsRecursively(context, targetPackageName, it)
                    }
            }
        }

        private fun isFileReferenceParameter(parameterName: String?): Boolean {
            if (parameterName == null) return false
            if (parameterName in KNOWN_FILE_REFERENCE_PARAM_NAMES) return true
            return parameterName.endsWith("Uri", ignoreCase = true) ||
                parameterName.endsWith("Uris", ignoreCase = true)
        }

        private fun isUriMetadata(dataType: AppFunctionDataTypeMetadata?): Boolean {
            if (dataType == null) return false
            if (dataType is AppFunctionObjectTypeMetadata && dataType.qualifiedName == "android.net.Uri") {
                return true
            }
            if (dataType is AppFunctionReferenceTypeMetadata && dataType.referenceDataType == "android.net.Uri") {
                return true
            }
            return false
        }

        private suspend fun executeInternalTool(toolCall: LlmResponsePart.ToolCall): Result<String> {
            return runCatching {
                when (toolCall.functionId) {
                    "getCurrentLocation" -> {
                        val location = agentInternalTools.getCurrentLocation()
                        if (location != null) {
                            JSONObject().apply {
                                put("latitude", location.latitude)
                                put("longitude", location.longitude)
                            }.toString()
                        } else {
                            "null"
                        }
                    }
                    "geocodeAddress" -> {
                        val address =
                            toolCall.arguments["address"] as? String
                                ?: throw IllegalArgumentException(
                                    "Missing required parameter 'address'",
                                )
                        val location = agentInternalTools.geocodeAddress(address)
                        if (location != null) {
                            JSONObject().apply {
                                put("latitude", location.latitude)
                                put("longitude", location.longitude)
                            }.toString()
                        } else {
                            "null"
                        }
                    }
                    "generateImage" -> {
                        val prompt =
                            toolCall.arguments["prompt"] as? String
                                ?: throw IllegalArgumentException(
                                    "Missing required parameter 'prompt'",
                                )
                        val aspectRatio = toolCall.arguments["aspectRatio"] as? String
                        val result = agentInternalTools.generateImage(prompt, aspectRatio)
                        JSONObject().apply {
                            put("imageUri", result.imageUri)
                            put("mimeType", result.mimeType)
                            put("prompt", result.prompt)
                        }.toString()
                    }
                    else ->
                        throw IllegalArgumentException(
                            "Unknown internal tool: ${toolCall.functionId}",
                        )
                }
            }
        }

        companion object {
            private val KNOWN_FILE_REFERENCE_PARAM_NAMES =
                setOf(
                    "wallpaperUri",
                    "imageUri",
                    "attachmentUri",
                    "ringtoneUri",
                    "profilePictureUri",
                    "audioUri",
                    "voiceNoteUri",
                )
        }
    }
