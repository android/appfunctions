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

import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Enum representing the supported LLM providers. */
@Serializable
enum class LlmProviderName(val displayName: String) {
    GEMINI("Gemini"),
}

/** Represents an LLM model offered by a specific [LlmProviderName]. */
@Serializable
data class LlmModel(
    val providerName: LlmProviderName,
    val modelName: String,
)

class LlmModelConverter {
    private val dbJson =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @TypeConverter
    fun fromLlmModel(model: LlmModel): String = dbJson.encodeToString(model)

    @TypeConverter
    fun toLlmModel(jsonString: String): LlmModel = dbJson.decodeFromString(jsonString)
}
