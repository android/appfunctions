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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManageThreadsUseCaseTest {
    private lateinit var database: AppDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var chatRepository: ChatRepository
    private lateinit var useCase: ManageThreadsUseCase
    private val testModelPro = LlmModel(LlmProviderName.GEMINI, "gemini-2.5-pro")
    private val testModelFlash = LlmModel(LlmProviderName.GEMINI, "gemini-2.5-flash")

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
        useCase = ManageThreadsUseCase(chatRepository)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun getThreads_returnsThreadsFromRepository() =
        runBlocking {
            val threadId = useCase.createThread(testModelPro)

            val result = useCase.getThreads().first()

            assertEquals(1, result.size)
            assertEquals(threadId, result[0].threadId)
            assertEquals(testModelPro, result[0].llmModel)
        }

    @Test
    fun createThread_createsThreadInRepositoryAndReturnsId() =
        runBlocking {
            val threadId = useCase.createThread(testModelPro)

            val threads = useCase.getThreads().first()
            assertEquals(1, threads.size)
            assertEquals(threadId, threads[0].threadId)
            assertEquals(testModelPro, threads[0].llmModel)
        }

    @Test
    fun updateThreadModel_updatesThreadInRepository() =
        runBlocking {
            val threadId = useCase.createThread(testModelPro)

            useCase.updateThreadModel(threadId, testModelFlash)

            val threads = useCase.getThreads().first()
            assertEquals(1, threads.size)
            assertEquals(threadId, threads[0].threadId)
            assertEquals(testModelFlash, threads[0].llmModel)
        }

    @Test
    fun deleteThread_removesThreadFromRepository() =
        runBlocking {
            val threadId = useCase.createThread(testModelPro)

            useCase.deleteThread(threadId)

            val threads = useCase.getThreads().first()
            assertEquals(0, threads.size)
        }
}
