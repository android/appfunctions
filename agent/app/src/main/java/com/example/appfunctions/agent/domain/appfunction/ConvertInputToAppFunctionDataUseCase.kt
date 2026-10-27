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
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionBooleanTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionDoubleTypeMetadata
import androidx.appfunctions.metadata.AppFunctionFloatTypeMetadata
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionLongTypeMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionParcelableTypeMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject

/**
 * Use case to convert a map of raw inputs to type-safe [AppFunctionData], resolving remote
 * `http://` and `https://` file reference URLs into local `content://` URIs when needed.
 */
class ConvertInputToAppFunctionDataUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        internal var remoteFileDownloader: suspend (Context, String) -> String =
            ::downloadRemoteFileToContentUri

        /**
         * Executes the use case.
         *
         * @param parameters The list of parameter metadata for the function.
         * @param components The component metadata for resolving references.
         * @param inputs The map of raw inputs from the UI or LLM tool call.
         * @return The converted [AppFunctionData].
         */
        suspend operator fun invoke(
            parameters: List<AppFunctionParameterMetadata>,
            components: AppFunctionComponentsMetadata,
            inputs: Map<String, Any>,
        ): Result<AppFunctionData> =
            try {
                val resolvedInputs =
                    resolveRemoteFileReferencesRecursively(
                        parametersMetadata = parameters,
                        components = components,
                        inputs = inputs,
                    )
                withContext(Dispatchers.Default) {
                    val builder = AppFunctionData.Builder(parameters, components)
                    for (parameter in parameters) {
                        val value = resolvedInputs[parameter.name] ?: continue
                        setParameterValue(
                            builder,
                            parameter.name,
                            parameter.dataType,
                            value,
                            components,
                        )
                    }
                    Result.success(builder.build())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }

        private suspend fun resolveRemoteFileReferencesRecursively(
            parametersMetadata: List<AppFunctionParameterMetadata>,
            components: AppFunctionComponentsMetadata,
            inputs: Map<String, Any>,
        ): Map<String, Any> {
            val paramMap = parametersMetadata.associateBy { it.name }
            return buildMap(inputs.size) {
                for ((key, value) in inputs) {
                    val paramMeta = paramMap[key]
                    put(
                        key,
                        resolveValueRecursively(
                            dataType = paramMeta?.dataType,
                            components = components,
                            value = value,
                            paramName = key,
                        ),
                    )
                }
            }
        }

        private suspend fun resolveValueRecursively(
            dataType: AppFunctionDataTypeMetadata?,
            components: AppFunctionComponentsMetadata,
            value: Any,
            paramName: String?,
        ): Any {
            val resolvedType =
                if (dataType is AppFunctionReferenceTypeMetadata) {
                    components.dataTypes[dataType.referenceDataType] ?: dataType
                } else {
                    dataType
                }

            return when (value) {
                is String -> {
                    val shouldResolve =
                        isFileReferenceParameter(paramName) ||
                            isUriMetadata(dataType, components)
                    if (
                        shouldResolve &&
                        (value.startsWith("http://") || value.startsWith("https://"))
                    ) {
                        try {
                            remoteFileDownloader(context, value)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            throw IOException(
                                "Failed to download remote file reference: ${e.message}",
                                e,
                            )
                        }
                    } else {
                        value
                    }
                }
                is Map<*, *> -> {
                    val objectProperties =
                        (resolvedType as? AppFunctionObjectTypeMetadata)?.properties
                    buildMap(value.size) {
                        for (entry in value.entries) {
                            val k = entry.key as String
                            val propType = objectProperties?.get(k)
                            val resolvedProp =
                                entry.value?.let {
                                    resolveValueRecursively(
                                        dataType = propType,
                                        components = components,
                                        value = it,
                                        paramName = k,
                                    )
                                } ?: ""
                            put(k, resolvedProp)
                        }
                    }
                }
                is List<*> -> {
                    val itemType = (resolvedType as? AppFunctionArrayTypeMetadata)?.itemType
                    buildList {
                        for (item in value) {
                            if (item != null) {
                                add(
                                    resolveValueRecursively(
                                        dataType = itemType,
                                        components = components,
                                        value = item,
                                        paramName = paramName,
                                    ),
                                )
                            }
                        }
                    }
                }
                else -> value
            }
        }

        private fun isFileReferenceParameter(parameterName: String?): Boolean {
            if (parameterName == null) return false
            if (parameterName in KNOWN_FILE_REFERENCE_PARAM_NAMES) return true
            return parameterName.endsWith("Uri", ignoreCase = true) ||
                parameterName.endsWith("Uris", ignoreCase = true)
        }

        private fun isUriMetadata(
            dataType: AppFunctionDataTypeMetadata?,
            components: AppFunctionComponentsMetadata,
        ): Boolean {
            if (dataType == null) return false
            if (
                dataType is AppFunctionObjectTypeMetadata &&
                dataType.qualifiedName == ANDROID_NET_URI
            ) {
                return true
            }
            if (
                dataType is AppFunctionParcelableTypeMetadata &&
                dataType.qualifiedName == ANDROID_NET_URI
            ) {
                return true
            }
            if (dataType is AppFunctionReferenceTypeMetadata) {
                if (dataType.referenceDataType == ANDROID_NET_URI) {
                    return true
                }
                val resolved = components.dataTypes[dataType.referenceDataType]
                if (resolved != null && resolved !== dataType) {
                    return isUriMetadata(resolved, components)
                }
            }
            return false
        }

        private fun setParameterValue(
            builder: AppFunctionData.Builder,
            name: String,
            dataType: AppFunctionDataTypeMetadata,
            value: Any,
            components: AppFunctionComponentsMetadata,
        ) {
            when (dataType) {
                is AppFunctionStringTypeMetadata -> {
                    builder.setString(name, value.toString())
                }
                is AppFunctionIntTypeMetadata -> {
                    when (value) {
                        is Number -> builder.setInt(name, value.toInt())
                        is String -> if (value.isNotEmpty()) builder.setInt(name, value.toInt())
                    }
                }
                is AppFunctionLongTypeMetadata -> {
                    when (value) {
                        is Number -> builder.setLong(name, value.toLong())
                        is String -> if (value.isNotEmpty()) builder.setLong(name, value.toLong())
                    }
                }
                is AppFunctionBooleanTypeMetadata -> {
                    when (value) {
                        is Boolean -> builder.setBoolean(name, value)
                        is String ->
                            if (value.isNotEmpty()) {
                                builder.setBoolean(name, value.toBoolean())
                            }
                    }
                }
                is AppFunctionDoubleTypeMetadata -> {
                    when (value) {
                        is Number -> builder.setDouble(name, value.toDouble())
                        is String ->
                            if (value.isNotEmpty()) {
                                builder.setDouble(name, value.toDouble())
                            }
                    }
                }
                is AppFunctionFloatTypeMetadata -> {
                    when (value) {
                        is Number -> builder.setFloat(name, value.toFloat())
                        is String ->
                            if (value.isNotEmpty()) {
                                builder.setFloat(name, value.toFloat())
                            }
                    }
                }
                is AppFunctionObjectTypeMetadata -> {
                    val objData = toAppFunctionData(dataType, value, components)
                    builder.setAppFunctionData(name, objData)
                }
                is AppFunctionArrayTypeMetadata -> {
                    setArrayValue(builder, name, dataType, value as List<Any>, components)
                }
                is AppFunctionReferenceTypeMetadata -> {
                    val referenceKey = dataType.referenceDataType
                    val objectType =
                        components.dataTypes[referenceKey] as? AppFunctionObjectTypeMetadata
                    if (objectType != null) {
                        val objData = toAppFunctionData(objectType, value, components)
                        builder.setAppFunctionData(name, objData)
                    }
                }
            }
        }

        private fun toAppFunctionData(
            objectType: AppFunctionObjectTypeMetadata,
            value: Any,
            components: AppFunctionComponentsMetadata,
        ): AppFunctionData {
            if (objectType.qualifiedName == ANDROID_NET_URI && value is String) {
                val uriPropertyName = objectType.properties.keys.firstOrNull() ?: "uri"
                return AppFunctionData.Builder(objectType, components)
                    .setString(uriPropertyName, value)
                    .build()
            }
            return convertObject(objectType, value as Map<String, Any>, components)
        }

        private fun convertObject(
            objectType: AppFunctionObjectTypeMetadata,
            values: Map<String, Any>,
            components: AppFunctionComponentsMetadata,
        ): AppFunctionData {
            val builder = AppFunctionData.Builder(objectType, components)
            for ((propName, propType) in objectType.properties) {
                val value = values[propName] ?: continue
                setParameterValue(builder, propName, propType, value, components)
            }
            return builder.build()
        }

        private fun setArrayValue(
            builder: AppFunctionData.Builder,
            name: String,
            arrayType: AppFunctionArrayTypeMetadata,
            values: List<Any>,
            components: AppFunctionComponentsMetadata,
        ) {
            when (val itemType = arrayType.itemType) {
                is AppFunctionStringTypeMetadata -> {
                    builder.setStringList(name, values.map { it as String })
                }
                is AppFunctionIntTypeMetadata -> {
                    val intArray =
                        values
                            .map { if (it is Number) it.toInt() else (it as String).toInt() }
                            .toIntArray()
                    builder.setIntArray(name, intArray)
                }
                is AppFunctionLongTypeMetadata -> {
                    val longArray =
                        values
                            .map { if (it is Number) it.toLong() else (it as String).toLong() }
                            .toLongArray()
                    builder.setLongArray(name, longArray)
                }
                is AppFunctionBooleanTypeMetadata -> {
                    val booleanArray = values.map { it as Boolean }.toBooleanArray()
                    builder.setBooleanArray(name, booleanArray)
                }
                is AppFunctionDoubleTypeMetadata -> {
                    val doubleArray =
                        values
                            .map {
                                if (it is Number) it.toDouble() else (it as String).toDouble()
                            }
                            .toDoubleArray()
                    builder.setDoubleArray(name, doubleArray)
                }
                is AppFunctionFloatTypeMetadata -> {
                    val floatArray =
                        values
                            .map {
                                if (it is Number) it.toFloat() else (it as String).toFloat()
                            }
                            .toFloatArray()
                    builder.setFloatArray(name, floatArray)
                }
                is AppFunctionObjectTypeMetadata -> {
                    val dataList =
                        values.map { item ->
                            toAppFunctionData(itemType, item, components)
                        }
                    builder.setAppFunctionDataList(name, dataList)
                }
                is AppFunctionReferenceTypeMetadata -> {
                    val referenceKey = itemType.referenceDataType
                    val objectType =
                        components.dataTypes[referenceKey] as? AppFunctionObjectTypeMetadata
                    if (objectType != null) {
                        val dataList =
                            values.map { item ->
                                toAppFunctionData(objectType, item, components)
                            }
                        builder.setAppFunctionDataList(name, dataList)
                    }
                }
            }
        }

        companion object {
            private const val ANDROID_NET_URI = "android.net.Uri"

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

            private suspend fun downloadRemoteFileToContentUri(
                context: Context,
                urlString: String,
            ): String =
                withContext(Dispatchers.IO) {
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
                        contentUri.toString()
                    } finally {
                        connection.disconnect()
                    }
                }
        }
    }
