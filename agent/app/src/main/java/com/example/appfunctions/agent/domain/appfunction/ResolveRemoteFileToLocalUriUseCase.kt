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

import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionParcelableTypeMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import com.example.appfunctions.agent.data.FileDownloadHelper
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject

/**
 * Use case to resolve remote `http://` and `https://` file reference URLs in raw input maps into
 * local `content://` URIs via [FileDownloadHelper].
 */
class ResolveRemoteFileToLocalUriUseCase
    @Inject
    constructor(
        private val fileDownloadHelper: FileDownloadHelper,
    ) {
        /**
         * Resolves remote file reference URLs in [inputs] to local `content://` URIs.
         *
         * @param parameters The list of parameter metadata for the function.
         * @param components The component metadata for resolving references.
         * @param inputs The map of raw inputs.
         * @return A new input map with remote file reference URLs replaced by local `content://`
         *   URIs.
         */
        suspend operator fun invoke(
            parameters: List<AppFunctionParameterMetadata>,
            components: AppFunctionComponentsMetadata,
            inputs: Map<String, Any>,
        ): Map<String, Any> {
            val paramMap = parameters.associateBy { it.name }
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
                            fileDownloadHelper.downloadToLocalUri(value)
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
                            val key = entry.key as String
                            val propType = objectProperties?.get(key)
                            val entryValue = entry.value
                            val resolvedProp =
                                if (entryValue != null) {
                                    resolveValueRecursively(
                                        dataType = propType,
                                        components = components,
                                        value = entryValue,
                                        paramName = key,
                                    )
                                } else {
                                    ""
                                }
                            put(key, resolvedProp)
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
        }
    }
