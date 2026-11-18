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
package com.example.appfunctions.agent.ui.voice

import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import com.example.appfunctions.agent.domain.ActiveLiveSession
import com.example.appfunctions.agent.domain.LiveAgentOrchestrator
import com.example.appfunctions.agent.domain.LiveAgentStatus
import com.example.appfunctions.agent.domain.LlmProvider
import com.example.appfunctions.agent.domain.LlmProviderFactory
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.domain.appfunction.GetInstalledAppsUseCase
import com.example.appfunctions.agent.domain.chat.ManageThreadsUseCase
import com.example.appfunctions.agent.domain.live.CheckAudioPermissionUseCase
import com.example.appfunctions.agent.ui.screens.agentdemo.FakeSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class LiveVoiceOverlayStateHolderTest {
    private lateinit var liveAgentOrchestrator: LiveAgentOrchestrator
    private lateinit var manageThreadsUseCase: ManageThreadsUseCase
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var llmProviderFactory: LlmProviderFactory
    private lateinit var llmProvider: LlmProvider
    private lateinit var checkAudioPermissionUseCase: CheckAudioPermissionUseCase
    private lateinit var getInstalledAppsUseCase: GetInstalledAppsUseCase

    private val activeSessionFlow = MutableStateFlow<ActiveLiveSession?>(null)
    private val testModel = LlmModel(LlmProviderName.GEMINI, "gemini-2.5-flash")
    private val chatAppInfo = AppInfo("com.example.chat", "ChatApp", null)

    @Before
    fun setUp() {
        liveAgentOrchestrator = mockk(relaxed = true)
        manageThreadsUseCase = mockk(relaxed = true)
        settingsRepository = FakeSettingsRepository()
        llmProvider = mockk(relaxed = true)
        llmProviderFactory = mockk(relaxed = true)
        checkAudioPermissionUseCase = mockk()
        getInstalledAppsUseCase = mockk()

        every { liveAgentOrchestrator.activeSession } returns activeSessionFlow
        every { llmProviderFactory.getProvider(any()) } returns llmProvider
        every { checkAudioPermissionUseCase() } returns true
        every { getInstalledAppsUseCase() } returns listOf(chatAppInfo)
    }

    @Test
    fun `OnSessionShown runs session, maps InvokingTool AppInfo, and hides on Ended`() =
        runTest {
            val statusChannel = Channel<LiveAgentStatus>(Channel.UNLIMITED)
            stubRunSession("thread-1", "com.example.chat", statusChannel.consumeAsFlow())

            val holder = createStateHolder()
            advanceUntilIdle()

            holder.onEvent(
                LiveOverlayUiEvent.OnSessionShown(
                    threadId = "thread-1",
                    targetPackageName = "com.example.chat",
                ),
            )
            statusChannel.send(LiveAgentStatus.InvokingTool("sendMessage", "com.example.chat"))
            advanceUntilIdle()

            assertTrue(holder.uiState.value.isVisible)
            assertEquals("thread-1", holder.uiState.value.threadId)
            assertEquals(
                LiveOverlayStatus.InvokingTool("sendMessage", "com.example.chat"),
                holder.uiState.value.status,
            )
            assertEquals(chatAppInfo, holder.uiState.value.invokingApp)
            assertTrue(holder.uiState.value.keepVisibleOnSystemDialogsClose)

            statusChannel.send(LiveAgentStatus.Ended)
            statusChannel.close()
            advanceUntilIdle()

            assertFalse(holder.uiState.value.isVisible)
            assertNull(holder.uiState.value.status)
            assertFalse(holder.uiState.value.keepVisibleOnSystemDialogsClose)
            holder.close()
        }

    @Test
    fun `OnSessionShown without threadId uses existing thread and avoids duplicate restart`() =
        runTest {
            val existingThread = ThreadEntity("thread-existing", 1000L, testModel, null)
            every { manageThreadsUseCase.getThreads() } returns flowOf(listOf(existingThread))
            val statusChannel = Channel<LiveAgentStatus>(Channel.UNLIMITED)
            stubRunSession("thread-existing", null, statusChannel.consumeAsFlow())

            val holder = createStateHolder()

            holder.onEvent(LiveOverlayUiEvent.OnSessionShown())
            statusChannel.send(LiveAgentStatus.Listening)
            advanceUntilIdle()

            assertTrue(holder.uiState.value.isVisible)
            assertEquals("thread-existing", holder.uiState.value.threadId)
            assertEquals(LiveOverlayStatus.Listening, holder.uiState.value.status)

            // Re-showing without explicit args while active keeps the current session running.
            holder.onEvent(LiveOverlayUiEvent.OnSessionShown())
            advanceUntilIdle()
            verify(exactly = 1) { liveAgentOrchestrator.runSession("thread-existing", null) }

            holder.onEvent(LiveOverlayUiEvent.OnSessionHidden)
            advanceUntilIdle()
            assertFalse(holder.uiState.value.isVisible)
            assertNull(holder.uiState.value.status)
            holder.close()
        }

    @Test
    fun `OnSessionShown creates default thread from selected provider when no thread exists`() =
        runTest {
            settingsRepository.setGeminiApiKey("valid-key")
            every { manageThreadsUseCase.getThreads() } returns flowOf(emptyList())
            coEvery { llmProvider.getAvailableModels("valid-key") } returns listOf(testModel)
            coEvery { manageThreadsUseCase.createThread(testModel) } returns "thread-created"
            val statusChannel = Channel<LiveAgentStatus>(Channel.UNLIMITED)
            stubRunSession("thread-created", null, statusChannel.consumeAsFlow())

            val holder = createStateHolder()

            holder.onEvent(LiveOverlayUiEvent.OnSessionShown())
            statusChannel.send(LiveAgentStatus.Listening)
            advanceUntilIdle()

            coVerify(exactly = 1) { manageThreadsUseCase.createThread(testModel) }
            assertEquals("thread-created", holder.uiState.value.threadId)
            assertEquals(LiveOverlayStatus.Listening, holder.uiState.value.status)
            holder.close()
        }

    @Test
    fun `OnSessionShown surfaces NoModelAvailable when no thread exists and no model is available`() =
        runTest {
            every { manageThreadsUseCase.getThreads() } returns flowOf(emptyList())
            coEvery { llmProvider.getAvailableModels(any()) } returns emptyList()

            val holder = createStateHolder()

            holder.onEvent(LiveOverlayUiEvent.OnSessionShown())
            advanceUntilIdle()

            assertTrue(holder.uiState.value.isVisible)
            assertEquals(
                LiveOverlayStatus.Error.NoModelAvailable(LlmProviderName.GEMINI.displayName),
                holder.uiState.value.status,
            )
            assertFalse(holder.uiState.value.keepVisibleOnSystemDialogsClose)
            coVerify(exactly = 0) { manageThreadsUseCase.createThread(any()) }
            verify(exactly = 0) { liveAgentOrchestrator.runSession(any(), any()) }
            holder.close()
        }

    @Test
    fun `OnSessionShown surfaces AudioPermissionRequired when microphone permission is missing`() =
        runTest {
            every { checkAudioPermissionUseCase() } returns false

            val holder = createStateHolder()

            holder.onEvent(LiveOverlayUiEvent.OnSessionShown(threadId = "thread-1"))
            advanceUntilIdle()

            assertTrue(holder.uiState.value.isVisible)
            assertEquals(
                LiveOverlayStatus.Error.AudioPermissionRequired,
                holder.uiState.value.status,
            )
            verify(exactly = 0) { liveAgentOrchestrator.runSession(any(), any()) }
            holder.close()
        }

    @Test
    fun `Error state stays visible and OnRetryClicked awaits cleanup before restarting`() =
        runTest {
            val firstCleanedUp = AtomicBoolean(false)
            var secondSawCleanup = false
            var calls = 0

            every { liveAgentOrchestrator.runSession("thread-1", "com.example.chat") } answers {
                calls++
                if (calls == 1) {
                    flow {
                        activeSessionFlow.value =
                            ActiveLiveSession("thread-1", LiveAgentStatus.Error("Disconnected"))
                        emit(LiveAgentStatus.Error("Disconnected"))
                        activeSessionFlow.value = null
                        try {
                            awaitCancellation()
                        } finally {
                            firstCleanedUp.set(true)
                        }
                    }
                } else {
                    secondSawCleanup = firstCleanedUp.get()
                    flow {
                        activeSessionFlow.value =
                            ActiveLiveSession("thread-1", LiveAgentStatus.Listening)
                        emit(LiveAgentStatus.Listening)
                        awaitCancellation()
                    }
                }
            }

            val holder = createStateHolder()

            holder.onEvent(
                LiveOverlayUiEvent.OnSessionShown(
                    threadId = "thread-1",
                    targetPackageName = "com.example.chat",
                ),
            )
            advanceUntilIdle()

            assertTrue(holder.uiState.value.isVisible)
            assertEquals(
                LiveOverlayStatus.Error.SessionFailed("Disconnected"),
                holder.uiState.value.status,
            )

            holder.onEvent(LiveOverlayUiEvent.OnRetryClicked)
            advanceUntilIdle()

            assertEquals(2, calls)
            assertTrue(firstCleanedUp.get())
            assertTrue(secondSawCleanup)
            assertEquals(LiveOverlayStatus.Listening, holder.uiState.value.status)

            holder.onEvent(LiveOverlayUiEvent.OnStopClicked)
            advanceUntilIdle()
            assertFalse(holder.uiState.value.isVisible)
            holder.close()
        }

    private fun stubRunSession(
        threadId: String,
        targetPackageName: String?,
        upstream: Flow<LiveAgentStatus>,
    ) {
        every { liveAgentOrchestrator.runSession(threadId, targetPackageName) } returns
            upstream
                .onEach { status ->
                    activeSessionFlow.value =
                        if (status is LiveAgentStatus.Ended) {
                            null
                        } else {
                            ActiveLiveSession(threadId = threadId, status = status)
                        }
                }
                .onCompletion { activeSessionFlow.value = null }
    }

    private fun TestScope.createStateHolder(): LiveVoiceOverlayStateHolder {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return LiveVoiceOverlayStateHolder(
            liveAgentOrchestrator = liveAgentOrchestrator,
            manageThreadsUseCase = manageThreadsUseCase,
            settingsRepository = settingsRepository,
            llmProviderFactory = llmProviderFactory,
            checkAudioPermissionUseCase = checkAudioPermissionUseCase,
            getInstalledAppsUseCase = getInstalledAppsUseCase,
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
        )
    }
}
