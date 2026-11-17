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

/** Filters out entries with `null` values from a tool-call arguments [Map]. */
internal fun Map<String, Any?>.filterNotNullValues(): Map<String, Any> =
    buildMap {
        for ((key, value) in this@filterNotNullValues) {
            if (value != null) {
                put(key, value)
            }
        }
    }

/** Turns e.g. `"com.example.notes.NoteFunctions#createNote"` into `"createNote"`. */
internal fun String.toShortFunctionName(): String = substringAfterLast('#').substringAfterLast('.')
