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
import androidx.appfunctions.metadata.AppFunctionMetadata
import com.example.appfunctions.agent.domain.LiveLlmEvent
import com.example.appfunctions.agent.domain.LiveLlmProvider
import com.example.appfunctions.agent.domain.LiveLlmSession
import com.example.appfunctions.agent.domain.LlmResponsePart
import com.example.appfunctions.agent.domain.ToolOutput
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
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
    @Inject
    constructor(
        private val httpClient: HttpClient,
        private val toolConverter: GeminiToolConverter,
    ) : LiveLlmProvider {
        internal var webSocketConnector:
            suspend (wsUrl: String, block: suspend WebSocketSession.() -> Unit) -> Unit =
            { wsUrl, block ->
                httpClient.webSocket(urlString = wsUrl, block = block)
            }

        override suspend fun connect(
            apiKey: String,
            tools: List<AppFunctionMetadata>,
            block: suspend (LiveLlmSession) -> Unit,
        ) {
            val wsUrl = "$GEMINI_LIVE_WS_URL?key=$apiKey"
            webSocketConnector(wsUrl) {
                val setupMessage = buildSetupMessage(tools)
                Log.d(TAG, "Sending Gemini Live setup for model $LIVE_MODEL_NAME")
                send(Frame.Text(setupMessage.toString()))

                val firstFrameText =
                    receiveNextJsonText()
                        ?: throw IllegalStateException(
                            "WebSocket closed before setupComplete was received",
                        )
                val firstJson = Json.parseToJsonElement(firstFrameText).jsonObject
                if (!firstJson.containsKey("setupComplete")) {
                    throw IllegalStateException("Unexpected setup response: $firstFrameText")
                }

                val session = createSession(webSocketSession = this, tools = tools)
                try {
                    block(session)
                } finally {
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
        }

        private fun createSession(
            webSocketSession: WebSocketSession,
            tools: List<AppFunctionMetadata>,
        ): LiveLlmSession =
            object : LiveLlmSession {
                override val events: Flow<LiveLlmEvent> =
                    flow {
                        while (currentCoroutineContext().isActive && webSocketSession.isActive) {
                            val messageText = webSocketSession.receiveNextJsonText() ?: break
                            val serverJson =
                                runCatching {
                                    Json.parseToJsonElement(messageText).jsonObject
                                }.getOrNull() ?: continue

                            for (event in parseServerMessage(serverJson, tools)) {
                                emit(event)
                            }
                        }
                    }

                override suspend fun sendAudio(pcmBytes: ByteArray) {
                    val realtimeInput = buildRealtimeAudioMessage(pcmBytes)
                    webSocketSession.send(Frame.Text(realtimeInput.toString()))
                }

                override suspend fun sendToolResponses(outputs: List<ToolOutput>) {
                    val toolResponseMessage = buildToolResponseMessage(outputs, tools)
                    Log.d(TAG, "Sending Gemini Live toolResponse: $toolResponseMessage")
                    webSocketSession.send(Frame.Text(toolResponseMessage.toString()))
                }
            }

        private suspend fun WebSocketSession.receiveNextJsonText(): String? {
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

        internal fun parseServerMessage(
            serverJson: JsonObject,
            tools: List<AppFunctionMetadata>,
        ): List<LiveLlmEvent> {
            val events = mutableListOf<LiveLlmEvent>()

            val serverContent = serverJson["serverContent"]?.jsonObject
            if (serverContent != null) {
                val interrupted =
                    serverContent["interrupted"]?.jsonPrimitive?.booleanOrNull ?: false
                if (interrupted) {
                    events.add(LiveLlmEvent.Interrupted)
                }

                val inputTranscription =
                    serverContent["inputTranscription"]
                        ?.jsonObject
                        ?.get("text")
                        ?.jsonPrimitive
                        ?.contentOrNull
                if (!inputTranscription.isNullOrEmpty()) {
                    events.add(LiveLlmEvent.InputTranscription(inputTranscription))
                }

                val outputTranscription =
                    serverContent["outputTranscription"]
                        ?.jsonObject
                        ?.get("text")
                        ?.jsonPrimitive
                        ?.contentOrNull
                if (!outputTranscription.isNullOrEmpty()) {
                    events.add(LiveLlmEvent.OutputTranscription(outputTranscription))
                }

                val parts = serverContent["modelTurn"]?.jsonObject?.get("parts")?.jsonArray
                if (parts != null) {
                    for (part in parts) {
                        val inlineData = part.jsonObject["inlineData"]?.jsonObject ?: continue
                        val mimeType =
                            inlineData["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
                        val base64Data =
                            inlineData["data"]?.jsonPrimitive?.contentOrNull ?: continue
                        if (mimeType.startsWith("audio/pcm")) {
                            val pcmBytes = Base64.getMimeDecoder().decode(base64Data)
                            events.add(LiveLlmEvent.AudioChunk(pcmBytes))
                        }
                    }
                }

                val turnComplete =
                    serverContent["turnComplete"]?.jsonPrimitive?.booleanOrNull ?: false
                if (turnComplete) {
                    events.add(LiveLlmEvent.TurnComplete)
                }
            }

            val toolCallObj = serverJson["toolCall"]?.jsonObject
            if (toolCallObj != null) {
                val functionCalls =
                    toolCallObj["functionCalls"]?.jsonArray ?: JsonArray(emptyList())
                val parsedCalls =
                    functionCalls.mapNotNull { callElement ->
                        val callObj = callElement.jsonObject
                        val callId = callObj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                        val name =
                            callObj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val matchingTool = toolConverter.findToolByName(name, tools)
                        val argumentsMap = callObj["args"]?.jsonObject.toArgumentsMap()

                        LlmResponsePart.ToolCall(
                            packageName = matchingTool?.packageName ?: "",
                            functionId = matchingTool?.id ?: name,
                            arguments = argumentsMap,
                            callId = callId,
                        )
                    }
                if (parsedCalls.isNotEmpty()) {
                    events.add(LiveLlmEvent.ToolCallRequest(parsedCalls))
                }
            }

            return events
        }

        internal fun buildSetupMessage(tools: List<AppFunctionMetadata>): JsonObject {
            val functionDeclarations =
                toolConverter
                    .selectDistinctTools(tools)
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

        internal fun buildRealtimeAudioMessage(pcmBytes: ByteArray): JsonObject {
            val base64Audio = Base64.getEncoder().encodeToString(pcmBytes)
            return buildJsonObject {
                put(
                    "realtimeInput",
                    buildJsonObject {
                        put(
                            "audio",
                            buildJsonObject {
                                put("mimeType", INPUT_AUDIO_MIME_TYPE)
                                put("data", base64Audio)
                            },
                        )
                    },
                )
            }
        }

        internal fun buildToolResponseMessage(
            toolOutputs: List<ToolOutput>,
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
                                    val mappedName =
                                        toolConverter.resolveToolName(output.functionId, tools)
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
                Always reply to the user using concise, friendly, natural spoken language.
                You have access to Android AppFunctions and internal device tools. Use them whenever the user asks to perform an action or look up information on their device.
                When a user asks you to generate an image, call the generateImage tool and then confirm to the user in a natural sentence.
                When a tool returns status "confirmation_required", let the user know that a confirmation button is on their screen and ask them to tap it to proceed.
                """.trimIndent()
        }

        companion object {
            private const val TAG = "GeminiLiveProvider"
            const val LIVE_MODEL_NAME = "models/gemini-3.8-live"
            private const val DEFAULT_VOICE_NAME = "CHARON"
            private const val INPUT_AUDIO_MIME_TYPE = "audio/pcm;rate=16000"
            private const val GEMINI_LIVE_WS_URL =
                "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        }
    }
