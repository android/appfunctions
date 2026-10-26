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
import android.os.Bundle
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentVoiceInteractionServiceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AgentVoiceInteractionService.resetForTesting()
    }

    @After
    fun tearDown() {
        AgentVoiceInteractionService.resetForTesting()
    }

    @Test
    fun `launchLiveSession returns false when service is not active digital assistant`() {
        AgentVoiceInteractionService.isActiveServiceChecker = { false }

        val launched =
            AgentVoiceInteractionService.launchLiveSession(
                context = context,
                threadId = "thread-1",
                targetPackageName = "com.example.chat",
            )

        assertFalse(launched)
        assertNull(AgentVoiceInteractionService.consumePendingSession())
    }

    @Test
    fun `launchLiveSession calls showSession immediately when service is active and ready`() {
        AgentVoiceInteractionService.isActiveServiceChecker = { true }
        val service =
            spyk(Robolectric.buildService(AgentVoiceInteractionService::class.java).create().get())
        val argsSlot = slot<Bundle>()
        every { service.showSession(capture(argsSlot), any()) } just Runs
        service.onReady()

        val launched =
            AgentVoiceInteractionService.launchLiveSession(
                context = context,
                threadId = "thread-42",
                targetPackageName = "com.example.notes",
            )

        assertTrue(launched)
        verify(exactly = 1) { service.showSession(any(), 0) }
        assertEquals(
            "thread-42",
            argsSlot.captured.getString(AgentVoiceInteractionService.EXTRA_THREAD_ID),
        )
        assertEquals(
            "com.example.notes",
            argsSlot.captured.getString(AgentVoiceInteractionService.EXTRA_TARGET_PACKAGE_NAME),
        )
    }

    @Test
    fun `launchLiveSession returns false when active in settings but unbound and showAssist fails`() {
        AgentVoiceInteractionService.isActiveServiceChecker = { true }

        val launched =
            AgentVoiceInteractionService.launchLiveSession(
                context = context,
                threadId = "thread-1",
                targetPackageName = null,
            )

        assertFalse(launched)
        assertNull(AgentVoiceInteractionService.consumePendingSession())
    }

    @Test
    fun `createAssistantSettingsIntent targets ACTION_VOICE_INPUT_SETTINGS`() {
        val intent = AgentVoiceInteractionService.createAssistantSettingsIntent()

        assertEquals(Settings.ACTION_VOICE_INPUT_SETTINGS, intent.action)
    }

    @Test
    fun `onReady does not self-trigger showSession while settings UI is in foreground`() {
        val service =
            spyk(Robolectric.buildService(AgentVoiceInteractionService::class.java).create().get())
        every { service.showSession(any(), any()) } just Runs

        service.onReady()

        verify(exactly = 0) { service.showSession(any(), any()) }
    }
}
