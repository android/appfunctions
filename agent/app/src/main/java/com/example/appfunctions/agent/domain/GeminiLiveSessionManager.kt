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

import android.util.Base64
import android.util.Log
import androidx.appfunctions.metadata.AppFunctionMetadata
import com.example.appfunctions.agent.data.GeminiLiveAudioHelper
import com.example.appfunctions.agent.data.GeminiToolConverter
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.domain.chat.SendMessageUseCase
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a bidirectional voice session with the Gemini Live API (`models/gemini-3.8-live`),
 * bridging realtime audio I/O, transcriptions, and Android AppFunction tool execution.
 */
@Singleton
class GeminiLiveSessionManager
    @Inject
    constructor(
        private val httpClient: HttpClient,
        private val toolConverter: GeminiToolConverter,
        private val settingsRepository: SettingsRepository,
        private val agentOrchestrator: AgentOrchestrator,
        private val sendMessageUseCase: SendMessageUseCase,
    ) {
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        private val _state = MutableStateFlow<LiveSessionState>(LiveSessionState.Idle)

        /** Current state of the Gemini Live session. */
        val state: StateFlow<LiveSessionState> = _state.asStateFlow()

        private var sessionJob: Job? = null
        private var audioHelper: GeminiLiveAudioHelper? = null

        internal var audioHelperFactory: () -> GeminiLiveAudioHelper = { GeminiLiveAudioHelper() }

        /** Toggles the Gemini Live voice session for [threadId]. */
        fun toggleSession(
            threadId: String,
            targetPackageName: String? = null,
        ) {
            when (_state.value) {
                is LiveSessionState.Connecting,
                is LiveSessionState.Listening,
                -> stopSession()
                is LiveSessionState.Idle,
                is LiveSessionState.Error,
                -> startSession(threadId, targetPackageName)
            }
        }

        /** Starts a new Gemini Live voice session for [threadId]. */
        fun startSession(
            threadId: String,
            targetPackageName: String? = null,
        ) {
            stopSessionInternal(resetState = false)
            _state.value = LiveSessionState.Connecting

            sessionJob =
                scope.launch {
                    val userTranscriptBuilder = StringBuilder()
                    val modelTranscriptBuilder = StringBuilder()
                    try {
                        val apiKey =
                            settingsRepository.geminiApiKey.first()?.takeIf { it.isNotBlank() }
                        if (apiKey == null) {
                            val errorMsg = "API key is missing for GEMINI"
                            sendMessageUseCase(
                                threadId = threadId,
                                role = MessageRole.ASSISTANT,
                                textContent = errorMsg,
                                processingStatus = MessageProcessingStatus.FAILED,
                            )
                            _state.value = LiveSessionState.Error(errorMsg)
                            return@launch
                        }

                        val tools = agentOrchestrator.getEnabledTools(targetPackageName)
                        val wsUrl = "$GEMINI_LIVE_WS_URL?key=$apiKey"

                        httpClient.webSocket(urlString = wsUrl) {
                            val setupMessage = buildSetupMessage(tools)
                            Log.d(TAG, "Sending Gemini Live setup for model $LIVE_MODEL_NAME")
                            send(Frame.Text(setupMessage.toString()))

                            val firstFrameText = receiveNextJsonText()
                            if (firstFrameText == null) {
                                throw IllegalStateException(
                                    "WebSocket closed before setupComplete was received",
                                )
                            }
                            val firstJson = Json.parseToJsonElement(firstFrameText).jsonObject
                            if (!firstJson.containsKey("setupComplete")) {
                                throw IllegalStateException(
                                    "Unexpected setup response: $firstFrameText",
                                )
                            }

                            val helper =
                                audioHelperFactory().also {
                                    it.start()
                                    audioHelper = it
                                }
                            _state.value = LiveSessionState.Listening

                            val sendAudioJob =
                                launch {
                                    helper.listenToRecording().collect { pcmBytes ->
                                        val base64Audio =
                                            Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
                                        val realtimeInput =
                                            buildJsonObject {
                                                put(
                                                    "realtimeInput",
                                                    buildJsonObject {
                                                        put(
                                                            "audio",
                                                            buildJsonObject {
                                                                put(
                                                                    "mimeType",
                                                                    "audio/pcm;rate=16000",
                                                                )
                                                                put("data", base64Audio)
                                                            },
                                                        )
                                                    },
                                                )
                                            }
                                        send(Frame.Text(realtimeInput.toString()))
                                    }
                                }

                            try {
                                while (isActive) {
                                    val messageText = receiveNextJsonText() ?: break
                                    val serverJson =
                                        runCatching {
                                            Json.parseToJsonElement(messageText).jsonObject
                                        }.getOrNull() ?: continue

                                    handleServerMessage(
                                        serverJson = serverJson,
                                        threadId = threadId,
                                        tools = tools,
                                        helper = helper,
                                        userTranscriptBuilder = userTranscriptBuilder,
                                        modelTranscriptBuilder = modelTranscriptBuilder,
                                    )
                                }
                            } finally {
                                sendAudioJob.cancel()
                                runCatching {
                                    close(
                                        CloseReason(
                                            CloseReason.Codes.NORMAL,
                                            "Session ended by client",
                                        ),
                                    )
                                }
                            }
                        }

                        flushTranscripts(threadId, userTranscriptBuilder, modelTranscriptBuilder)
                        _state.value = LiveSessionState.Idle
                    } catch (e: CancellationException) {
                        scope.launch {
                            flushTranscripts(
                                threadId,
                                userTranscriptBuilder,
                                modelTranscriptBuilder,
                            )
                        }
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Gemini Live session error", e)
                        flushTranscripts(threadId, userTranscriptBuilder, modelTranscriptBuilder)
                        val errorText =
                            "Gemini Live error: ${e.message ?: "Connection failed"}"
                        runCatching {
                            sendMessageUseCase(
                                threadId = threadId,
                                role = MessageRole.ASSISTANT,
                                textContent = errorText,
                                processingStatus = MessageProcessingStatus.FAILED,
                            )
                        }
                        _state.value = LiveSessionState.Error(errorText)
                    } finally {
                        audioHelper?.release()
                        audioHelper = null
                    }
                }
        }

        /** Stops any active Gemini Live voice session. */
        fun stopSession() {
            stopSessionInternal(resetState = true)
        }

        private fun stopSessionInternal(resetState: Boolean) {
            sessionJob?.cancel()
            sessionJob = null
            audioHelper?.release()
            audioHelper = null
            if (resetState) {
                _state.value = LiveSessionState.Idle
            }
        }

        private suspend fun DefaultClientWebSocketSession.receiveNextJsonText(): String? {
            while (isActive) {
                val frame = runCatching { incoming.receive() }.getOrNull() ?: return null
                when (frame) {
                    is Frame.Text -> return frame.readText()
                    is Frame.Binary -> return frame.readBytes().decodeToString()
                    is Frame.Close -> return null
                    else -> continue
                }
            }
            return null
        }

        private suspend fun DefaultClientWebSocketSession.handleServerMessage(
            serverJson: JsonObject,
            threadId: String,
            tools: List<AppFunctionMetadata>,
            helper: GeminiLiveAudioHelper,
            userTranscriptBuilder: StringBuilder,
            modelTranscriptBuilder: StringBuilder,
        ) {
            val serverContent = serverJson["serverContent"]?.jsonObject
            if (serverContent != null) {
                val interrupted =
                    serverContent["interrupted"]?.jsonPrimitive?.booleanOrNull ?: false
                if (interrupted) {
                    helper.stopAudio()
                    flushTranscript(threadId, MessageRole.ASSISTANT, modelTranscriptBuilder)
                }

                val inputTranscription =
                    serverContent["inputTranscription"]
                        ?.jsonObject
                        ?.get("text")
                        ?.jsonPrimitive
                        ?.contentOrNull
                if (!inputTranscription.isNullOrEmpty()) {
                    userTranscriptBuilder.append(inputTranscription)
                }

                val outputTranscription =
                    serverContent["outputTranscription"]
                        ?.jsonObject
                        ?.get("text")
                        ?.jsonPrimitive
                        ?.contentOrNull
                if (!outputTranscription.isNullOrEmpty()) {
                    // Flush user transcript before model response transcript starts
                    flushTranscript(threadId, MessageRole.USER, userTranscriptBuilder)
                    modelTranscriptBuilder.append(outputTranscription)
                }

                val parts =
                    serverContent["modelTurn"]?.jsonObject?.get("parts")?.jsonArray
                if (parts != null) {
                    for (part in parts) {
                        val inlineData = part.jsonObject["inlineData"]?.jsonObject ?: continue
                        val mimeType =
                            inlineData["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
                        val base64Data =
                            inlineData["data"]?.jsonPrimitive?.contentOrNull ?: continue
                        if (mimeType.startsWith("audio/pcm")) {
                            val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
                            helper.queueAudio(pcmBytes)
                        }
                    }
                }

                val turnComplete =
                    serverContent["turnComplete"]?.jsonPrimitive?.booleanOrNull ?: false
                if (turnComplete) {
                    flushTranscripts(threadId, userTranscriptBuilder, modelTranscriptBuilder)
                }
            }

            val toolCallObj = serverJson["toolCall"]?.jsonObject
            if (toolCallObj != null) {
                flushTranscripts(threadId, userTranscriptBuilder, modelTranscriptBuilder)
                val functionCalls = toolCallObj["functionCalls"]?.jsonArray ?: JsonArray(emptyList())
                if (functionCalls.isNotEmpty()) {
                    helper.pauseRecording()
                    try {
                        val parsedCalls = mutableListOf<LlmResponsePart.ToolCall>()
                        val unknownCalls = mutableListOf<Pair<String, String>>()

                        for (callElement in functionCalls) {
                            val callObj = callElement.jsonObject
                            val callId = callObj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                            val name = callObj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                            val matchingTool =
                                tools.find { tool -> toolConverter.getToolName(tool) == name }

                            if (matchingTool == null) {
                                unknownCalls.add(callId to name)
                                continue
                            }

                            val argsObj = callObj["args"]?.jsonObject
                            val argumentsMap = mutableMapOf<String, Any?>()
                            if (argsObj != null) {
                                for ((key, value) in argsObj) {
                                    argumentsMap[key] = value.toPrimitive()
                                }
                            }
                            parsedCalls.add(
                                LlmResponsePart.ToolCall(
                                    packageName = matchingTool.packageName,
                                    functionId = matchingTool.id,
                                    arguments = argumentsMap,
                                    callId = callId,
                                ),
                            )
                        }

                        val toolOutputs =
                            if (parsedCalls.isNotEmpty()) {
                                agentOrchestrator.executeLiveToolCalls(
                                    threadId = threadId,
                                    toolCalls = parsedCalls,
                                    tools = tools,
                                )
                            } else {
                                emptyList()
                            }

                        val toolResponseMessage =
                            buildToolResponseMessage(
                                toolOutputs = toolOutputs,
                                unknownCalls = unknownCalls,
                                tools = tools,
                            )
                        Log.d(TAG, "Sending Gemini Live toolResponse: $toolResponseMessage")
                        send(Frame.Text(toolResponseMessage.toString()))
                    } finally {
                        helper.resumeRecording()
                    }
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

        internal fun buildSetupMessage(tools: List<AppFunctionMetadata>): JsonObject {
            val functionDeclarations =
                tools
                    .sortedByDescending { it.id.startsWith(it.packageName) }
                    .distinctBy { toolConverter.getToolName(it) }
                    .mapNotNull { tool ->
                        try {
                            toolConverter.convertForLiveApi(tool)
                        } catch (e: IllegalArgumentException) {
                            Log.e(TAG, "Failed to convert tool ${tool.id}: ${e.message}", e)
                            null
                        }
                    }

            return buildJsonObject {
                put(
                    "setup",
                    buildJsonObject {
                        put("model", LIVE_MODEL_NAME)
                        put(
                            "generationConfig",
                            buildJsonObject {
                                put(
                                    "responseModalities",
                                    buildJsonArray { add(JsonPrimitive("AUDIO")) },
                                )
                                put(
                                    "speechConfig",
                                    buildJsonObject {
                                        put(
                                            "voiceConfig",
                                            buildJsonObject {
                                                put(
                                                    "prebuiltVoiceConfig",
                                                    buildJsonObject {
                                                        put("voiceName", DEFAULT_VOICE_NAME)
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                        put(
                            "systemInstruction",
                            buildJsonObject {
                                put(
                                    "parts",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("text", getSystemInstruction())
                                            },
                                        )
                                    },
                                )
                            },
                        )
                        if (functionDeclarations.isNotEmpty()) {
                            put(
                                "tools",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put(
                                                "functionDeclarations",
                                                JsonArray(functionDeclarations),
                                            )
                                        },
                                    )
                                },
                            )
                        }
                        put("inputAudioTranscription", buildJsonObject {})
                        put("outputAudioTranscription", buildJsonObject {})
                    },
                )
            }
        }

        private fun buildToolResponseMessage(
            toolOutputs: List<ToolOutput>,
            unknownCalls: List<Pair<String, String>>,
            tools: List<AppFunctionMetadata>,
        ): JsonObject {
            return buildJsonObject {
                put(
                    "toolResponse",
                    buildJsonObject {
                        put(
                            "functionResponses",
                            buildJsonArray {
                                for (output in toolOutputs) {
                                    val matchingTool = tools.find { it.id == output.functionId }
                                    val mappedName =
                                        if (matchingTool != null) {
                                            toolConverter.getToolName(matchingTool)
                                        } else {
                                            output.functionId
                                        }
                                    val parsedResult =
                                        runCatching {
                                            Json.parseToJsonElement(output.result)
                                        }.getOrElse {
                                            JsonPrimitive(output.result)
                                        }
                                    add(
                                        buildJsonObject {
                                            if (output.callId.isNotEmpty()) {
                                                put("id", output.callId)
                                            }
                                            put("name", mappedName)
                                            put(
                                                "response",
                                                buildJsonObject {
                                                    put("result", parsedResult)
                                                },
                                            )
                                        },
                                    )
                                }
                                for ((callId, unknownName) in unknownCalls) {
                                    add(
                                        buildJsonObject {
                                            if (callId.isNotEmpty()) {
                                                put("id", callId)
                                            }
                                            put("name", unknownName)
                                            put(
                                                "response",
                                                buildJsonObject {
                                                    put(
                                                        "error",
                                                        "Unknown function: $unknownName",
                                                    )
                                                },
                                            )
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        }

        private fun JsonElement.toPrimitive(): Any? {
            return when (this) {
                is JsonPrimitive -> {
                    if (this.isString) return this.content
                    return this.content.toBooleanStrictOrNull()
                        ?: this.content.toLongOrNull()
                        ?: this.content.toDoubleOrNull()
                }
                is JsonObject -> this.mapValues { it.value.toPrimitive() }
                is JsonArray -> this.map { it.toPrimitive() }
            }
        }

        private fun getSystemInstruction(): String {
            val currentDate = LocalDate.now().toString()
            return """
                You are an AI voice assistant running on Android using Gemini Live. Today's date is $currentDate.
                Always reply to the user using concise, friendly, natural spoken language.
                You have access to Android AppFunctions and internal device tools. Use them whenever the user asks to perform an action or look up information on their device.
                When a user asks you to generate an image, call the generateImage tool and then confirm to the user in a natural sentence.
                When a tool returns status "confirmation_required", let the user know that a confirmation button is on their screen and ask them to tap it to proceed.
                """.trimIndent()
        }

        companion object {
            private const val TAG = "GeminiLiveSessionMgr"
            const val LIVE_MODEL_NAME = "models/gemini-3.8-live"
            private const val DEFAULT_VOICE_NAME = "CHARON"
            private const val GEMINI_LIVE_WS_URL =
                "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        }
    }
