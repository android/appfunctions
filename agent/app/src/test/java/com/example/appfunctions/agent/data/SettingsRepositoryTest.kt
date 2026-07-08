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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.appfunctions.agent.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Unit tests for [DataStoreSettingsRepository] using a temporary file. */
class SettingsRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        dataStore =
            PreferenceDataStoreFactory.create(
                produceFile = { temporaryFolder.newFile("test_settings.preferences_pb") },
            )
        repository = DataStoreSettingsRepository(dataStore)
    }

    @Test
    fun setGeminiApiKey_persistsValue() =
        runTest {
            repository.setGeminiApiKey("gemini_key")
            val value = repository.geminiApiKey.first()
            if (BuildConfig.IS_RETAIL) {
                assertEquals(BuildConfig.GEMINI_API_KEY, value)
            } else {
                assertEquals("gemini_key", value)
            }
        }

    @Test
    fun setSelectedProvider_persistsValue() =
        runTest {
            repository.setSelectedProvider(LlmProviderName.GEMINI)
            val value = repository.selectedProvider.first()
            assertEquals(LlmProviderName.GEMINI, value)
        }

    @Test
    fun serviceTier_defaultsToPriority() =
        runTest {
            val value = repository.serviceTier.first()
            assertEquals(ServiceTier.PRIORITY, value)
        }

    @Test
    fun setServiceTier_persistsValue() =
        runTest {
            repository.setServiceTier(ServiceTier.PRIORITY)
            val value = repository.serviceTier.first()
            assertEquals(ServiceTier.PRIORITY, value)
        }
}
