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
package com.example.appfunctions.agent.domain.live

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.appfunctions.agent.MainActivity
import com.example.appfunctions.agent.voice.AgentVoiceInteractionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveSessionUseCasesTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `CheckAudioPermissionUseCase returns true when granted and false when denied`() {
        val useCase = CheckAudioPermissionUseCase(application)

        Shadows.shadowOf(application).denyPermissions(Manifest.permission.RECORD_AUDIO)
        assertFalse(useCase())

        Shadows.shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        assertTrue(useCase())
    }

    @Test
    fun `CheckVoiceServiceActiveUseCase checks Settings Secure active voice_interaction_service`() {
        val useCase = CheckVoiceServiceActiveUseCase(application)

        Settings.Secure.putString(application.contentResolver, KEY_VOICE_INTERACTION_SERVICE, null)
        assertFalse(useCase())

        Settings.Secure.putString(
            application.contentResolver,
            KEY_VOICE_INTERACTION_SERVICE,
            ComponentName("com.other.assistant", "com.other.assistant.VoiceService")
                .flattenToString(),
        )
        assertFalse(useCase())

        Settings.Secure.putString(
            application.contentResolver,
            KEY_VOICE_INTERACTION_SERVICE,
            ComponentName(application, AgentVoiceInteractionService::class.java).flattenToString(),
        )
        assertTrue(useCase())
    }

    @Test
    fun `MainActivity createOpenChatIntent builds deep-link VIEW intent targeting MainActivity`() {
        val withThreadIntent = MainActivity.createOpenChatIntent(application, "thread-42")
        assertEquals(Intent.ACTION_VIEW, withThreadIntent.action)
        assertEquals(
            ComponentName(application, MainActivity::class.java),
            withThreadIntent.component,
        )
        assertEquals("appfunctions-agent://chat?threadId=thread-42", withThreadIntent.dataString)
        assertTrue(withThreadIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(withThreadIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)

        val withoutThreadIntent = MainActivity.createOpenChatIntent(application, null)
        assertEquals("appfunctions-agent://chat", withoutThreadIntent.dataString)
        assertNull(withoutThreadIntent.data?.getQueryParameter(MainActivity.ARG_THREAD_ID))
    }

    private companion object {
        const val KEY_VOICE_INTERACTION_SERVICE = "voice_interaction_service"
    }
}
