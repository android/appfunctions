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
package com.example.appfunctions.agent.data

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.appfunctions.metadata.AppFunctionMetadata
import com.example.appfunctions.agent.domain.LiveAudioConfig
import com.example.appfunctions.agent.domain.LiveLlmEvent
import com.example.appfunctions.agent.domain.LiveLlmProvider
import com.example.appfunctions.agent.domain.LiveLlmSession
import com.example.appfunctions.agent.domain.LlmResponsePart
import com.example.appfunctions.agent.domain.ToolOutput
import com.example.appfunctions.agent.domain.appfunction.findToolByName
import com.example.appfunctions.agent.domain.appfunction.resolveToolName
import com.example.appfunctions.agent.domain.appfunction.selectDistinctTools
import com.example.appfunctions.agent.domain.toArgumentsMap
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini implementation of [LiveLlmProvider] over the `BidiGenerateContent` WebSocket API
 * (`models/gemini-3.8-live`).
 */
@Singleton
class GeminiLiveProviderImpl
    @VisibleForTesting
    internal constructor(
        private val toolConverter: GeminiLiveToolConverter,
        private val webSocketConnector: suspend (wsUrl: String) -> WebSocketSession,
    ) : LiveLlmProvider {
        @Inject
        constructor(
            httpClient: HttpClient,
            toolConverter: GeminiLiveToolConverter,
        ) : this(
            toolConverter = toolConverter,
            webSocketConnector = { wsUrl -> httpClient.webSocketSession(urlString = wsUrl) },
        )

        override suspend fun connect(
            apiKey: String,
            tools: List<AppFunctionMetadata>,
            voiceName: String?,
        ): LiveLlmSession {
            val resolvedVoiceName =
                voiceName?.takeIf { it.isNotBlank() } ?: GeminiLiveVoice.DEFAULT.apiName
            val wsUrl = "$GEMINI_LIVE_WS_URL?key=$apiKey"
            val webSocketSession = webSocketConnector(wsUrl)
            try {
                val setupMessage = buildSetupMessage(tools = tools, voiceName = resolvedVoiceName)
                Log.d(
                    TAG,
                    "Sending Gemini Live setup for model $LIVE_MODEL_NAME (voice=$resolvedVoiceName)",
                )
                webSocketSession.send(Frame.Text(setupMessage.toString()))

                val firstFrameText =
                    when (val firstFrame = webSocketSession.receiveNextFrame()) {
                        is FrameReadResult.JsonText -> firstFrame.text
                        is FrameReadResult.NormalClose ->
                            throw IllegalStateException(
                                "WebSocket closed before $KEY_SETUP_COMPLETE was received",
                            )
                        is FrameReadResult.ErrorClose ->
                            throw IllegalStateException(
                                "WebSocket failed before $KEY_SETUP_COMPLETE was received: ${firstFrame.message}",
                                firstFrame.cause,
                            )
                    }
                val firstJson = Json.parseToJsonElement(firstFrameText).jsonObject
                if (!firstJson.containsKey(KEY_SETUP_COMPLETE)) {
                    throw IllegalStateException("Unexpected setup response: $firstFrameText")
                }

                return createSession(webSocketSession = webSocketSession, tools = tools)
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    closeWebSocketQuietly(webSocketSession)
                }
                throw t
            }
        }

        private fun createSession(
            webSocketSession: WebSocketSession,
            tools: List<AppFunctionMetadata>,
        ): LiveLlmSession =
            object : LiveLlmSession {
                override val audioConfig: LiveAudioConfig =
                    LiveAudioConfig(
                        inputSampleRateHz = DEFAULT_INPUT_SAMPLE_RATE_HZ,
                        outputSampleRateHz = DEFAULT_OUTPUT_SAMPLE_RATE_HZ,
                    )

                override val events: Flow<LiveLlmEvent> =
                    flow {
                        while (currentCoroutineContext().isActive && webSocketSession.isActive) {
                            val messageText =
                                when (val frameResult = webSocketSession.receiveNextFrame()) {
                                    is FrameReadResult.JsonText -> frameResult.text
                                    is FrameReadResult.NormalClose -> break
                                    is FrameReadResult.ErrorClose -> {
                                        emit(
                                            LiveLlmEvent.Error(
                                                message = frameResult.message,
                                                cause = frameResult.cause,
                                            ),
                                        )
                                        break
                                    }
                                }
                            val serverJson =
                                try {
                                    Json.parseToJsonElement(messageText).jsonObject
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to parse Gemini Live server frame", e)
                                    emit(
                                        LiveLlmEvent.Error(
                                            message = "Failed to parse Gemini Live server frame",
                                            cause = e,
                                        ),
                                    )
                                    continue
                                }

                            for (event in parseServerMessage(serverJson, tools)) {
                                emit(event)
                            }
                        }
                    }

                override suspend fun sendAudio(
                    pcmBytes: ByteArray,
                    sampleRateHz: Int,
                ) {
                    val realtimeInput =
                        buildRealtimeAudioMessage(
                            pcmBytes = pcmBytes,
                            sampleRateHz = sampleRateHz,
                        )
                    webSocketSession.send(Frame.Text(realtimeInput.toString()))
                }

                override suspend fun sendToolResponses(outputs: List<ToolOutput>) {
                    val toolResponseMessage = buildToolResponseMessage(outputs, tools)
                    Log.d(TAG, "Sending Gemini Live toolResponse: $toolResponseMessage")
                    webSocketSession.send(Frame.Text(toolResponseMessage.toString()))
                }

                override suspend fun close() {
                    closeWebSocketQuietly(webSocketSession)
                }
            }

        private suspend fun closeWebSocketQuietly(webSocketSession: WebSocketSession) {
            try {
                webSocketSession.close(
                    CloseReason(
                        CloseReason.Codes.NORMAL,
                        CLOSE_REASON_CLIENT_ENDED,
                    ),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to close Gemini Live WebSocket cleanly", e)
            }
        }

        private suspend fun WebSocketSession.receiveNextFrame(): FrameReadResult {
            while (isActive) {
                val frame =
                    try {
                        incoming.receive()
                    } catch (_: ClosedReceiveChannelException) {
                        return FrameReadResult.NormalClose
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Gemini Live WebSocket transport error", e)
                        return FrameReadResult.ErrorClose(
                            message = "WebSocket transport error: ${e.message}",
                            cause = e,
                        )
                    }
                when (frame) {
                    is Frame.Text -> return FrameReadResult.JsonText(frame.readText())
                    is Frame.Binary -> return FrameReadResult.JsonText(frame.readBytes().decodeToString())
                    is Frame.Close -> {
                        val reason = frame.readReason()
                        return if (reason != null && reason.code != CloseReason.Codes.NORMAL.code) {
                            Log.w(
                                TAG,
                                "Gemini Live WebSocket closed by server: code=${reason.code}, message=${reason.message}",
                            )
                            FrameReadResult.ErrorClose(
                                message =
                                    "WebSocket closed by server (code=${reason.code}): ${reason.message}",
                            )
                        } else {
                            FrameReadResult.NormalClose
                        }
                    }
                    else -> continue
                }
            }
            return FrameReadResult.NormalClose
        }

        private fun parseServerMessage(
            serverJson: JsonObject,
            tools: List<AppFunctionMetadata>,
        ): List<LiveLlmEvent> {
            return when {
                KEY_SERVER_CONTENT in serverJson -> {
                    val serverContent = serverJson[KEY_SERVER_CONTENT]?.jsonObject
                    if (serverContent != null) {
                        parseServerContent(serverContent)
                    } else {
                        listOf(
                            LiveLlmEvent.Error(
                                "Invalid '$KEY_SERVER_CONTENT' payload in server message: $serverJson",
                            ),
                        )
                    }
                }
                KEY_TOOL_CALL in serverJson -> {
                    val toolCallObj = serverJson[KEY_TOOL_CALL]?.jsonObject
                    if (toolCallObj != null) {
                        parseToolCall(toolCallObj, tools)
                    } else {
                        listOf(
                            LiveLlmEvent.Error(
                                "Invalid '$KEY_TOOL_CALL' payload in server message: $serverJson",
                            ),
                        )
                    }
                }
                else -> emptyList()
            }
        }

        private fun parseServerContent(serverContent: JsonObject): List<LiveLlmEvent> {
            return when {
                serverContent[KEY_INTERRUPTED]?.jsonPrimitive?.booleanOrNull == true -> {
                    listOf(LiveLlmEvent.Interrupted)
                }
                serverContent[KEY_TURN_COMPLETE]?.jsonPrimitive?.booleanOrNull == true -> {
                    listOf(LiveLlmEvent.TurnComplete)
                }
                KEY_INPUT_TRANSCRIPTION in serverContent -> {
                    val text =
                        serverContent[KEY_INPUT_TRANSCRIPTION]
                            ?.jsonObject
                            ?.get(KEY_TEXT)
                            ?.jsonPrimitive
                            ?.contentOrNull
                    if (!text.isNullOrEmpty()) {
                        listOf(LiveLlmEvent.InputTranscription(text))
                    } else {
                        emptyList()
                    }
                }
                KEY_OUTPUT_TRANSCRIPTION in serverContent -> {
                    val text =
                        serverContent[KEY_OUTPUT_TRANSCRIPTION]
                            ?.jsonObject
                            ?.get(KEY_TEXT)
                            ?.jsonPrimitive
                            ?.contentOrNull
                    if (!text.isNullOrEmpty()) {
                        listOf(LiveLlmEvent.OutputTranscription(text))
                    } else {
                        emptyList()
                    }
                }
                KEY_MODEL_TURN in serverContent -> {
                    val modelTurn = serverContent[KEY_MODEL_TURN]?.jsonObject
                    if (modelTurn != null) {
                        parseModelTurn(modelTurn)
                    } else {
                        listOf(
                            LiveLlmEvent.Error(
                                "Invalid '$KEY_MODEL_TURN' payload in serverContent: $serverContent",
                            ),
                        )
                    }
                }
                else -> emptyList()
            }
        }

        private fun parseModelTurn(modelTurn: JsonObject): List<LiveLlmEvent> {
            val parts =
                modelTurn[KEY_PARTS]?.jsonArray
                    ?: return listOf(
                        LiveLlmEvent.Error("modelTurn is missing '$KEY_PARTS' array: $modelTurn"),
                    )
            val events = mutableListOf<LiveLlmEvent>()
            for (part in parts) {
                val partObj = part.jsonObject
                val inlineData = partObj[KEY_INLINE_DATA]?.jsonObject ?: continue
                val mimeType = inlineData[KEY_MIME_TYPE]?.jsonPrimitive?.contentOrNull
                if (mimeType == null || !mimeType.startsWith(MIME_TYPE_AUDIO_PCM_PREFIX)) {
                    events.add(
                        LiveLlmEvent.Error(
                            "Unsupported inlineData mimeType in modelTurn: $mimeType",
                        ),
                    )
                    continue
                }
                val base64Data = inlineData[KEY_DATA]?.jsonPrimitive?.contentOrNull
                if (base64Data.isNullOrEmpty()) {
                    events.add(
                        LiveLlmEvent.Error("inlineData is missing audio '$KEY_DATA' in modelTurn"),
                    )
                    continue
                }
                try {
                    val pcmBytes = Base64.getMimeDecoder().decode(base64Data)
                    val sampleRateHz = extractSampleRateHz(mimeType)
                    events.add(
                        LiveLlmEvent.AudioChunk(
                            pcmBytes = pcmBytes,
                            sampleRateHz = sampleRateHz,
                        ),
                    )
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Failed to decode base64 PCM audio chunk", e)
                    events.add(
                        LiveLlmEvent.Error(
                            message = "Failed to decode base64 PCM audio chunk",
                            cause = e,
                        ),
                    )
                }
            }
            return events
        }

        private fun extractSampleRateHz(mimeType: String): Int {
            val rateParam =
                mimeType
                    .split(';')
                    .map { it.trim() }
                    .firstOrNull { it.startsWith(MIME_TYPE_RATE_PARAM, ignoreCase = true) }
            val parsedRate = rateParam?.substringAfter('=')?.toIntOrNull()
            return parsedRate ?: DEFAULT_OUTPUT_SAMPLE_RATE_HZ
        }

        private fun parseToolCall(
            toolCallObj: JsonObject,
            tools: List<AppFunctionMetadata>,
        ): List<LiveLlmEvent> {
            val functionCalls =
                toolCallObj[KEY_FUNCTION_CALLS]?.jsonArray
                    ?: return listOf(
                        LiveLlmEvent.Error(
                            "toolCall is missing '$KEY_FUNCTION_CALLS' array: $toolCallObj",
                        ),
                    )
            if (functionCalls.isEmpty()) {
                return listOf(
                    LiveLlmEvent.Error("toolCall '$KEY_FUNCTION_CALLS' array is empty"),
                )
            }

            val events = mutableListOf<LiveLlmEvent>()
            val validCalls = mutableListOf<LlmResponsePart.ToolCall>()

            for (callElement in functionCalls) {
                val callObj = callElement.jsonObject
                val callId = callObj[KEY_ID]?.jsonPrimitive?.contentOrNull
                if (callId.isNullOrBlank()) {
                    events.add(
                        LiveLlmEvent.Error("Function call missing '$KEY_ID' in response: $callObj"),
                    )
                    continue
                }
                val name = callObj[KEY_NAME]?.jsonPrimitive?.contentOrNull
                if (name.isNullOrBlank()) {
                    events.add(
                        LiveLlmEvent.Error("Called function without a '$KEY_NAME': $callObj"),
                    )
                    continue
                }
                val matchingTool = toolConverter.findToolByName(name, tools)
                if (matchingTool == null) {
                    events.add(
                        LiveLlmEvent.Error("Called unknown function: $name"),
                    )
                    continue
                }
                val argumentsMap = callObj[KEY_ARGS]?.jsonObject.toArgumentsMap()
                validCalls.add(
                    LlmResponsePart.ToolCall(
                        packageName = matchingTool.packageName,
                        functionId = matchingTool.id,
                        arguments = argumentsMap,
                        callId = callId,
                    ),
                )
            }

            if (validCalls.isNotEmpty()) {
                events.add(LiveLlmEvent.ToolCallRequest(validCalls))
            }
            return events
        }

        private fun buildSetupMessage(
            tools: List<AppFunctionMetadata>,
            voiceName: String,
        ): JsonObject {
            val functionDeclarations =
                toolConverter
                    .selectDistinctTools(tools)
                    .mapNotNull { tool ->
                        try {
                            toolConverter.convert(tool)
                        } catch (e: IllegalArgumentException) {
                            Log.e(TAG, "Failed to convert tool ${tool.id}: ${e.message}", e)
                            null
                        }
                    }

            return buildJsonObject {
                put(
                    KEY_SETUP,
                    buildJsonObject {
                        put(KEY_MODEL, LIVE_MODEL_NAME)
                        put(
                            KEY_GENERATION_CONFIG,
                            buildJsonObject {
                                put(
                                    KEY_RESPONSE_MODALITIES,
                                    buildJsonArray { add(JsonPrimitive(VALUE_MODALITY_AUDIO)) },
                                )
                                put(
                                    KEY_SPEECH_CONFIG,
                                    buildJsonObject {
                                        put(
                                            KEY_VOICE_CONFIG,
                                            buildJsonObject {
                                                put(
                                                    KEY_PREBUILT_VOICE_CONFIG,
                                                    buildJsonObject {
                                                        put(KEY_VOICE_NAME, voiceName)
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                        put(
                            KEY_SYSTEM_INSTRUCTION,
                            buildJsonObject {
                                put(
                                    KEY_PARTS,
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put(KEY_TEXT, getSystemInstruction())
                                            },
                                        )
                                    },
                                )
                            },
                        )
                        if (functionDeclarations.isNotEmpty()) {
                            put(
                                KEY_TOOLS,
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put(
                                                KEY_FUNCTION_DECLARATIONS,
                                                JsonArray(functionDeclarations),
                                            )
                                        },
                                    )
                                },
                            )
                        }
                        put(KEY_INPUT_AUDIO_TRANSCRIPTION, buildJsonObject {})
                        put(KEY_OUTPUT_AUDIO_TRANSCRIPTION, buildJsonObject {})
                    },
                )
            }
        }

        private fun buildRealtimeAudioMessage(
            pcmBytes: ByteArray,
            sampleRateHz: Int,
        ): JsonObject {
            val base64Audio = Base64.getEncoder().encodeToString(pcmBytes)
            return buildJsonObject {
                put(
                    KEY_REALTIME_INPUT,
                    buildJsonObject {
                        put(
                            KEY_AUDIO,
                            buildJsonObject {
                                put(KEY_MIME_TYPE, "$MIME_TYPE_AUDIO_PCM_PREFIX;$MIME_TYPE_RATE_PARAM$sampleRateHz")
                                put(KEY_DATA, base64Audio)
                            },
                        )
                    },
                )
            }
        }

        private fun buildToolResponseMessage(
            toolOutputs: List<ToolOutput>,
            tools: List<AppFunctionMetadata>,
        ): JsonObject {
            return buildJsonObject {
                put(
                    KEY_TOOL_RESPONSE,
                    buildJsonObject {
                        put(
                            KEY_FUNCTION_RESPONSES,
                            buildJsonArray {
                                for (output in toolOutputs) {
                                    val mappedName =
                                        toolConverter.resolveToolName(output.functionId, tools)
                                    val parsedResult =
                                        try {
                                            Json.parseToJsonElement(output.result)
                                        } catch (_: Exception) {
                                            JsonPrimitive(output.result)
                                        }
                                    add(
                                        buildJsonObject {
                                            if (output.callId.isNotEmpty()) {
                                                put(KEY_ID, output.callId)
                                            }
                                            put(KEY_NAME, mappedName)
                                            put(
                                                KEY_RESPONSE,
                                                buildJsonObject {
                                                    put(KEY_RESULT, parsedResult)
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

        private fun getSystemInstruction(): String {
            val currentDate = LocalDate.now().toString()
            return """
                You are an AI voice assistant running on Android using Gemini Live. Today's date is $currentDate.
                Default to English for understanding and replying unless the user clearly speaks in or asks to switch to another language.
                Always reply to the user using concise, friendly, natural spoken language.
                You have access to Android AppFunctions and internal device tools. Use them whenever the user asks to perform an action or look up information on their device.
                Execute user-requested actions directly without claiming an on-screen confirmation card or button is shown; if you ever need confirmation before a sensitive action, ask the user verbally.
                When a user asks you to generate an image, call the generateImage tool and then confirm to the user in a natural sentence.
                """.trimIndent()
        }

        private sealed class FrameReadResult {
            data class JsonText(val text: String) : FrameReadResult()

            object NormalClose : FrameReadResult()

            data class ErrorClose(
                val message: String,
                val cause: Throwable? = null,
            ) : FrameReadResult()
        }

        companion object {
            private const val TAG = "GeminiLiveProvider"
            const val LIVE_MODEL_NAME = "models/gemini-3.8-live"
            const val DEFAULT_INPUT_SAMPLE_RATE_HZ = 16_000
            const val DEFAULT_OUTPUT_SAMPLE_RATE_HZ = 24_000
            private const val GEMINI_LIVE_WS_URL =
                "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
            private const val CLOSE_REASON_CLIENT_ENDED = "Session ended by client"

            private const val KEY_SETUP = "setup"
            private const val KEY_SETUP_COMPLETE = "setupComplete"
            private const val KEY_MODEL = "model"
            private const val KEY_GENERATION_CONFIG = "generationConfig"
            private const val KEY_RESPONSE_MODALITIES = "responseModalities"
            private const val VALUE_MODALITY_AUDIO = "AUDIO"
            private const val KEY_SPEECH_CONFIG = "speechConfig"
            private const val KEY_VOICE_CONFIG = "voiceConfig"
            private const val KEY_PREBUILT_VOICE_CONFIG = "prebuiltVoiceConfig"
            private const val KEY_VOICE_NAME = "voiceName"
            private const val KEY_SYSTEM_INSTRUCTION = "systemInstruction"
            private const val KEY_PARTS = "parts"
            private const val KEY_TEXT = "text"
            private const val KEY_TOOLS = "tools"
            private const val KEY_FUNCTION_DECLARATIONS = "functionDeclarations"
            private const val KEY_INPUT_AUDIO_TRANSCRIPTION = "inputAudioTranscription"
            private const val KEY_OUTPUT_AUDIO_TRANSCRIPTION = "outputAudioTranscription"

            private const val KEY_SERVER_CONTENT = "serverContent"
            private const val KEY_INTERRUPTED = "interrupted"
            private const val KEY_INPUT_TRANSCRIPTION = "inputTranscription"
            private const val KEY_OUTPUT_TRANSCRIPTION = "outputTranscription"
            private const val KEY_MODEL_TURN = "modelTurn"
            private const val KEY_INLINE_DATA = "inlineData"
            private const val KEY_MIME_TYPE = "mimeType"
            private const val KEY_DATA = "data"
            private const val KEY_TURN_COMPLETE = "turnComplete"

            private const val KEY_TOOL_CALL = "toolCall"
            private const val KEY_FUNCTION_CALLS = "functionCalls"
            private const val KEY_ID = "id"
            private const val KEY_NAME = "name"
            private const val KEY_ARGS = "args"

            private const val KEY_REALTIME_INPUT = "realtimeInput"
            private const val KEY_AUDIO = "audio"

            private const val KEY_TOOL_RESPONSE = "toolResponse"
            private const val KEY_FUNCTION_RESPONSES = "functionResponses"
            private const val KEY_RESPONSE = "response"
            private const val KEY_RESULT = "result"

            private const val MIME_TYPE_AUDIO_PCM_PREFIX = "audio/pcm"
            private const val MIME_TYPE_RATE_PARAM = "rate="
        }
    }
