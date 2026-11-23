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
package com.example.appfunctions.agent.ui.voice

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.domain.LiveAgentOrchestrator
import com.example.appfunctions.agent.domain.LiveAgentStatus
import com.example.appfunctions.agent.domain.LlmProviderFactory
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.domain.appfunction.GetInstalledAppsUseCase
import com.example.appfunctions.agent.domain.chat.ManageThreadsUseCase
import com.example.appfunctions.agent.domain.live.CheckAudioPermissionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Session-scoped state holder for the floating Live voice session overlay.
 *
 * Owns the [Job] collecting [LiveAgentOrchestrator.runSession] from `onShow` to `onHide`, observes
 * [LiveAgentOrchestrator.activeSession] as the single source of truth for active session status,
 * and exposes a single read-only [uiState] flow and [onEvent] entry point.
 */
class LiveVoiceOverlayStateHolder
    @VisibleForTesting
    internal constructor(
        private val liveAgentOrchestrator: LiveAgentOrchestrator,
        private val manageThreadsUseCase: ManageThreadsUseCase,
        private val settingsRepository: SettingsRepository,
        private val llmProviderFactory: LlmProviderFactory,
        private val checkAudioPermissionUseCase: CheckAudioPermissionUseCase,
        private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
        mainDispatcher: CoroutineDispatcher,
        ioDispatcher: CoroutineDispatcher,
    ) : AutoCloseable {
        @Inject
        constructor(
            liveAgentOrchestrator: LiveAgentOrchestrator,
            manageThreadsUseCase: ManageThreadsUseCase,
            settingsRepository: SettingsRepository,
            llmProviderFactory: LlmProviderFactory,
            checkAudioPermissionUseCase: CheckAudioPermissionUseCase,
            getInstalledAppsUseCase: GetInstalledAppsUseCase,
        ) : this(
            liveAgentOrchestrator = liveAgentOrchestrator,
            manageThreadsUseCase = manageThreadsUseCase,
            settingsRepository = settingsRepository,
            llmProviderFactory = llmProviderFactory,
            checkAudioPermissionUseCase = checkAudioPermissionUseCase,
            getInstalledAppsUseCase = getInstalledAppsUseCase,
            mainDispatcher = Dispatchers.Main.immediate,
            ioDispatcher = Dispatchers.IO,
        )

        private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
        private val isVisible = MutableStateFlow(false)
        private val localSessionState =
            MutableStateFlow<LocalOverlaySessionState>(LocalOverlaySessionState.Idle)
        private val installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
        private var sessionJob: Job? = null

        /** Read-only UI state observed by `AgentVoiceInteractionSession` and [LiveVoiceSessionOverlay]. */
        val uiState: StateFlow<LiveOverlayUiState> =
            combine(
                isVisible,
                localSessionState,
                liveAgentOrchestrator.activeSession,
                installedApps,
            ) { visible, localState, activeSession, apps ->
                val resolvedThreadId: String?
                val status: LiveOverlayStatus?
                when (localState) {
                    LocalOverlaySessionState.Idle -> {
                        resolvedThreadId = activeSession?.threadId
                        status = activeSession?.status?.toOverlayStatus()
                    }
                    is LocalOverlaySessionState.Preparing -> {
                        resolvedThreadId = localState.threadId
                        status = LiveOverlayStatus.Connecting
                    }
                    is LocalOverlaySessionState.Running -> {
                        resolvedThreadId = activeSession?.threadId ?: localState.threadId
                        status =
                            activeSession?.status?.toOverlayStatus()
                                ?: LiveOverlayStatus.Connecting
                    }
                    is LocalOverlaySessionState.Failed -> {
                        resolvedThreadId = localState.threadId
                        status = localState.error
                    }
                }

                val invokingApp =
                    (status as? LiveOverlayStatus.InvokingTool)
                        ?.packageName
                        ?.takeIf { it.isNotBlank() }
                        ?.let { pkg -> apps.find { it.packageName == pkg } }

                val isActiveConversation =
                    status is LiveOverlayStatus.Connecting ||
                        status is LiveOverlayStatus.Listening ||
                        status is LiveOverlayStatus.InvokingTool

                LiveOverlayUiState(
                    isVisible = visible,
                    threadId = resolvedThreadId,
                    status = status,
                    invokingApp = invokingApp,
                    keepVisibleOnSystemDialogsClose = visible && isActiveConversation,
                )
            }
                .stateIn(
                    scope = scope,
                    started = SharingStarted.Eagerly,
                    initialValue = LiveOverlayUiState(),
                )

        init {
            scope.launch {
                installedApps.value = withContext(ioDispatcher) { getInstalledAppsUseCase() }
            }
        }

        /** Handles UI and session-window events in unidirectional data flow. */
        fun onEvent(event: LiveOverlayUiEvent) {
            when (event) {
                is LiveOverlayUiEvent.OnSessionShown -> {
                    isVisible.value = true
                    val shouldKeepRunning =
                        event.threadId == null &&
                            event.targetPackageName == null &&
                            sessionJob?.isActive == true
                    if (!shouldKeepRunning) {
                        launchSession(
                            requestedThreadId = event.threadId,
                            requestedTargetPackageName = event.targetPackageName,
                        )
                    }
                }
                LiveOverlayUiEvent.OnSessionHidden,
                LiveOverlayUiEvent.OnStopClicked,
                -> stopAndHide()
                LiveOverlayUiEvent.OnRetryClicked -> retrySession()
            }
        }

        private fun stopAndHide() {
            sessionJob?.cancel()
            sessionJob = null
            localSessionState.value = LocalOverlaySessionState.Idle
            isVisible.value = false
        }

        private fun retrySession() {
            val current = localSessionState.value
            val retryThreadId =
                when (current) {
                    LocalOverlaySessionState.Idle -> null
                    is LocalOverlaySessionState.Preparing -> current.threadId
                    is LocalOverlaySessionState.Running -> current.threadId
                    is LocalOverlaySessionState.Failed -> current.threadId
                }
            val retryTargetPackageName =
                when (current) {
                    LocalOverlaySessionState.Idle -> null
                    is LocalOverlaySessionState.Preparing -> current.targetPackageName
                    is LocalOverlaySessionState.Running -> current.targetPackageName
                    is LocalOverlaySessionState.Failed -> current.targetPackageName
                }
            launchSession(
                requestedThreadId = retryThreadId,
                requestedTargetPackageName = retryTargetPackageName,
            )
        }

        private fun launchSession(
            requestedThreadId: String?,
            requestedTargetPackageName: String?,
        ) {
            val previousJob = sessionJob
            previousJob?.cancel()
            isVisible.value = true
            localSessionState.value =
                LocalOverlaySessionState.Preparing(
                    threadId = requestedThreadId,
                    targetPackageName = requestedTargetPackageName,
                )

            sessionJob =
                scope.launch {
                    previousJob?.join()
                    if (!checkAudioPermissionUseCase()) {
                        localSessionState.value =
                            LocalOverlaySessionState.Failed(
                                threadId = requestedThreadId,
                                targetPackageName = requestedTargetPackageName,
                                error = LiveOverlayStatus.Error.AudioPermissionRequired,
                            )
                        return@launch
                    }

                    val resolvedThreadId = requestedThreadId ?: createLiveThreadId()

                    localSessionState.value =
                        LocalOverlaySessionState.Running(
                            threadId = resolvedThreadId,
                            targetPackageName = requestedTargetPackageName,
                        )

                    // Verified via checkAudioPermissionUseCase() immediately above.
                    @SuppressLint("MissingPermission")
                    val sessionFlow =
                        liveAgentOrchestrator.runSession(
                            threadId = resolvedThreadId,
                            targetPackageName = requestedTargetPackageName,
                        )

                    sessionFlow.collect { status ->
                        when (status) {
                            LiveAgentStatus.Ended -> {
                                localSessionState.value = LocalOverlaySessionState.Idle
                                isVisible.value = false
                            }
                            is LiveAgentStatus.Error -> {
                                localSessionState.value =
                                    LocalOverlaySessionState.Failed(
                                        threadId = resolvedThreadId,
                                        targetPackageName = requestedTargetPackageName,
                                        error =
                                            LiveOverlayStatus.Error.SessionFailed(status.message),
                                    )
                            }
                            LiveAgentStatus.Connecting,
                            LiveAgentStatus.Listening,
                            is LiveAgentStatus.InvokingTool,
                            -> Unit
                        }
                    }
                }
        }

        private suspend fun createLiveThreadId(): String {
            val selectedProvider = settingsRepository.selectedProvider.first()
            val liveModel = llmProviderFactory.getLiveProvider(selectedProvider).liveModel
            return manageThreadsUseCase.createThread(liveModel)
        }

        override fun close() {
            scope.cancel()
        }
    }

private sealed interface LocalOverlaySessionState {
    data object Idle : LocalOverlaySessionState

    data class Preparing(
        val threadId: String?,
        val targetPackageName: String?,
    ) : LocalOverlaySessionState

    data class Running(
        val threadId: String,
        val targetPackageName: String?,
    ) : LocalOverlaySessionState

    data class Failed(
        val threadId: String?,
        val targetPackageName: String?,
        val error: LiveOverlayStatus.Error,
    ) : LocalOverlaySessionState
}

private fun LiveAgentStatus.toOverlayStatus(): LiveOverlayStatus? =
    when (this) {
        LiveAgentStatus.Connecting -> LiveOverlayStatus.Connecting
        LiveAgentStatus.Listening -> LiveOverlayStatus.Listening
        is LiveAgentStatus.InvokingTool ->
            LiveOverlayStatus.InvokingTool(
                functionId = functionId,
                packageName = packageName,
            )
        is LiveAgentStatus.Error -> LiveOverlayStatus.Error.SessionFailed(message)
        LiveAgentStatus.Ended -> null
    }
