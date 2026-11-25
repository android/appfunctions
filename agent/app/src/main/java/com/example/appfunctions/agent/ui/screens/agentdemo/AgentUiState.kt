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
import com.example.appfunctions.agent.domain.LiveAgentStatus
import com.example.appfunctions.agent.domain.appfunction.AppInfo

/** Represents the UI state for the Agent Demo screen. */
sealed class AgentUiState {
    object Loading : AgentUiState()

    data class Loaded(
        val currentThread: ThreadEntity? = null,
        val isCurrentThreadLive: Boolean = false,
        val messages: List<MessageEntity> = emptyList(),
        val status: AgentStatus = AgentStatus.Idle,
        val liveStatus: LiveAgentStatus? = null,
        val isAnotherThreadLiveBusy: Boolean = false,
        val liveSetupPrompt: LiveSetupPrompt? = null,
        val threads: List<ThreadEntity> = emptyList(),
        val activePendingActionIds: Set<String> = emptySet(),
        val installedApps: List<AppInfo> = emptyList(),
        val availableModels: List<LlmModel> = emptyList(),
        /** The model for new threads among [availableModels], as its provider resolves it. */
        val defaultModel: LlmModel? = null,
    ) : AgentUiState() {
        /**
         * The model for a new thread: the current thread's model while [availableModels] offers it,
         * otherwise [defaultModel]. While no models are loaded, new threads keep the current
         * thread's model. A Live thread's model is never copied.
         */
        val newThreadModel: LlmModel?
            get() =
                currentThread
                    ?.llmModel
                    ?.takeUnless { isCurrentThreadLive }
                    ?.takeIf { availableModels.isEmpty() || it in availableModels }
                    ?: defaultModel
    }
}

/** Setup prompt or launch request emitted when starting a Live voice session. */
sealed class LiveSetupPrompt {
    data class RequestAudioPermission(val targetPackageName: String? = null) : LiveSetupPrompt()

    data class SelectDigitalAssistant(val targetPackageName: String? = null) : LiveSetupPrompt()

    data class LaunchAssistSession(
        val threadId: String,
        val targetPackageName: String? = null,
    ) : LiveSetupPrompt()

    data object AudioPermissionDenied : LiveSetupPrompt()

    data object AssistantNotSelected : LiveSetupPrompt()

    data object AssistantSettingsUnavailable : LiveSetupPrompt()

    data object AssistLaunchFailed : LiveSetupPrompt()
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

    data class OnStartLiveSession(val targetPackageName: String? = null) : AgentUiEvent()

    data class OnLiveSessionLaunchResult(val isLaunched: Boolean) : AgentUiEvent()

    data class OnAudioPermissionResult(val isGranted: Boolean) : AgentUiEvent()

    data object OnAssistantSettingsReturned : AgentUiEvent()

    data object OnAssistantSettingsUnavailable : AgentUiEvent()

    data object OnDismissLiveSetupPrompt : AgentUiEvent()
}
