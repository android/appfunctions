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

import android.app.PendingIntent
import android.util.Log
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class LiveAgentOrchestratorTest {
    private lateinit var llmProviderFactory: LlmProviderFactory
    private lateinit var liveLlmProvider: LiveLlmProvider
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var getEnabledToolsUseCase: GetEnabledToolsUseCase
    private lateinit var executeToolCallUseCase: ExecuteToolCallUseCase
    private lateinit var savePendingIntentUseCase: SavePendingIntentUseCase
    private lateinit var sendMessageUseCase: SendMessageUseCase
    private var currentAudioFactory: () -> LiveAudioSession = { mockk(relaxed = true) }

    private lateinit var orchestrator: LiveAgentOrchestrator

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        llmProviderFactory = mockk()
        liveLlmProvider = mockk()
        settingsRepository = mockk(relaxed = true)
        getEnabledToolsUseCase = mockk()
        executeToolCallUseCase = mockk()
        savePendingIntentUseCase = mockk(relaxed = true)
        sendMessageUseCase = mockk(relaxed = true)

        every { settingsRepository.selectedProvider } returns flowOf(LlmProviderName.GEMINI)
        every { settingsRepository.geminiApiKey } returns flowOf("test-api-key")
        every { llmProviderFactory.getLiveProvider(LlmProviderName.GEMINI) } returns liveLlmProvider
        coEvery { getEnabledToolsUseCase(any()) } returns emptyList()

        orchestrator =
            LiveAgentOrchestrator(
                llmProviderFactory = llmProviderFactory,
                settingsRepository = settingsRepository,
                getEnabledToolsUseCase = getEnabledToolsUseCase,
                executeToolCallUseCase = executeToolCallUseCase,
                savePendingIntentUseCase = savePendingIntentUseCase,
                sendMessageUseCase = sendMessageUseCase,
                audioSessionFactory = LiveAudioSessionFactory { currentAudioFactory() },
            )
    }

    @Test
    fun `startSession reports Error and persists FAILED message when API key is missing`() =
        runTest {
            val ioDispatcher = StandardTestDispatcher(testScheduler)
            orchestrator.ioDispatcher = ioDispatcher
            every { settingsRepository.geminiApiKey } returns flowOf(null)

            orchestrator.startSession("thread-1")
            advanceUntilIdle()

            assertEquals(
                LiveSessionState.Error("API key is missing for GEMINI"),
                orchestrator.state.value,
            )
            coVerify {
                sendMessageUseCase(
                    threadId = "thread-1",
                    role = MessageRole.ASSISTANT,
                    textContent = "API key is missing for GEMINI",
                    processingStatus = MessageProcessingStatus.FAILED,
                )
            }
        }

    @Test
    fun `stopSession does not call audioSession release synchronously on caller thread`() =
        runTest {
            val ioDispatcher = StandardTestDispatcher(testScheduler)
            orchestrator.ioDispatcher = ioDispatcher

            val mockAudio =
                mockk<LiveAudioSession>(relaxed = true).apply {
                    every { listenToRecording() } returns emptyFlow()
                }
            currentAudioFactory = { mockAudio }

            val eventChannel = Channel<LiveLlmEvent>(Channel.UNLIMITED)
            stubLiveConnection(eventChannel.receiveAsFlow())

            orchestrator.startSession("thread-1")
            runCurrent()
            assertEquals(LiveSessionState.Listening, orchestrator.state.value)

            orchestrator.stopSession()
            assertEquals(LiveSessionState.Idle, orchestrator.state.value)
            verify(exactly = 0) { mockAudio.release() }

            advanceUntilIdle()
            verify(exactly = 1) { mockAudio.release() }
        }

    @Test
    fun `startSession waits for prior session audio teardown before starting new LiveAudioSession`() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val session1Listening = CountDownLatch(1)
        val release1Entered = CountDownLatch(1)
        val allowRelease1Finish = CountDownLatch(1)
        val session2Started = CountDownLatch(1)

        val audio1 =
            mockk<LiveAudioSession>(relaxed = true).apply {
                every { listenToRecording() } returns emptyFlow()
                every { start() } answers {
                    events.add("start1")
                    session1Listening.countDown()
                }
                every { release() } answers {
                    events.add("release1_begin")
                    release1Entered.countDown()
                    assertTrue(allowRelease1Finish.await(2, TimeUnit.SECONDS))
                    events.add("release1_end")
                }
            }
        val audio2 =
            mockk<LiveAudioSession>(relaxed = true).apply {
                every { listenToRecording() } returns emptyFlow()
                every { start() } answers {
                    events.add("start2")
                    session2Started.countDown()
                }
                every { release() } answers {
                    events.add("release2")
                }
            }

        val counter = AtomicInteger(0)
        currentAudioFactory = { if (counter.incrementAndGet() == 1) audio1 else audio2 }

        val ch1 = Channel<LiveLlmEvent>(Channel.UNLIMITED)
        val ch2 = Channel<LiveLlmEvent>(Channel.UNLIMITED)
        val connCounter = AtomicInteger(0)
        coEvery { liveLlmProvider.connect(any(), any(), any()) } coAnswers {
            val block = thirdArg<suspend (LiveLlmSession) -> Unit>()
            val flow = if (connCounter.incrementAndGet() == 1) ch1.receiveAsFlow() else ch2.receiveAsFlow()
            block(FakeLiveLlmSession(flow))
        }

        orchestrator.startSession("thread-1")
        assertTrue(session1Listening.await(2, TimeUnit.SECONDS))

        orchestrator.stopSession()
        assertTrue(release1Entered.await(2, TimeUnit.SECONDS))

        orchestrator.startSession("thread-1")
        Thread.sleep(50)
        assertEquals(listOf("start1", "release1_begin"), events.toList())

        allowRelease1Finish.countDown()
        assertTrue(session2Started.await(2, TimeUnit.SECONDS))
        assertEquals(
            listOf("start1", "release1_begin", "release1_end", "start2"),
            events.toList(),
        )

        orchestrator.stopSession()
    }

    @Test
    fun `streams audio chunks, flushes transcriptions in order, and stops playback on interruption`() =
        runTest {
            val ioDispatcher = StandardTestDispatcher(testScheduler)
            orchestrator.ioDispatcher = ioDispatcher

            val micPcm = byteArrayOf(1, 2, 3, 4)
            val modelPcm = byteArrayOf(9, 8, 7, 6)
            val queuedChunks = mutableListOf<ByteArray>()
            val mockAudio =
                mockk<LiveAudioSession>(relaxed = true).apply {
                    every { listenToRecording() } returns flowOf(micPcm)
                    every { queueAudio(any()) } answers { queuedChunks.add(firstArg()) }
                }
            currentAudioFactory = { mockAudio }

            val eventChannel = Channel<LiveLlmEvent>(Channel.UNLIMITED)
            val fakeSession = stubLiveConnection(eventChannel.receiveAsFlow())

            orchestrator.startSession("thread-1")
            runCurrent()

            eventChannel.trySend(LiveLlmEvent.InputTranscription("What is "))
            eventChannel.trySend(LiveLlmEvent.InputTranscription("the weather?"))
            eventChannel.trySend(LiveLlmEvent.OutputTranscription("It is sunny"))
            eventChannel.trySend(LiveLlmEvent.AudioChunk(modelPcm))
            eventChannel.trySend(LiveLlmEvent.Interrupted)
            eventChannel.close()
            advanceUntilIdle()

            assertEquals(1, fakeSession.sentAudio.size)
            assertArrayEquals(micPcm, fakeSession.sentAudio.first())
            assertEquals(1, queuedChunks.size)
            assertArrayEquals(modelPcm, queuedChunks.first())
            verify(exactly = 1) { mockAudio.stopAudio() }
            coVerifyOrder {
                sendMessageUseCase(
                    threadId = "thread-1",
                    role = MessageRole.USER,
                    textContent = "What is the weather?",
                    processingStatus = MessageProcessingStatus.PROCESSED,
                )
                sendMessageUseCase(
                    threadId = "thread-1",
                    role = MessageRole.ASSISTANT,
                    textContent = "It is sunny",
                    processingStatus = MessageProcessingStatus.PROCESSED,
                )
            }
        }

    @Test
    fun `ToolCallRequest gates recording, updates status, saves PendingIntent and attachments, and sends ToolOutputs`() =
        runTest {
            val ioDispatcher = StandardTestDispatcher(testScheduler)
            orchestrator.ioDispatcher = ioDispatcher

            val noteTool = createSampleNoteTool()
            coEvery { getEnabledToolsUseCase("com.example.notes") } returns listOf(noteTool)

            val noteCall =
                LlmResponsePart.ToolCall(
                    packageName = "com.example.notes",
                    functionId = noteTool.id,
                    arguments = mapOf("title" to "Shopping"),
                    callId = "call-1",
                )
            val confirmCall =
                LlmResponsePart.ToolCall(
                    packageName = "com.example.notes",
                    functionId = "deleteAll",
                    arguments = emptyMap(),
                    callId = "call-2",
                )
            val pendingIntent = mockk<PendingIntent>()
            val observedStatuses = mutableListOf<AgentStatus>()

            coEvery {
                executeToolCallUseCase(noteCall, listOf(noteTool), "thread-1")
            } answers {
                observedStatuses.add(orchestrator.status.value)
                ToolCallExecutionResult.Success(
                    toolOutput = ToolOutput(noteTool.id, "call-1", """{"status":"ok"}"""),
                    attachment = MessageAttachment("content://img/1.png", "image/png"),
                )
            }
            coEvery {
                executeToolCallUseCase(confirmCall, listOf(noteTool), "thread-1")
            } answers {
                observedStatuses.add(orchestrator.status.value)
                ToolCallExecutionResult.PendingIntentRequired("pi-123", pendingIntent)
            }

            val mockAudio =
                mockk<LiveAudioSession>(relaxed = true).apply {
                    every { listenToRecording() } returns emptyFlow()
                }
            currentAudioFactory = { mockAudio }

            val eventChannel = Channel<LiveLlmEvent>(Channel.UNLIMITED)
            eventChannel.trySend(LiveLlmEvent.ToolCallRequest(listOf(noteCall, confirmCall)))
            val toolResponseSent = CompletableDeferred<Unit>()
            val fakeSession =
                stubLiveConnection(
                    events = eventChannel.receiveAsFlow(),
                    onToolResponses = {
                        toolResponseSent.complete(Unit)
                        eventChannel.close()
                    },
                )

            orchestrator.startSession("thread-1", targetPackageName = "com.example.notes")
            advanceUntilIdle()

            assertTrue(toolResponseSent.isCompleted)
            assertEquals(AgentStatus.Idle, orchestrator.status.value)
            assertEquals(
                listOf(
                    AgentStatus.InvokingTool(noteTool.id, "com.example.notes"),
                    AgentStatus.InvokingTool("deleteAll", "com.example.notes"),
                ),
                observedStatuses,
            )
            verifyOrder {
                mockAudio.start()
                mockAudio.pauseRecording()
                mockAudio.resumeRecording()
                mockAudio.release()
            }
            verify { savePendingIntentUseCase("pi-123", pendingIntent) }
            coVerify {
                sendMessageUseCase(
                    threadId = "thread-1",
                    role = MessageRole.ASSISTANT,
                    textContent = "",
                    processingStatus = MessageProcessingStatus.PROCESSED,
                    pendingIntentId = "pi-123",
                )
                sendMessageUseCase(
                    threadId = "thread-1",
                    role = MessageRole.ASSISTANT,
                    textContent = "",
                    processingStatus = MessageProcessingStatus.PROCESSED,
                    attachments = listOf(MessageAttachment("content://img/1.png", "image/png")),
                )
            }
            assertEquals(2, fakeSession.sentToolOutputs.single().size)
            assertEquals("""{"status":"ok"}""", fakeSession.sentToolOutputs.single()[0].result)
            assertTrue(
                fakeSession.sentToolOutputs
                    .single()[1]
                    .result
                    .contains("confirmation_required"),
            )
        }

    private fun stubLiveConnection(
        events: Flow<LiveLlmEvent>,
        onToolResponses: (List<ToolOutput>) -> Unit = {},
    ): FakeLiveLlmSession {
        val fakeSession = FakeLiveLlmSession(events, onToolResponses)
        coEvery { liveLlmProvider.connect(any(), any(), any()) } coAnswers {
            val block = thirdArg<suspend (LiveLlmSession) -> Unit>()
            block(fakeSession)
        }
        return fakeSession
    }

    private class FakeLiveLlmSession(
        override val events: Flow<LiveLlmEvent>,
        private val onToolResponses: (List<ToolOutput>) -> Unit = {},
    ) : LiveLlmSession {
        val sentAudio = mutableListOf<ByteArray>()
        val sentToolOutputs = mutableListOf<List<ToolOutput>>()

        override suspend fun sendAudio(pcmBytes: ByteArray) {
            sentAudio.add(pcmBytes)
        }

        override suspend fun sendToolResponses(outputs: List<ToolOutput>) {
            sentToolOutputs.add(outputs)
            onToolResponses(outputs)
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
