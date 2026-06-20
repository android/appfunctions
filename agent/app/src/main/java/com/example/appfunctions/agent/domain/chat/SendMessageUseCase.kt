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

import com.example.appfunctions.agent.data.ChatRepository
import com.example.appfunctions.agent.data.db.entities.MessageEntity
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import java.util.UUID
import javax.inject.Inject

/** Use case to send a message in a chat thread. */
class SendMessageUseCase
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
    ) {
        /**
         * Executes the use case.
         *
         * @param threadId The ID of the thread to send the message to.
         * @param role The role of the sender (USER or ASSISTANT).
         * @param textContent The content of the message.
         * @param processingStatus The processing status of the message.
         */
        suspend operator fun invoke(
            threadId: String,
            role: MessageRole,
            textContent: String,
            processingStatus: MessageProcessingStatus,
            pendingIntentId: String? = null,
            targetPackageName: String? = null,
        ) {
            val message =
                MessageEntity(
                    messageId = UUID.randomUUID().toString(),
                    threadId = threadId,
                    role = role,
                    textContent = textContent,
                    timestamp = System.currentTimeMillis(),
                    processingStatus = processingStatus,
                    pendingIntentId = pendingIntentId,
                    targetPackageName = targetPackageName,
                )
            chatRepository.sendMessage(message)
        }
    }
