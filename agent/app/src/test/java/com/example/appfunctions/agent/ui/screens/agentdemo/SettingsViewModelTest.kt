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

import androidx.compose.runtime.snapshots.Snapshot
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.ServiceTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private lateinit var viewModel: SettingsViewModel
    private lateinit var fakeRepository: FakeSettingsRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeSettingsRepository()
        viewModel = SettingsViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_reflectsRepositoryValues() =
        runTest {
            fakeRepository.setGeminiApiKey("gemini_key")
            fakeRepository.setSelectedProvider(LlmProviderName.GEMINI)

            // Recreate ViewModel to trigger init load
            viewModel = SettingsViewModel(fakeRepository)

            val job = backgroundScope.launch { viewModel.uiState.collect {} }
            testScheduler.advanceUntilIdle()

            assertEquals("gemini_key", viewModel.geminiApiKeyState.text.toString())

            val uiState = viewModel.uiState.value
            assertEquals(LlmProviderName.GEMINI, uiState.selectedProvider)

            job.cancel()
        }

    @Test
    fun updateGeminiApiKey_updatesRepository() =
        runTest {
            val job = backgroundScope.launch { viewModel.uiState.collect {} }

            Snapshot.withMutableSnapshot {
                viewModel.geminiApiKeyState.edit { replace(0, length, "new_gemini_key") }
            }
            Snapshot.sendApplyNotifications()
            yield()

            viewModel.saveSettings()

            testScheduler.advanceUntilIdle()

            assertEquals("new_gemini_key", fakeRepository.geminiApiKey.first())

            job.cancel()
        }

    @Test
    fun setSelectedProvider_updatesRepository() =
        runTest {
            val job = backgroundScope.launch { viewModel.uiState.collect {} }

            viewModel.setSelectedProvider(LlmProviderName.GEMINI)

            testScheduler.advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals(LlmProviderName.GEMINI, uiState.selectedProvider)

            job.cancel()
        }

    @Test
    fun setServiceTier_updatesRepository() =
        runTest {
            val job = backgroundScope.launch { viewModel.uiState.collect {} }

            viewModel.setServiceTier(ServiceTier.PRIORITY)

            testScheduler.advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals(ServiceTier.PRIORITY, uiState.serviceTier)

            job.cancel()
        }
}
