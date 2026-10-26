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
package com.example.appfunctions.agent.domain

import android.util.Log
import androidx.appfunctions.metadata.AppFunctionMetadata
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.data.db.entities.MessageAttachment
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.domain.appfunction.ExecuteToolCallUseCase
import com.example.appfunctions.agent.domain.appfunction.GetEnabledToolsUseCase
import com.example.appfunctions.agent.domain.appfunction.ToolCallExecutionResult
import com.example.appfunctions.agent.domain.chat.SendMessageUseCase
import com.example.appfunctions.agent.domain.pendingintent.SavePendingIntentUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider-agnostic orchestrator for bidirectional live voice sessions, bridging realtime audio
 * capture/playback, transcript persistence, and Android AppFunction tool execution via
 * [LiveLlmProvider].
 */
@Singleton
class LiveAgentOrchestrator
    @Inject
    constructor(
        private val llmProviderFactory: LlmProviderFactory,
        private val settingsRepository: SettingsRepository,
        private val getEnabledToolsUseCase: GetEnabledToolsUseCase,
        private val executeToolCallUseCase: ExecuteToolCallUseCase,
        private val savePendingIntentUseCase: SavePendingIntentUseCase,
        private val sendMessageUseCase: SendMessageUseCase,
        private val audioSessionFactory: LiveAudioSessionFactory,
    ) {
        private val scope = CoroutineScope(SupervisorJob())
        private val stateLock = Any()
        private val sessionMutex = Mutex()

        private val _state = MutableStateFlow<LiveSessionState>(LiveSessionState.Idle)

        /** Current connection/listening state of the live voice session. */
        val state: StateFlow<LiveSessionState> = _state.asStateFlow()

        private val _status = MutableStateFlow<AgentStatus>(AgentStatus.Idle)

        /** Current tool invocation status emitted during active live voice sessions. */
        val status: StateFlow<AgentStatus> = _status.asStateFlow()

        private var sessionJob: Job? = null
        private var activeSessionId: Long = 0L

        internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

        /** Toggles the live voice session for [threadId]. */
        fun toggleSession(
            threadId: String,
            targetPackageName: String? = null,
        ) {
            val shouldStop =
                synchronized(stateLock) {
                    _state.value is LiveSessionState.Connecting ||
                        _state.value is LiveSessionState.Listening
                }
            if (shouldStop) {
                stopSession()
            } else {
                startSession(threadId, targetPackageName)
            }
        }

        /** Starts a new live voice session for [threadId]. */
        fun startSession(
            threadId: String,
            targetPackageName: String? = null,
        ) {
            val (mySessionId, previousJob) =
                synchronized(stateLock) {
                    val nextId = ++activeSessionId
                    val prev = sessionJob
                    prev?.cancel()
                    _state.value = LiveSessionState.Connecting
                    _status.value = AgentStatus.Idle
                    nextId to prev
                }

            lateinit var launchedJob: Job
            launchedJob =
                scope.launch(ioDispatcher) {
                    try {
                        // Serialize full audio hardware teardown of any prior session before
                        // opening a new session so two AudioRecord instances never overlap.
                        previousJob?.cancelAndJoin()
                        sessionMutex.withLock {
                            if (!isActive || !isCurrentSession(mySessionId)) {
                                return@withLock
                            }
                            runSession(
                                sessionId = mySessionId,
                                threadId = threadId,
                                targetPackageName = targetPackageName,
                            )
                        }
                    } finally {
                        synchronized(stateLock) {
                            if (sessionJob === launchedJob) {
                                sessionJob = null
                            }
                        }
                    }
                }

            synchronized(stateLock) {
                if (activeSessionId == mySessionId) {
                    sessionJob = launchedJob
                } else {
                    launchedJob.cancel()
                }
            }
        }

        /**
         * Stops any active live voice session without blocking the calling (UI) thread.
         *
         * Audio hardware teardown runs asynchronously on [ioDispatcher] inside the session
         * coroutine's `finally` block under [sessionMutex].
         */
        fun stopSession() {
            val jobToCancel =
                synchronized(stateLock) {
                    activeSessionId++
                    _state.value = LiveSessionState.Idle
                    _status.value = AgentStatus.Idle
                    sessionJob
                }
            jobToCancel?.cancel()
        }

        private fun isCurrentSession(sessionId: Long): Boolean = synchronized(stateLock) { activeSessionId == sessionId }

        private fun updateStateIfCurrent(
            sessionId: Long,
            newState: LiveSessionState,
        ) {
            synchronized(stateLock) {
                if (activeSessionId == sessionId) {
                    _state.value = newState
                }
            }
        }

        private fun updateStatusIfCurrent(
            sessionId: Long,
            newStatus: AgentStatus,
        ) {
            synchronized(stateLock) {
                if (activeSessionId == sessionId) {
                    _status.value = newStatus
                }
            }
        }

        private suspend fun getApiKey(provider: LlmProviderName): String? {
            return when (provider) {
                LlmProviderName.GEMINI -> settingsRepository.geminiApiKey.first()
            }
        }

        private suspend fun runSession(
            sessionId: Long,
            threadId: String,
            targetPackageName: String?,
        ) {
            val userTranscriptBuilder = StringBuilder()
            val modelTranscriptBuilder = StringBuilder()
            var localAudioSession: LiveAudioSession? = null

            try {
                val providerName = settingsRepository.selectedProvider.first()
                val apiKey = getApiKey(providerName)?.takeIf { it.isNotBlank() }
                if (!currentCoroutineContext().isActive || !isCurrentSession(sessionId)) {
                    return
                }
                if (apiKey == null) {
                    val errorMsg = "API key is missing for ${providerName.name}"
                    sendMessageUseCase(
                        threadId = threadId,
                        role = MessageRole.ASSISTANT,
                        textContent = errorMsg,
                        processingStatus = MessageProcessingStatus.FAILED,
                    )
                    updateStateIfCurrent(sessionId, LiveSessionState.Error(errorMsg))
                    return
                }

                val tools = getEnabledToolsUseCase(targetPackageName)
                currentCoroutineContext().ensureActive()
                if (!isCurrentSession(sessionId)) return

                val liveProvider = llmProviderFactory.getLiveProvider(providerName)

                liveProvider.connect(apiKey = apiKey, tools = tools) { session ->
                    currentCoroutineContext().ensureActive()
                    if (!isCurrentSession(sessionId)) return@connect

                    val audioSession = audioSessionFactory.create()
                    localAudioSession = audioSession
                    audioSession.start()

                    if (!currentCoroutineContext().isActive || !isCurrentSession(sessionId)) {
                        return@connect
                    }
                    updateStateIfCurrent(sessionId, LiveSessionState.Listening)

                    coroutineScope {
                        val sendAudioJob =
                            launch {
                                audioSession.listenToRecording().collect { pcmBytes ->
                                    session.sendAudio(pcmBytes)
                                }
                            }

                        try {
                            session.events
                                .takeWhile {
                                    currentCoroutineContext().isActive &&
                                        isCurrentSession(sessionId)
                                }
                                .collect { event ->
                                    handleLiveEvent(
                                        event = event,
                                        sessionId = sessionId,
                                        session = session,
                                        threadId = threadId,
                                        tools = tools,
                                        audioSession = audioSession,
                                        userTranscriptBuilder = userTranscriptBuilder,
                                        modelTranscriptBuilder = modelTranscriptBuilder,
                                    )
                                }
                        } finally {
                            sendAudioJob.cancel()
                        }
                    }
                }

                if (currentCoroutineContext().isActive && isCurrentSession(sessionId)) {
                    flushTranscripts(threadId, userTranscriptBuilder, modelTranscriptBuilder)
                    updateStateIfCurrent(sessionId, LiveSessionState.Idle)
                } else {
                    flushRemainingTranscriptsAsync(
                        threadId = threadId,
                        userTranscriptBuilder = userTranscriptBuilder,
                        modelTranscriptBuilder = modelTranscriptBuilder,
                    )
                }
            } catch (e: CancellationException) {
                flushRemainingTranscriptsAsync(
                    threadId = threadId,
                    userTranscriptBuilder = userTranscriptBuilder,
                    modelTranscriptBuilder = modelTranscriptBuilder,
                )
                throw e
            } catch (e: Exception) {
                if (!currentCoroutineContext().isActive || !isCurrentSession(sessionId)) {
                    flushRemainingTranscriptsAsync(
                        threadId = threadId,
                        userTranscriptBuilder = userTranscriptBuilder,
                        modelTranscriptBuilder = modelTranscriptBuilder,
                    )
                    return
                }
                Log.e(TAG, "Live session error", e)
                flushTranscripts(threadId, userTranscriptBuilder, modelTranscriptBuilder)
                val errorText = "Live session error: ${e.message ?: "Connection failed"}"
                runCatching {
                    sendMessageUseCase(
                        threadId = threadId,
                        role = MessageRole.ASSISTANT,
                        textContent = errorText,
                        processingStatus = MessageProcessingStatus.FAILED,
                    )
                }
                updateStateIfCurrent(sessionId, LiveSessionState.Error(errorText))
            } finally {
                updateStatusIfCurrent(sessionId, AgentStatus.Idle)
                localAudioSession?.release()
                localAudioSession = null
            }
        }

        private suspend fun handleLiveEvent(
            event: LiveLlmEvent,
            sessionId: Long,
            session: LiveLlmSession,
            threadId: String,
            tools: List<AppFunctionMetadata>,
            audioSession: LiveAudioSession,
            userTranscriptBuilder: StringBuilder,
            modelTranscriptBuilder: StringBuilder,
        ) {
            when (event) {
                LiveLlmEvent.Interrupted -> {
                    audioSession.stopAudio()
                    flushTranscript(threadId, MessageRole.ASSISTANT, modelTranscriptBuilder)
                }

                is LiveLlmEvent.InputTranscription -> {
                    userTranscriptBuilder.append(event.text)
                }

                is LiveLlmEvent.OutputTranscription -> {
                    // Flush user transcript before model response transcript starts.
                    flushTranscript(threadId, MessageRole.USER, userTranscriptBuilder)
                    modelTranscriptBuilder.append(event.text)
                }

                is LiveLlmEvent.AudioChunk -> {
                    audioSession.queueAudio(event.pcmBytes)
                }

                LiveLlmEvent.TurnComplete -> {
                    flushTranscripts(threadId, userTranscriptBuilder, modelTranscriptBuilder)
                }

                is LiveLlmEvent.ToolCallRequest -> {
                    flushTranscripts(threadId, userTranscriptBuilder, modelTranscriptBuilder)
                    if (event.calls.isNotEmpty()) {
                        audioSession.pauseRecording()
                        try {
                            val toolOutputs =
                                executeLiveToolCalls(
                                    sessionId = sessionId,
                                    threadId = threadId,
                                    toolCalls = event.calls,
                                    tools = tools,
                                )
                            session.sendToolResponses(toolOutputs)
                        } finally {
                            audioSession.resumeRecording()
                        }
                    }
                }
            }
        }

        private suspend fun executeLiveToolCalls(
            sessionId: Long,
            threadId: String,
            toolCalls: List<LlmResponsePart.ToolCall>,
            tools: List<AppFunctionMetadata>,
        ): List<ToolOutput> {
            val outputs = mutableListOf<ToolOutput>()
            val capturedAttachments = mutableListOf<MessageAttachment>()
            try {
                for (toolCall in toolCalls) {
                    updateStatusIfCurrent(
                        sessionId,
                        AgentStatus.InvokingTool(toolCall.functionId, toolCall.packageName),
                    )
                    when (
                        val result =
                            executeToolCallUseCase(
                                toolCall = toolCall,
                                tools = tools,
                                threadId = threadId,
                            )
                    ) {
                        is ToolCallExecutionResult.Success -> {
                            outputs.add(result.toolOutput)
                            result.attachment?.let { capturedAttachments.add(it) }
                        }

                        is ToolCallExecutionResult.PendingIntentRequired -> {
                            savePendingIntentUseCase(result.pendingIntentId, result.pendingIntent)
                            sendMessageUseCase(
                                threadId = threadId,
                                role = MessageRole.ASSISTANT,
                                textContent = "",
                                processingStatus = MessageProcessingStatus.PROCESSED,
                                pendingIntentId = result.pendingIntentId,
                            )
                            outputs.add(
                                ToolOutput(
                                    functionId = toolCall.functionId,
                                    callId = toolCall.callId,
                                    result = CONFIRMATION_REQUIRED_RESULT,
                                ),
                            )
                        }

                        is ToolCallExecutionResult.Failure -> {
                            outputs.add(
                                ToolOutput(
                                    functionId = toolCall.functionId,
                                    callId = toolCall.callId,
                                    result = result.errorMessage,
                                ),
                            )
                        }
                    }
                }

                if (capturedAttachments.isNotEmpty()) {
                    sendMessageUseCase(
                        threadId = threadId,
                        role = MessageRole.ASSISTANT,
                        textContent = "",
                        processingStatus = MessageProcessingStatus.PROCESSED,
                        attachments = capturedAttachments,
                    )
                }
            } finally {
                updateStatusIfCurrent(sessionId, AgentStatus.Idle)
            }
            return outputs
        }

        private fun flushRemainingTranscriptsAsync(
            threadId: String,
            userTranscriptBuilder: StringBuilder,
            modelTranscriptBuilder: StringBuilder,
        ) {
            val remainingUserText = userTranscriptBuilder.toString().trim()
            val remainingModelText = modelTranscriptBuilder.toString().trim()
            userTranscriptBuilder.clear()
            modelTranscriptBuilder.clear()
            if (remainingUserText.isEmpty() && remainingModelText.isEmpty()) return

            scope.launch(ioDispatcher) {
                if (remainingUserText.isNotEmpty()) {
                    sendMessageUseCase(
                        threadId = threadId,
                        role = MessageRole.USER,
                        textContent = remainingUserText,
                        processingStatus = MessageProcessingStatus.PROCESSED,
                    )
                }
                if (remainingModelText.isNotEmpty()) {
                    sendMessageUseCase(
                        threadId = threadId,
                        role = MessageRole.ASSISTANT,
                        textContent = remainingModelText,
                        processingStatus = MessageProcessingStatus.PROCESSED,
                    )
                }
            }
        }

        private suspend fun flushTranscripts(
            threadId: String,
            userTranscriptBuilder: StringBuilder,
            modelTranscriptBuilder: StringBuilder,
        ) {
            flushTranscript(threadId, MessageRole.USER, userTranscriptBuilder)
            flushTranscript(threadId, MessageRole.ASSISTANT, modelTranscriptBuilder)
        }

        private suspend fun flushTranscript(
            threadId: String,
            role: MessageRole,
            builder: StringBuilder,
        ) {
            val text = builder.toString().trim()
            builder.clear()
            if (text.isNotEmpty()) {
                sendMessageUseCase(
                    threadId = threadId,
                    role = role,
                    textContent = text,
                    processingStatus = MessageProcessingStatus.PROCESSED,
                )
            }
        }

        companion object {
            private const val TAG = "LiveAgentOrchestrator"
            private const val CONFIRMATION_REQUIRED_RESULT =
                """{"status":"confirmation_required","message":"A confirmation button has been displayed in the chat. Ask the user to tap it to confirm."}"""
        }
    }
