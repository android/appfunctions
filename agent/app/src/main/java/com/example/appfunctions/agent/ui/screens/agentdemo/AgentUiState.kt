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
import com.example.appfunctions.agent.data.db.entities.MessageEntity
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import com.example.appfunctions.agent.domain.AgentStatus
import com.example.appfunctions.agent.domain.appfunction.AppInfo

/** Represents the UI state for the Agent Demo screen. */
sealed class AgentUiState {
    object Loading : AgentUiState()

    data class Loaded(
        val currentThread: ThreadEntity,
        val messages: List<MessageEntity> = emptyList(),
        val status: AgentStatus = AgentStatus.Idle,
        val threads: List<ThreadEntity> = emptyList(),
        val activePendingActionIds: Set<String> = emptySet(),
        val installedApps: List<AppInfo> = emptyList(),
    ) : AgentUiState()
}

/** Represents UI events for the Agent Demo screen. */
sealed class AgentUiEvent {
    data class OnSendMessage(
        val text: String,
        val targetPackageName: String? = null,
    ) : AgentUiEvent()

    data class OnModelSelected(val model: LlmModel) : AgentUiEvent()

    data class OnCreateThread(val model: LlmModel) : AgentUiEvent()

    data class OnThreadSelected(val threadId: String) : AgentUiEvent()

    data class OnConfirmAction(val pendingIntentId: String) : AgentUiEvent()
}
