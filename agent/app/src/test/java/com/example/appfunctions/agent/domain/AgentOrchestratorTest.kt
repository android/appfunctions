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
package com.example.appfunctions.agent.domain

import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.data.db.entities.MessageEntity
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import com.example.appfunctions.agent.domain.appfunction.ConvertInputToAppFunctionDataUseCase
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.chat.ManageThreadsUseCase
import com.example.appfunctions.agent.domain.chat.ObservePendingMessagesUseCase
import com.example.appfunctions.agent.domain.chat.SendMessageUseCase
import com.example.appfunctions.agent.domain.chat.UpdateMessageParams
import com.example.appfunctions.agent.domain.chat.UpdateMessageUseCase
import com.example.appfunctions.agent.domain.chat.UpdateThreadParams
import com.example.appfunctions.agent.domain.chat.UpdateThreadUseCase
import com.example.appfunctions.agent.domain.pendingintent.SavePendingIntentUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentOrchestratorTest {
    private val observePendingMessagesUseCase: ObservePendingMessagesUseCase = mockk()
    private val updateMessageUseCase: UpdateMessageUseCase = mockk(relaxed = true)
    private val updateThreadUseCase: UpdateThreadUseCase = mockk(relaxed = true)
    private val manageThreadsUseCase: ManageThreadsUseCase = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val llmProviderFactory: LlmProviderFactory = mockk()
    private val getAppFunctionsUseCase: GetAppFunctionsUseCase = mockk()
    private val executeAppFunctionUseCase: ExecuteAppFunctionUseCase = mockk()
    private val sendMessageUseCase: SendMessageUseCase = mockk(relaxed = true)
    private val convertInputToAppFunctionDataUseCase: ConvertInputToAppFunctionDataUseCase = mockk()
    private val savePendingIntentUseCase: SavePendingIntentUseCase = mockk(relaxed = true)

    private lateinit var agentOrchestrator: AgentOrchestrator

    @Before
    fun setUp() {
        agentOrchestrator =
            AgentOrchestrator(
                manageThreadsUseCase = manageThreadsUseCase,
                observePendingMessagesUseCase = observePendingMessagesUseCase,
                sendMessageUseCase = sendMessageUseCase,
                updateMessageUseCase = updateMessageUseCase,
                updateThreadUseCase = updateThreadUseCase,
                llmProviderFactory = llmProviderFactory,
                settingsRepository = settingsRepository,
                getAppFunctionsUseCase = getAppFunctionsUseCase,
                convertInputToAppFunctionDataUseCase = convertInputToAppFunctionDataUseCase,
                executeAppFunctionUseCase = executeAppFunctionUseCase,
                savePendingIntentUseCase = savePendingIntentUseCase,
            )
    }

    @Test
    fun `observeAndProcessMessages fails when API key is missing`() =
        runTest {
            val threadId = "thread_1"
            val message = createUserMessage(threadId, "Hello", messageId = "msg_1")
            val thread = createThread(threadId)

            setupDefaultMocks(threadId, message, thread, apiKey = null)

            agentOrchestrator.observeAndProcessMessages(threadId)

            // Verify behavior (state)
            assertEquals(AgentStatus.Idle, agentOrchestrator.status.value)

            // Verify interactions
            coVerify {
                updateMessageUseCase(
                    message.messageId,
                    UpdateMessageParams(status = MessageProcessingStatus.PROCESSED),
                )
                sendMessageUseCase(
                    threadId = threadId,
                    role = MessageRole.ASSISTANT,
                    textContent = "API key is missing for GEMINI",
                    processingStatus = MessageProcessingStatus.FAILED,
                )
            }
        }

    @Test
    fun `observeAndProcessMessages fails when LLM returns error`() =
        runTest {
            val threadId = "thread_1"
            val message = createUserMessage(threadId, "Hello", messageId = "msg_1")
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()

            setupDefaultMocks(threadId, message, thread, llmProvider = llmProvider)
            coEvery { getAppFunctionsUseCase() } returns flowOf(emptyMap())

            val errorMsg = "LLM failed"
            coEvery { llmProvider.generateResponse(any(), any(), any(), any(), any()) } returns
                LlmResponse.Error(errorMsg)

            agentOrchestrator.observeAndProcessMessages(threadId)

            // Verify behavior (state)
            assertEquals(AgentStatus.Idle, agentOrchestrator.status.value)

            // Verify interactions
            coVerify {
                updateMessageUseCase(
                    message.messageId,
                    UpdateMessageParams(status = MessageProcessingStatus.PROCESSED),
                )
                sendMessageUseCase(
                    threadId = threadId,
                    role = MessageRole.ASSISTANT,
                    textContent = errorMsg,
                    processingStatus = MessageProcessingStatus.FAILED,
                )
            }
        }

    @Test
    fun `observeAndProcessMessages succeeds when LLM returns text`() =
        runTest {
            val threadId = "thread_1"
            val message = createUserMessage(threadId, "Hello", messageId = "msg_1")
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()

            setupDefaultMocks(threadId, message, thread, llmProvider = llmProvider)
            coEvery { getAppFunctionsUseCase() } returns flowOf(emptyMap())

            val responseText = "Hi there"
            coEvery { llmProvider.generateResponse(any(), any(), any(), any(), any()) } returns
                LlmResponse.Success(
                    "interaction_123",
                    listOf(LlmResponsePart.Text(responseText)),
                )

            agentOrchestrator.observeAndProcessMessages(threadId)

            // Verify behavior (state)
            assertEquals(AgentStatus.Idle, agentOrchestrator.status.value)

            // Verify interactions
            coVerify {
                updateThreadUseCase(threadId, UpdateThreadParams(interactionId = "interaction_123"))
                sendMessageUseCase(
                    threadId = threadId,
                    role = MessageRole.ASSISTANT,
                    textContent = responseText,
                    processingStatus = MessageProcessingStatus.PROCESSED,
                )
                updateMessageUseCase(
                    message.messageId,
                    UpdateMessageParams(status = MessageProcessingStatus.PROCESSED),
                )
            }
        }

    @Test
    fun `observeAndProcessMessages scopes tools when targetPackageName is set`() =
        runTest {
            val threadId = "thread_1"
            val message =
                createUserMessage(
                    threadId = threadId,
                    textContent = "run geo code address for n1c4ag",
                    targetPackageName = "com.google.android.appfunctiontestingagent",
                )
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()

            val tool1 = createMockTool("com.google.android.appfunctiontestingagent", "run_geo_code")
            val tool2 = createMockTool("com.google.android.digitalwellbeing", "digital_well_being_tool")
            mockAppFunctions(listOf(tool1, tool2))

            setupDefaultMocks(threadId, message, thread, llmProvider = llmProvider)

            coEvery {
                llmProvider.generateResponse(any(), any(), any(), any(), any())
            } returns LlmResponse.Success("interaction_id", listOf(LlmResponsePart.Text("Success")))

            agentOrchestrator.observeAndProcessMessages(threadId)

            coVerify {
                llmProvider.generateResponse(
                    previousInteractionId = null,
                    input = eq(LlmInput.UserMessage("run geo code address for n1c4ag")),
                    tools = listOf(tool1),
                    apiKey = "dummy_key",
                    modelName = any(),
                )
            }
        }

    @Test
    fun `observeAndProcessMessages does not scope tools when targetPackageName is null`() =
        runTest {
            val threadId = "thread_1"
            val message = createUserMessage(threadId, "run geo code address for n1c4ag")
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()

            val tool1 = createMockTool("com.google.android.appfunctiontestingagent", "run_geo_code")
            val tool2 = createMockTool("com.google.android.digitalwellbeing", "digital_well_being_tool")
            mockAppFunctions(listOf(tool1, tool2))

            setupDefaultMocks(threadId, message, thread, llmProvider = llmProvider)

            coEvery {
                llmProvider.generateResponse(any(), any(), any(), any(), any())
            } returns LlmResponse.Success("interaction_id", listOf(LlmResponsePart.Text("Success")))

            agentOrchestrator.observeAndProcessMessages(threadId)

            coVerify {
                llmProvider.generateResponse(
                    previousInteractionId = null,
                    input = eq(LlmInput.UserMessage("run geo code address for n1c4ag")),
                    tools = listOf(tool1, tool2),
                    apiKey = "dummy_key",
                    modelName = any(),
                )
            }
        }

    private fun createUserMessage(
        threadId: String,
        textContent: String,
        messageId: String = "message_1",
        targetPackageName: String? = null,
    ) = MessageEntity(
        messageId = messageId,
        threadId = threadId,
        role = MessageRole.USER,
        textContent = textContent,
        timestamp = System.currentTimeMillis(),
        processingStatus = MessageProcessingStatus.PENDING_AGENT_RESPONSE,
        targetPackageName = targetPackageName,
    )

    private fun createThread(
        threadId: String,
        llmModel: LlmModel = LlmModel.GEMINI_3_FLASH_PREVIEW,
        latestInteractionId: String? = null,
    ) = ThreadEntity(
        threadId = threadId,
        createdAt = System.currentTimeMillis(),
        llmModel = llmModel,
        latestInteractionId = latestInteractionId,
    )

    private fun createMockTool(
        packageName: String,
        id: String,
        isEnabled: Boolean = true,
    ): AppFunctionMetadata {
        val tool = mockk<AppFunctionMetadata>()
        every { tool.packageName } returns packageName
        every { tool.id } returns id
        every { tool.isEnabled } returns isEnabled
        return tool
    }

    private fun mockAppFunctions(tools: List<AppFunctionMetadata>) {
        val packageMetadata = mockk<AppFunctionPackageMetadata>(relaxed = true)
        coEvery { getAppFunctionsUseCase() } returns flowOf(mapOf(packageMetadata to tools))
    }

    private fun setupDefaultMocks(
        threadId: String,
        message: MessageEntity,
        thread: ThreadEntity,
        apiKey: String? = "dummy_key",
        disconnectedApps: Set<String> = emptySet(),
        llmProvider: LlmProvider = mockk(),
    ) {
        coEvery { observePendingMessagesUseCase(threadId) } returns
            flow {
                delay(10)
                emit(message)
            }
        coEvery { manageThreadsUseCase.getThread(threadId) } returns flowOf(thread)
        coEvery { settingsRepository.geminiApiKey } returns flowOf(apiKey)
        coEvery { settingsRepository.disconnectedApps } returns flowOf(disconnectedApps)
        coEvery { llmProviderFactory.getProvider(LlmProviderName.GEMINI) } returns llmProvider
    }
}
