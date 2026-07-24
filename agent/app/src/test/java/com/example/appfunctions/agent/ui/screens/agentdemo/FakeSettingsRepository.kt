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
package com.example.appfunctions.agent.ui.screens.agentdemo

import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.ServiceTier
import com.example.appfunctions.agent.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository : SettingsRepository {
    private val _geminiApiKey = MutableStateFlow<String?>(null)
    override val geminiApiKey: Flow<String?> = _geminiApiKey

    private val _selectedProvider = MutableStateFlow<LlmProviderName>(LlmProviderName.GEMINI)
    override val selectedProvider: Flow<LlmProviderName> = _selectedProvider

    private val _serviceTier = MutableStateFlow(ServiceTier.STANDARD)
    override val serviceTier: Flow<ServiceTier> = _serviceTier

    private val _pinnedApps = MutableStateFlow<Set<String>>(emptySet())
    override val pinnedApps: Flow<Set<String>> = _pinnedApps

    override suspend fun setGeminiApiKey(apiKey: String) {
        _geminiApiKey.value = apiKey
    }

    override suspend fun setSelectedProvider(provider: LlmProviderName) {
        _selectedProvider.value = provider
    }

    override suspend fun setServiceTier(tier: ServiceTier) {
        _serviceTier.value = tier
    }

    override suspend fun setAppPinned(
        packageName: String,
        pinned: Boolean,
    ) {
        if (pinned) {
            _pinnedApps.value += packageName
        } else {
            _pinnedApps.value -= packageName
        }
    }

    private val _disconnectedApps = MutableStateFlow<Set<String>>(emptySet())
    override val disconnectedApps: Flow<Set<String>> = _disconnectedApps

    override suspend fun setAppConnected(
        packageName: String,
        connected: Boolean,
    ) {
        if (connected) {
            _disconnectedApps.value -= packageName
        } else {
            _disconnectedApps.value += packageName
        }
    }
}
