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
import com.example.appfunctions.agent.domain.ActiveLiveSession
import com.example.appfunctions.agent.domain.AgentOrchestrator
import com.example.appfunctions.agent.domain.AgentStatus
import com.example.appfunctions.agent.domain.LiveAgentOrchestrator
import com.example.appfunctions.agent.domain.LiveAgentStatus
import com.example.appfunctions.agent.domain.LiveLlmProvider
import com.example.appfunctions.agent.domain.LlmProvider
import com.example.appfunctions.agent.domain.LlmProviderFactory
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsParams
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.appfunction.GetInstalledAppsUseCase
import com.example.appfunctions.agent.domain.chat.GetChatHistoryUseCase
import com.example.appfunctions.agent.domain.chat.ManageThreadsUseCase
import com.example.appfunctions.agent.domain.chat.SendMessageUseCase
import com.example.appfunctions.agent.domain.live.CheckAudioPermissionUseCase
import com.example.appfunctions.agent.domain.live.CheckVoiceServiceActiveUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private lateinit var liveAgentOrchestrator: LiveAgentOrchestrator
    private lateinit var checkAudioPermissionUseCase: CheckAudioPermissionUseCase
    private lateinit var checkVoiceServiceActiveUseCase: CheckVoiceServiceActiveUseCase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var llmProviderFactory: LlmProviderFactory
    private lateinit var llmProvider: LlmProvider
    private lateinit var liveLlmProvider: LiveLlmProvider
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var observeActivePendingIntentsUseCase: ObserveActivePendingIntentsUseCase
    private lateinit var launchPendingIntentUseCase: LaunchPendingIntentUseCase
    private lateinit var consumePendingIntentUseCase: ConsumePendingIntentUseCase
    private lateinit var getInstalledAppsUseCase: GetInstalledAppsUseCase
    private lateinit var getAppFunctionsUseCase: GetAppFunctionsUseCase

    private lateinit var viewModel: AgentDemoViewModel

    private val testModelFlash = LlmModel(LlmProviderName.GEMINI, "gemini-2.5-flash")
    private val testModelPro = LlmModel(LlmProviderName.GEMINI, "gemini-2.5-pro")
    private val testModelLive = LlmModel(LlmProviderName.GEMINI, "gemini-3.8-live")

    private val threadsFlow = MutableStateFlow<List<ThreadEntity>>(emptyList())
    private val selectedProviderFlow = MutableStateFlow<LlmProviderName>(LlmProviderName.GEMINI)
    private val geminiApiKeyFlow = MutableStateFlow<String?>(null)
    private val agentStatusFlow = MutableStateFlow<AgentStatus>(AgentStatus.Idle)
    private val activeLiveSessionFlow = MutableStateFlow<ActiveLiveSession?>(null)
    private val messagesFlow = MutableStateFlow<List<MessageEntity>>(emptyList())
    private val activePendingActionIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        getChatHistoryUseCase = mockk()
        manageThreadsUseCase = mockk()
        sendMessageUseCase = mockk()
        agentOrchestrator = mockk()
        liveAgentOrchestrator = mockk(relaxed = true)
        checkAudioPermissionUseCase = mockk()
        checkVoiceServiceActiveUseCase = mockk()
        settingsRepository = mockk()
        llmProviderFactory = mockk()
        llmProvider = mockk()
        liveLlmProvider = mockk()
        savedStateHandle = SavedStateHandle()
        observeActivePendingIntentsUseCase = mockk()
        launchPendingIntentUseCase = mockk()
        consumePendingIntentUseCase = mockk(relaxed = true)
        getInstalledAppsUseCase = mockk()
        getAppFunctionsUseCase = mockk()

        every { getInstalledAppsUseCase() } returns emptyList()
        every {
            getAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
        } returns emptyFlow()

        every { manageThreadsUseCase.getThreads() } returns threadsFlow
        every { settingsRepository.selectedProvider } returns selectedProviderFlow
        every { settingsRepository.geminiApiKey } returns geminiApiKeyFlow
        every { llmProviderFactory.getProvider(any()) } returns llmProvider
        coEvery { llmProvider.getAvailableModels(any()) } returns listOf(testModelFlash, testModelPro)
        every { llmProviderFactory.getLiveProvider(any()) } returns liveLlmProvider
        every { liveLlmProvider.liveModel } returns testModelLive
        every { agentOrchestrator.status } returns agentStatusFlow
        every { liveAgentOrchestrator.activeSession } returns activeLiveSessionFlow
        every { checkAudioPermissionUseCase() } returns true
        every { checkVoiceServiceActiveUseCase() } returns true
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
            liveAgentOrchestrator,
            checkAudioPermissionUseCase,
            checkVoiceServiceActiveUseCase,
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

    @Test
    fun `uiState reflects LiveAgentOrchestrator activeSession on current and other threads`() =
        runTest {
            val thread1 = ThreadEntity("thread-1", 2000L, testModelFlash, null)
            val thread2 = ThreadEntity("thread-2", 1000L, testModelFlash, null)
            threadsFlow.value = listOf(thread1, thread2)

            viewModel = createViewModel()

            activeLiveSessionFlow.value =
                ActiveLiveSession(
                    threadId = "thread-1",
                    status = LiveAgentStatus.Listening,
                )

            val stateOnThread1 = viewModel.uiState.value as AgentUiState.Loaded
            assertEquals(LiveAgentStatus.Listening, stateOnThread1.liveStatus)
            assertFalse(stateOnThread1.isAnotherThreadLiveBusy)

            viewModel.onEvent(AgentUiEvent.OnThreadSelected("thread-2"))

            val stateOnThread2 = viewModel.uiState.value as AgentUiState.Loaded
            assertNull(stateOnThread2.liveStatus)
            assertTrue(stateOnThread2.isAnotherThreadLiveBusy)
        }

    @Test
    fun `OnStartLiveSession checks permission and assistant role before creating Live thread and emitting LaunchAssistSession`() =
        runTest {
            val thread1 = ThreadEntity("thread-1", 2000L, testModelFlash, null)
            threadsFlow.value = listOf(thread1)
            every { checkAudioPermissionUseCase() } returns false
            every { checkVoiceServiceActiveUseCase() } returns false
            coEvery { manageThreadsUseCase.createThread(testModelLive) } coAnswers {
                val liveThread = ThreadEntity("thread-live", 3000L, testModelLive, null)
                threadsFlow.value = listOf(liveThread) + threadsFlow.value
                "thread-live"
            }

            viewModel = createViewModel()

            viewModel.onEvent(AgentUiEvent.OnStartLiveSession("com.example.chat"))
            assertEquals(
                LiveSetupPrompt.RequestAudioPermission("com.example.chat"),
                (viewModel.uiState.value as AgentUiState.Loaded).liveSetupPrompt,
            )

            every { checkAudioPermissionUseCase() } returns true
            viewModel.onEvent(AgentUiEvent.OnAudioPermissionResult(isGranted = true))
            assertEquals(
                LiveSetupPrompt.SelectDigitalAssistant("com.example.chat"),
                (viewModel.uiState.value as AgentUiState.Loaded).liveSetupPrompt,
            )

            every { checkVoiceServiceActiveUseCase() } returns true
            viewModel.onEvent(AgentUiEvent.OnAssistantSettingsReturned)
            val afterAssistantReady = viewModel.uiState.value as AgentUiState.Loaded
            coVerify(exactly = 1) { manageThreadsUseCase.createThread(testModelLive) }
            assertEquals("thread-live", afterAssistantReady.currentThread?.threadId)
            assertTrue(afterAssistantReady.isCurrentThreadLive)
            assertEquals(
                LiveSetupPrompt.LaunchAssistSession("thread-live", "com.example.chat"),
                afterAssistantReady.liveSetupPrompt,
            )

            viewModel.onEvent(AgentUiEvent.OnLiveSessionLaunchResult(isLaunched = true))
            val afterLaunched = viewModel.uiState.value as AgentUiState.Loaded
            assertNull(afterLaunched.liveSetupPrompt)
            assertEquals("thread-live", afterLaunched.currentThread?.threadId)
        }

    @Test
    fun `OnAssistantSettingsReturned surfaces AssistantNotSelected when voice service is still inactive`() =
        runTest {
            val thread1 = ThreadEntity("thread-1", 2000L, testModelFlash, null)
            threadsFlow.value = listOf(thread1)
            every { checkAudioPermissionUseCase() } returns true
            every { checkVoiceServiceActiveUseCase() } returns false

            viewModel = createViewModel()

            viewModel.onEvent(AgentUiEvent.OnStartLiveSession("com.example.chat"))
            assertEquals(
                LiveSetupPrompt.SelectDigitalAssistant("com.example.chat"),
                (viewModel.uiState.value as AgentUiState.Loaded).liveSetupPrompt,
            )

            viewModel.onEvent(AgentUiEvent.OnAssistantSettingsReturned)
            val afterReturned = viewModel.uiState.value as AgentUiState.Loaded
            assertEquals(LiveSetupPrompt.AssistantNotSelected, afterReturned.liveSetupPrompt)
            assertEquals("thread-1", afterReturned.currentThread?.threadId)
            coVerify(exactly = 0) { manageThreadsUseCase.createThread(any()) }
        }

    @Test
    fun `OnLiveSessionLaunchResult failure deletes pending Live thread and restores previous thread`() =
        runTest {
            val thread1 = ThreadEntity("thread-1", 2000L, testModelFlash, null)
            threadsFlow.value = listOf(thread1)
            coEvery { manageThreadsUseCase.createThread(testModelLive) } coAnswers {
                val liveThread = ThreadEntity("thread-live", 3000L, testModelLive, null)
                threadsFlow.value = listOf(liveThread) + threadsFlow.value
                "thread-live"
            }
            coEvery { manageThreadsUseCase.deleteThread("thread-live") } coAnswers {
                threadsFlow.value = threadsFlow.value.filterNot { it.threadId == "thread-live" }
            }

            viewModel = createViewModel()

            viewModel.onEvent(AgentUiEvent.OnStartLiveSession())
            assertEquals(
                "thread-live",
                (viewModel.uiState.value as AgentUiState.Loaded).currentThread?.threadId,
            )

            viewModel.onEvent(AgentUiEvent.OnLiveSessionLaunchResult(isLaunched = false))
            val afterFailure = viewModel.uiState.value as AgentUiState.Loaded
            coVerify(exactly = 1) { manageThreadsUseCase.deleteThread("thread-live") }
            assertEquals("thread-1", afterFailure.currentThread?.threadId)
            assertEquals(LiveSetupPrompt.AssistLaunchFailed, afterFailure.liveSetupPrompt)
        }

    @Test
    fun `Live-model thread ignores OnSendMessage and OnModelSelected creates new text thread`() =
        runTest {
            val liveThread = ThreadEntity("thread-live", 3000L, testModelLive, null)
            threadsFlow.value = listOf(liveThread)
            coEvery { manageThreadsUseCase.createThread(testModelPro) } coAnswers {
                val newTextThread = ThreadEntity("thread-text", 4000L, testModelPro, null)
                threadsFlow.value = listOf(newTextThread, liveThread)
                "thread-text"
            }

            viewModel = createViewModel()
            assertTrue((viewModel.uiState.value as AgentUiState.Loaded).isCurrentThreadLive)

            viewModel.onEvent(AgentUiEvent.OnSendMessage("Should be ignored"))
            coVerify(exactly = 0) {
                sendMessageUseCase(any(), any(), any(), any(), any(), any(), any())
            }

            viewModel.onEvent(AgentUiEvent.OnModelSelected(testModelPro))
            coVerify(exactly = 0) { manageThreadsUseCase.updateThreadModel(any(), any()) }
            coVerify(exactly = 1) { manageThreadsUseCase.createThread(testModelPro) }
            val afterModelSelected = viewModel.uiState.value as AgentUiState.Loaded
            assertEquals("thread-text", afterModelSelected.currentThread?.threadId)
            assertFalse(afterModelSelected.isCurrentThreadLive)
        }

    @Test
    fun `Live setup failure events surface error prompts and dismiss clears prompt`() =
        runTest {
            val thread1 = ThreadEntity("thread-1", 2000L, testModelFlash, null)
            threadsFlow.value = listOf(thread1)
            viewModel = createViewModel()

            viewModel.onEvent(AgentUiEvent.OnAudioPermissionResult(isGranted = false))
            assertEquals(
                LiveSetupPrompt.AudioPermissionDenied,
                (viewModel.uiState.value as AgentUiState.Loaded).liveSetupPrompt,
            )

            viewModel.onEvent(AgentUiEvent.OnAssistantSettingsUnavailable)
            assertEquals(
                LiveSetupPrompt.AssistantSettingsUnavailable,
                (viewModel.uiState.value as AgentUiState.Loaded).liveSetupPrompt,
            )

            viewModel.onEvent(AgentUiEvent.OnLiveSessionLaunchResult(isLaunched = false))
            assertEquals(
                LiveSetupPrompt.AssistLaunchFailed,
                (viewModel.uiState.value as AgentUiState.Loaded).liveSetupPrompt,
            )

            viewModel.onEvent(AgentUiEvent.OnDismissLiveSetupPrompt)
            assertNull((viewModel.uiState.value as AgentUiState.Loaded).liveSetupPrompt)
        }

    @Test
    fun `newThreadModel is first available model when current thread model is not offered`() =
        runTest {
            val ttsModel = LlmModel(LlmProviderName.GEMINI, "gemini-2.5-flash-preview-tts")
            val existingThread =
                ThreadEntity(
                    "thread-1",
                    System.currentTimeMillis(),
                    ttsModel,
                    null,
                )
            threadsFlow.value = listOf(existingThread)

            viewModel = createViewModel()

            val loadedState = viewModel.uiState.value as AgentUiState.Loaded
            assertEquals(existingThread, loadedState.currentThread)
            assertEquals(testModelFlash, loadedState.newThreadModel)
        }
}
