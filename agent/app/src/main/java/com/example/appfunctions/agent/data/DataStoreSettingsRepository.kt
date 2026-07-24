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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.appfunctions.agent.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Implementation of [SettingsRepository] using Jetpack DataStore. */
class DataStoreSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        // TODO: Make sure the API keys are not leaked in the I/O event.
        private object PreferencesKeys {
            val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
            val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
            val SERVICE_TIER = stringPreferencesKey("service_tier")
            val PINNED_APPS = stringSetPreferencesKey("pinned_apps")
            val DISCONNECTED_APPS = stringSetPreferencesKey("disconnected_apps")
        }

        override val geminiApiKey: Flow<String?> =
            if (BuildConfig.IS_RETAIL) {
                flowOf(BuildConfig.GEMINI_API_KEY)
            } else {
                dataStore.data.map { preferences -> preferences[PreferencesKeys.GEMINI_API_KEY] }
            }

        override val selectedProvider: Flow<LlmProviderName> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.SELECTED_PROVIDER]?.let { storedValue ->
                    when (storedValue.uppercase()) {
                        "GEMINI" -> LlmProviderName.GEMINI
                        else -> LlmProviderName.GEMINI
                    }
                } ?: LlmProviderName.GEMINI
            }

        override val serviceTier: Flow<ServiceTier> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.SERVICE_TIER]?.let { storedValue ->
                    ServiceTier.entries.find { it.name.equals(storedValue, ignoreCase = true) }
                } ?: ServiceTier.PRIORITY
            }

        override val pinnedApps: Flow<Set<String>> =
            dataStore.data.map { preferences -> preferences[PreferencesKeys.PINNED_APPS] ?: emptySet() }

        override val disconnectedApps: Flow<Set<String>> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.DISCONNECTED_APPS] ?: emptySet()
            }

        override suspend fun setGeminiApiKey(apiKey: String) {
            dataStore.edit { preferences -> preferences[PreferencesKeys.GEMINI_API_KEY] = apiKey }
        }

        override suspend fun setSelectedProvider(provider: LlmProviderName) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.SELECTED_PROVIDER] = provider.name
            }
        }

        override suspend fun setServiceTier(tier: ServiceTier) {
            dataStore.edit { preferences -> preferences[PreferencesKeys.SERVICE_TIER] = tier.name }
        }

        override suspend fun setAppPinned(
            packageName: String,
            pinned: Boolean,
        ) {
            dataStore.edit { preferences ->
                val currentPinned = preferences[PreferencesKeys.PINNED_APPS] ?: emptySet()
                val newPinned =
                    if (pinned) {
                        currentPinned + packageName
                    } else {
                        currentPinned - packageName
                    }
                preferences[PreferencesKeys.PINNED_APPS] = newPinned
            }
        }

        override suspend fun setAppConnected(
            packageName: String,
            connected: Boolean,
        ) {
            dataStore.edit { preferences ->
                val currentDisconnected = preferences[PreferencesKeys.DISCONNECTED_APPS] ?: emptySet()
                val newDisconnected =
                    if (connected) {
                        currentDisconnected - packageName
                    } else {
                        currentDisconnected + packageName
                    }
                preferences[PreferencesKeys.DISCONNECTED_APPS] = newDisconnected
            }
        }
    }
