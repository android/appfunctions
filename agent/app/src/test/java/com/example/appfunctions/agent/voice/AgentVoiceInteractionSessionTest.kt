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
package com.example.appfunctions.agent.voice

import android.content.Context
import android.graphics.Rect
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import androidx.test.core.app.ApplicationProvider
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import com.example.appfunctions.agent.domain.AgentStatus
import com.example.appfunctions.agent.domain.LiveAgentOrchestrator
import com.example.appfunctions.agent.domain.LiveSessionState
import com.example.appfunctions.agent.domain.chat.ManageThreadsUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentVoiceInteractionSessionTest {
    private lateinit var context: Context
    private lateinit var liveAgentOrchestrator: LiveAgentOrchestrator
    private lateinit var manageThreadsUseCase: ManageThreadsUseCase

    private val liveStateFlow = MutableStateFlow<LiveSessionState>(LiveSessionState.Idle)
    private val agentStatusFlow = MutableStateFlow<AgentStatus>(AgentStatus.Idle)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AgentVoiceInteractionService.resetForTesting()

        liveAgentOrchestrator = mockk(relaxed = true)
        manageThreadsUseCase = mockk(relaxed = true)

        every { liveAgentOrchestrator.state } returns liveStateFlow
        every { liveAgentOrchestrator.status } returns agentStatusFlow
    }

    @After
    fun tearDown() {
        AgentVoiceInteractionService.resetForTesting()
    }

    @Test
    fun `onShow starts LiveAgentOrchestrator with requested threadId and targetPackageName`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val session =
                spyk(
                    AgentVoiceInteractionSession(
                        context = context,
                        liveAgentOrchestrator = liveAgentOrchestrator,
                        manageThreadsUseCase = manageThreadsUseCase,
                        mainDispatcher = testDispatcher,
                    ),
                )
            every { session.hide() } just Runs

            val args =
                AgentVoiceInteractionService.createSessionArgs(
                    threadId = "thread-123",
                    targetPackageName = "com.example.chat",
                )
            session.onShow(args, 0)
            advanceUntilIdle()

            verify(exactly = 1) {
                liveAgentOrchestrator.startSession(
                    threadId = "thread-123",
                    targetPackageName = "com.example.chat",
                )
            }
            session.onDestroy()
        }

    @Test
    fun `onShow without threadId resolves default thread via ManageThreadsUseCase`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            every { manageThreadsUseCase.getThreads() } returns flowOf(emptyList())
            coEvery { manageThreadsUseCase.createThread(LlmModel.DEFAULT) } returns "thread-created"

            val session =
                spyk(
                    AgentVoiceInteractionSession(
                        context = context,
                        liveAgentOrchestrator = liveAgentOrchestrator,
                        manageThreadsUseCase = manageThreadsUseCase,
                        mainDispatcher = testDispatcher,
                    ),
                )
            every { session.hide() } just Runs

            session.onShow(null, 0)
            advanceUntilIdle()

            coVerify(exactly = 1) { manageThreadsUseCase.createThread(LlmModel.DEFAULT) }
            verify(exactly = 1) {
                liveAgentOrchestrator.startSession(
                    threadId = "thread-created",
                    targetPackageName = null,
                )
            }
            session.onDestroy()
        }

    @Test
    fun `onShow without threadId reuses newest existing thread when present`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val existingThread =
                ThreadEntity(
                    "thread-existing",
                    1000L,
                    LlmModel.DEFAULT,
                    null,
                )
            every { manageThreadsUseCase.getThreads() } returns flowOf(listOf(existingThread))

            val session =
                spyk(
                    AgentVoiceInteractionSession(
                        context = context,
                        liveAgentOrchestrator = liveAgentOrchestrator,
                        manageThreadsUseCase = manageThreadsUseCase,
                        mainDispatcher = testDispatcher,
                    ),
                )
            every { session.hide() } just Runs

            session.onShow(null, 0)
            advanceUntilIdle()

            coVerify(exactly = 0) { manageThreadsUseCase.createThread(any()) }
            verify(exactly = 1) {
                liveAgentOrchestrator.startSession(
                    threadId = "thread-existing",
                    targetPackageName = null,
                )
            }
            session.onDestroy()
        }

    @Test
    fun `onComputeInsets restricts touchable region to floating overlay bounds`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val session =
                AgentVoiceInteractionSession(
                    context = context,
                    liveAgentOrchestrator = liveAgentOrchestrator,
                    manageThreadsUseCase = manageThreadsUseCase,
                    mainDispatcher = testDispatcher,
                )

            val pillBounds = Rect(32, 1800, 1048, 1980)
            session.updateOverlayBounds(pillBounds)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            val insets = VoiceInteractionSession.Insets()
            session.onComputeInsets(insets)

            assertEquals(VoiceInteractionSession.Insets.TOUCHABLE_INSETS_REGION, insets.touchableInsets)
            assertEquals(pillBounds, insets.touchableRegion.bounds)
            session.onDestroy()
        }

    @Test
    fun `onCloseSystemDialogs keeps session visible while Listening and hides when Idle`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val session =
                spyk(
                    AgentVoiceInteractionSession(
                        context = context,
                        liveAgentOrchestrator = liveAgentOrchestrator,
                        manageThreadsUseCase = manageThreadsUseCase,
                        mainDispatcher = testDispatcher,
                    ),
                )
            every { session.hide() } just Runs

            liveStateFlow.value = LiveSessionState.Listening
            session.onCloseSystemDialogs()
            verify(exactly = 0) { session.hide() }

            liveStateFlow.value = LiveSessionState.Idle
            session.onCloseSystemDialogs()
            verify(exactly = 1) { session.hide() }

            session.onDestroy()
        }

    @Test
    fun `stopAndHide and onHide stop LiveAgentOrchestrator`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val session =
                spyk(
                    AgentVoiceInteractionSession(
                        context = context,
                        liveAgentOrchestrator = liveAgentOrchestrator,
                        manageThreadsUseCase = manageThreadsUseCase,
                        mainDispatcher = testDispatcher,
                    ),
                )
            every { session.hide() } just Runs

            session.stopAndHide()
            verify(exactly = 1) { liveAgentOrchestrator.stopSession() }
            verify(exactly = 1) { session.hide() }

            session.onHide()
            verify(exactly = 2) { liveAgentOrchestrator.stopSession() }

            session.onDestroy()
        }
}
