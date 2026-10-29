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
package com.example.appfunctions.agent.domain

import com.example.appfunctions.agent.data.db.entities.MessageAttachment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Converts a [JsonElement] recursively into standard Kotlin primitive, [Map], or [List] values. */
internal fun JsonElement.toPrimitive(): Any? {
    return when (this) {
        JsonNull -> null
        is JsonPrimitive -> {
            if (this.isString) return this.content
            return this.content.toBooleanStrictOrNull()
                ?: this.content.toLongOrNull()
                ?: this.content.toDoubleOrNull()
        }
        is JsonObject -> this.mapValues { it.value.toPrimitive() }
        is JsonArray -> this.map { it.toPrimitive() }
    }
}

/** Converts a nullable JSON arguments object into a Kotlin arguments map. */
internal fun JsonObject?.toArgumentsMap(): Map<String, Any?> {
    if (this == null) return emptyMap()
    return mapValues { (_, value) -> value.toPrimitive() }
}

/** Filters out null values from a tool-call [arguments] map. */
internal fun nonNullArguments(arguments: Map<String, Any?>): Map<String, Any> =
    buildMap {
        for ((key, value) in arguments) {
            if (value != null) {
                put(key, value)
            }
        }
    }

/** Extracts an optional [MessageAttachment] (`imageUri` and `mimeType`) from a tool result JSON string. */
internal fun extractMessageAttachment(jsonString: String): MessageAttachment? {
    return try {
        val json = Json.parseToJsonElement(jsonString) as? JsonObject ?: return null
        val uri = json["imageUri"]?.jsonPrimitive?.contentOrNull
        if (!uri.isNullOrBlank()) {
            val mimeType =
                json["mimeType"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: "image/png"
            MessageAttachment(uri = uri, mimeType = mimeType)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}
