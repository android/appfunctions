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
import com.example.appfunctions.agent.domain.withSession
import io.ktor.client.HttpClient
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
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
import java.io.IOException
import java.util.Base64
import kotlin.coroutines.CoroutineContext

class GeminiLiveProviderImplTest {
    private lateinit var httpClient: HttpClient
    private lateinit var toolConverter: GeminiLiveToolConverter

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        httpClient = mockk(relaxed = true)
        toolConverter = GeminiLiveToolConverter(GeminiToolConverter())
    }

    @Test
    fun `connect sends setup message with model, audio modality, voice, transcriptions, and tools`() =
        runTest {
            val tool = createSampleNoteTool()
            val incomingFrames = Channel<Frame>(Channel.UNLIMITED)
            incomingFrames.trySend(Frame.Text("""{"setupComplete":{}}"""))
            incomingFrames.close()

            val sentFrames = mutableListOf<String>()
            var capturedWsUrl: String? = null
            val provider =
                createProvider { wsUrl ->
                    capturedWsUrl = wsUrl
                    createMockWebSocketSession(
                        context = coroutineContext,
                        incomingFrames = incomingFrames,
                        onSendText = { sentFrames.add(it) },
                    )
                }

            provider.withSession(
                apiKey = "secret-key",
                tools = listOf(tool),
                voice = GeminiLiveVoice.AOEDE,
            ) {}

            assertTrue(capturedWsUrl!!.endsWith("?key=secret-key"))
            val setup = Json.parseToJsonElement(sentFrames.first()).jsonObject["setup"]!!.jsonObject
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
            assertEquals("Aoede", voiceName)

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

            val provider =
                createProvider {
                    createMockWebSocketSession(coroutineContext, incomingFrames)
                }

            try {
                provider.connect(apiKey = "key-123", tools = emptyList())
                fail("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("setupComplete"))
            }
        }

    @Test
    fun `connect throws IllegalStateException when first frame is missing setupComplete`() =
        runTest {
            val incomingFrames = Channel<Frame>(Channel.UNLIMITED)
            incomingFrames.trySend(Frame.Text("""{"error":{"message":"Invalid argument"}}"""))
            incomingFrames.close()

            val provider =
                createProvider {
                    createMockWebSocketSession(coroutineContext, incomingFrames)
                }

            try {
                provider.connect(apiKey = "key-123", tools = emptyList())
                fail("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.startsWith("Unexpected setup response:"))
            }
        }

    @Test
    fun `connect rethrows CancellationException during handshake without wrapping as IllegalStateException`() =
        runTest {
            val wsSession = mockk<WebSocketSession>(relaxed = true)
            every { wsSession.coroutineContext } returns coroutineContext
            every { wsSession.incoming } returns
                mockk {
                    coEvery { receive() } throws CancellationException("Handshake cancelled")
                }

            val provider = createProvider { wsSession }

            try {
                provider.connect(apiKey = "key-123", tools = emptyList())
                fail("Expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("Handshake cancelled", e.message)
            }
        }

    @Test
    fun `withSession closes WebSocket with NORMAL CloseReason when block completes normally or throws`() =
        runTest {
            val incomingFramesNormal = Channel<Frame>(Channel.UNLIMITED)
            incomingFramesNormal.trySend(Frame.Text("""{"setupComplete":{}}"""))
            incomingFramesNormal.close()
            val normalCloseReasons = mutableListOf<CloseReason?>()

            val providerNormal =
                createProvider {
                    createMockWebSocketSession(
                        context = coroutineContext,
                        incomingFrames = incomingFramesNormal,
                        onSendClose = { normalCloseReasons.add(it) },
                    )
                }

            providerNormal.withSession(apiKey = "key-123", tools = emptyList()) {}

            assertEquals(
                listOf(CloseReason(CloseReason.Codes.NORMAL, "Session ended by client")),
                normalCloseReasons,
            )

            val incomingFramesFailing = Channel<Frame>(Channel.UNLIMITED)
            incomingFramesFailing.trySend(Frame.Text("""{"setupComplete":{}}"""))
            incomingFramesFailing.close()
            val failingCloseReasons = mutableListOf<CloseReason?>()

            val providerFailing =
                createProvider {
                    createMockWebSocketSession(
                        context = coroutineContext,
                        incomingFrames = incomingFramesFailing,
                        onSendClose = { failingCloseReasons.add(it) },
                    )
                }

            try {
                providerFailing.withSession(apiKey = "key-123", tools = emptyList()) {
                    throw IllegalStateException("Caller failed")
                }
                fail("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                assertEquals("Caller failed", e.message)
            }

            assertEquals(
                listOf(CloseReason(CloseReason.Codes.NORMAL, "Session ended by client")),
                failingCloseReasons,
            )
        }

    @Test
    fun `connect and events decode Frame_Binary server frames`() =
        runTest {
            val incomingFrames = Channel<Frame>(Channel.UNLIMITED)
            incomingFrames.trySend(
                Frame.Binary(fin = true, data = """{"setupComplete":{}}""".encodeToByteArray()),
            )
            incomingFrames.trySend(
                Frame.Binary(
                    fin = true,
                    data = """{"serverContent":{"turnComplete":true}}""".encodeToByteArray(),
                ),
            )
            incomingFrames.close()

            val provider =
                createProvider {
                    createMockWebSocketSession(coroutineContext, incomingFrames)
                }

            val receivedEvents = mutableListOf<LiveLlmEvent>()
            provider.withSession(apiKey = "key-123", tools = emptyList()) { session ->
                receivedEvents.addAll(session.events.toList())
            }

            assertEquals(listOf(LiveLlmEvent.TurnComplete), receivedEvents)
        }

    @Test
    fun `sendAudio encodes PCM bytes with specified sampleRateHz in realtimeInput frame`() =
        runTest {
            val incomingFrames = Channel<Frame>(Channel.UNLIMITED)
            incomingFrames.trySend(Frame.Text("""{"setupComplete":{}}"""))
            incomingFrames.close()

            val sentFrames = mutableListOf<String>()
            val provider =
                createProvider {
                    createMockWebSocketSession(
                        context = coroutineContext,
                        incomingFrames = incomingFrames,
                        onSendText = { sentFrames.add(it) },
                    )
                }

            provider.withSession(apiKey = "key-123", tools = emptyList()) { session ->
                session.sendAudio(pcmBytes = byteArrayOf(1, 2, 3), sampleRateHz = 16_000)
            }

            assertEquals(2, sentFrames.size)
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
        }

    @Test
    fun `events emits Interrupted when serverContent has interrupted true`() =
        runTest {
            val events =
                collectSingleServerFrame(
                    """{"serverContent":{"interrupted":true}}""",
                )
            assertEquals(listOf(LiveLlmEvent.Interrupted), events)
        }

    @Test
    fun `events emits InputTranscription when serverContent has inputTranscription`() =
        runTest {
            val events =
                collectSingleServerFrame(
                    """{"serverContent":{"inputTranscription":{"text":"Create a note"}}}""",
                )
            assertEquals(listOf(LiveLlmEvent.InputTranscription("Create a note")), events)
        }

    @Test
    fun `events emits OutputTranscription when serverContent has outputTranscription`() =
        runTest {
            val events =
                collectSingleServerFrame(
                    """{"serverContent":{"outputTranscription":{"text":"Creating note"}}}""",
                )
            assertEquals(listOf(LiveLlmEvent.OutputTranscription("Creating note")), events)
        }

    @Test
    fun `events emits AudioChunk with sampleRateHz when serverContent has modelTurn inlineData`() =
        runTest {
            val expectedPcm = byteArrayOf(10, 20, 30, 40)
            val encodedPcm = Base64.getEncoder().encodeToString(expectedPcm)

            val events =
                collectSingleServerFrame(
                    """
                    {
                      "serverContent": {
                        "modelTurn": {
                          "parts": [
                            {
                              "inlineData": {
                                "mimeType": "audio/pcm;rate=24000",
                                "data": "$encodedPcm"
                              }
                            }
                          ]
                        }
                      }
                    }
                    """.trimIndent(),
                )

            assertEquals(
                listOf(LiveLlmEvent.AudioChunk(pcmBytes = expectedPcm, sampleRateHz = 24_000)),
                events,
            )
        }

    @Test
    fun `events emits TurnComplete when serverContent has turnComplete true`() =
        runTest {
            val events =
                collectSingleServerFrame(
                    """{"serverContent":{"turnComplete":true}}""",
                )
            assertEquals(listOf(LiveLlmEvent.TurnComplete), events)
        }

    @Test
    fun `events emits ToolCallRequest and sendToolResponses sends mapped functionResponses`() =
        runTest {
            val tool = createSampleNoteTool()
            val incomingFrames = Channel<Frame>(Channel.UNLIMITED)
            incomingFrames.trySend(Frame.Text("""{"setupComplete":{}}"""))
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
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                ),
            )
            incomingFrames.close()

            val sentFrames = mutableListOf<String>()
            val provider =
                createProvider {
                    createMockWebSocketSession(
                        context = coroutineContext,
                        incomingFrames = incomingFrames,
                        onSendText = { sentFrames.add(it) },
                    )
                }

            val receivedEvents = mutableListOf<LiveLlmEvent>()
            provider.withSession(apiKey = "key-123", tools = listOf(tool)) { session ->
                receivedEvents.addAll(session.events.toList())
                session.sendToolResponses(
                    listOf(
                        ToolOutput(
                            functionId = tool.id,
                            callId = "call-1",
                            result = """{"noteId":"n-1"}""",
                        ),
                    ),
                )
            }

            assertEquals(1, receivedEvents.size)
            val toolCallReq = receivedEvents.single() as LiveLlmEvent.ToolCallRequest
            assertEquals(1, toolCallReq.calls.size)
            assertEquals("com.example.notes", toolCallReq.calls[0].packageName)
            assertEquals("com.example.notes.createNote", toolCallReq.calls[0].functionId)
            assertEquals("call-1", toolCallReq.calls[0].callId)
            assertEquals("Shopping", toolCallReq.calls[0].arguments["title"])
            @Suppress("UNCHECKED_CAST")
            val meta = toolCallReq.calls[0].arguments["meta"] as Map<String, Any?>
            assertEquals(true, meta["pinned"])
            assertEquals(2L, meta["priority"])

            val responses =
                Json.parseToJsonElement(sentFrames.last())
                    .jsonObject["toolResponse"]!!
                    .jsonObject["functionResponses"]!!
                    .jsonArray
            assertEquals(1, responses.size)
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
        }

    @Test
    fun `events emits Error when toolCall references unknown function or is missing id or name`() =
        runTest {
            val tool = createSampleNoteTool()
            val events =
                collectSingleServerFrame(
                    frameJson =
                        """
                        {
                          "toolCall": {
                            "functionCalls": [
                              {
                                "id": "call-unknown",
                                "name": "hallucinated_tool",
                                "args": {}
                              },
                              {
                                "name": "com_example_notes_createNote",
                                "args": {}
                              },
                              {
                                "id": "call-no-name",
                                "args": {}
                              }
                            ]
                          }
                        }
                        """.trimIndent(),
                    tools = listOf(tool),
                )

            assertEquals(3, events.size)
            assertTrue((events[0] as LiveLlmEvent.Error).message.contains("hallucinated_tool"))
            assertTrue((events[1] as LiveLlmEvent.Error).message.contains("id"))
            assertTrue((events[2] as LiveLlmEvent.Error).message.contains("name"))
        }

    @Test
    fun `events recovers from malformed JSON frame mid-session and emits subsequent valid events`() =
        runTest {
            val incomingFrames = Channel<Frame>(Channel.UNLIMITED)
            incomingFrames.trySend(Frame.Text("""{"setupComplete":{}}"""))
            incomingFrames.trySend(Frame.Text("not-valid-json"))
            incomingFrames.trySend(Frame.Text("""{"serverContent":{"turnComplete":true}}"""))
            incomingFrames.close()

            val provider =
                createProvider {
                    createMockWebSocketSession(coroutineContext, incomingFrames)
                }

            val receivedEvents = mutableListOf<LiveLlmEvent>()
            provider.withSession(apiKey = "key-123", tools = emptyList()) { session ->
                receivedEvents.addAll(session.events.toList())
            }

            assertEquals(2, receivedEvents.size)
            assertTrue(receivedEvents[0] is LiveLlmEvent.Error)
            assertEquals(LiveLlmEvent.TurnComplete, receivedEvents[1])
        }

    @Test
    fun `events emits Error when server closes WebSocket with non-normal CloseReason or transport exception`() =
        runTest {
            val incomingCloseFrames = Channel<Frame>(Channel.UNLIMITED)
            incomingCloseFrames.trySend(Frame.Text("""{"setupComplete":{}}"""))
            incomingCloseFrames.trySend(
                Frame.Close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid API key")),
            )
            incomingCloseFrames.close()

            val providerClose =
                createProvider {
                    createMockWebSocketSession(coroutineContext, incomingCloseFrames)
                }

            val closeEvents = mutableListOf<LiveLlmEvent>()
            providerClose.withSession(apiKey = "bad-key", tools = emptyList()) { session ->
                closeEvents.addAll(session.events.toList())
            }

            assertEquals(1, closeEvents.size)
            assertTrue((closeEvents.single() as LiveLlmEvent.Error).message.contains("Invalid API key"))

            var receiveCount = 0
            val wsSession = mockk<WebSocketSession>(relaxed = true)
            every { wsSession.coroutineContext } returns coroutineContext
            every { wsSession.incoming } returns
                mockk {
                    coEvery { receive() } coAnswers {
                        receiveCount++
                        if (receiveCount == 1) {
                            Frame.Text("""{"setupComplete":{}}""")
                        } else {
                            throw IOException("Connection reset by peer")
                        }
                    }
                }

            val providerError = createProvider { wsSession }
            val transportEvents = mutableListOf<LiveLlmEvent>()
            providerError.withSession(apiKey = "key-123", tools = emptyList()) { session ->
                transportEvents.addAll(session.events.toList())
            }

            assertEquals(1, transportEvents.size)
            assertTrue(
                (transportEvents.single() as LiveLlmEvent.Error)
                    .message
                    .contains("Connection reset by peer"),
            )
        }

    private suspend fun CoroutineScope.collectSingleServerFrame(
        frameJson: String,
        tools: List<AppFunctionMetadata> = emptyList(),
    ): List<LiveLlmEvent> {
        val incomingFrames = Channel<Frame>(Channel.UNLIMITED)
        incomingFrames.trySend(Frame.Text("""{"setupComplete":{}}"""))
        incomingFrames.trySend(Frame.Text(frameJson))
        incomingFrames.close()

        val provider =
            createProvider {
                createMockWebSocketSession(coroutineContext, incomingFrames)
            }

        val receivedEvents = mutableListOf<LiveLlmEvent>()
        provider.withSession(apiKey = "key-123", tools = tools) { session ->
            receivedEvents.addAll(session.events.toList())
        }
        return receivedEvents
    }

    private fun createProvider(connector: suspend (String) -> WebSocketSession): GeminiLiveProviderImpl =
        GeminiLiveProviderImpl(
            httpClient = httpClient,
            toolConverter = toolConverter,
            webSocketConnector = connector,
        )

    private fun createMockWebSocketSession(
        context: CoroutineContext,
        incomingFrames: Channel<Frame>,
        onSendText: (String) -> Unit = {},
        onSendClose: (CloseReason?) -> Unit = {},
    ): WebSocketSession {
        val outgoingFrames = Channel<Frame>(Channel.UNLIMITED)
        return mockk<WebSocketSession>(relaxed = true).apply {
            every { coroutineContext } returns context
            every { incoming } returns incomingFrames
            every { outgoing } returns outgoingFrames
            coEvery { send(any<Frame>()) } coAnswers {
                when (val frame = firstArg<Frame>()) {
                    is Frame.Text -> onSendText(frame.readText())
                    is Frame.Close -> onSendClose(frame.readReason())
                    else -> {}
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
