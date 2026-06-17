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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appfunctions.agent.MainActivity
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import com.example.appfunctions.agent.domain.AgentOrchestrator
import com.example.appfunctions.agent.domain.AgentStatus
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.appfunction.GetInstalledAppsUseCase
import com.example.appfunctions.agent.domain.chat.GetChatHistoryUseCase
import com.example.appfunctions.agent.domain.chat.ManageThreadsUseCase
import com.example.appfunctions.agent.domain.chat.SendMessageUseCase
import com.example.appfunctions.agent.domain.pendingintent.ConsumePendingIntentUseCase
import com.example.appfunctions.agent.domain.pendingintent.LaunchPendingIntentUseCase
import com.example.appfunctions.agent.domain.pendingintent.ObserveActivePendingIntentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class AgentDemoViewModel
@Inject
constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getChatHistoryUseCase: GetChatHistoryUseCase,
    private val manageThreadsUseCase: ManageThreadsUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val agentOrchestrator: AgentOrchestrator,
    private val settingsRepository: SettingsRepository,
    private val observeActivePendingIntentsUseCase: ObserveActivePendingIntentsUseCase,
    private val launchPendingIntentUseCase: LaunchPendingIntentUseCase,
    private val consumePendingIntentUseCase: ConsumePendingIntentUseCase,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val getAppFunctionsUseCase: GetAppFunctionsUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow<AgentUiState>(AgentUiState.Loading)
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())

    private var observationJob: Job? = null

    init {
        viewModelScope.launch {
            getAppFunctionsUseCase().collect { toolsMap ->
                val allTools = toolsMap.values.flatten()
                val packagesWithTools = allTools.map { it.packageName }.toSet()
                val filteredApps = withContext(Dispatchers.IO) {
                    getInstalledAppsUseCase().filter { it.packageName in packagesWithTools }
                }
                _installedApps.value = filteredApps
            }
        }

        viewModelScope.launch {
            observeActivePendingIntentsUseCase().collect { activePendingActionIds ->
                val currentState = _uiState.value
                if (currentState is AgentUiState.Loaded) {
                    _uiState.value =
                        currentState.copy(activePendingActionIds = activePendingActionIds)
                }
            }
        }

        viewModelScope.launch {
            val threads = manageThreadsUseCase.getThreads().first()
            if (threads.isEmpty()) {
                createAndSelectThread(LlmModel.DEFAULT)
            }
        }

        viewModelScope.launch {
            combine(
                manageThreadsUseCase.getThreads(),
                settingsRepository.selectedProvider,
                agentOrchestrator.status,
                savedStateHandle.getStateFlow<String?>(MainActivity.ARG_THREAD_ID, null),
                _installedApps
            ) {
                    threads,
                    provider,
                    status,
                    targetThreadId,
                    apps
                ->
                ThreadConfig(threads, provider, status, targetThreadId, apps)
            }
                .collectLatest { (threads, provider, status, targetThreadId, apps) ->
                    val currentThread =
                        threads.find { it.threadId == targetThreadId } ?: threads.firstOrNull()

                    val previousThreadId =
                        (_uiState.value as? AgentUiState.Loaded)?.currentThread?.threadId

                    if (currentThread == null) {
                        observationJob?.cancel()
                        observationJob = null
                        _uiState.value = AgentUiState.Loading
                    } else {
                        val currentLoadedState = _uiState.value as? AgentUiState.Loaded
                        _uiState.value =
                            AgentUiState.Loaded(
                                currentThread = currentThread,
                                messages = currentLoadedState?.messages ?: emptyList(),
                                status = status,
                                threads = threads,
                                activePendingActionIds =
                                currentLoadedState?.activePendingActionIds ?: emptySet(),
                                installedApps = apps
                            )

                        // Start observing messages for the current thread if not already doing so
                        if (observationJob == null || previousThreadId != currentThread.threadId) {
                            observationJob?.cancel()
                            observationJob =
                                viewModelScope.launch {
                                    launch {
                                        getChatHistoryUseCase(currentThread.threadId).collect {
                                                messages ->
                                            val currentState = _uiState.value
                                            if (currentState is AgentUiState.Loaded) {
                                                _uiState.value =
                                                    currentState.copy(messages = messages)
                                            }
                                        }
                                    }
                                    launch {
                                        agentOrchestrator.observeAndProcessMessages(
                                            currentThread.threadId
                                        )
                                    }
                                }
                        }
                    }
                }
        }
    }

    fun onEvent(event: AgentUiEvent) {
        val currentState = _uiState.value
        when (event) {
            is AgentUiEvent.OnSendMessage -> {
                if (currentState is AgentUiState.Loaded) {
                    viewModelScope.launch {
                        sendMessageUseCase(
                            threadId = currentState.currentThread.threadId,
                            role = MessageRole.USER,
                            textContent = event.text,
                            processingStatus = MessageProcessingStatus.PENDING_AGENT_RESPONSE,
                            targetPackageName = event.targetPackageName
                        )
                    }
                }
            }
            is AgentUiEvent.OnModelSelected -> {
                if (currentState is AgentUiState.Loaded) {
                    viewModelScope.launch {
                        manageThreadsUseCase.updateThreadModel(
                            currentState.currentThread.threadId,
                            event.model
                        )
                    }
                }
            }
            is AgentUiEvent.OnCreateThread -> {
                viewModelScope.launch { createAndSelectThread(event.model) }
            }
            is AgentUiEvent.OnThreadSelected -> {
                savedStateHandle[MainActivity.ARG_THREAD_ID] = event.threadId
            }
            is AgentUiEvent.OnConfirmAction -> {
                val pendingIntent = consumePendingIntentUseCase(event.pendingIntentId)
                if (pendingIntent != null) {
                    launchPendingIntentUseCase(pendingIntent)
                }
            }
        }
    }

    private suspend fun createAndSelectThread(llmModel: LlmModel) {
        val threadId = manageThreadsUseCase.createThread(llmModel)
        savedStateHandle[MainActivity.ARG_THREAD_ID] = threadId
    }
}

private data class ThreadConfig(
    val threads: List<ThreadEntity>,
    val provider: LlmProviderName,
    val status: AgentStatus,
    val targetThreadId: String?,
    val installedApps: List<AppInfo>
)
