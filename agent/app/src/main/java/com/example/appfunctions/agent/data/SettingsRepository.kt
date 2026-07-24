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

import kotlinx.coroutines.flow.Flow

/** Repository to manage user settings like API keys and provider selection. */
interface SettingsRepository {
    /** Flow of the Gemini API key. */
    val geminiApiKey: Flow<String?>

    /** Flow of the selected LLM provider. */
    val selectedProvider: Flow<LlmProviderName>

    /** Sets the Gemini API key. */
    suspend fun setGeminiApiKey(apiKey: String)

    /** Sets the selected LLM provider. */
    suspend fun setSelectedProvider(provider: LlmProviderName)

    /** Flow of the service tier requested for LLM API calls. */
    val serviceTier: Flow<ServiceTier>

    /** Sets the service tier requested for LLM API calls. */
    suspend fun setServiceTier(tier: ServiceTier)

    /** Flow of pinned app package names. */
    val pinnedApps: Flow<Set<String>>

    /** Sets the pin status of an app. */
    suspend fun setAppPinned(
        packageName: String,
        pinned: Boolean,
    )

    /** Flow of disconnected app package names. */
    val disconnectedApps: Flow<Set<String>>

    /** Sets the connection status of an app. */
    suspend fun setAppConnected(
        packageName: String,
        connected: Boolean,
    )
}
