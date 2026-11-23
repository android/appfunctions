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
import com.example.appfunctions.agent.domain.ActiveLiveSession
import com.example.appfunctions.agent.domain.AgentOrchestrator
import com.example.appfunctions.agent.domain.AgentStatus
import com.example.appfunctions.agent.domain.LiveAgentOrchestrator
import com.example.appfunctions.agent.domain.LlmProviderFactory
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsParams
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.appfunction.GetInstalledAppsUseCase
import com.example.appfunctions.agent.domain.chat.GetChatHistoryUseCase
import com.example.appfunctions.agent.domain.chat.ManageThreadsUseCase
import com.example.appfunctions.agent.domain.chat.SendMessageUseCase
import com.example.appfunctions.agent.domain.live.CheckAudioPermissionUseCase
import com.example.appfunctions.agent.domain.live.CheckVoiceServiceActiveUseCase
import com.example.appfunctions.agent.domain.pendingintent.ConsumePendingIntentUseCase
import com.example.appfunctions.agent.domain.pendingintent.LaunchPendingIntentUseCase
import com.example.appfunctions.agent.domain.pendingintent.ObserveActivePendingIntentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

@HiltViewModel
class AgentDemoViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val getChatHistoryUseCase: GetChatHistoryUseCase,
        private val manageThreadsUseCase: ManageThreadsUseCase,
        private val sendMessageUseCase: SendMessageUseCase,
        private val agentOrchestrator: AgentOrchestrator,
        private val liveAgentOrchestrator: LiveAgentOrchestrator,
        private val checkAudioPermissionUseCase: CheckAudioPermissionUseCase,
        private val checkVoiceServiceActiveUseCase: CheckVoiceServiceActiveUseCase,
        private val settingsRepository: SettingsRepository,
        private val llmProviderFactory: LlmProviderFactory,
        private val observeActivePendingIntentsUseCase: ObserveActivePendingIntentsUseCase,
        private val launchPendingIntentUseCase: LaunchPendingIntentUseCase,
        private val consumePendingIntentUseCase: ConsumePendingIntentUseCase,
        private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
        private val getAppFunctionsUseCase: GetAppFunctionsUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<AgentUiState>(AgentUiState.Loading)
        val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

        private val installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
        private val availableModels = MutableStateFlow<List<LlmModel>>(emptyList())
        private val liveSetupPrompt = MutableStateFlow<LiveSetupPrompt?>(null)

        private var observationJob: Job? = null
        private var createLiveThreadJob: Job? = null

        init {
            viewModelScope.launch {
                settingsRepository.geminiApiKey.collectLatest { geminiApiKey ->
                    val models =
                        LlmProviderName.entries.flatMap { providerName ->
                            val apiKey =
                                when (providerName) {
                                    LlmProviderName.GEMINI -> geminiApiKey
                                }
                            llmProviderFactory.getProvider(providerName).getAvailableModels(apiKey)
                        }
                    availableModels.value = models
                    if (models.isNotEmpty() && manageThreadsUseCase.getThreads().first().isEmpty()) {
                        createAndSelectThread(models.first())
                    }
                }
            }

            viewModelScope.launch {
                getAppFunctionsUseCase(
                    GetAppFunctionsParams(includeInternalTools = false),
                ).collect { toolsMap ->
                    val allTools = toolsMap.values.flatten()
                    val packagesWithTools = allTools.map { it.packageName }.toSet()
                    val filteredApps =
                        withContext(Dispatchers.IO) {
                            getInstalledAppsUseCase().filter { it.packageName in packagesWithTools }
                        }
                    installedApps.value = filteredApps
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
                combine(
                    combine(
                        manageThreadsUseCase.getThreads(),
                        settingsRepository.selectedProvider,
                        agentOrchestrator.status,
                        liveAgentOrchestrator.activeSession,
                    ) { threads, provider, status, activeLiveSession ->
                        AgentStatusSnapshot(threads, provider, status, activeLiveSession)
                    },
                    savedStateHandle.getStateFlow<String?>(MainActivity.ARG_THREAD_ID, null),
                    installedApps,
                    availableModels,
                    liveSetupPrompt,
                ) {
                        snapshot,
                        targetThreadId,
                        apps,
                        models,
                        setupPrompt,
                    ->
                    ThreadConfig(
                        threads = snapshot.threads,
                        provider = snapshot.provider,
                        status = snapshot.status,
                        activeLiveSession = snapshot.activeLiveSession,
                        targetThreadId = targetThreadId,
                        installedApps = apps,
                        availableModels = models,
                        liveSetupPrompt = setupPrompt,
                    )
                }
                    .collectLatest { config ->
                        val currentThread =
                            config.threads.find { it.threadId == config.targetThreadId }
                                ?: config.threads.firstOrNull()

                        val previousThreadId =
                            (_uiState.value as? AgentUiState.Loaded)?.currentThread?.threadId

                        val activeLiveSession = config.activeLiveSession
                        val liveStatusForCurrentThread =
                            if (
                                currentThread != null &&
                                activeLiveSession?.threadId == currentThread.threadId
                            ) {
                                activeLiveSession.status
                            } else {
                                null
                            }
                        val isAnotherThreadLiveBusy =
                            activeLiveSession != null &&
                                activeLiveSession.threadId != currentThread?.threadId

                        val currentLoadedState = _uiState.value as? AgentUiState.Loaded
                        val isCurrentThreadLive =
                            currentThread != null &&
                                currentThread.llmModel ==
                                llmProviderFactory
                                    .getLiveProvider(currentThread.llmModel.providerName)
                                    .liveModel
                        _uiState.value =
                            AgentUiState.Loaded(
                                currentThread = currentThread,
                                isCurrentThreadLive = isCurrentThreadLive,
                                messages =
                                    if (currentThread != null && currentThread.threadId == previousThreadId) {
                                        currentLoadedState?.messages ?: emptyList()
                                    } else {
                                        emptyList()
                                    },
                                status = config.status,
                                liveStatus = liveStatusForCurrentThread,
                                isAnotherThreadLiveBusy = isAnotherThreadLiveBusy,
                                liveSetupPrompt = config.liveSetupPrompt,
                                threads = config.threads,
                                activePendingActionIds =
                                    currentLoadedState?.activePendingActionIds ?: emptySet(),
                                installedApps = config.installedApps,
                                availableModels = config.availableModels,
                            )

                        if (currentThread == null) {
                            observationJob?.cancel()
                            observationJob = null
                        } else if (observationJob == null || previousThreadId != currentThread.threadId) {
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
                                            currentThread.threadId,
                                        )
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
                    val currentThread =
                        (currentState as? AgentUiState.Loaded)
                            ?.takeUnless { it.isCurrentThreadLive }
                            ?.currentThread
                    if (currentThread != null) {
                        viewModelScope.launch {
                            sendMessageUseCase(
                                threadId = currentThread.threadId,
                                role = MessageRole.USER,
                                textContent = event.text,
                                processingStatus = MessageProcessingStatus.PENDING_AGENT_RESPONSE,
                                targetPackageName = event.targetPackageName,
                            )
                        }
                    }
                }
                is AgentUiEvent.OnModelSelected -> {
                    if (currentState is AgentUiState.Loaded) {
                        viewModelScope.launch {
                            val currentThread = currentState.currentThread
                            if (currentThread != null && !currentState.isCurrentThreadLive) {
                                manageThreadsUseCase.updateThreadModel(
                                    currentThread.threadId,
                                    event.model,
                                )
                            } else {
                                createAndSelectThread(event.model)
                            }
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
                is AgentUiEvent.OnStartLiveSession -> {
                    tryStartLiveSession(event.targetPackageName)
                }
                is AgentUiEvent.OnLiveSessionLaunchResult -> {
                    val pendingThreadId =
                        savedStateHandle.get<String?>(KEY_PENDING_LAUNCH_THREAD_ID)
                    val previousThreadId =
                        savedStateHandle.get<String?>(KEY_PREVIOUS_THREAD_ID)
                    clearPendingLaunchThread()
                    if (event.isLaunched) {
                        liveSetupPrompt.value = null
                    } else {
                        savedStateHandle[MainActivity.ARG_THREAD_ID] = previousThreadId
                        if (pendingThreadId != null) {
                            viewModelScope.launch {
                                manageThreadsUseCase.deleteThread(pendingThreadId)
                            }
                        }
                        liveSetupPrompt.value = LiveSetupPrompt.AssistLaunchFailed
                    }
                }
                is AgentUiEvent.OnAudioPermissionResult -> {
                    val pendingTarget =
                        savedStateHandle.get<String?>(KEY_PENDING_LIVE_TARGET_PACKAGE)
                    if (event.isGranted) {
                        liveSetupPrompt.value = null
                        tryStartLiveSession(pendingTarget)
                    } else {
                        clearPendingLiveTarget()
                        liveSetupPrompt.value = LiveSetupPrompt.AudioPermissionDenied
                    }
                }
                AgentUiEvent.OnAssistantSettingsReturned -> {
                    val pendingTarget =
                        savedStateHandle.get<String?>(KEY_PENDING_LIVE_TARGET_PACKAGE)
                    clearPendingLiveTarget()
                    if (checkVoiceServiceActiveUseCase()) {
                        launchNewLiveSession(pendingTarget)
                    } else {
                        liveSetupPrompt.value = LiveSetupPrompt.AssistantNotSelected
                    }
                }
                AgentUiEvent.OnAssistantSettingsUnavailable -> {
                    clearPendingLiveTarget()
                    liveSetupPrompt.value = LiveSetupPrompt.AssistantSettingsUnavailable
                }
                AgentUiEvent.OnDismissLiveSetupPrompt -> {
                    clearPendingLiveTarget()
                    liveSetupPrompt.value = null
                }
            }
        }

        private fun tryStartLiveSession(targetPackageName: String?) {
            if (
                liveAgentOrchestrator.activeSession.value != null ||
                agentOrchestrator.status.value != AgentStatus.Idle
            ) {
                return
            }
            if (!checkAudioPermissionUseCase()) {
                savedStateHandle[KEY_PENDING_LIVE_TARGET_PACKAGE] = targetPackageName
                liveSetupPrompt.value = LiveSetupPrompt.RequestAudioPermission(targetPackageName)
                return
            }
            if (!checkVoiceServiceActiveUseCase()) {
                savedStateHandle[KEY_PENDING_LIVE_TARGET_PACKAGE] = targetPackageName
                liveSetupPrompt.value = LiveSetupPrompt.SelectDigitalAssistant(targetPackageName)
                return
            }
            clearPendingLiveTarget()
            launchNewLiveSession(targetPackageName)
        }

        private fun launchNewLiveSession(targetPackageName: String?) {
            if (
                createLiveThreadJob?.isActive == true ||
                savedStateHandle.get<String?>(KEY_PENDING_LAUNCH_THREAD_ID) != null
            ) {
                return
            }
            createLiveThreadJob =
                viewModelScope.launch {
                    val previousThreadId =
                        (_uiState.value as? AgentUiState.Loaded)?.currentThread?.threadId
                    val selectedProvider = settingsRepository.selectedProvider.first()
                    val liveModel = llmProviderFactory.getLiveProvider(selectedProvider).liveModel
                    val liveThreadId = manageThreadsUseCase.createThread(liveModel)
                    savedStateHandle[KEY_PENDING_LAUNCH_THREAD_ID] = liveThreadId
                    savedStateHandle[KEY_PREVIOUS_THREAD_ID] = previousThreadId
                    savedStateHandle[MainActivity.ARG_THREAD_ID] = liveThreadId
                    liveSetupPrompt.value =
                        LiveSetupPrompt.LaunchAssistSession(
                            threadId = liveThreadId,
                            targetPackageName = targetPackageName,
                        )
                }
        }

        private fun clearPendingLiveTarget() {
            savedStateHandle.remove<String?>(KEY_PENDING_LIVE_TARGET_PACKAGE)
        }

        private fun clearPendingLaunchThread() {
            savedStateHandle.remove<String?>(KEY_PENDING_LAUNCH_THREAD_ID)
            savedStateHandle.remove<String?>(KEY_PREVIOUS_THREAD_ID)
        }

        private suspend fun createAndSelectThread(llmModel: LlmModel) {
            val threadId = manageThreadsUseCase.createThread(llmModel)
            savedStateHandle[MainActivity.ARG_THREAD_ID] = threadId
        }

        private companion object {
            const val KEY_PENDING_LIVE_TARGET_PACKAGE = "pending_live_target_package"
            const val KEY_PENDING_LAUNCH_THREAD_ID = "pending_launch_live_thread_id"
            const val KEY_PREVIOUS_THREAD_ID = "previous_thread_id_before_live"
        }
    }

private data class AgentStatusSnapshot(
    val threads: List<ThreadEntity>,
    val provider: LlmProviderName,
    val status: AgentStatus,
    val activeLiveSession: ActiveLiveSession?,
)

private data class ThreadConfig(
    val threads: List<ThreadEntity>,
    val provider: LlmProviderName,
    val status: AgentStatus,
    val activeLiveSession: ActiveLiveSession?,
    val targetThreadId: String?,
    val installedApps: List<AppInfo>,
    val availableModels: List<LlmModel>,
    val liveSetupPrompt: LiveSetupPrompt?,
)
