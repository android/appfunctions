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

import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentUiStateTest {
    private val flashLiteModel = LlmModel(LlmProviderName.GEMINI, "gemini-3.1-flash-lite")
    private val proModel = LlmModel(LlmProviderName.GEMINI, "gemini-3.1-pro-preview")
    private val ttsModel = LlmModel(LlmProviderName.GEMINI, "gemini-2.5-flash-preview-tts")
    private val liveModel = LlmModel(LlmProviderName.GEMINI, "gemini-3.8-live")

    private fun threadWithModel(model: LlmModel): ThreadEntity =
        ThreadEntity(
            threadId = "thread-1",
            createdAt = 1_000L,
            llmModel = model,
        )

    @Test
    fun newThreadModel_currentModelOffered_returnsCurrentModel() {
        val uiState =
            AgentUiState.Loaded(
                currentThread = threadWithModel(proModel),
                availableModels = listOf(flashLiteModel, proModel),
            )

        assertEquals(proModel, uiState.newThreadModel)
    }

    @Test
    fun newThreadModel_currentModelNotOffered_returnsFirstAvailableModel() {
        val uiState =
            AgentUiState.Loaded(
                currentThread = threadWithModel(ttsModel),
                availableModels = listOf(flashLiteModel, proModel),
            )

        assertEquals(flashLiteModel, uiState.newThreadModel)
    }

    @Test
    fun newThreadModel_noModelsLoaded_returnsCurrentModel() {
        val uiState =
            AgentUiState.Loaded(
                currentThread = threadWithModel(proModel),
                availableModels = emptyList(),
            )

        assertEquals(proModel, uiState.newThreadModel)
    }

    @Test
    fun newThreadModel_noCurrentThread_returnsFirstAvailableModel() {
        val uiState =
            AgentUiState.Loaded(
                currentThread = null,
                availableModels = listOf(flashLiteModel, proModel),
            )

        assertEquals(flashLiteModel, uiState.newThreadModel)
    }

    @Test
    fun newThreadModel_noCurrentThreadAndNoModels_returnsNull() {
        val uiState = AgentUiState.Loaded(currentThread = null, availableModels = emptyList())

        assertNull(uiState.newThreadModel)
    }

    @Test
    fun newThreadModel_currentThreadLive_returnsFirstAvailableModel() {
        val uiState =
            AgentUiState.Loaded(
                currentThread = threadWithModel(liveModel),
                isCurrentThreadLive = true,
                availableModels = listOf(flashLiteModel, liveModel),
            )

        assertEquals(flashLiteModel, uiState.newThreadModel)
    }

    @Test
    fun newThreadModel_currentThreadLiveAndNoModels_returnsNull() {
        val uiState =
            AgentUiState.Loaded(
                currentThread = threadWithModel(liveModel),
                isCurrentThreadLive = true,
                availableModels = emptyList(),
            )

        assertNull(uiState.newThreadModel)
    }
}
