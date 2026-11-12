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
import androidx.annotation.RequiresApi

/** Repository to manage [AppInteractionSession] instances per chat thread. */
@RequiresApi(Build.VERSION_CODES_FULL.CINNAMON_BUN_2)
interface AppInteractionSessionRepository {
    /**
     * Caches an [AppInteractionSession] for the given [threadId].
     *
     * @param threadId The chat thread identifier associated with the session.
     * @param appInteractionSession The active [AppInteractionSession] to store.
     */
    fun saveSession(
        threadId: String,
        appInteractionSession: AppInteractionSession,
    )

    /**
     * Retrieves the cached [AppInteractionSession] for the given [threadId], or `null` if none
     * exists.
     */
    fun getSession(threadId: String): AppInteractionSession?

    /**
     * Removes and closes the [AppInteractionSession] associated with the given [threadId], if one
     * exists.
     */
    fun invalidateSession(threadId: String)
}
