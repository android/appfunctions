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
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import com.example.appfunctions.agent.domain.LiveLlmEvent
import com.example.appfunctions.agent.domain.ToolOutput
import io.ktor.client.HttpClient
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Base64

class GeminiLiveProviderImplTest {
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var provider: GeminiLiveProviderImpl

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        settingsRepository = mockk(relaxed = true)
        every { settingsRepository.geminiLiveVoice } returns flowOf(GeminiLiveVoice.DEFAULT)

        provider =
            GeminiLiveProviderImpl(
                httpClient = mockk<HttpClient>(relaxed = true),
                toolConverter = GeminiToolConverter(),
                settingsRepository = settingsRepository,
            )
    }

    @Test
    fun `buildSetupMessage configures gemini-3_8-live model, audio modality, transcriptions, and tools`() {
        val tool = createSampleNoteTool()

        val setupEnvelope = provider.buildSetupMessage(listOf(tool))
        val setup = setupEnvelope["setup"]!!.jsonObject

        assertEquals("models/gemini-3.8-live", setup["model"]!!.jsonPrimitive.content)

        val modalities =
            setup["generationConfig"]!!
                .jsonObject["responseModalities"]!!
                .jsonArray
                .map { it.jsonPrimitive.content }
        assertEquals(listOf("AUDIO"), modalities)

        val voiceName =
            setup["generationConfig"]!!
                .jsonObject["speechConfig"]!!
                .jsonObject["voiceConfig"]!!
                .jsonObject["prebuiltVoiceConfig"]!!
                .jsonObject["voiceName"]!!
                .jsonPrimitive
                .content
        assertEquals("Zephyr", voiceName)

        assertTrue(setup.containsKey("inputAudioTranscription"))
        assertTrue(setup.containsKey("outputAudioTranscription"))
        val systemInstructionText =
            setup["systemInstruction"]!!
                .jsonObject["parts"]!!
                .jsonArray
                .first()
                .jsonObject["text"]!!
                .jsonPrimitive
                .content
        assertTrue(
            systemInstructionText.contains(
                "Default to English for understanding and replying unless the user clearly " +
                    "speaks in or asks to switch to another language.",
            ),
        )
        assertTrue(
            systemInstructionText.contains(
                "Execute user-requested actions directly without claiming an on-screen " +
                    "confirmation card or button is shown",
            ),
        )
        assertFalse(systemInstructionText.contains("confirmation_required"))

        val declarations =
            setup["tools"]!!
                .jsonArray
                .first()
                .jsonObject["functionDeclarations"]!!
                .jsonArray
        assertEquals(1, declarations.size)
        assertEquals(
            "com_example_notes_createNote",
            declarations.first().jsonObject["name"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `connect throws IllegalStateException when WebSocket closes before setupComplete`() =
        runTest {
            val incomingFrames = Channel<Frame>(Channel.UNLIMITED)
            incomingFrames.close()

            provider.webSocketConnector = { _, block ->
                coroutineScope {
                    createMockWebSocketSession(coroutineContext, incomingFrames).block()
                }
            }

            try {
                provider.connect(apiKey = "key-123", tools = emptyList()) {}
                fail("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("setupComplete"))
            }
        }

    @Test
    fun `connect sends selected GeminiLiveVoice from SettingsRepository in setup message`() =
        runTest {
            every { settingsRepository.geminiLiveVoice } returns flowOf(GeminiLiveVoice.AOEDE)

            val incomingFrames = Channel<Frame>(Channel.UNLIMITED)
            incomingFrames.trySend(Frame.Text("""{"setupComplete":{}}"""))
            incomingFrames.close()

            val sentFrames = mutableListOf<String>()
            provider.webSocketConnector = { _, block ->
                coroutineScope {
                    createMockWebSocketSession(
                        context = coroutineContext,
                        incomingFrames = incomingFrames,
                        onSendText = { sentFrames.add(it) },
                    ).block()
                }
            }

            provider.connect(apiKey = "key-123", tools = emptyList()) {}

            val setupVoiceName =
                Json.parseToJsonElement(sentFrames.first())
                    .jsonObject["setup"]!!
                    .jsonObject["generationConfig"]!!
                    .jsonObject["speechConfig"]!!
                    .jsonObject["voiceConfig"]!!
                    .jsonObject["prebuiltVoiceConfig"]!!
                    .jsonObject["voiceName"]!!
                    .jsonPrimitive
                    .content
            assertEquals("Aoede", setupVoiceName)
        }

    @Test
    fun `connect exchanges realtimeInput audio, parses server events, and sends mapped toolResponse`() =
        runTest {
            val tool = createSampleNoteTool()
            val expectedPcm = byteArrayOf(10, 20, 30, 40)
            val encodedPcm = Base64.getEncoder().encodeToString(expectedPcm)

            val incomingFrames = Channel<Frame>(Channel.UNLIMITED)
            val sentFrames = mutableListOf<String>()
            var capturedWsUrl: String? = null

            incomingFrames.trySend(Frame.Text("""{"setupComplete":{}}"""))
            incomingFrames.trySend(
                Frame.Text(
                    """
                    {
                      "serverContent": {
                        "interrupted": true,
                        "inputTranscription": { "text": "Create a note" },
                        "outputTranscription": { "text": "Creating note" },
                        "modelTurn": {
                          "parts": [
                            {
                              "inlineData": {
                                "mimeType": "audio/pcm;rate=24000",
                                "data": "$encodedPcm"
                              }
                            }
                          ]
                        },
                        "turnComplete": true
                      }
                    }
                    """.trimIndent(),
                ),
            )
            incomingFrames.trySend(
                Frame.Text(
                    """
                    {
                      "toolCall": {
                        "functionCalls": [
                          {
                            "id": "call-1",
                            "name": "com_example_notes_createNote",
                            "args": {
                              "title": "Shopping",
                              "meta": { "pinned": true, "priority": 2 }
                            }
                          },
                          {
                            "id": "call-unknown",
                            "name": "hallucinated_tool",
                            "args": {}
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                ),
            )
            incomingFrames.close()

            provider.webSocketConnector = { wsUrl, block ->
                capturedWsUrl = wsUrl
                coroutineScope {
                    val ws =
                        createMockWebSocketSession(
                            context = coroutineContext,
                            incomingFrames = incomingFrames,
                            onSendText = { sentFrames.add(it) },
                        )
                    ws.block()
                }
            }

            val receivedEvents = mutableListOf<LiveLlmEvent>()
            provider.connect(apiKey = "secret-key", tools = listOf(tool)) { session ->
                session.sendAudio(byteArrayOf(1, 2, 3))
                receivedEvents.addAll(session.events.toList())
                session.sendToolResponses(
                    listOf(
                        ToolOutput(
                            functionId = tool.id,
                            callId = "call-1",
                            result = """{"noteId":"n-1"}""",
                        ),
                        ToolOutput(
                            functionId = "hallucinated_tool",
                            callId = "call-unknown",
                            result = "Tool not found: hallucinated_tool",
                        ),
                    ),
                )
            }

            assertTrue(capturedWsUrl!!.endsWith("?key=secret-key"))
            assertEquals(3, sentFrames.size)

            // Frame 0: setup
            assertTrue(Json.parseToJsonElement(sentFrames[0]).jsonObject.containsKey("setup"))

            // Frame 1: realtimeInput audio
            val realtimeAudio =
                Json.parseToJsonElement(sentFrames[1])
                    .jsonObject["realtimeInput"]!!
                    .jsonObject["audio"]!!
                    .jsonObject
            assertEquals("audio/pcm;rate=16000", realtimeAudio["mimeType"]!!.jsonPrimitive.content)
            assertArrayEquals(
                byteArrayOf(1, 2, 3),
                Base64.getDecoder().decode(realtimeAudio["data"]!!.jsonPrimitive.content),
            )

            // Received events
            assertEquals(6, receivedEvents.size)
            assertEquals(LiveLlmEvent.Interrupted, receivedEvents[0])
            assertEquals(LiveLlmEvent.InputTranscription("Create a note"), receivedEvents[1])
            assertEquals(LiveLlmEvent.OutputTranscription("Creating note"), receivedEvents[2])
            assertArrayEquals(expectedPcm, (receivedEvents[3] as LiveLlmEvent.AudioChunk).pcmBytes)
            assertEquals(LiveLlmEvent.TurnComplete, receivedEvents[4])

            val toolCallReq = receivedEvents[5] as LiveLlmEvent.ToolCallRequest
            assertEquals(2, toolCallReq.calls.size)
            assertEquals("com.example.notes", toolCallReq.calls[0].packageName)
            assertEquals("com.example.notes.createNote", toolCallReq.calls[0].functionId)
            assertEquals("Shopping", toolCallReq.calls[0].arguments["title"])
            @Suppress("UNCHECKED_CAST")
            val meta = toolCallReq.calls[0].arguments["meta"] as Map<String, Any?>
            assertEquals(true, meta["pinned"])
            assertEquals(2L, meta["priority"])

            assertEquals("", toolCallReq.calls[1].packageName)
            assertEquals("hallucinated_tool", toolCallReq.calls[1].functionId)

            // Frame 2: toolResponse
            val responses =
                Json.parseToJsonElement(sentFrames[2])
                    .jsonObject["toolResponse"]!!
                    .jsonObject["functionResponses"]!!
                    .jsonArray
            assertEquals(2, responses.size)
            assertEquals(
                "com_example_notes_createNote",
                responses[0].jsonObject["name"]!!.jsonPrimitive.content,
            )
            assertEquals(
                "n-1",
                responses[0]
                    .jsonObject["response"]!!
                    .jsonObject["result"]!!
                    .jsonObject["noteId"]!!
                    .jsonPrimitive
                    .content,
            )
            assertEquals(
                "hallucinated_tool",
                responses[1].jsonObject["name"]!!.jsonPrimitive.content,
            )
        }

    private fun createMockWebSocketSession(
        context: kotlin.coroutines.CoroutineContext,
        incomingFrames: Channel<Frame>,
        onSendText: (String) -> Unit = {},
    ): WebSocketSession {
        val outgoingFrames = Channel<Frame>(Channel.UNLIMITED)
        return mockk<WebSocketSession>(relaxed = true).apply {
            every { coroutineContext } returns context
            every { incoming } returns incomingFrames
            every { outgoing } returns outgoingFrames
            coEvery { send(any<Frame>()) } coAnswers {
                val frame = firstArg<Frame>()
                if (frame is Frame.Text) {
                    onSendText(frame.readText())
                }
            }
        }
    }

    private fun createSampleNoteTool(): AppFunctionMetadata =
        AppFunctionMetadata(
            id = "com.example.notes.createNote",
            packageName = "com.example.notes",
            isEnabled = true,
            schema = null,
            parameters =
                listOf(
                    AppFunctionParameterMetadata(
                        name = "title",
                        isRequired = true,
                        dataType = AppFunctionStringTypeMetadata(isNullable = false),
                        description = "Note title",
                    ),
                ),
            response =
                AppFunctionResponseMetadata(
                    valueType = AppFunctionStringTypeMetadata(isNullable = false),
                    description = "",
                ),
            components = AppFunctionComponentsMetadata(emptyMap()),
            description = "Creates a note",
            deprecation = null,
        )
}
