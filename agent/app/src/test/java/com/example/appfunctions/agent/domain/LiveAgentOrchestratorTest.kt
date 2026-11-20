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
import com.example.appfunctions.agent.data.GeminiLiveVoice
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.db.entities.MessageAttachment
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import com.example.appfunctions.agent.domain.appfunction.ConvertInputToAppFunctionDataUseCase
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionResult
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsParams
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.appfunction.ResolveRemoteFileToLocalUriUseCase
import com.example.appfunctions.agent.domain.chat.ManageThreadsUseCase
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveAgentOrchestratorTest {
    private lateinit var manageThreadsUseCase: ManageThreadsUseCase
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
            manageThreadsUseCase = mockk()
            llmProviderFactory = mockk()
            liveLlmProvider = mockk()
            settingsRepository = FakeSettingsRepository()
            settingsRepository.setGeminiApiKey("test-api-key")
            settingsRepository.setGeminiLiveVoice(GeminiLiveVoice.AOEDE)
            getAppFunctionsUseCase = mockk()
            resolveRemoteFileToLocalUriUseCase = mockk()
            convertInputToAppFunctionDataUseCase = mockk()
            executeAppFunctionUseCase = mockk()
            launchPendingIntentUseCase = mockk()
            sendMessageUseCase = mockk(relaxed = true)

            every { manageThreadsUseCase.getThread("thread-1") } returns
                flowOf(
                    ThreadEntity(
                        threadId = "thread-1",
                        createdAt = 1L,
                        llmModel = LlmModel(LlmProviderName.GEMINI, "gemini-2.5-flash"),
                    ),
                )
            every { launchPendingIntentUseCase(any()) } returns Result.success(Unit)
            every { llmProviderFactory.getLiveProvider(LlmProviderName.GEMINI) } returns liveLlmProvider
            every { getAppFunctionsUseCase(any()) } returns flowOf(emptyMap())
            coEvery { resolveRemoteFileToLocalUriUseCase(any(), any(), any()) } answers { thirdArg() }
            every {
                convertInputToAppFunctionDataUseCase(any(), any(), any())
            } returns Result.success(mockk())
        }

    private fun createOrchestrator(): LiveAgentOrchestrator =
        LiveAgentOrchestrator(
            manageThreadsUseCase = manageThreadsUseCase,
            llmProviderFactory = llmProviderFactory,
            settingsRepository = settingsRepository,
            getAppFunctionsUseCase = getAppFunctionsUseCase,
            resolveRemoteFileToLocalUriUseCase = resolveRemoteFileToLocalUriUseCase,
            convertInputToAppFunctionDataUseCase = convertInputToAppFunctionDataUseCase,
            executeAppFunctionUseCase = executeAppFunctionUseCase,
            launchPendingIntentUseCase = launchPendingIntentUseCase,
            sendMessageUseCase = sendMessageUseCase,
            audioSessionFactory = LiveAudioSessionFactory { config -> currentAudioFactory(config) },
        )

    @Test
    fun `runSession emits Error and persists FAILED message when API key is missing`() =
        runTest {
            val orchestrator = createOrchestrator()
            settingsRepository.setGeminiApiKey("")

            val statuses = mutableListOf<LiveAgentStatus>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    orchestrator.runSession("thread-1").toList(statuses)
                }
            advanceUntilIdle()
            job.join()

            assertEquals(
                listOf(
                    LiveAgentStatus.Connecting,
                    LiveAgentStatus.Error("API key is missing for GEMINI"),
                ),
                statuses,
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
    fun `runSession emits Error when thread does not exist`() =
        runTest {
            val orchestrator = createOrchestrator()
            every { manageThreadsUseCase.getThread("missing-thread") } returns flowOf(null)

            val statuses = mutableListOf<LiveAgentStatus>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    orchestrator.runSession("missing-thread").toList(statuses)
                }
            advanceUntilIdle()
            job.join()

            assertEquals(
                listOf(
                    LiveAgentStatus.Connecting,
                    LiveAgentStatus.Error("Thread not found: missing-thread"),
                ),
                statuses,
            )
        }

    @Test
    fun `cancelling collector flushes partial transcripts and releases audioSession`() =
        runTest {
            val orchestrator = createOrchestrator()

            val mockAudio =
                mockk<LiveAudioSession>(relaxed = true).apply {
                    every { listenToRecording() } returns emptyFlow()
                }
            currentAudioFactory = { mockAudio }

            val eventChannel = Channel<LiveLlmEvent>(Channel.UNLIMITED)
            stubLiveConnection(eventChannel.receiveAsFlow())

            val statuses = mutableListOf<LiveAgentStatus>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    orchestrator.runSession("thread-1").collect { statuses.add(it) }
                }
            runCurrent()
            assertEquals(
                listOf(LiveAgentStatus.Connecting, LiveAgentStatus.Listening),
                statuses,
            )

            eventChannel.trySend(LiveLlmEvent.InputTranscription("Partial user utterance"))
            runCurrent()

            job.cancel()
            advanceUntilIdle()
            assertEquals(
                listOf(LiveAgentStatus.Connecting, LiveAgentStatus.Listening),
                statuses,
            )
            verify(exactly = 1) { mockAudio.release() }
            coVerify {
                sendMessageUseCase(
                    threadId = "thread-1",
                    role = MessageRole.USER,
                    textContent = "Partial user utterance",
                    processingStatus = MessageProcessingStatus.PROCESSED,
                )
            }
        }

    @Test
    fun `concurrent runSession fails fast while active or tearing down and succeeds after cancelAndJoin`() =
        runTest {
            val orchestrator = createOrchestrator()
            val events = mutableListOf<String>()
            val allowTeardown1Finish = CompletableDeferred<Unit>()

            val audio1 =
                mockk<LiveAudioSession>(relaxed = true).apply {
                    every { listenToRecording() } returns emptyFlow()
                    every { start() } answers { events.add("start1") }
                    every { release() } answers { events.add("release1") }
                }
            val audio2 =
                mockk<LiveAudioSession>(relaxed = true).apply {
                    every { listenToRecording() } returns emptyFlow()
                    every { start() } answers { events.add("start2") }
                    every { release() } answers { events.add("release2") }
                }

            var audioCounter = 0
            currentAudioFactory = { if (++audioCounter == 1) audio1 else audio2 }

            coEvery {
                sendMessageUseCase(
                    threadId = "thread-1",
                    role = MessageRole.USER,
                    textContent = "Hello",
                    processingStatus = MessageProcessingStatus.PROCESSED,
                )
            } coAnswers {
                events.add("teardown1_flush_begin")
                allowTeardown1Finish.await()
                events.add("teardown1_flush_end")
            }

            val ch1 = Channel<LiveLlmEvent>(Channel.UNLIMITED)
            val ch2 = Channel<LiveLlmEvent>(Channel.UNLIMITED)
            var connCounter = 0
            coEvery { liveLlmProvider.connect(any(), any(), any()) } coAnswers {
                val flow =
                    if (++connCounter == 1) {
                        ch1.receiveAsFlow()
                    } else {
                        ch2.receiveAsFlow()
                    }
                FakeLiveLlmSession(flow)
            }

            val statuses1 = mutableListOf<LiveAgentStatus>()
            val job1 =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    orchestrator.runSession("thread-1").collect { statuses1.add(it) }
                }
            runCurrent()
            assertEquals(listOf("start1"), events)

            // A concurrent call while session 1 is active fails fast without starting audio2
            val activeRejectStatuses = orchestrator.runSession("thread-1").toList()
            assertEquals(
                listOf(LiveAgentStatus.Error(LiveAgentOrchestrator.ERROR_SESSION_ALREADY_ACTIVE)),
                activeRejectStatuses,
            )
            assertEquals(listOf("start1"), events)

            ch1.trySend(LiveLlmEvent.InputTranscription("Hello"))
            runCurrent()

            // Cancel session 1 while its NonCancellable teardown is still in flight
            job1.cancel()
            runCurrent()
            assertEquals(listOf("start1", "teardown1_flush_begin"), events)

            // A concurrent call during teardown also fails fast rather than queuing on the mic
            val teardownRejectStatuses = orchestrator.runSession("thread-1").toList()
            assertEquals(
                listOf(LiveAgentStatus.Error(LiveAgentOrchestrator.ERROR_SESSION_ALREADY_ACTIVE)),
                teardownRejectStatuses,
            )

            // Once teardown completes (e.g. caller awaits cancelAndJoin), starting a new session succeeds
            allowTeardown1Finish.complete(Unit)
            job1.join()
            assertEquals(
                listOf("start1", "teardown1_flush_begin", "teardown1_flush_end", "release1"),
                events,
            )

            val job2 =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    orchestrator.runSession("thread-1").collect {}
                }
            runCurrent()
            assertEquals(
                listOf(
                    "start1",
                    "teardown1_flush_begin",
                    "teardown1_flush_end",
                    "release1",
                    "start2",
                ),
                events,
            )

            job2.cancelAndJoin()
            assertEquals(
                listOf(
                    "start1",
                    "teardown1_flush_begin",
                    "teardown1_flush_end",
                    "release1",
                    "start2",
                    "release2",
                ),
                events,
            )
        }

    @Test
    fun `streams audio chunks, passes thread provider voice, flushes transcriptions in order, and emits Ended`() =
        runTest {
            val orchestrator = createOrchestrator()

            val micPcm = byteArrayOf(1, 2, 3, 4)
            val modelPcm = byteArrayOf(9, 8, 7, 6)
            val queuedChunks = mutableListOf<Pair<ByteArray, Int>>()
            var capturedConfig: LiveAudioConfig? = null
            var capturedVoiceName: String? = null
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
            val fakeSession = FakeLiveLlmSession(eventChannel.receiveAsFlow())
            coEvery { liveLlmProvider.connect(any(), any(), any()) } coAnswers {
                capturedVoiceName = thirdArg()
                fakeSession
            }

            val statuses = mutableListOf<LiveAgentStatus>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    orchestrator.runSession("thread-1").toList(statuses)
                }
            runCurrent()

            eventChannel.trySend(LiveLlmEvent.InputTranscription("What is "))
            eventChannel.trySend(LiveLlmEvent.InputTranscription("the weather?"))
            eventChannel.trySend(LiveLlmEvent.OutputTranscription("It is sunny"))
            eventChannel.trySend(LiveLlmEvent.AudioChunk(modelPcm, 24_000))
            eventChannel.trySend(LiveLlmEvent.Interrupted)
            eventChannel.close()
            advanceUntilIdle()
            job.join()

            assertEquals("Aoede", capturedVoiceName)
            assertEquals(fakeSession.audioConfig, capturedConfig)
            assertEquals(1, fakeSession.sentAudio.size)
            assertArrayEquals(micPcm, fakeSession.sentAudio.first().first)
            assertEquals(16_000, fakeSession.sentAudio.first().second)
            assertEquals(1, queuedChunks.size)
            assertArrayEquals(modelPcm, queuedChunks.first().first)
            assertEquals(24_000, queuedChunks.first().second)
            assertEquals(
                listOf(
                    LiveAgentStatus.Connecting,
                    LiveAgentStatus.Listening,
                    LiveAgentStatus.Ended,
                ),
                statuses,
            )
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
    fun `recoverable LiveLlmEvent Error keeps session active while fatal Error ends session with FAILED message`() =
        runTest {
            val orchestrator = createOrchestrator()
            val mockAudio =
                mockk<LiveAudioSession>(relaxed = true).apply {
                    every { listenToRecording() } returns emptyFlow()
                }
            currentAudioFactory = { mockAudio }

            val eventChannel = Channel<LiveLlmEvent>(Channel.UNLIMITED)
            stubLiveConnection(eventChannel.receiveAsFlow())

            val statuses = mutableListOf<LiveAgentStatus>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    orchestrator.runSession("thread-1").toList(statuses)
                }
            runCurrent()

            // Non-fatal error (e.g. skipped malformed frame) must NOT end the session
            eventChannel.trySend(
                LiveLlmEvent.Error(
                    message = "Failed to parse Gemini Live server frame",
                    isFatal = false,
                ),
            )
            runCurrent()
            assertEquals(
                listOf(LiveAgentStatus.Connecting, LiveAgentStatus.Listening),
                statuses,
            )
            verify(exactly = 0) { mockAudio.release() }

            // Subsequent valid events continue normally until a fatal error arrives
            eventChannel.trySend(LiveLlmEvent.InputTranscription("Turn on the lights"))
            eventChannel.trySend(
                LiveLlmEvent.Error(
                    message = "WebSocket closed by server (code=1011): Internal error",
                    isFatal = true,
                ),
            )
            advanceUntilIdle()
            job.join()

            assertEquals(
                listOf(
                    LiveAgentStatus.Connecting,
                    LiveAgentStatus.Listening,
                    LiveAgentStatus.Error(
                        "Live session error: WebSocket closed by server (code=1011): Internal error",
                    ),
                ),
                statuses,
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
    fun `ToolCallRequest gates recording, emits InvokingTool statuses, launches PendingIntent, and sends ToolOutputs`() =
        runTest {
            val orchestrator = createOrchestrator()

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

            coEvery {
                convertInputToAppFunctionDataUseCase(any(), any(), any())
            } returns Result.success(mockData)
            coEvery {
                executeAppFunctionUseCase(noteTool, mockData, "thread-1")
            } coAnswers {
                yield()
                ExecuteAppFunctionResult.Data(
                    data = mockData,
                    formattedJson =
                        """{"status":"ok","imageUri":"content://img/1.png","mimeType":"image/png"}""",
                )
            }
            coEvery {
                executeAppFunctionUseCase(callTool, mockData, "thread-1")
            } coAnswers {
                yield()
                ExecuteAppFunctionResult.PendingIntentAction(pendingIntent)
            }

            val mockAudio =
                mockk<LiveAudioSession>(relaxed = true).apply {
                    every { listenToRecording() } returns emptyFlow()
                }
            currentAudioFactory = { mockAudio }

            val eventChannel = Channel<LiveLlmEvent>(Channel.UNLIMITED)
            val toolResponseSent = CompletableDeferred<Unit>()
            val fakeSession =
                stubLiveConnection(
                    events = eventChannel.receiveAsFlow(),
                    onToolResponses = {
                        toolResponseSent.complete(Unit)
                        eventChannel.close()
                    },
                )

            val statuses = mutableListOf<LiveAgentStatus>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    orchestrator
                        .runSession("thread-1", targetPackageName = "com.example.notes")
                        .toList(statuses)
                }
            runCurrent()
            eventChannel.trySend(LiveLlmEvent.ToolCallRequest(listOf(noteCall, callAction)))
            advanceUntilIdle()
            job.join()

            assertTrue(toolResponseSent.isCompleted)
            assertEquals(
                listOf(
                    LiveAgentStatus.Connecting,
                    LiveAgentStatus.Listening,
                    LiveAgentStatus.InvokingTool(noteTool.id, "com.example.notes"),
                    LiveAgentStatus.InvokingTool("makeCall", "com.example.notes"),
                    LiveAgentStatus.Listening,
                    LiveAgentStatus.Ended,
                ),
                statuses,
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
                    textContent = "Launched makeCall (com.example.notes)",
                    processingStatus = MessageProcessingStatus.PROCESSED,
                )
            }
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
    fun `ToolCallRequest records FAILED message and reports failure when PendingIntent or execution fails`() =
        runTest {
            val orchestrator = createOrchestrator()

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

            orchestrator.runSession("thread-1").toList()

            coVerify {
                sendMessageUseCase(
                    threadId = "thread-1",
                    role = MessageRole.ASSISTANT,
                    textContent =
                        "Failed to launch makeCall (com.example.chat): PendingIntent canceled",
                    processingStatus = MessageProcessingStatus.FAILED,
                )
            }
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

    @Test
    fun `activeSession reflects running session status and ignores rejected concurrent session`() =
        runTest {
            val orchestrator = createOrchestrator()
            val eventChannel = Channel<LiveLlmEvent>(Channel.UNLIMITED)
            stubLiveConnection(eventChannel.receiveAsFlow())

            assertNull(orchestrator.activeSession.value)

            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    orchestrator.runSession("thread-1").collect {}
                }
            advanceUntilIdle()

            assertEquals(
                ActiveLiveSession(threadId = "thread-1", status = LiveAgentStatus.Listening),
                orchestrator.activeSession.value,
            )

            // A concurrent runSession fails fast without overwriting activeSession.
            val concurrentStatuses = mutableListOf<LiveAgentStatus>()
            orchestrator.runSession("thread-2").toList(concurrentStatuses)
            assertEquals(
                listOf(LiveAgentStatus.Error(LiveAgentOrchestrator.ERROR_SESSION_ALREADY_ACTIVE)),
                concurrentStatuses,
            )
            assertEquals(
                ActiveLiveSession(threadId = "thread-1", status = LiveAgentStatus.Listening),
                orchestrator.activeSession.value,
            )

            eventChannel.close()
            advanceUntilIdle()
            job.join()

            assertNull(orchestrator.activeSession.value)
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
