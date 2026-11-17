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
package com.example.appfunctions.agent.domain.appinteraction

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// TODO: Improve the consent UX. Instead of firing the IntentSender directly from the
//  Application context (subject to background activity launch restrictions, and opened in a
//  separate task without user context), route it to the UI layer:
//  - Show a message in the chat thread, e.g. "I need your consent to proceed", with a button
//    the user taps to launch the consent UI from the foreground activity.
//  - If no UI is visible when the request arrives, post a notification that deep-links back to
//    the thread so the user can start the consent from there.
//  - Show an "Awaiting consent" agent status while the request is pending.
//  - Clear the pending request when the session operation completes, fails or is cancelled.

/**
 * Use case to launch a user consent prompt via an [IntentSender] requested during an app
 * interaction session.
 */
@Singleton
class HandleUserConsentUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Starts the given [intentSender] as a new task to prompt the user for consent.
         *
         * @param intentSender The [IntentSender] provided by the system to request user consent.
         */
        operator fun invoke(intentSender: IntentSender) {
            context.startIntentSender(
                intentSender,
                Intent().apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
                0,
                0,
                0,
                null,
            )
        }
    }
