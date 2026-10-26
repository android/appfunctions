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

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * Android [VoiceInteractionService] entry point for Gemini Live voice sessions.
 *
 * Running Live voice sessions inside a [VoiceInteractionService] / [AgentVoiceInteractionSession]
 * keeps microphone recording active and renders a system-wide floating overlay even when
 * `MainActivity` moves to the background (for example, when an AppFunction launches a confirmation
 * `PendingIntent` or the user switches into a target app like `ChatApp`).
 */
class AgentVoiceInteractionService : VoiceInteractionService() {
    @Volatile private var isServiceReady: Boolean = false

    override fun onReady() {
        super.onReady()
        isServiceReady = true
        activeInstance = this
        Log.d(TAG, "AgentVoiceInteractionService onReady")
    }

    override fun onShutdown() {
        isServiceReady = false
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onShutdown()
    }

    override fun onDestroy() {
        isServiceReady = false
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AgentVoiceService"

        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_TARGET_PACKAGE_NAME = "extra_target_package_name"

        @Volatile private var activeInstance: AgentVoiceInteractionService? = null
        private val pendingSessionArgs = AtomicReference<Bundle?>(null)

        internal var isActiveServiceChecker: (Context) -> Boolean = { context ->
            isActiveService(
                context,
                ComponentName(context, AgentVoiceInteractionService::class.java),
            )
        }

        /** Returns true if [AgentVoiceInteractionService] is currently the active assistant service. */
        fun isVoiceServiceActive(context: Context): Boolean = isActiveServiceChecker(context)

        /** Builds the session arguments [Bundle] for [threadId] and optional [targetPackageName]. */
        fun createSessionArgs(
            threadId: String?,
            targetPackageName: String? = null,
        ): Bundle =
            Bundle().apply {
                if (threadId != null) {
                    putString(EXTRA_THREAD_ID, threadId)
                }
                if (targetPackageName != null) {
                    putString(EXTRA_TARGET_PACKAGE_NAME, targetPackageName)
                }
            }

        /** Clears any queued pending session request. */
        internal fun clearPendingSession() {
            pendingSessionArgs.set(null)
        }

        internal fun consumePendingSession(): Bundle? = pendingSessionArgs.getAndSet(null)

        internal fun resetForTesting() {
            activeInstance = null
            pendingSessionArgs.set(null)
            isActiveServiceChecker = { context ->
                isActiveService(
                    context,
                    ComponentName(context, AgentVoiceInteractionService::class.java),
                )
            }
        }

        /**
         * Triggers [showSession] if [AgentVoiceInteractionService] is currently the active and
         * bound [VoiceInteractionService]. Returns `false` if the user must select this app in
         * Digital Assistant settings.
         */
        fun launchLiveSession(
            context: Context,
            threadId: String,
            targetPackageName: String? = null,
        ): Boolean {
            if (!isVoiceServiceActive(context)) {
                Log.d(TAG, "AgentVoiceInteractionService is not the active digital assistant")
                return false
            }
            val args = createSessionArgs(threadId, targetPackageName)
            val instance = activeInstance
            if (instance != null && instance.isServiceReady) {
                clearPendingSession()
                Log.d(TAG, "Showing live voice session via bound service")
                instance.showSession(args, 0)
                return true
            }

            pendingSessionArgs.set(args)
            val showedViaActivity =
                runCatching { context.findActivity()?.showAssist(args) == true }
                    .getOrDefault(false)
            if (!showedViaActivity) {
                clearPendingSession()
                Log.w(TAG, "Voice service is selected in settings but not currently bound")
                return false
            }
            return true
        }

        /**
         * Creates an [Intent] opening Android's Digital Assistant picker settings
         * ([Settings.ACTION_VOICE_INPUT_SETTINGS]).
         *
         * Note: `RoleManager.ROLE_ASSISTANT` is marked `requestable="false"` in AOSP
         * `PermissionController`, so `RoleManager.createRequestRoleIntent` immediately finishes
         * without showing UI; Digital Assistant selection must go through
         * [Settings.ACTION_VOICE_INPUT_SETTINGS].
         */
        fun createAssistantSettingsIntent(): Intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)

        private tailrec fun Context.findActivity(): Activity? =
            when (this) {
                is Activity -> this
                is ContextWrapper -> baseContext.findActivity()
                else -> null
            }
    }
}
