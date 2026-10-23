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
package com.example.appfunctions.agent.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.db.AppDatabase
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
class ChatDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var chatDao: ChatDao
    private val testModel = LlmModel(LlmProviderName.GEMINI, "gemini-2.5-flash")

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
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetMessages() =
        runBlocking {
            val thread = ThreadEntity("thread_1", System.currentTimeMillis(), testModel)
            chatDao.insertThread(thread)

            val message =
                MessageEntity(
                    "msg_1",
                    "thread_1",
                    MessageRole.USER,
                    "Hello",
                    System.currentTimeMillis(),
                    MessageProcessingStatus.PROCESSED,
                )
            chatDao.insertMessage(message)

            val messages = chatDao.getMessagesForThread("thread_1").first()
            assertEquals(1, messages.size)
            assertEquals(message, messages[0])
        }

    @Test
    fun updateMessageStatus() =
        runBlocking {
            val thread = ThreadEntity("thread_1", System.currentTimeMillis(), testModel)
            chatDao.insertThread(thread)

            val message =
                MessageEntity(
                    "msg_1",
                    "thread_1",
                    MessageRole.USER,
                    "Hello",
                    System.currentTimeMillis(),
                    MessageProcessingStatus.PENDING_AGENT_RESPONSE,
                )
            chatDao.insertMessage(message)

            chatDao.updateMessageStatus("msg_1", MessageProcessingStatus.PROCESSED)

            val messages = chatDao.getMessagesForThread("thread_1").first()
            assertEquals(MessageProcessingStatus.PROCESSED, messages[0].processingStatus)
        }

    @Test
    fun deleteThreadCascades() =
        runBlocking {
            val thread = ThreadEntity("thread_1", System.currentTimeMillis(), testModel)
            chatDao.insertThread(thread)

            val message =
                MessageEntity(
                    "msg_1",
                    "thread_1",
                    MessageRole.USER,
                    "Hello",
                    System.currentTimeMillis(),
                    MessageProcessingStatus.PROCESSED,
                )
            chatDao.insertMessage(message)

            chatDao.deleteThread("thread_1")

            val messages = chatDao.getMessagesForThread("thread_1").first()
            assertEquals(0, messages.size)
        }

    @Test
    fun insertAndUpdateThreadWithDynamicLlmModel() =
        runBlocking {
            val dynamicModel = LlmModel(LlmProviderName.GEMINI, "gemini-4.0-ultra-experimental")
            val thread = ThreadEntity("thread_1", System.currentTimeMillis(), dynamicModel)
            chatDao.insertThread(thread)

            val retrieved = chatDao.getThread("thread_1").first()
            assertEquals(dynamicModel, retrieved?.llmModel)

            val updatedModel = LlmModel(LlmProviderName.GEMINI, "gemini-4.1-flash")
            chatDao.updateThreadModel("thread_1", updatedModel)
            assertEquals(updatedModel, chatDao.getThread("thread_1").first()?.llmModel)
        }
}
