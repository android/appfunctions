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

/**
 * Processing tier requested for Gemini API calls via the `service_tier` request field.
 *
 * [STANDARD] omits the field, which selects the API default. [PRIORITY] requests lower-latency,
 * higher-priority processing; it requires a Tier 2+ billing account and is billed at a higher
 * rate. The API may silently downgrade a priority request back to standard when the priority
 * quota is exhausted, reported via the `service_tier` field in the response body.
 */
enum class ServiceTier(
    /** Value written to the `service_tier` request field, or null to omit it (API default). */
    val apiValue: String?,
) {
    STANDARD(null),
    PRIORITY("priority"),
}
