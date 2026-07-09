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
package com.example.appfunctions.agent.data

import android.util.Log
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionBooleanTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionDoubleTypeMetadata
import androidx.appfunctions.metadata.AppFunctionFloatTypeMetadata
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionLongTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import com.example.appfunctions.agent.domain.appfunction.ToolConverter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Implementation of [ToolConverter] for Gemini tools. */
@Singleton
class GeminiToolConverter
    @Inject
    constructor() : ToolConverter<JsonObject> {
        override fun convert(tool: AppFunctionMetadata): JsonObject {
            return convertToolToGeminiSchema(tool)
        }

        override fun getToolName(tool: AppFunctionMetadata): String {
            return getGeminiFunctionName(tool)
        }

        private fun convertToolToGeminiSchema(tool: AppFunctionMetadata): JsonObject {
            val combinedName = getGeminiFunctionName(tool)
            return buildJsonObject {
                put(KEY_NAME, JsonPrimitive(combinedName))
                put(KEY_DESCRIPTION, JsonPrimitive(tool.description))
                put(
                    KEY_PARAMETERS,
                    buildJsonObject {
                        put(KEY_TYPE, JsonPrimitive(VALUE_OBJECT))
                        put(
                            KEY_PROPERTIES,
                            buildJsonObject {
                                tool.parameters.forEach { parameter ->
                                    val typeSchema =
                                        mapDataTypeToGeminiSchema(
                                            parameter.dataType,
                                            tool.components,
                                            tool.id,
                                            parameterName = parameter.name,
                                        )
                                    put(
                                        parameter.name,
                                        buildJsonObject {
                                            typeSchema.forEach { (key, value) -> put(key, value) }
                                            put(KEY_DESCRIPTION, JsonPrimitive(parameter.description))
                                        },
                                    )
                                }
                            },
                        )
                        val requiredParams = tool.parameters.filter { it.isRequired }.map { it.name }
                        if (requiredParams.isNotEmpty()) {
                            put(
                                KEY_REQUIRED,
                                buildJsonArray { requiredParams.forEach { add(JsonPrimitive(it)) } },
                            )
                        }
                    },
                )
            }
        }

        private fun getGeminiFunctionName(tool: AppFunctionMetadata): String {
            val baseName = tool.id.replace(INVALID_NAME_CHARS, TOOL_ID_SEPARATOR)
            val components = baseName.split(TOOL_ID_SEPARATOR)
            var result = ""

            // Build from right to left, keeping the most specific parts of the package/id
            for (i in components.indices.reversed()) {
                val component = components[i]
                val separator = if (result.isEmpty()) "" else TOOL_ID_SEPARATOR

                val newLength = result.length + separator.length + component.length
                if (newLength <= MAX_NAME_LENGTH) {
                    result = component + separator + result
                } else {
                    break
                }
            }
            if (result.isEmpty()) {
                result = baseName.takeLast(MAX_NAME_LENGTH)
            }
            return result
        }

        private fun mapDataTypeToGeminiSchema(
            dataType: AppFunctionDataTypeMetadata,
            components: AppFunctionComponentsMetadata,
            functionId: String,
            visitedReferences: Set<String> = emptySet(),
            parameterName: String? = null,
        ): JsonObject {
            return when (dataType) {
                is AppFunctionStringTypeMetadata ->
                    buildJsonObject {
                        put(KEY_TYPE, JsonPrimitive(VALUE_STRING))
                        if (isFileReferenceParameter(parameterName)) {
                            put(KEY_FORMAT, JsonPrimitive(VALUE_FILE_REFERENCE))
                        }
                        val enumValues = dataType.enumValues
                        if (!enumValues.isNullOrEmpty()) {
                            put(
                                KEY_ENUMS,
                                buildJsonArray {
                                    for (enumValue in enumValues) {
                                        add(JsonPrimitive(enumValue))
                                    }
                                },
                            )
                        }
                    }
                is AppFunctionLongTypeMetadata ->
                    buildJsonObject { put(KEY_TYPE, JsonPrimitive(VALUE_INTEGER)) }
                is AppFunctionIntTypeMetadata ->
                    buildJsonObject {
                        put(KEY_TYPE, JsonPrimitive(VALUE_INTEGER))
                        val enumValues = dataType.enumValues
                        if (!enumValues.isNullOrEmpty()) {
                            put(
                                KEY_ENUMS,
                                buildJsonArray {
                                    for (enumValue in enumValues) {
                                        add(JsonPrimitive(enumValue))
                                    }
                                },
                            )
                        }
                    }
                is AppFunctionBooleanTypeMetadata ->
                    buildJsonObject { put(KEY_TYPE, JsonPrimitive(VALUE_BOOLEAN)) }
                is AppFunctionDoubleTypeMetadata ->
                    buildJsonObject { put(KEY_TYPE, JsonPrimitive(VALUE_NUMBER)) }
                is AppFunctionFloatTypeMetadata ->
                    buildJsonObject { put(KEY_TYPE, JsonPrimitive(VALUE_NUMBER)) }
                is AppFunctionArrayTypeMetadata ->
                    buildJsonObject {
                        put(KEY_TYPE, JsonPrimitive(VALUE_ARRAY))
                        put(
                            KEY_ITEMS,
                            mapDataTypeToGeminiSchema(
                                dataType.itemType,
                                components,
                                functionId,
                                visitedReferences,
                                parameterName,
                            ),
                        )
                    }
                is AppFunctionObjectTypeMetadata ->
                    if (dataType.qualifiedName == "android.net.Uri") {
                        buildJsonObject {
                            put(KEY_TYPE, JsonPrimitive(VALUE_STRING))
                            put(KEY_FORMAT, JsonPrimitive(VALUE_FILE_REFERENCE))
                        }
                    } else {
                        buildJsonObject {
                            put(KEY_TYPE, JsonPrimitive(VALUE_OBJECT))
                            put(
                                KEY_PROPERTIES,
                                buildJsonObject {
                                    dataType.properties.forEach { (name, type) ->
                                        put(
                                            name,
                                            mapDataTypeToGeminiSchema(
                                                type,
                                                components,
                                                functionId,
                                                visitedReferences,
                                                parameterName = name,
                                            ),
                                        )
                                    }
                                },
                            )
                            if (dataType.required.isNotEmpty()) {
                                put(
                                    KEY_REQUIRED,
                                    buildJsonArray {
                                        dataType.required.forEach { name -> add(JsonPrimitive(name)) }
                                    },
                                )
                            }
                        }
                    }
                is AppFunctionReferenceTypeMetadata -> {
                    val referenceKey = dataType.referenceDataType
                    if (referenceKey == "android.net.Uri") {
                        return buildJsonObject {
                            put(KEY_TYPE, JsonPrimitive(VALUE_STRING))
                            put(KEY_FORMAT, JsonPrimitive(VALUE_FILE_REFERENCE))
                        }
                    }
                    if (visitedReferences.contains(referenceKey)) {
                        Log.d(
                            "GeminiToolConverter",
                            "Circular reference detected for $referenceKey in function $functionId. Breaking cycle.",
                        )
                        return buildJsonObject { put(KEY_TYPE, JsonPrimitive(VALUE_OBJECT)) }
                    }
                    val objectType =
                        components.dataTypes[referenceKey]
                            ?: throw IllegalArgumentException(
                                "Reference type $referenceKey not found in components for function $functionId",
                            )
                    mapDataTypeToGeminiSchema(
                        objectType,
                        components,
                        functionId,
                        visitedReferences + referenceKey,
                        parameterName,
                    )
                }
                else ->
                    throw IllegalArgumentException(
                        "Unsupported data type: $dataType for function $functionId",
                    )
            }
        }

        private fun isFileReferenceParameter(parameterName: String?): Boolean {
            if (parameterName == null) return false
            if (parameterName in KNOWN_FILE_REFERENCE_PARAM_NAMES) return true
            return parameterName.endsWith("Uri", ignoreCase = true) ||
                parameterName.endsWith("Uris", ignoreCase = true)
        }

        companion object {
            private const val TOOL_ID_SEPARATOR = "_"
            private const val KEY_NAME = "name"
            private const val KEY_DESCRIPTION = "description"
            private const val KEY_PARAMETERS = "parameters"
            private const val KEY_TYPE = "type"
            private const val KEY_FORMAT = "format"
            private const val VALUE_FILE_REFERENCE = "file_reference"
            private val KNOWN_FILE_REFERENCE_PARAM_NAMES =
                setOf(
                    "wallpaperUri",
                    "imageUri",
                    "attachmentUri",
                    "ringtoneUri",
                    "profilePictureUri",
                    "audioUri",
                )
            private const val VALUE_OBJECT = "object"
            private const val KEY_PROPERTIES = "properties"
            private const val KEY_REQUIRED = "required"
            private const val VALUE_STRING = "string"
            private const val VALUE_INTEGER = "integer"
            private const val VALUE_BOOLEAN = "boolean"
            private const val VALUE_NUMBER = "number"
            private const val VALUE_ARRAY = "array"
            private const val KEY_ITEMS = "items"
            private const val KEY_ENUMS = "enums"
            private val INVALID_NAME_CHARS = Regex("[^a-zA-Z0-9_]")
            private const val MAX_NAME_LENGTH = 64
        }
    }
