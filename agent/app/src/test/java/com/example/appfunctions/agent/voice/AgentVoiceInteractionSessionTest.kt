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
import com.example.appfunctions.agent.ui.voice.LiveOverlayStatus
import com.example.appfunctions.agent.ui.voice.LiveOverlayUiEvent
import com.example.appfunctions.agent.ui.voice.LiveOverlayUiState
import com.example.appfunctions.agent.ui.voice.LiveVoiceOverlayStateHolder
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentVoiceInteractionSessionTest {
    private lateinit var context: Context
    private lateinit var stateHolder: LiveVoiceOverlayStateHolder
    private val uiStateFlow = MutableStateFlow(LiveOverlayUiState())

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        stateHolder = mockk(relaxed = true)
        every { stateHolder.uiState } returns uiStateFlow
    }

    @Test
    fun `onShow forwards session args and onHide dispatches OnSessionHidden`() {
        val session = spyk(AgentVoiceInteractionSession(context, stateHolder))
        every { session.hide() } just Runs
        every { stateHolder.onEvent(any()) } answers {
            val event = firstArg<LiveOverlayUiEvent>()
            if (event is LiveOverlayUiEvent.OnSessionShown) {
                uiStateFlow.value = LiveOverlayUiState(isVisible = true)
            } else if (event is LiveOverlayUiEvent.OnSessionHidden) {
                uiStateFlow.value = LiveOverlayUiState(isVisible = false)
            }
        }

        session.onCreate()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        verify(exactly = 0) { session.hide() }

        val args = AgentVoiceInteractionSession.createSessionArgs("thread-7", "com.example.notes")
        session.onShow(args, 0)
        verify(exactly = 1) {
            stateHolder.onEvent(
                LiveOverlayUiEvent.OnSessionShown(
                    threadId = "thread-7",
                    targetPackageName = "com.example.notes",
                ),
            )
        }

        session.onHide()
        verify(exactly = 1) { stateHolder.onEvent(LiveOverlayUiEvent.OnSessionHidden) }

        session.onDestroy()
        verify(exactly = 1) { stateHolder.close() }
    }

    @Test
    fun `isVisible transitioning to false while STARTED hides VoiceInteractionSession`() {
        val session = spyk(AgentVoiceInteractionSession(context, stateHolder))
        every { session.hide() } just Runs
        every { stateHolder.onEvent(any()) } answers {
            if (firstArg<LiveOverlayUiEvent>() is LiveOverlayUiEvent.OnSessionShown) {
                uiStateFlow.value =
                    LiveOverlayUiState(
                        isVisible = true,
                        status = LiveOverlayStatus.Listening,
                    )
            }
        }

        session.onCreate()
        session.onShow(null, 0)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        verify(exactly = 0) { session.hide() }

        uiStateFlow.value = LiveOverlayUiState(isVisible = false)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        verify(exactly = 1) { session.hide() }

        session.onDestroy()
    }

    @Test
    fun `onCloseSystemDialogs respects keepVisibleOnSystemDialogsClose`() {
        val session = spyk(AgentVoiceInteractionSession(context, stateHolder))
        every { session.hide() } just Runs

        uiStateFlow.value =
            LiveOverlayUiState(
                isVisible = true,
                status = LiveOverlayStatus.Listening,
                keepVisibleOnSystemDialogsClose = true,
            )
        session.onCloseSystemDialogs()
        verify(exactly = 0) { session.hide() }

        uiStateFlow.value = LiveOverlayUiState(keepVisibleOnSystemDialogsClose = false)
        session.onCloseSystemDialogs()
        verify(exactly = 1) { session.hide() }

        session.onDestroy()
    }

    @Test
    fun `onComputeInsets restricts touchable region to floating overlay bounds`() {
        val session = AgentVoiceInteractionSession(context, stateHolder)

        val pillBounds = Rect(32, 1800, 1048, 1980)
        session.updateOverlayBounds(pillBounds)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val insets = VoiceInteractionSession.Insets()
        session.onComputeInsets(insets)

        assertEquals(
            VoiceInteractionSession.Insets.TOUCHABLE_INSETS_REGION,
            insets.touchableInsets,
        )
        assertEquals(pillBounds, insets.touchableRegion.bounds)
        session.onDestroy()
    }
}
