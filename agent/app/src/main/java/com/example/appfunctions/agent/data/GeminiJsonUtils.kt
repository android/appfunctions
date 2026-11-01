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

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
