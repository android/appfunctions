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

import android.app.AppInteractionException
import android.app.AppInteractionManager
import android.app.AppInteractionSession
import android.content.Context
import android.os.Build
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import com.example.appfunctions.agent.data.AppInteractionSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Use case to create or update an [AppInteractionSession] for a given chat thread and set of target
 * packages, handling any required user consent flows.
 */
@Singleton
class StartAppInteractionSessionUseCase
    @Inject
    constructor(
        @ApplicationContext private val applicationContext: Context,
        private val appInteractionSessionRepository: AppInteractionSessionRepository,
        private val handleUserConsentUseCase: HandleUserConsentUseCase,
    ) {
        /**
         * Starts a new [AppInteractionSession] or updates an existing session cached for
         * [threadId] to include [targetPackages].
         *
         * @param threadId Chat thread ID used to look up and cache the session. Callers without a
         *   real chat thread should pass a stable default thread ID so the session is reused.
         * @param targetPackages The package names of the target apps to interact with.
         * @return An active [AppInteractionSession] approved for [targetPackages].
         */
        @RequiresApi(Build.VERSION_CODES_FULL.CINNAMON_BUN_2)
        suspend operator fun invoke(
            threadId: String,
            targetPackages: List<String>,
        ): AppInteractionSession {
            val previouslySavedSession = appInteractionSessionRepository.getSession(threadId)
            if (previouslySavedSession != null) {
                // Always update the target packages. If it is already approved, the API would
                // simply return without asking additional user consent.
                return updateExistingSession(previouslySavedSession, targetPackages)
            }
            return createSession(targetPackages).also {
                appInteractionSessionRepository.saveSession(threadId, it)
            }
        }

        @RequiresApi(Build.VERSION_CODES_FULL.CINNAMON_BUN_2)
        private suspend fun updateExistingSession(
            appInteractionSession: AppInteractionSession,
            targetPackages: List<String>,
        ): AppInteractionSession {
            return suspendCancellableCoroutine { continuation ->
                appInteractionSession.update(
                    AppInteractionSession.UpdateParams.Builder()
                        .setAdditionalTargetPackages(targetPackages)
                        .setOnStartIntentSenderCallback(
                            Runnable::run,
                            { intentSender ->
                                handleUserConsentUseCase(intentSender)
                            },
                        )
                        .build(),
                    Runnable::run,
                    object : OutcomeReceiver<Void, AppInteractionException> {
                        override fun onResult(p0: Void?) {
                            continuation.resume(appInteractionSession)
                        }

                        override fun onError(error: AppInteractionException) {
                            continuation.resumeWithException(error)
                        }
                    },
                )
            }
        }

        @RequiresApi(Build.VERSION_CODES_FULL.CINNAMON_BUN_2)
        private suspend fun createSession(targetPackages: List<String>): AppInteractionSession {
            val appInteractionManager = checkNotNull(applicationContext.getSystemService(AppInteractionManager::class.java))
            return suspendCancellableCoroutine { continuation ->
                appInteractionManager.createSession(
                    AppInteractionSession.CreateParams.Builder()
                        .setTargetPackages(targetPackages)
                        .setOnStartIntentSenderCallback(
                            Runnable::run,
                            { intentSender ->
                                handleUserConsentUseCase(intentSender)
                            },
                        )
                        .build(),
                    Runnable::run,
                    object : OutcomeReceiver<AppInteractionSession, AppInteractionException> {
                        override fun onResult(session: AppInteractionSession) {
                            // If the coroutine was cancelled while waiting (e.g. the user left
                            // during the consent flow), nobody will own this session; close it.
                            continuation.resume(session) { _, droppedSession, _ ->
                                droppedSession.close()
                            }
                        }

                        override fun onError(error: AppInteractionException) {
                            continuation.resumeWithException(error)
                        }
                    },
                )
            }
        }
    }
