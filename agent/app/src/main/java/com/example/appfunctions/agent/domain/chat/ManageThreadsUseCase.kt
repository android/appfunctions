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
package com.example.appfunctions.agent.domain.chat

import android.os.Build
import com.example.appfunctions.agent.data.AppInteractionSessionRepository
import com.example.appfunctions.agent.data.ChatRepository
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

/** Use case to manage chat threads. */
class ManageThreadsUseCase
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
        private val appInteractionSessionRepository: Lazy<AppInteractionSessionRepository>,
    ) {
        /** Returns a flow of all threads. */
        fun getThreads(): Flow<List<ThreadEntity>> {
            return chatRepository.getThreads()
        }

        /** Returns a flow of a specific thread. */
        fun getThread(threadId: String): Flow<ThreadEntity?> {
            return chatRepository.getThread(threadId)
        }

        /** Creates a new thread. */
        suspend fun createThread(llmModel: LlmModel): String {
            val threadId = UUID.randomUUID().toString()
            val thread =
                ThreadEntity(
                    threadId = threadId,
                    createdAt = System.currentTimeMillis(),
                    llmModel = llmModel,
                    latestInteractionId = null,
                )
            chatRepository.createThread(thread)
            return threadId
        }

        /** Updates the model of a thread. */
        suspend fun updateThreadModel(
            threadId: String,
            llmModel: LlmModel,
        ) {
            chatRepository.updateThreadModel(threadId, llmModel)
        }

        /** Deletes a thread and closes any [android.app.AppInteractionSession] cached for it. */
        suspend fun deleteThread(threadId: String) {
            chatRepository.deleteThread(threadId)
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.CINNAMON_BUN_2) {
                appInteractionSessionRepository.get().invalidateSession(threadId)
            }
        }
    }
