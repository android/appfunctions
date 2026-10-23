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

import com.example.appfunctions.agent.data.db.dao.ChatDao
import com.example.appfunctions.agent.data.db.entities.MessageEntity
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RoomChatRepositoryTest {
    private lateinit var chatDao: ChatDao
    private lateinit var repository: RoomChatRepository

    @Before
    fun setup() {
        chatDao = mockk()
        repository = RoomChatRepository(chatDao)
    }

    @Test
    fun getMessagesForThread() =
        runBlocking {
            val threadId = "thread_1"
            val messages =
                listOf(
                    MessageEntity(
                        "msg_1",
                        threadId,
                        MessageRole.USER,
                        "Hello",
                        System.currentTimeMillis(),
                        MessageProcessingStatus.PROCESSED,
                    ),
                )
            every { chatDao.getMessagesForThread(threadId) } returns flowOf(messages)

            val result = repository.getMessagesForThread(threadId).first()
            assertEquals(messages, result)
        }

    @Test
    fun sendMessage() =
        runBlocking {
            val message =
                MessageEntity(
                    "msg_1",
                    "thread_1",
                    MessageRole.USER,
                    "Hello",
                    System.currentTimeMillis(),
                    MessageProcessingStatus.PROCESSED,
                )
            coEvery { chatDao.insertMessage(message) } returns Unit

            repository.sendMessage(message)

            coVerify { chatDao.insertMessage(message) }
        }

    @Test
    fun updateMessageStatus() =
        runBlocking {
            val messageId = "msg_1"
            val status = MessageProcessingStatus.PROCESSED
            coEvery { chatDao.updateMessageStatus(messageId, status) } returns Unit

            repository.updateMessageStatus(messageId, status)

            coVerify { chatDao.updateMessageStatus(messageId, status) }
        }

    @Test
    fun updateThreadModel() =
        runBlocking {
            val threadId = "thread_1"
            val model = LlmModel(LlmProviderName.GEMINI, "gemini-2.5-flash")
            coEvery { chatDao.updateThreadModel(threadId, model) } returns Unit

            repository.updateThreadModel(threadId, model)

            coVerify { chatDao.updateThreadModel(threadId, model) }
        }
}
