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
package com.example.appfunctions.agent.ui.screens.agentdemo

import androidx.lifecycle.SavedStateHandle
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.data.db.entities.MessageEntity
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import com.example.appfunctions.agent.domain.AgentOrchestrator
import com.example.appfunctions.agent.domain.AgentStatus
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.appfunction.GetInstalledAppsUseCase
import com.example.appfunctions.agent.domain.chat.GetChatHistoryUseCase
import com.example.appfunctions.agent.domain.chat.ManageThreadsUseCase
import com.example.appfunctions.agent.domain.chat.SendMessageUseCase
import com.example.appfunctions.agent.domain.pendingintent.ConsumePendingIntentUseCase
import com.example.appfunctions.agent.domain.pendingintent.LaunchPendingIntentUseCase
import com.example.appfunctions.agent.domain.pendingintent.ObserveActivePendingIntentsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentDemoViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var getChatHistoryUseCase: GetChatHistoryUseCase
    private lateinit var manageThreadsUseCase: ManageThreadsUseCase
    private lateinit var sendMessageUseCase: SendMessageUseCase
    private lateinit var agentOrchestrator: AgentOrchestrator
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var observeActivePendingIntentsUseCase: ObserveActivePendingIntentsUseCase
    private lateinit var launchPendingIntentUseCase: LaunchPendingIntentUseCase
    private lateinit var consumePendingIntentUseCase: ConsumePendingIntentUseCase
    private lateinit var getInstalledAppsUseCase: GetInstalledAppsUseCase
    private lateinit var getAppFunctionsUseCase: GetAppFunctionsUseCase

    private lateinit var viewModel: AgentDemoViewModel

    private val threadsFlow = MutableStateFlow<List<ThreadEntity>>(emptyList())
    private val selectedProviderFlow = MutableStateFlow<LlmProviderName>(LlmProviderName.GEMINI)
    private val agentStatusFlow = MutableStateFlow<AgentStatus>(AgentStatus.Idle)
    private val messagesFlow = MutableStateFlow<List<MessageEntity>>(emptyList())
    private val activePendingActionIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        getChatHistoryUseCase = mockk()
        manageThreadsUseCase = mockk()
        sendMessageUseCase = mockk()
        agentOrchestrator = mockk()
        settingsRepository = mockk()
        savedStateHandle = SavedStateHandle()
        observeActivePendingIntentsUseCase = mockk()
        launchPendingIntentUseCase = mockk()
        consumePendingIntentUseCase = mockk(relaxed = true)
        getInstalledAppsUseCase = mockk()
        getAppFunctionsUseCase = mockk()

        every { getInstalledAppsUseCase() } returns emptyList()
        every { getAppFunctionsUseCase() } returns flowOf(emptyMap())

        every { manageThreadsUseCase.getThreads() } returns threadsFlow
        every { settingsRepository.selectedProvider } returns selectedProviderFlow
        every { agentOrchestrator.status } returns agentStatusFlow
        every { getChatHistoryUseCase(any()) } returns messagesFlow
        every { observeActivePendingIntentsUseCase() } returns activePendingActionIdsFlow
        every { observeActivePendingIntentsUseCase() } returns activePendingActionIdsFlow

        coEvery { agentOrchestrator.observeAndProcessMessages(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onEvent OnModelSelected updates thread model when thread exists`() =
        runTest {
            val existingThread =
                ThreadEntity(
                    "thread-1",
                    System.currentTimeMillis(),
                    LlmModel.GEMINI_3_FLASH_PREVIEW,
                    null,
                )
            threadsFlow.value = listOf(existingThread)
            coEvery {
                manageThreadsUseCase.updateThreadModel("thread-1", LlmModel.GEMINI_3_1_PRO_PREVIEW)
            } returns Unit

            viewModel =
                AgentDemoViewModel(
                    savedStateHandle,
                    getChatHistoryUseCase,
                    manageThreadsUseCase,
                    sendMessageUseCase,
                    agentOrchestrator,
                    settingsRepository,
                    observeActivePendingIntentsUseCase,
                    launchPendingIntentUseCase,
                    consumePendingIntentUseCase,
                    getInstalledAppsUseCase,
                    getAppFunctionsUseCase,
                )

            viewModel.onEvent(AgentUiEvent.OnModelSelected(LlmModel.GEMINI_3_1_PRO_PREVIEW))

            coVerify {
                manageThreadsUseCase.updateThreadModel("thread-1", LlmModel.GEMINI_3_1_PRO_PREVIEW)
            }
        }

    @Test
    fun `init creates thread when none exists`() =
        runTest {
            threadsFlow.value = emptyList()
            coEvery { manageThreadsUseCase.createThread(LlmModel.DEFAULT) } returns "thread-new"

            viewModel =
                AgentDemoViewModel(
                    savedStateHandle,
                    getChatHistoryUseCase,
                    manageThreadsUseCase,
                    sendMessageUseCase,
                    agentOrchestrator,
                    settingsRepository,
                    observeActivePendingIntentsUseCase,
                    launchPendingIntentUseCase,
                    consumePendingIntentUseCase,
                    getInstalledAppsUseCase,
                    getAppFunctionsUseCase,
                )

            coVerify { manageThreadsUseCase.createThread(LlmModel.DEFAULT) }
        }

    @Test
    fun `init does not create thread when one already exists`() =
        runTest {
            val existingThread =
                ThreadEntity(
                    "thread-1",
                    System.currentTimeMillis(),
                    LlmModel.GEMINI_3_1_PRO_PREVIEW,
                    null,
                )
            threadsFlow.value = listOf(existingThread)
            selectedProviderFlow.value = LlmProviderName.GEMINI

            viewModel =
                AgentDemoViewModel(
                    savedStateHandle,
                    getChatHistoryUseCase,
                    manageThreadsUseCase,
                    sendMessageUseCase,
                    agentOrchestrator,
                    settingsRepository,
                    observeActivePendingIntentsUseCase,
                    launchPendingIntentUseCase,
                    consumePendingIntentUseCase,
                    getInstalledAppsUseCase,
                    getAppFunctionsUseCase,
                )

            coVerify(exactly = 0) { manageThreadsUseCase.createThread(any()) }
            assertEquals(
                existingThread,
                (viewModel.uiState.value as AgentUiState.Loaded).currentThread,
            )
        }

    @Test
    fun `onEvent OnSendMessage calls SendMessageUseCase`() =
        runTest {
            val existingThread =
                ThreadEntity(
                    "thread-1",
                    System.currentTimeMillis(),
                    LlmModel.GEMINI_3_1_PRO_PREVIEW,
                    null,
                )
            threadsFlow.value = listOf(existingThread)
            selectedProviderFlow.value = LlmProviderName.GEMINI

            viewModel =
                AgentDemoViewModel(
                    savedStateHandle,
                    getChatHistoryUseCase,
                    manageThreadsUseCase,
                    sendMessageUseCase,
                    agentOrchestrator,
                    settingsRepository,
                    observeActivePendingIntentsUseCase,
                    launchPendingIntentUseCase,
                    consumePendingIntentUseCase,
                    getInstalledAppsUseCase,
                    getAppFunctionsUseCase,
                )

            coEvery { sendMessageUseCase(any(), any(), any(), any()) } returns Unit

            viewModel.onEvent(AgentUiEvent.OnSendMessage("Hello"))

            coVerify {
                sendMessageUseCase(
                    threadId = "thread-1",
                    role = MessageRole.USER,
                    textContent = "Hello",
                    processingStatus = MessageProcessingStatus.PENDING_AGENT_RESPONSE,
                )
            }
        }

    @Test
    fun `state updates when messages are received`() =
        runTest {
            val existingThread =
                ThreadEntity(
                    "thread-1",
                    System.currentTimeMillis(),
                    LlmModel.GEMINI_3_1_PRO_PREVIEW,
                    null,
                )
            threadsFlow.value = listOf(existingThread)
            selectedProviderFlow.value = LlmProviderName.GEMINI

            viewModel =
                AgentDemoViewModel(
                    savedStateHandle,
                    getChatHistoryUseCase,
                    manageThreadsUseCase,
                    sendMessageUseCase,
                    agentOrchestrator,
                    settingsRepository,
                    observeActivePendingIntentsUseCase,
                    launchPendingIntentUseCase,
                    consumePendingIntentUseCase,
                    getInstalledAppsUseCase,
                    getAppFunctionsUseCase,
                )

            val message =
                MessageEntity(
                    "msg-1",
                    "thread-1",
                    MessageRole.USER,
                    "Hello",
                    System.currentTimeMillis(),
                    MessageProcessingStatus.PROCESSED,
                )
            messagesFlow.value = listOf(message)

            assertEquals(listOf(message), (viewModel.uiState.value as AgentUiState.Loaded).messages)
        }

    @Test
    fun `init selects newest thread when multiple exist`() =
        runTest {
            val oldThread =
                ThreadEntity(
                    "thread-old",
                    System.currentTimeMillis() - 1000,
                    LlmModel.GEMINI_3_1_PRO_PREVIEW,
                    null,
                )
            val newThread =
                ThreadEntity(
                    "thread-new",
                    System.currentTimeMillis(),
                    LlmModel.GEMINI_3_1_PRO_PREVIEW,
                    null,
                )
            threadsFlow.value = listOf(newThread, oldThread)
            selectedProviderFlow.value = LlmProviderName.GEMINI

            viewModel =
                AgentDemoViewModel(
                    savedStateHandle,
                    getChatHistoryUseCase,
                    manageThreadsUseCase,
                    sendMessageUseCase,
                    agentOrchestrator,
                    settingsRepository,
                    observeActivePendingIntentsUseCase,
                    launchPendingIntentUseCase,
                    consumePendingIntentUseCase,
                    getInstalledAppsUseCase,
                    getAppFunctionsUseCase,
                )

            assertEquals(newThread, (viewModel.uiState.value as AgentUiState.Loaded).currentThread)
        }
}
