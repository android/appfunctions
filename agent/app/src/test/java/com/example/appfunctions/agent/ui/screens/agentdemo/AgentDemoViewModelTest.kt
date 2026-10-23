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
import androidx.lifecycle.ViewModelStore
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.data.db.entities.MessageEntity
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import com.example.appfunctions.agent.domain.AgentOrchestrator
import com.example.appfunctions.agent.domain.AgentStatus
import com.example.appfunctions.agent.domain.LlmProvider
import com.example.appfunctions.agent.domain.LlmProviderFactory
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
import kotlinx.coroutines.flow.emptyFlow
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
    private val viewModelStore = ViewModelStore()

    private lateinit var getChatHistoryUseCase: GetChatHistoryUseCase
    private lateinit var manageThreadsUseCase: ManageThreadsUseCase
    private lateinit var sendMessageUseCase: SendMessageUseCase
    private lateinit var agentOrchestrator: AgentOrchestrator
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var llmProviderFactory: LlmProviderFactory
    private lateinit var llmProvider: LlmProvider
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var observeActivePendingIntentsUseCase: ObserveActivePendingIntentsUseCase
    private lateinit var launchPendingIntentUseCase: LaunchPendingIntentUseCase
    private lateinit var consumePendingIntentUseCase: ConsumePendingIntentUseCase
    private lateinit var getInstalledAppsUseCase: GetInstalledAppsUseCase
    private lateinit var getAppFunctionsUseCase: GetAppFunctionsUseCase

    private lateinit var viewModel: AgentDemoViewModel

    private val testModelFlash = LlmModel(LlmProviderName.GEMINI, "gemini-2.5-flash")
    private val testModelPro = LlmModel(LlmProviderName.GEMINI, "gemini-2.5-pro")

    private val threadsFlow = MutableStateFlow<List<ThreadEntity>>(emptyList())
    private val selectedProviderFlow = MutableStateFlow<LlmProviderName>(LlmProviderName.GEMINI)
    private val geminiApiKeyFlow = MutableStateFlow<String?>(null)
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
        llmProviderFactory = mockk()
        llmProvider = mockk()
        savedStateHandle = SavedStateHandle()
        observeActivePendingIntentsUseCase = mockk()
        launchPendingIntentUseCase = mockk()
        consumePendingIntentUseCase = mockk(relaxed = true)
        getInstalledAppsUseCase = mockk()
        getAppFunctionsUseCase = mockk()

        every { getInstalledAppsUseCase() } returns emptyList()
        every { getAppFunctionsUseCase() } returns emptyFlow()

        every { manageThreadsUseCase.getThreads() } returns threadsFlow
        every { settingsRepository.selectedProvider } returns selectedProviderFlow
        every { settingsRepository.geminiApiKey } returns geminiApiKeyFlow
        every { llmProviderFactory.getProvider(any()) } returns llmProvider
        coEvery { llmProvider.getAvailableModels(any()) } returns listOf(testModelFlash, testModelPro)
        every { agentOrchestrator.status } returns agentStatusFlow
        every { getChatHistoryUseCase(any()) } returns messagesFlow
        every { observeActivePendingIntentsUseCase() } returns activePendingActionIdsFlow

        coEvery { agentOrchestrator.observeAndProcessMessages(any()) } returns Unit
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AgentDemoViewModel =
        AgentDemoViewModel(
            savedStateHandle,
            getChatHistoryUseCase,
            manageThreadsUseCase,
            sendMessageUseCase,
            agentOrchestrator,
            settingsRepository,
            llmProviderFactory,
            observeActivePendingIntentsUseCase,
            launchPendingIntentUseCase,
            consumePendingIntentUseCase,
            getInstalledAppsUseCase,
            getAppFunctionsUseCase,
        ).also { viewModelStore.put("AgentDemoViewModel", it) }

    @Test
    fun `onEvent OnModelSelected updates thread model when thread exists`() =
        runTest {
            val existingThread =
                ThreadEntity(
                    "thread-1",
                    System.currentTimeMillis(),
                    testModelFlash,
                    null,
                )
            threadsFlow.value = listOf(existingThread)
            coEvery {
                manageThreadsUseCase.updateThreadModel("thread-1", testModelPro)
            } returns Unit

            viewModel = createViewModel()

            viewModel.onEvent(AgentUiEvent.OnModelSelected(testModelPro))

            coVerify {
                manageThreadsUseCase.updateThreadModel("thread-1", testModelPro)
            }
        }

    @Test
    fun `init creates thread when none exists`() =
        runTest {
            threadsFlow.value = emptyList()
            coEvery { manageThreadsUseCase.createThread(testModelFlash) } returns "thread-new"

            viewModel = createViewModel()

            coVerify { manageThreadsUseCase.createThread(testModelFlash) }
        }

    @Test
    fun `init does not create thread when one already exists`() =
        runTest {
            val existingThread =
                ThreadEntity(
                    "thread-1",
                    System.currentTimeMillis(),
                    testModelPro,
                    null,
                )
            threadsFlow.value = listOf(existingThread)
            selectedProviderFlow.value = LlmProviderName.GEMINI

            viewModel = createViewModel()

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
                    testModelPro,
                    null,
                )
            threadsFlow.value = listOf(existingThread)
            selectedProviderFlow.value = LlmProviderName.GEMINI

            viewModel = createViewModel()

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
                    testModelPro,
                    null,
                )
            threadsFlow.value = listOf(existingThread)
            selectedProviderFlow.value = LlmProviderName.GEMINI

            viewModel = createViewModel()

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
                    testModelPro,
                    null,
                )
            val newThread =
                ThreadEntity(
                    "thread-new",
                    System.currentTimeMillis(),
                    testModelPro,
                    null,
                )
            threadsFlow.value = listOf(newThread, oldThread)
            selectedProviderFlow.value = LlmProviderName.GEMINI

            viewModel = createViewModel()

            assertEquals(newThread, (viewModel.uiState.value as AgentUiState.Loaded).currentThread)
        }

    @Test
    fun `availableModels loads dynamically from provider`() =
        runTest {
            val dynamicModels = listOf(testModelPro)
            coEvery { llmProvider.getAvailableModels("test-api-key") } returns dynamicModels
            geminiApiKeyFlow.value = "test-api-key"
            val existingThread =
                ThreadEntity(
                    "thread-1",
                    System.currentTimeMillis(),
                    testModelFlash,
                    null,
                )
            threadsFlow.value = listOf(existingThread)

            viewModel = createViewModel()

            assertEquals(
                dynamicModels,
                (viewModel.uiState.value as AgentUiState.Loaded).availableModels,
            )
        }

    @Test
    fun `init emits Loaded with null currentThread when no threads and no models exist`() =
        runTest {
            threadsFlow.value = emptyList()
            coEvery { llmProvider.getAvailableModels(any()) } returns emptyList()

            viewModel = createViewModel()

            val loadedState = viewModel.uiState.value as AgentUiState.Loaded
            assertEquals(null, loadedState.currentThread)
            assertEquals(emptyList<LlmModel>(), loadedState.availableModels)
        }
}
