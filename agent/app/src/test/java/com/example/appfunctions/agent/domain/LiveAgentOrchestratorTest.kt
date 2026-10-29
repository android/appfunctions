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
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.db.entities.MessageAttachment
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.domain.appfunction.ConvertInputToAppFunctionDataUseCase
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionResult
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsParams
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.appfunction.ResolveRemoteFileToLocalUriUseCase
import com.example.appfunctions.agent.domain.chat.SendMessageUseCase
import com.example.appfunctions.agent.domain.pendingintent.LaunchPendingIntentUseCase
import com.example.appfunctions.agent.ui.screens.agentdemo.FakeSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveAgentOrchestratorTest {
    private lateinit var llmProviderFactory: LlmProviderFactory
    private lateinit var liveLlmProvider: LiveLlmProvider
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var getAppFunctionsUseCase: GetAppFunctionsUseCase
    private lateinit var resolveRemoteFileToLocalUriUseCase: ResolveRemoteFileToLocalUriUseCase
    private lateinit var convertInputToAppFunctionDataUseCase: ConvertInputToAppFunctionDataUseCase
    private lateinit var executeAppFunctionUseCase: ExecuteAppFunctionUseCase
    private lateinit var launchPendingIntentUseCase: LaunchPendingIntentUseCase
    private lateinit var sendMessageUseCase: SendMessageUseCase
    private var currentAudioFactory: (LiveAudioConfig) -> LiveAudioSession = {
        mockk(relaxed = true)
    }

    @Before
    fun setUp() =
        runTest {
            llmProviderFactory = mockk()
            liveLlmProvider = mockk()
            settingsRepository = FakeSettingsRepository()
            settingsRepository.setSelectedProvider(LlmProviderName.GEMINI)
            settingsRepository.setGeminiApiKey("test-api-key")
            getAppFunctionsUseCase = mockk()
            resolveRemoteFileToLocalUriUseCase = mockk()
            convertInputToAppFunctionDataUseCase = mockk()
            executeAppFunctionUseCase = mockk()
            launchPendingIntentUseCase = mockk()
            sendMessageUseCase = mockk(relaxed = true)

            every { launchPendingIntentUseCase(any()) } returns Result.success(Unit)
            every { llmProviderFactory.getLiveProvider(LlmProviderName.GEMINI) } returns liveLlmProvider
            every { getAppFunctionsUseCase(any()) } returns flowOf(emptyMap())
            coEvery { resolveRemoteFileToLocalUriUseCase(any(), any(), any()) } answers { thirdArg() }
            every {
                convertInputToAppFunctionDataUseCase(any(), any(), any())
            } returns Result.success(mockk())
        }

    private fun createOrchestrator(ioDispatcher: CoroutineDispatcher = Dispatchers.IO): LiveAgentOrchestrator =
        LiveAgentOrchestrator(
            llmProviderFactory = llmProviderFactory,
            settingsRepository = settingsRepository,
            getAppFunctionsUseCase = getAppFunctionsUseCase,
            resolveRemoteFileToLocalUriUseCase = resolveRemoteFileToLocalUriUseCase,
            convertInputToAppFunctionDataUseCase = convertInputToAppFunctionDataUseCase,
            executeAppFunctionUseCase = executeAppFunctionUseCase,
            launchPendingIntentUseCase = launchPendingIntentUseCase,
            sendMessageUseCase = sendMessageUseCase,
            audioSessionFactory = LiveAudioSessionFactory { config -> currentAudioFactory(config) },
            ioDispatcher = ioDispatcher,
        )

    @Test
    fun `startSession reports Error and persists FAILED message when API key is missing`() =
        runTest {
            val orchestrator = createOrchestrator(StandardTestDispatcher(testScheduler))
            settingsRepository.setGeminiApiKey("")

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
            val orchestrator = createOrchestrator(StandardTestDispatcher(testScheduler))

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
        val orchestrator = createOrchestrator()
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
            val flow =
                if (connCounter.incrementAndGet() == 1) {
                    ch1.receiveAsFlow()
                } else {
                    ch2.receiveAsFlow()
                }
            FakeLiveLlmSession(flow)
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
            val orchestrator = createOrchestrator(StandardTestDispatcher(testScheduler))

            val micPcm = byteArrayOf(1, 2, 3, 4)
            val modelPcm = byteArrayOf(9, 8, 7, 6)
            val queuedChunks = mutableListOf<Pair<ByteArray, Int>>()
            var capturedConfig: LiveAudioConfig? = null
            val mockAudio =
                mockk<LiveAudioSession>(relaxed = true).apply {
                    every { inputSampleRateHz } returns 16_000
                    every { listenToRecording() } returns flowOf(micPcm)
                    every { queueAudio(any(), any()) } answers {
                        queuedChunks.add(firstArg<ByteArray>() to secondArg<Int>())
                    }
                }
            currentAudioFactory = { config ->
                capturedConfig = config
                mockAudio
            }

            val eventChannel = Channel<LiveLlmEvent>(Channel.UNLIMITED)
            val fakeSession = stubLiveConnection(eventChannel.receiveAsFlow())

            orchestrator.startSession("thread-1")
            runCurrent()

            eventChannel.trySend(LiveLlmEvent.InputTranscription("What is "))
            eventChannel.trySend(LiveLlmEvent.InputTranscription("the weather?"))
            eventChannel.trySend(LiveLlmEvent.OutputTranscription("It is sunny"))
            eventChannel.trySend(LiveLlmEvent.AudioChunk(modelPcm, 24_000))
            eventChannel.trySend(LiveLlmEvent.Interrupted)
            eventChannel.close()
            advanceUntilIdle()

            assertEquals(fakeSession.audioConfig, capturedConfig)
            assertEquals(1, fakeSession.sentAudio.size)
            assertArrayEquals(micPcm, fakeSession.sentAudio.first().first)
            assertEquals(16_000, fakeSession.sentAudio.first().second)
            assertEquals(1, queuedChunks.size)
            assertArrayEquals(modelPcm, queuedChunks.first().first)
            assertEquals(24_000, queuedChunks.first().second)
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
    fun `LiveLlmEvent Error flushes transcripts, persists FAILED message, and transitions state to Error`() =
        runTest {
            val orchestrator = createOrchestrator(StandardTestDispatcher(testScheduler))
            val mockAudio =
                mockk<LiveAudioSession>(relaxed = true).apply {
                    every { listenToRecording() } returns emptyFlow()
                }
            currentAudioFactory = { mockAudio }

            val eventChannel = Channel<LiveLlmEvent>(Channel.UNLIMITED)
            stubLiveConnection(eventChannel.receiveAsFlow())

            orchestrator.startSession("thread-1")
            runCurrent()

            eventChannel.trySend(LiveLlmEvent.InputTranscription("Turn on the lights"))
            eventChannel.trySend(
                LiveLlmEvent.Error("WebSocket closed by server (code=1011): Internal error"),
            )
            advanceUntilIdle()

            assertEquals(
                LiveSessionState.Error(
                    "Live session error: WebSocket closed by server (code=1011): Internal error",
                ),
                orchestrator.state.value,
            )
            verify(atLeast = 1) { mockAudio.stopAudio() }
            verify(exactly = 1) { mockAudio.release() }
            coVerifyOrder {
                sendMessageUseCase(
                    threadId = "thread-1",
                    role = MessageRole.USER,
                    textContent = "Turn on the lights",
                    processingStatus = MessageProcessingStatus.PROCESSED,
                )
                sendMessageUseCase(
                    threadId = "thread-1",
                    role = MessageRole.ASSISTANT,
                    textContent =
                        "Live session error: WebSocket closed by server (code=1011): Internal error",
                    processingStatus = MessageProcessingStatus.FAILED,
                )
            }
        }

    @Test
    fun `ToolCallRequest gates recording, updates status, launches PendingIntent directly, saves attachments, and sends ToolOutputs`() =
        runTest {
            val orchestrator = createOrchestrator(StandardTestDispatcher(testScheduler))

            val notesPkg =
                AppFunctionPackageMetadata(
                    packageName = "com.example.notes",
                    components = AppFunctionComponentsMetadata(),
                )
            val noteTool = createSampleTool(notesPkg, "com.example.notes.createNote")
            val callTool = createSampleTool(notesPkg, "makeCall")
            every {
                getAppFunctionsUseCase(
                    GetAppFunctionsParams(
                        targetPackageNames = setOf("com.example.notes"),
                        isEnabled = true,
                        isConnected = true,
                        includeInternalTools = true,
                    ),
                )
            } returns flowOf(mapOf(notesPkg to listOf(noteTool, callTool)))

            val noteCall =
                LlmResponsePart.ToolCall(
                    packageName = "com.example.notes",
                    functionId = noteTool.id,
                    arguments = mapOf("title" to "Shopping"),
                    callId = "call-1",
                )
            val callAction =
                LlmResponsePart.ToolCall(
                    packageName = "com.example.notes",
                    functionId = "makeCall",
                    arguments = emptyMap(),
                    callId = "call-2",
                )
            val pendingIntent = mockk<PendingIntent>()
            val mockData = mockk<AppFunctionData>()
            val observedStatuses = mutableListOf<AgentStatus>()

            coEvery {
                convertInputToAppFunctionDataUseCase(any(), any(), any())
            } returns Result.success(mockData)
            coEvery {
                executeAppFunctionUseCase(noteTool, mockData, "thread-1")
            } answers {
                observedStatuses.add(orchestrator.status.value)
                ExecuteAppFunctionResult.Data(
                    data = mockData,
                    formattedJson =
                        """{"status":"ok","imageUri":"content://img/1.png","mimeType":"image/png"}""",
                )
            }
            coEvery {
                executeAppFunctionUseCase(callTool, mockData, "thread-1")
            } answers {
                observedStatuses.add(orchestrator.status.value)
                ExecuteAppFunctionResult.PendingIntentAction(pendingIntent)
            }

            val mockAudio =
                mockk<LiveAudioSession>(relaxed = true).apply {
                    every { listenToRecording() } returns emptyFlow()
                }
            currentAudioFactory = { mockAudio }

            val eventChannel = Channel<LiveLlmEvent>(Channel.UNLIMITED)
            eventChannel.trySend(LiveLlmEvent.ToolCallRequest(listOf(noteCall, callAction)))
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
                    AgentStatus.InvokingTool("makeCall", "com.example.notes"),
                ),
                observedStatuses,
            )
            verifyOrder {
                mockAudio.start()
                mockAudio.pauseRecording()
                mockAudio.resumeRecording()
                mockAudio.release()
            }
            verify(exactly = 1) { launchPendingIntentUseCase(pendingIntent) }
            coVerify {
                sendMessageUseCase(
                    threadId = "thread-1",
                    role = MessageRole.ASSISTANT,
                    textContent = "",
                    processingStatus = MessageProcessingStatus.PROCESSED,
                    attachments = listOf(MessageAttachment("content://img/1.png", "image/png")),
                )
            }
            assertEquals(2, fakeSession.sentToolOutputs.single().size)
            assertTrue(
                fakeSession.sentToolOutputs
                    .single()[0]
                    .result
                    .contains("\"status\":\"ok\""),
            )
            assertTrue(
                fakeSession.sentToolOutputs
                    .single()[1]
                    .result
                    .contains("\"status\":\"launched\""),
            )
        }

    @Test
    fun `ToolCallRequest reports failure ToolOutput when PendingIntent launch or AppFunction execution fails`() =
        runTest {
            val orchestrator = createOrchestrator(StandardTestDispatcher(testScheduler))

            val chatPkg =
                AppFunctionPackageMetadata(
                    packageName = "com.example.chat",
                    components = AppFunctionComponentsMetadata(),
                )
            val callTool = createSampleTool(chatPkg, "makeCall")
            val msgTool = createSampleTool(chatPkg, "sendMessage")
            every { getAppFunctionsUseCase(any()) } returns
                flowOf(mapOf(chatPkg to listOf(callTool, msgTool)))

            val pendingIntent = mockk<PendingIntent>()
            val mockData = mockk<AppFunctionData>()
            every { launchPendingIntentUseCase(pendingIntent) } returns
                Result.failure(PendingIntent.CanceledException())
            coEvery {
                convertInputToAppFunctionDataUseCase(any(), any(), any())
            } returns Result.success(mockData)
            coEvery { executeAppFunctionUseCase(callTool, mockData, "thread-1") } returns
                ExecuteAppFunctionResult.PendingIntentAction(pendingIntent)
            coEvery { executeAppFunctionUseCase(msgTool, mockData, "thread-1") } returns
                ExecuteAppFunctionResult.Error(
                    AppFunctionPermissionRequiredException("Contacts permission denied"),
                )

            val callAction =
                LlmResponsePart.ToolCall(
                    packageName = "com.example.chat",
                    functionId = "makeCall",
                    arguments = emptyMap(),
                    callId = "call-err",
                )
            val msgAction =
                LlmResponsePart.ToolCall(
                    packageName = "com.example.chat",
                    functionId = "sendMessage",
                    arguments = emptyMap(),
                    callId = "call-perm",
                )

            val mockAudio =
                mockk<LiveAudioSession>(relaxed = true).apply {
                    every { listenToRecording() } returns emptyFlow()
                }
            currentAudioFactory = { mockAudio }

            val eventChannel = Channel<LiveLlmEvent>(Channel.UNLIMITED)
            eventChannel.trySend(LiveLlmEvent.ToolCallRequest(listOf(callAction, msgAction)))
            val fakeSession =
                stubLiveConnection(
                    events = eventChannel.receiveAsFlow(),
                    onToolResponses = { eventChannel.close() },
                )

            orchestrator.startSession("thread-1")
            advanceUntilIdle()

            val outputs = fakeSession.sentToolOutputs.single()
            assertEquals(2, outputs.size)
            assertTrue(
                outputs[0].result.contains(
                    "Tool execution failed for makeCall: PendingIntent canceled",
                ),
            )
            assertEquals(
                "Tool execution failed for sendMessage: Error: " +
                    "AppFunctionPermissionRequiredException - Contacts permission denied",
                outputs[1].result,
            )
        }

    private fun stubLiveConnection(
        events: Flow<LiveLlmEvent>,
        onToolResponses: (List<ToolOutput>) -> Unit = {},
    ): FakeLiveLlmSession {
        val fakeSession = FakeLiveLlmSession(events, onToolResponses)
        coEvery { liveLlmProvider.connect(any(), any(), any()) } returns fakeSession
        return fakeSession
    }

    private class FakeLiveLlmSession(
        override val events: Flow<LiveLlmEvent>,
        private val onToolResponses: (List<ToolOutput>) -> Unit = {},
    ) : LiveLlmSession {
        override val audioConfig: LiveAudioConfig =
            LiveAudioConfig(
                inputSampleRateHz = 16_000,
                outputSampleRateHz = 24_000,
            )

        val sentAudio = mutableListOf<Pair<ByteArray, Int>>()
        val sentToolOutputs = mutableListOf<List<ToolOutput>>()

        override suspend fun sendAudio(
            pcmBytes: ByteArray,
            sampleRateHz: Int,
        ) {
            sentAudio.add(pcmBytes to sampleRateHz)
        }

        override suspend fun sendToolResponses(outputs: List<ToolOutput>) {
            sentToolOutputs.add(outputs)
            onToolResponses(outputs)
        }

        override suspend fun close() {}
    }

    private fun createSampleTool(
        packageMetadata: AppFunctionPackageMetadata,
        functionId: String,
    ): AppFunctionMetadata =
        AppFunctionMetadata(
            name = AppFunctionName(packageMetadata.packageName, functionId),
            schema = null,
            parameters =
                listOf(
                    AppFunctionParameterMetadata(
                        name = "title",
                        isRequired = false,
                        dataType = AppFunctionStringTypeMetadata(isNullable = true),
                        description = "Optional title",
                    ),
                ),
            response =
                AppFunctionResponseMetadata(
                    valueType = AppFunctionStringTypeMetadata(isNullable = false),
                    description = "",
                ),
            description = "Sample tool",
            deprecation = null,
            packageMetadata = packageMetadata,
        )
}
