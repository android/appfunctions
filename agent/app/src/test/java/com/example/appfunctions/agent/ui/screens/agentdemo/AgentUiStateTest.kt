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
    private val proModel = LlmModel(LlmProviderName.GEMINI, "gemini-3.1-pro-preview")
    private val flashModel = LlmModel(LlmProviderName.GEMINI, "gemini-flash-latest")
    private val ttsModel = LlmModel(LlmProviderName.GEMINI, "gemini-2.5-flash-preview-tts")
    private val liveModel = LlmModel(LlmProviderName.GEMINI, "gemini-3.8-live")

    // The default model isn't the first available model, so that the tests tell the two apart.
    private val availableModels = listOf(proModel, flashModel)
    private val defaultModel = flashModel

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
                availableModels = availableModels,
                defaultModel = defaultModel,
            )

        assertEquals(proModel, uiState.newThreadModel)
    }

    @Test
    fun newThreadModel_currentModelNotOffered_returnsDefaultModel() {
        val uiState =
            AgentUiState.Loaded(
                currentThread = threadWithModel(ttsModel),
                availableModels = availableModels,
                defaultModel = defaultModel,
            )

        assertEquals(defaultModel, uiState.newThreadModel)
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
    fun newThreadModel_noCurrentThread_returnsDefaultModel() {
        val uiState =
            AgentUiState.Loaded(
                currentThread = null,
                availableModels = availableModels,
                defaultModel = defaultModel,
            )

        assertEquals(defaultModel, uiState.newThreadModel)
    }

    @Test
    fun newThreadModel_noCurrentThreadAndNoModels_returnsNull() {
        val uiState = AgentUiState.Loaded(currentThread = null, availableModels = emptyList())

        assertNull(uiState.newThreadModel)
    }

    @Test
    fun newThreadModel_currentThreadLive_returnsDefaultModel() {
        val uiState =
            AgentUiState.Loaded(
                currentThread = threadWithModel(liveModel),
                isCurrentThreadLive = true,
                availableModels = listOf(liveModel) + availableModels,
                defaultModel = defaultModel,
            )

        assertEquals(defaultModel, uiState.newThreadModel)
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
