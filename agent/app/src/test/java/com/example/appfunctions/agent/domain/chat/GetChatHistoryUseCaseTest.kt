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

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.appfunctions.agent.data.ChatRepository
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.RoomChatRepository
import com.example.appfunctions.agent.data.db.AppDatabase
import com.example.appfunctions.agent.data.db.dao.ChatDao
import com.example.appfunctions.agent.data.db.entities.MessageEntity
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GetChatHistoryUseCaseTest {
    private lateinit var database: AppDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var chatRepository: ChatRepository
    private lateinit var useCase: GetChatHistoryUseCase

    @Before
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()
        chatDao = database.chatDao()
        chatRepository = RoomChatRepository(chatDao)
        useCase = GetChatHistoryUseCase(chatRepository)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun invoke_returnsMessagesFromRepository() =
        runBlocking {
            val threadId = "thread_1"
            val thread =
                ThreadEntity(
                    threadId,
                    System.currentTimeMillis(),
                    LlmModel(LlmProviderName.GEMINI, "gemini-2.5-pro"),
                )
            chatRepository.createThread(thread)

            val message =
                MessageEntity(
                    messageId = "msg_1",
                    threadId = threadId,
                    role = MessageRole.USER,
                    textContent = "Hello",
                    timestamp = System.currentTimeMillis(),
                    processingStatus = MessageProcessingStatus.PROCESSED,
                )
            chatRepository.sendMessage(message)

            val result = useCase(threadId).first()

            assertEquals(1, result.size)
            assertEquals(message, result[0])
        }
}
