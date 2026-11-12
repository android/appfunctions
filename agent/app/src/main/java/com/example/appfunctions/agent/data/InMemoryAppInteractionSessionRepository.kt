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
package com.example.appfunctions.agent.data

import android.app.AppInteractionSession
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton in-memory implementation of [AppInteractionSessionRepository] that maps chat thread IDs
 * to active [AppInteractionSession] instances and automatically evicts closed sessions.
 */
@Singleton
@RequiresApi(Build.VERSION_CODES_FULL.CINNAMON_BUN_2)
class InMemoryAppInteractionSessionRepository
    @Inject
    constructor() : AppInteractionSessionRepository {
        private val sessionMap = ConcurrentHashMap<String, AppInteractionSession>()

        private val executor = Executors.newSingleThreadExecutor()

        private val lifecycleListener =
            object : AppInteractionSession.LifecycleListener {
                override fun onAppInteractionSessionClosed(
                    session: AppInteractionSession,
                    reason: Int,
                ) {
                    val targetEntry = sessionMap.entries.find { it.value == session } ?: return
                    Log.d(TAG, "Session for ${targetEntry.key} is closed: $reason.")
                    sessionMap.remove(targetEntry.key)
                }
            }

        override fun saveSession(
            threadId: String,
            appInteractionSession: AppInteractionSession,
        ) {
            appInteractionSession.addLifecycleListener(
                executor,
                lifecycleListener,
            )
            sessionMap[threadId] = appInteractionSession
        }

        override fun getSession(threadId: String): AppInteractionSession? {
            return sessionMap[threadId]
        }

        override fun invalidateSession(threadId: String) {
            sessionMap.remove(threadId)?.also {
                it.close()
            }
        }

        private companion object {
            const val TAG = "AppInteractionSessionRepository"
        }
    }
