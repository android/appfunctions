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

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.annotation.VisibleForTesting
import androidx.appfunctions.metadata.AppFunctionMetadata
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.data.db.entities.MessageAttachment
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.domain.appfunction.AppFunctionExceptionFormatter
import com.example.appfunctions.agent.domain.appfunction.ConvertInputToAppFunctionDataUseCase
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionResult
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsParams
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.appfunction.ResolveRemoteFileToLocalUriUseCase
import com.example.appfunctions.agent.domain.chat.SendMessageUseCase
import com.example.appfunctions.agent.domain.pendingintent.LaunchPendingIntentUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider-agnostic orchestrator for bidirectional live voice sessions, bridging realtime audio
 * capture/playback, transcript persistence, and Android AppFunction tool execution via
 * [LiveLlmProvider].
 */
@Singleton
class LiveAgentOrchestrator
    @VisibleForTesting
    internal constructor(
        private val llmProviderFactory: LlmProviderFactory,
        private val settingsRepository: SettingsRepository,
        private val getAppFunctionsUseCase: GetAppFunctionsUseCase,
        private val resolveRemoteFileToLocalUriUseCase: ResolveRemoteFileToLocalUriUseCase,
        private val convertInputToAppFunctionDataUseCase: ConvertInputToAppFunctionDataUseCase,
        private val executeAppFunctionUseCase: ExecuteAppFunctionUseCase,
        private val launchPendingIntentUseCase: LaunchPendingIntentUseCase,
        private val sendMessageUseCase: SendMessageUseCase,
        private val audioSessionFactory: LiveAudioSessionFactory,
        private val ioDispatcher: CoroutineDispatcher,
    ) {
        @Inject
        constructor(
            llmProviderFactory: LlmProviderFactory,
            settingsRepository: SettingsRepository,
            getAppFunctionsUseCase: GetAppFunctionsUseCase,
            resolveRemoteFileToLocalUriUseCase: ResolveRemoteFileToLocalUriUseCase,
            convertInputToAppFunctionDataUseCase: ConvertInputToAppFunctionDataUseCase,
            executeAppFunctionUseCase: ExecuteAppFunctionUseCase,
            launchPendingIntentUseCase: LaunchPendingIntentUseCase,
            sendMessageUseCase: SendMessageUseCase,
            audioSessionFactory: LiveAudioSessionFactory,
        ) : this(
            llmProviderFactory = llmProviderFactory,
            settingsRepository = settingsRepository,
            getAppFunctionsUseCase = getAppFunctionsUseCase,
            resolveRemoteFileToLocalUriUseCase = resolveRemoteFileToLocalUriUseCase,
            convertInputToAppFunctionDataUseCase = convertInputToAppFunctionDataUseCase,
            executeAppFunctionUseCase = executeAppFunctionUseCase,
            launchPendingIntentUseCase = launchPendingIntentUseCase,
            sendMessageUseCase = sendMessageUseCase,
            audioSessionFactory = audioSessionFactory,
            ioDispatcher = Dispatchers.IO,
        )

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

        /** Starts a new live voice session for [threadId]. */
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
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

        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        private suspend fun runSession(
            sessionId: Long,
            threadId: String,
            targetPackageName: String?,
        ) {
            val userTranscriptBuilder = StringBuilder()
            val modelTranscriptBuilder = StringBuilder()
            val terminatedWithError = AtomicBoolean(false)
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

                val targetPackageNames =
                    if (targetPackageName != null) {
                        setOf(targetPackageName)
                    } else {
                        null
                    }
                // Live sessions declare tools once during the initial connection setup handshake,
                // so full tool declarations are loaded upfront (progressiveToolDisclosure = false).
                val tools =
                    getAppFunctionsUseCase(
                        GetAppFunctionsParams(
                            targetPackageNames = targetPackageNames,
                            isEnabled = true,
                            isConnected = true,
                            includeInternalTools = true,
                        ),
                    ).first().values.flatten()
                currentCoroutineContext().ensureActive()
                if (!isCurrentSession(sessionId)) return

                val selectedVoice = settingsRepository.geminiLiveVoice.first()
                val liveProvider = llmProviderFactory.getLiveProvider(providerName)

                liveProvider.withSession(
                    apiKey = apiKey,
                    tools = tools,
                    voiceName = selectedVoice.apiName,
                ) { session ->
                    currentCoroutineContext().ensureActive()
                    if (!isCurrentSession(sessionId)) return@withSession

                    val audioSession = audioSessionFactory.create(session.audioConfig)
                    localAudioSession = audioSession
                    audioSession.start()

                    if (!currentCoroutineContext().isActive || !isCurrentSession(sessionId)) {
                        return@withSession
                    }
                    updateStateIfCurrent(sessionId, LiveSessionState.Listening)

                    coroutineScope {
                        val sendAudioJob =
                            launch {
                                audioSession.listenToRecording().collect { pcmBytes ->
                                    session.sendAudio(pcmBytes, audioSession.inputSampleRateHz)
                                }
                            }

                        try {
                            session.events.firstOrNull { event ->
                                if (
                                    !currentCoroutineContext().isActive ||
                                    !isCurrentSession(sessionId)
                                ) {
                                    true
                                } else {
                                    handleLiveEvent(
                                        event = event,
                                        sessionId = sessionId,
                                        session = session,
                                        threadId = threadId,
                                        tools = tools,
                                        audioSession = audioSession,
                                        userTranscriptBuilder = userTranscriptBuilder,
                                        modelTranscriptBuilder = modelTranscriptBuilder,
                                        terminatedWithError = terminatedWithError,
                                    )
                                    terminatedWithError.get() ||
                                        !currentCoroutineContext().isActive ||
                                        !isCurrentSession(sessionId)
                                }
                            }
                        } finally {
                            sendAudioJob.cancel()
                        }
                    }
                }

                if (currentCoroutineContext().isActive && isCurrentSession(sessionId)) {
                    flushTranscripts(threadId, userTranscriptBuilder, modelTranscriptBuilder)
                    if (!terminatedWithError.get()) {
                        updateStateIfCurrent(sessionId, LiveSessionState.Idle)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!currentCoroutineContext().isActive || !isCurrentSession(sessionId)) {
                    return
                }
                Log.e(TAG, "Live session error", e)
                flushTranscripts(threadId, userTranscriptBuilder, modelTranscriptBuilder)
                val errorText = "Live session error: ${e.message ?: "Connection failed"}"
                try {
                    sendMessageUseCase(
                        threadId = threadId,
                        role = MessageRole.ASSISTANT,
                        textContent = errorText,
                        processingStatus = MessageProcessingStatus.FAILED,
                    )
                } catch (sendError: CancellationException) {
                    throw sendError
                } catch (sendError: Exception) {
                    Log.w(TAG, "Failed to persist live session error message", sendError)
                }
                updateStateIfCurrent(sessionId, LiveSessionState.Error(errorText))
            } finally {
                withContext(NonCancellable) {
                    try {
                        flushTranscripts(threadId, userTranscriptBuilder, modelTranscriptBuilder)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to flush remaining live transcripts", e)
                    }
                    updateStatusIfCurrent(sessionId, AgentStatus.Idle)
                    localAudioSession?.release()
                    localAudioSession = null
                }
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
            terminatedWithError: AtomicBoolean,
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
                    audioSession.queueAudio(event.pcmBytes, event.sampleRateHz)
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

                is LiveLlmEvent.Error -> {
                    Log.w(TAG, "Live provider error: ${event.message}", event.cause)
                    terminatedWithError.set(true)
                    audioSession.stopAudio()
                    flushTranscripts(threadId, userTranscriptBuilder, modelTranscriptBuilder)
                    val errorText = "Live session error: ${event.message}"
                    sendMessageUseCase(
                        threadId = threadId,
                        role = MessageRole.ASSISTANT,
                        textContent = errorText,
                        processingStatus = MessageProcessingStatus.FAILED,
                    )
                    updateStateIfCurrent(sessionId, LiveSessionState.Error(errorText))
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
                    val matchingTool =
                        tools.find {
                            it.packageName == toolCall.packageName && it.id == toolCall.functionId
                        }
                    if (matchingTool == null) {
                        outputs.add(
                            ToolOutput(
                                functionId = toolCall.functionId,
                                callId = toolCall.callId,
                                result = "Tool not found: ${toolCall.functionId}",
                            ),
                        )
                        continue
                    }

                    val inputs = nonNullArguments(toolCall.arguments)
                    val convertedLocalFileInput =
                        try {
                            resolveRemoteFileToLocalUriUseCase(
                                parameters = matchingTool.parameters,
                                components = matchingTool.components,
                                inputs = inputs,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            outputs.add(
                                ToolOutput(
                                    functionId = toolCall.functionId,
                                    callId = toolCall.callId,
                                    result =
                                        "Failed to convert arguments for ${toolCall.functionId}\n $e",
                                ),
                            )
                            continue
                        }
                    val appFunctionDataResult =
                        convertInputToAppFunctionDataUseCase(
                            parameters = matchingTool.parameters,
                            components = matchingTool.components,
                            inputs = convertedLocalFileInput,
                        )
                    if (appFunctionDataResult.isFailure) {
                        val exception = appFunctionDataResult.exceptionOrNull()
                        if (exception is CancellationException) {
                            throw exception
                        }
                        outputs.add(
                            ToolOutput(
                                functionId = toolCall.functionId,
                                callId = toolCall.callId,
                                result =
                                    "Failed to convert arguments for ${toolCall.functionId}\n $exception",
                            ),
                        )
                        continue
                    }

                    when (
                        val executionResult =
                            executeAppFunctionUseCase(
                                function = matchingTool,
                                parameters = appFunctionDataResult.getOrThrow(),
                                threadId = threadId,
                            )
                    ) {
                        is ExecuteAppFunctionResult.Data -> {
                            outputs.add(
                                ToolOutput(
                                    functionId = toolCall.functionId,
                                    callId = toolCall.callId,
                                    result = executionResult.formattedJson,
                                ),
                            )
                            val attachment = extractMessageAttachment(executionResult.formattedJson)
                            if (attachment != null) {
                                capturedAttachments.add(attachment)
                            }
                        }

                        is ExecuteAppFunctionResult.ToolsDiscovered -> {
                            outputs.add(
                                ToolOutput(
                                    functionId = toolCall.functionId,
                                    callId = toolCall.callId,
                                    result = executionResult.formattedJson,
                                ),
                            )
                        }

                        is ExecuteAppFunctionResult.PendingIntentAction -> {
                            val launchResult =
                                launchPendingIntentUseCase(executionResult.pendingIntent)
                            if (launchResult.isSuccess) {
                                outputs.add(
                                    ToolOutput(
                                        functionId = toolCall.functionId,
                                        callId = toolCall.callId,
                                        result = PENDING_INTENT_LAUNCHED_RESULT,
                                    ),
                                )
                            } else {
                                val reason =
                                    launchResult.exceptionOrNull()?.message
                                        ?: "PendingIntent canceled"
                                outputs.add(
                                    ToolOutput(
                                        functionId = toolCall.functionId,
                                        callId = toolCall.callId,
                                        result =
                                            "Tool execution failed for ${toolCall.functionId}: $reason",
                                    ),
                                )
                            }
                        }

                        is ExecuteAppFunctionResult.Error -> {
                            val exception = executionResult.exception
                            if (exception is CancellationException) {
                                throw exception
                            }
                            val appFunctionException =
                                AppFunctionExceptionFormatter.getAppFunctionException(exception)
                            val errorResult =
                                if (appFunctionException != null) {
                                    AppFunctionExceptionFormatter.format(
                                        appFunctionException,
                                        toolCall.functionId,
                                    )
                                } else {
                                    "Tool execution failed for ${toolCall.functionId}: ${exception.message}"
                                }
                            outputs.add(
                                ToolOutput(
                                    functionId = toolCall.functionId,
                                    callId = toolCall.callId,
                                    result = errorResult,
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
            private const val PENDING_INTENT_LAUNCHED_RESULT =
                """{"status":"launched","message":"The action was executed and the target app UI was opened."}"""
        }
    }
