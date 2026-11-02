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

import androidx.appfunctions.metadata.AppFunctionMetadata
import com.example.appfunctions.agent.domain.appfunction.ToolConverter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ToolConverter] for Gemini Live (`BidiGenerateContent`) `FunctionDeclaration`
 * schemas, normalizing OpenAPI schema fields produced by [GeminiToolConverter].
 */
@Singleton
class GeminiLiveToolConverter
    @Inject
    constructor(
        private val geminiToolConverter: GeminiToolConverter,
    ) : ToolConverter<JsonObject> {
        override fun convert(tool: AppFunctionMetadata): JsonObject {
            val baseSchema = geminiToolConverter.convert(tool)
            return normalizeForLiveApi(baseSchema)
        }

        override fun getToolName(tool: AppFunctionMetadata): String {
            return geminiToolConverter.getToolName(tool)
        }

        private fun normalizeForLiveApi(obj: JsonObject): JsonObject {
            val typeValue = (obj[KEY_TYPE] as? JsonPrimitive)?.content
            return buildJsonObject {
                for ((key, value) in obj) {
                    when {
                        key == KEY_FORMAT &&
                            (value as? JsonPrimitive)?.content == VALUE_FILE_REFERENCE -> {
                            // Omit Interactions-API-specific file_reference format in BidiGenerateContent
                        }
                        key == KEY_ENUMS -> {
                            if (typeValue == VALUE_STRING) {
                                put(KEY_ENUM_LIVE, value)
                            }
                        }
                        value is JsonObject -> {
                            put(key, normalizeForLiveApi(value))
                        }
                        else -> {
                            put(key, value)
                        }
                    }
                }
            }
        }

        companion object {
            private const val KEY_TYPE = "type"
            private const val KEY_FORMAT = "format"
            private const val VALUE_FILE_REFERENCE = "file_reference"
            private const val VALUE_STRING = "string"
            private const val KEY_ENUMS = "enums"
            private const val KEY_ENUM_LIVE = "enum"
        }
    }
