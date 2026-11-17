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
import com.example.appfunctions.agent.domain.chat.ManageThreadsUseCase
import com.example.appfunctions.agent.domain.chat.SendMessageUseCase
import com.example.appfunctions.agent.domain.pendingintent.LaunchPendingIntentUseCase
import com.example.appfunctions.agent.domain.pendingintent.SavePendingIntentUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
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
import java.util.UUID
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
    @Inject
    constructor(
        private val manageThreadsUseCase: ManageThreadsUseCase,
        private val llmProviderFactory: LlmProviderFactory,
        private val settingsRepository: SettingsRepository,
        private val getAppFunctionsUseCase: GetAppFunctionsUseCase,
        private val resolveRemoteFileToLocalUriUseCase: ResolveRemoteFileToLocalUriUseCase,
        private val convertInputToAppFunctionDataUseCase: ConvertInputToAppFunctionDataUseCase,
        private val executeAppFunctionUseCase: ExecuteAppFunctionUseCase,
        private val launchPendingIntentUseCase: LaunchPendingIntentUseCase,
        private val savePendingIntentUseCase: SavePendingIntentUseCase,
        private val sendMessageUseCase: SendMessageUseCase,
        private val audioSessionFactory: LiveAudioSessionFactory,
    ) {
        private val sessionMutex = Mutex()

        private val _status = MutableStateFlow<LiveAgentStatus>(LiveAgentStatus.Idle)

        /** Current unified connection, listening, and tool-invocation status of the live session. */
        val status: StateFlow<LiveAgentStatus> = _status.asStateFlow()

        /**
         * Runs a bidirectional live voice session for [threadId] until the session ends or the
         * calling coroutine is cancelled.
         *
         * The caller (host service or ViewModel) owns the session `Job` and stops the session by
         * cancelling that `Job`. Concurrent calls serialize on [sessionMutex] so audio hardware
         * teardown of a cancelled session finishes before a subsequent session opens `AudioRecord`.
         */
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        suspend fun runSession(
            threadId: String,
            targetPackageName: String? = null,
        ) {
            sessionMutex.withLock {
                currentCoroutineContext().ensureActive()
                runSessionLocked(
                    threadId = threadId,
                    targetPackageName = targetPackageName,
                )
            }
        }

        private suspend fun getApiKey(provider: LlmProviderName): String? {
            return when (provider) {
                LlmProviderName.GEMINI -> settingsRepository.geminiApiKey.first()
            }
        }

        private suspend fun getVoiceName(provider: LlmProviderName): String {
            return when (provider) {
                LlmProviderName.GEMINI -> settingsRepository.geminiLiveVoice.first().apiName
            }
        }

        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        private suspend fun runSessionLocked(
            threadId: String,
            targetPackageName: String?,
        ) {
            _status.value = LiveAgentStatus.Connecting
            val userTranscriptBuilder = StringBuilder()
            val modelTranscriptBuilder = StringBuilder()
            val terminatedWithError = AtomicBoolean(false)
            var wasCancelled = false
            var localAudioSession: LiveAudioSession? = null

            try {
                val thread = manageThreadsUseCase.getThread(threadId).first()
                if (thread == null) {
                    val errorMsg = "Thread not found: $threadId"
                    terminatedWithError.set(true)
                    _status.value = LiveAgentStatus.Error(errorMsg)
                    return
                }

                val providerName = thread.llmModel.providerName
                val apiKey = getApiKey(providerName)?.takeIf { it.isNotBlank() }
                currentCoroutineContext().ensureActive()
                if (apiKey == null) {
                    val errorMsg = "API key is missing for ${providerName.name}"
                    terminatedWithError.set(true)
                    sendMessageUseCase(
                        threadId = threadId,
                        role = MessageRole.ASSISTANT,
                        textContent = errorMsg,
                        processingStatus = MessageProcessingStatus.FAILED,
                    )
                    _status.value = LiveAgentStatus.Error(errorMsg)
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

                val voiceName = getVoiceName(providerName)
                val liveProvider = llmProviderFactory.getLiveProvider(providerName)

                liveProvider.withSession(
                    apiKey = apiKey,
                    tools = tools,
                    voiceName = voiceName,
                ) { session ->
                    currentCoroutineContext().ensureActive()

                    val audioSession = audioSessionFactory.create(session.audioConfig)
                    localAudioSession = audioSession
                    audioSession.start()

                    currentCoroutineContext().ensureActive()
                    _status.value = LiveAgentStatus.Listening

                    coroutineScope {
                        val sendAudioJob =
                            launch {
                                audioSession.listenToRecording().collect { pcmBytes ->
                                    session.sendAudio(pcmBytes, audioSession.inputSampleRateHz)
                                }
                            }

                        try {
                            session.events.firstOrNull { event ->
                                if (!currentCoroutineContext().isActive) {
                                    true
                                } else {
                                    handleLiveEvent(
                                        event = event,
                                        session = session,
                                        threadId = threadId,
                                        tools = tools,
                                        audioSession = audioSession,
                                        userTranscriptBuilder = userTranscriptBuilder,
                                        modelTranscriptBuilder = modelTranscriptBuilder,
                                        terminatedWithError = terminatedWithError,
                                    )
                                    terminatedWithError.get() ||
                                        !currentCoroutineContext().isActive
                                }
                            }
                        } finally {
                            sendAudioJob.cancel()
                        }
                    }
                }
            } catch (e: CancellationException) {
                wasCancelled = true
                throw e
            } catch (e: Exception) {
                if (!currentCoroutineContext().isActive) {
                    wasCancelled = true
                    return
                }
                Log.e(TAG, "Live session error", e)
                terminatedWithError.set(true)
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
                _status.value = LiveAgentStatus.Error(errorText)
            } finally {
                withContext(NonCancellable) {
                    try {
                        flushTranscripts(threadId, userTranscriptBuilder, modelTranscriptBuilder)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to flush remaining live transcripts", e)
                    }
                    localAudioSession?.release()
                    localAudioSession = null
                    if (wasCancelled || !terminatedWithError.get()) {
                        _status.value = LiveAgentStatus.Idle
                    }
                }
            }
        }

        private suspend fun handleLiveEvent(
            event: LiveLlmEvent,
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
                    if (!event.isFatal) {
                        Log.w(TAG, "Recoverable live provider error: ${event.message}", event.cause)
                        return
                    }
                    Log.w(TAG, "Fatal live provider error: ${event.message}", event.cause)
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
                    _status.value = LiveAgentStatus.Error(errorText)
                }
            }
        }

        private suspend fun executeLiveToolCalls(
            threadId: String,
            toolCalls: List<LlmResponsePart.ToolCall>,
            tools: List<AppFunctionMetadata>,
        ): List<ToolOutput> {
            val outputs = mutableListOf<ToolOutput>()
            val capturedAttachments = mutableListOf<MessageAttachment>()
            try {
                for (toolCall in toolCalls) {
                    _status.value =
                        LiveAgentStatus.InvokingTool(toolCall.functionId, toolCall.packageName)
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

                    val inputs = toolCall.arguments.filterNotNullValues()
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
                            val attachment = executionResult.toAttachment()
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
                            val pendingIntentId = UUID.randomUUID().toString()
                            savePendingIntentUseCase(pendingIntentId, executionResult.pendingIntent)
                            val shortName = toolCall.functionId.toShortFunctionName()
                            val launchResult =
                                launchPendingIntentUseCase(executionResult.pendingIntent)
                            if (launchResult.isSuccess) {
                                sendMessageUseCase(
                                    threadId = threadId,
                                    role = MessageRole.ASSISTANT,
                                    textContent =
                                        "Launched $shortName (${toolCall.packageName})",
                                    processingStatus = MessageProcessingStatus.PROCESSED,
                                    pendingIntentId = pendingIntentId,
                                )
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
                                sendMessageUseCase(
                                    threadId = threadId,
                                    role = MessageRole.ASSISTANT,
                                    textContent =
                                        "Action ready for $shortName (${toolCall.packageName})",
                                    processingStatus = MessageProcessingStatus.PROCESSED,
                                    pendingIntentId = pendingIntentId,
                                )
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
                if (currentCoroutineContext().isActive) {
                    _status.value = LiveAgentStatus.Listening
                }
            }
            return outputs
        }

        // TODO: Bridge voice turn transcripts into subsequent typed turns. BidiGenerateContent does
        // not return an Interactions API interactionId, so thread.latestInteractionId is not
        // updated here; follow-up typed messages currently lack server-side context from voice turns.
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
