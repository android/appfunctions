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

import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import com.example.appfunctions.agent.data.AgentInternalTools
import com.example.appfunctions.agent.data.AgentInternalTools.Companion.EXPAND_FUNCTION_DECLARATIONS
import com.example.appfunctions.agent.data.AgentInternalTools.Companion.EXPAND_TOOL_DESCRIPTION
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.ServiceTier
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.data.ToolDiscoveryTools
import com.example.appfunctions.agent.data.db.entities.MessageAttachment
import com.example.appfunctions.agent.data.db.entities.MessageEntity
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import com.example.appfunctions.agent.domain.appfunction.ConvertInputToAppFunctionDataUseCase
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionResult
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsParams
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.appfunction.ResolveRemoteFileToLocalUriUseCase
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
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentOrchestratorTest {
    private val observePendingMessagesUseCase: ObservePendingMessagesUseCase = mockk()
    private val updateMessageUseCase: UpdateMessageUseCase = mockk(relaxed = true)
    private val updateThreadUseCase: UpdateThreadUseCase = mockk(relaxed = true)
    private val manageThreadsUseCase: ManageThreadsUseCase = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val llmProviderFactory: LlmProviderFactory = mockk()
    private val getAppFunctionsUseCase: GetAppFunctionsUseCase = mockk()
    private val resolveRemoteFileToLocalUriUseCase: ResolveRemoteFileToLocalUriUseCase = mockk()
    private val convertInputToAppFunctionDataUseCase: ConvertInputToAppFunctionDataUseCase = mockk()
    private val executeAppFunctionUseCase: ExecuteAppFunctionUseCase = mockk()
    private val sendMessageUseCase: SendMessageUseCase = mockk(relaxed = true)
    private val savePendingIntentUseCase: SavePendingIntentUseCase = mockk(relaxed = true)

    private lateinit var agentOrchestrator: AgentOrchestrator

    @Before
    fun setUp() {
        every { getAppFunctionsUseCase(any()) } returns flowOf(emptyMap())
        coEvery { resolveRemoteFileToLocalUriUseCase(any(), any(), any()) } answers { thirdArg() }

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
                resolveRemoteFileToLocalUriUseCase = resolveRemoteFileToLocalUriUseCase,
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

            assertEquals(AgentStatus.Idle, agentOrchestrator.status.value)

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

            val errorMsg = "LLM failed"
            coEvery {
                llmProvider.generateResponse(any(), any(), any(), any(), any(), any())
            } returns LlmResponse.Error(errorMsg)

            agentOrchestrator.observeAndProcessMessages(threadId)

            assertEquals(AgentStatus.Idle, agentOrchestrator.status.value)

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

            val responseText = "Hi there"
            coEvery {
                llmProvider.generateResponse(any(), any(), any(), any(), any(), any())
            } returns
                LlmResponse.Success("interaction_123", listOf(LlmResponsePart.Text(responseText)))

            agentOrchestrator.observeAndProcessMessages(threadId)

            assertEquals(AgentStatus.Idle, agentOrchestrator.status.value)

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
            val targetPkg = "com.google.android.appfunctiontestingagent"
            val message =
                createUserMessage(
                    threadId = threadId,
                    textContent = "run geo code address for n1c4ag",
                    targetPackageName = targetPkg,
                )
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()

            val tool1 = createMockTool(targetPkg, "run_geo_code")
            mockAppFunctions(listOf(tool1))

            setupDefaultMocks(threadId, message, thread, llmProvider = llmProvider)

            coEvery {
                llmProvider.generateResponse(any(), any(), any(), any(), any(), any())
            } returns LlmResponse.Success("interaction_id", listOf(LlmResponsePart.Text("Success")))

            agentOrchestrator.observeAndProcessMessages(threadId)

            verify {
                getAppFunctionsUseCase(
                    GetAppFunctionsParams(
                        targetPackageNames = setOf(targetPkg),
                        isEnabled = true,
                        isConnected = true,
                        includeInternalTools = true,
                    ),
                )
            }
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
            val tool2 =
                createMockTool("com.google.android.digitalwellbeing", "digital_well_being_tool")
            mockAppFunctions(listOf(tool1, tool2))

            setupDefaultMocks(threadId, message, thread, llmProvider = llmProvider)

            coEvery {
                llmProvider.generateResponse(any(), any(), any(), any(), any(), any())
            } returns LlmResponse.Success("interaction_id", listOf(LlmResponsePart.Text("Success")))

            agentOrchestrator.observeAndProcessMessages(threadId)

            verify {
                getAppFunctionsUseCase(
                    GetAppFunctionsParams(
                        targetPackageNames = null,
                        isEnabled = true,
                        isConnected = true,
                        includeInternalTools = true,
                    ),
                )
            }
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

    @Test
    fun `observeAndProcessMessages feeds AppFunctionException back to LLM as ToolOutput`() =
        runTest {
            val threadId = "thread_1"
            val message = createUserMessage(threadId, "create event")
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()

            val tool1 = createMockTool("com.example.calendar", "create_event")
            every { tool1.parameters } returns emptyList()
            every { tool1.components } returns mockk(relaxed = true)
            mockAppFunctions(listOf(tool1))

            setupDefaultMocks(threadId, message, thread, llmProvider = llmProvider)

            every {
                convertInputToAppFunctionDataUseCase(any(), any(), any())
            } returns Result.success(mockk())

            val appException =
                androidx.appfunctions.AppFunctionPermissionRequiredException(
                    "Calendar permission required",
                )
            coEvery {
                executeAppFunctionUseCase(any(), any(), any())
            } returns ExecuteAppFunctionResult.Error(appException)

            coEvery {
                llmProvider.generateResponse(null, any(), any(), any(), any())
            } returns
                LlmResponse.Success(
                    "interaction_1",
                    listOf(
                        LlmResponsePart.ToolCall(
                            packageName = "com.example.calendar",
                            functionId = "create_event",
                            arguments = emptyMap(),
                            callId = "call_1",
                        ),
                    ),
                )

            val expectedErrorOutput =
                "Tool execution failed for create_event: Error: " +
                    "AppFunctionPermissionRequiredException - Calendar permission required"
            coEvery {
                llmProvider.generateResponse(eq("interaction_1"), any(), any(), any(), any())
            } returns
                LlmResponse.Success(
                    "interaction_2",
                    listOf(LlmResponsePart.Text("Permission needed.")),
                )

            agentOrchestrator.observeAndProcessMessages(threadId)

            coVerify {
                llmProvider.generateResponse(
                    previousInteractionId = eq("interaction_1"),
                    input =
                        eq(
                            LlmInput.ToolResponse(
                                listOf(
                                    ToolOutput(
                                        functionId = "create_event",
                                        callId = "call_1",
                                        result = expectedErrorOutput,
                                    ),
                                ),
                            ),
                        ),
                    tools = listOf(tool1),
                    apiKey = "dummy_key",
                    modelName = any(),
                )
            }
        }

    @Test
    fun `observeAndProcessMessages chains resolveRemoteFileToLocalUriUseCase before converting input`() =
        runTest {
            val threadId = "thread_1"
            val message = createUserMessage(threadId, "set wallpaper")
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()

            val targetTool = createMockTool("com.example.targetapp", "setWallpaper")
            mockAppFunctions(listOf(targetTool))
            setupDefaultMocks(threadId, message, thread, llmProvider = llmProvider)

            val rawArgs = mapOf("wallpaperUri" to "https://example.com/wallpaper.png")
            val resolvedArgs = mapOf("wallpaperUri" to "content://com.example/resolved.png")
            coEvery {
                resolveRemoteFileToLocalUriUseCase(any(), any(), rawArgs)
            } returns resolvedArgs

            val toolCall =
                LlmResponsePart.ToolCall(
                    packageName = "com.example.targetapp",
                    functionId = "setWallpaper",
                    arguments = rawArgs,
                    callId = "call_1",
                )
            setupTwoStepToolCallAndExecution(
                llmProvider = llmProvider,
                toolCall = toolCall,
                secondResponseText = "Wallpaper set",
                toolResultJson = """{"ok":true}""",
            )

            agentOrchestrator.observeAndProcessMessages(threadId)

            coVerify(exactly = 1) {
                resolveRemoteFileToLocalUriUseCase(any(), any(), rawArgs)
            }
            verify(exactly = 1) {
                convertInputToAppFunctionDataUseCase(any(), any(), resolvedArgs)
            }
        }

    @Test
    fun `observeAndProcessMessages extracts attachments when tool returns imageUri and mimeType`() {
        runTest {
            val threadId = "thread_1"
            val message = createUserMessage(threadId, "generate image of a dog")
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()

            val generateTool = createMockTool("com.example.appfunctions.agent", "generateImage")
            mockAppFunctions(listOf(generateTool))
            setupDefaultMocks(threadId, message, thread, llmProvider = llmProvider)

            val toolCall =
                LlmResponsePart.ToolCall(
                    packageName = "com.example.appfunctions.agent",
                    functionId = "generateImage",
                    arguments = mapOf("prompt" to "dog"),
                    callId = "call_1",
                )
            setupTwoStepToolCallAndExecution(
                llmProvider = llmProvider,
                toolCall = toolCall,
                secondResponseText = "Here is your image!",
                toolResultJson =
                    """{"imageUri":"content://com.example.appfunctions.agent.fileprovider/cache/test.jpg",""" +
                        """"mimeType":"image/jpeg","prompt":"dog"}""",
            )

            agentOrchestrator.observeAndProcessMessages(threadId)

            val testUri =
                "content://com.example.appfunctions.agent.fileprovider/cache/test.jpg"
            coVerify {
                sendMessageUseCase(
                    threadId = threadId,
                    role = MessageRole.ASSISTANT,
                    textContent = "Here is your image!",
                    processingStatus = MessageProcessingStatus.PROCESSED,
                    pendingIntentId = null,
                    targetPackageName = null,
                    attachments = listOf(MessageAttachment(uri = testUri, mimeType = "image/jpeg")),
                )
            }
        }
    }

    @Test
    fun `progressive disclosure loads functions within the turn and shows each level`() =
        runTest {
            val threadId = "thread_1"
            val message = createUserMessage(threadId, "Create a note")
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()
            setupDefaultMocks(threadId, message, thread, llmProvider = llmProvider, progressiveToolDisclosure = true)
            mockProgressiveTools()
            mockToolExecution()

            val capturedTools = mutableListOf<List<AppFunctionMetadata>>()
            val capturedInputs = mutableListOf<LlmInput>()
            val statusesAtCall = mutableListOf<AgentStatus>()
            mockLlmResponses(
                llmProvider,
                capturedTools,
                capturedInputs,
                statusesAtCall,
                success("i1", expandToolDescriptionCall()),
                success("i2", expandFunctionDeclarationsCall()),
                success("i3", LlmResponsePart.ToolCall(NOTES_PKG, CREATE_NOTE, emptyMap(), "c3")),
                LlmResponse.Success("i4", listOf(LlmResponsePart.Text("Done"))),
            )

            agentOrchestrator.observeAndProcessMessages(threadId)

            val discoveryIds = listOf(EXPAND_TOOL_DESCRIPTION, EXPAND_FUNCTION_DECLARATIONS)
            // No app function declarations until the model loads them.
            assertEquals(discoveryIds, capturedTools[0].map { it.id })
            assertEquals(discoveryIds, capturedTools[1].map { it.id })
            assertEquals(discoveryIds + CREATE_NOTE, capturedTools[2].map { it.id })
            assertEquals(discoveryIds + CREATE_NOTE, capturedTools[3].map { it.id })
            assertEquals(
                listOf(
                    AgentStatus.CheckingCapabilities,
                    AgentStatus.ExploringApps(listOf(NOTES_PKG)),
                    AgentStatus.PlanningWithFunctions(listOf(NOTES_PKG), listOf(CREATE_NOTE)),
                    AgentStatus.InvokingTool(CREATE_NOTE, NOTES_PKG),
                ),
                statusesAtCall,
            )
            assertEquals(AgentStatus.Idle, agentOrchestrator.status.value)
            // Discovery tools run through the regular internal tool execution path.
            coVerify(exactly = 3) { executeAppFunctionUseCase(any(), any(), any()) }
            coVerify {
                sendMessageUseCase(
                    threadId = threadId,
                    role = MessageRole.ASSISTANT,
                    textContent = "Done",
                    processingStatus = MessageProcessingStatus.PROCESSED,
                    attachments = any(),
                )
            }
        }

    @Test
    fun `loaded functions do not carry over to the next message`() =
        runTest {
            val threadId = "thread_1"
            val first = createUserMessage(threadId, "Create a note", messageId = "msg_1")
            val second = createUserMessage(threadId, "Create another", messageId = "msg_2")
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()
            setupDefaultMocks(threadId, first, thread, llmProvider = llmProvider, progressiveToolDisclosure = true)
            coEvery { observePendingMessagesUseCase(threadId) } returns
                flow {
                    delay(10)
                    emit(first)
                    delay(10)
                    emit(second)
                }
            mockProgressiveTools()
            mockToolExecution()

            val capturedTools = mutableListOf<List<AppFunctionMetadata>>()
            mockLlmResponses(
                llmProvider,
                capturedTools,
                mutableListOf(),
                mutableListOf(),
                success("i1", expandFunctionDeclarationsCall()),
                LlmResponse.Success("i2", listOf(LlmResponsePart.Text("Done"))),
                LlmResponse.Success("i3", listOf(LlmResponsePart.Text("Done again"))),
            )

            agentOrchestrator.observeAndProcessMessages(threadId)

            assertEquals(3, capturedTools.size)
            assertTrue(capturedTools[1].any { it.id == CREATE_NOTE })
            // The second message starts again from the discovery tools only.
            assertEquals(
                listOf(EXPAND_TOOL_DESCRIPTION, EXPAND_FUNCTION_DECLARATIONS),
                capturedTools[2].map { it.id },
            )
        }

    @Test
    fun `calling a function that is not loaded returns a recoverable result`() =
        runTest {
            val threadId = "thread_1"
            val message = createUserMessage(threadId, "Create a note")
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()
            setupDefaultMocks(threadId, message, thread, llmProvider = llmProvider, progressiveToolDisclosure = true)
            mockProgressiveTools()

            val capturedInputs = mutableListOf<LlmInput>()
            mockLlmResponses(
                llmProvider,
                mutableListOf(),
                capturedInputs,
                mutableListOf(),
                success("i1", LlmResponsePart.UnknownToolCall("notes_createNote", "c1")),
                LlmResponse.Success("i2", listOf(LlmResponsePart.Text("Done"))),
            )

            agentOrchestrator.observeAndProcessMessages(threadId)

            val output = (capturedInputs[1] as LlmInput.ToolResponse).outputs.single()
            assertEquals("notes_createNote", output.functionId)
            assertEquals("c1", output.callId)
            assertTrue(output.result.contains("is not loaded"))
            assertTrue(output.result.contains(EXPAND_FUNCTION_DECLARATIONS))
            coVerify(exactly = 0) { executeAppFunctionUseCase(any(), any(), any()) }
            // The message completes normally instead of failing.
            coVerify(exactly = 0) {
                sendMessageUseCase(
                    threadId = any(),
                    role = any(),
                    textContent = any(),
                    processingStatus = MessageProcessingStatus.FAILED,
                )
            }
            coVerify {
                sendMessageUseCase(
                    threadId = threadId,
                    role = MessageRole.ASSISTANT,
                    textContent = "Done",
                    processingStatus = MessageProcessingStatus.PROCESSED,
                    attachments = any(),
                )
            }
        }

    @Test
    fun `targeted message followed by an untargeted one in the same thread recovers`() =
        runTest {
            val threadId = "thread_1"
            val targeted =
                createUserMessage(threadId, "Create a note", messageId = "msg_1", targetPackageName = NOTES_PKG)
            val untargeted = createUserMessage(threadId, "And another one", messageId = "msg_2")
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()
            setupDefaultMocks(threadId, targeted, thread, llmProvider = llmProvider, progressiveToolDisclosure = true)
            coEvery { observePendingMessagesUseCase(threadId) } returns
                flow {
                    delay(10)
                    emit(targeted)
                    delay(10)
                    emit(untargeted)
                }
            mockProgressiveTools()
            mockToolExecution()

            val capturedTools = mutableListOf<List<AppFunctionMetadata>>()
            val capturedInputs = mutableListOf<LlmInput>()
            mockLlmResponses(
                llmProvider,
                capturedTools,
                capturedInputs,
                mutableListOf(),
                // Message 1: the target app's functions are declared in full.
                success("i1", LlmResponsePart.ToolCall(NOTES_PKG, CREATE_NOTE, emptyMap(), "c1")),
                LlmResponse.Success("i2", listOf(LlmResponsePart.Text("Done"))),
                // Message 2: the model reuses the function from history, but it is not declared.
                success("i3", LlmResponsePart.UnknownToolCall("notes_createNote", "c2")),
                LlmResponse.Success("i4", listOf(LlmResponsePart.Text("Done again"))),
            )

            agentOrchestrator.observeAndProcessMessages(threadId)

            assertEquals(listOf(createNoteTool), capturedTools[0])
            assertEquals(
                listOf(EXPAND_TOOL_DESCRIPTION, EXPAND_FUNCTION_DECLARATIONS),
                capturedTools[2].map { it.id },
            )
            assertTrue(
                (capturedInputs[3] as LlmInput.ToolResponse).outputs.single().result.contains("is not loaded"),
            )
            coVerify {
                sendMessageUseCase(
                    threadId = threadId,
                    role = MessageRole.ASSISTANT,
                    textContent = "Done again",
                    processingStatus = MessageProcessingStatus.PROCESSED,
                    attachments = any(),
                )
            }
        }

    @Test
    fun `toggling the setting mid-thread switches the declared tools`() =
        runTest {
            val threadId = "thread_1"
            val first = createUserMessage(threadId, "Create a note", messageId = "msg_1")
            val second = createUserMessage(threadId, "Create another", messageId = "msg_2")
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()
            setupDefaultMocks(threadId, first, thread, llmProvider = llmProvider)
            coEvery { observePendingMessagesUseCase(threadId) } returns
                flow {
                    delay(10)
                    emit(first)
                    delay(10)
                    emit(second)
                }
            every { settingsRepository.progressiveToolDisclosure } returnsMany
                listOf(flowOf(true), flowOf(false))
            mockProgressiveTools()

            val capturedTools = mutableListOf<List<AppFunctionMetadata>>()
            mockLlmResponses(
                llmProvider,
                capturedTools,
                mutableListOf(),
                mutableListOf(),
                LlmResponse.Success("i1", listOf(LlmResponsePart.Text("One"))),
                LlmResponse.Success("i2", listOf(LlmResponsePart.Text("Two"))),
            )

            agentOrchestrator.observeAndProcessMessages(threadId)

            assertEquals(
                listOf(EXPAND_TOOL_DESCRIPTION, EXPAND_FUNCTION_DECLARATIONS),
                capturedTools[0].map { it.id },
            )
            assertEquals(listOf(createNoteTool), capturedTools[1])
        }

    @Test
    fun `progressive disclosure disabled requests all tools without discovery`() =
        runTest {
            val threadId = "thread_1"
            val message = createUserMessage(threadId, "Create a note")
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()
            setupDefaultMocks(threadId, message, thread, llmProvider = llmProvider, progressiveToolDisclosure = false)
            mockProgressiveTools()
            coEvery {
                llmProvider.generateResponse(any(), any(), any(), any(), any(), any())
            } returns LlmResponse.Success("interaction_id", listOf(LlmResponsePart.Text("Ok")))

            agentOrchestrator.observeAndProcessMessages(threadId)

            verify(exactly = 0) {
                getAppFunctionsUseCase(match { it.progressiveToolDisclosure })
            }
            coVerify {
                llmProvider.generateResponse(
                    previousInteractionId = null,
                    input = any(),
                    tools = listOf(createNoteTool),
                    apiKey = any(),
                    modelName = any(),
                )
            }
        }

    @Test
    fun `progressive disclosure is skipped when targetPackageName is set`() =
        runTest {
            val threadId = "thread_1"
            val message =
                createUserMessage(threadId, "Create a note", targetPackageName = NOTES_PKG)
            val thread = createThread(threadId)
            val llmProvider = mockk<LlmProvider>()
            setupDefaultMocks(threadId, message, thread, llmProvider = llmProvider, progressiveToolDisclosure = true)
            mockProgressiveTools()
            coEvery {
                llmProvider.generateResponse(any(), any(), any(), any(), any(), any())
            } returns LlmResponse.Success("interaction_id", listOf(LlmResponsePart.Text("Ok")))

            agentOrchestrator.observeAndProcessMessages(threadId)

            verify(exactly = 0) {
                getAppFunctionsUseCase(match { it.progressiveToolDisclosure })
            }
            coVerify {
                llmProvider.generateResponse(
                    previousInteractionId = null,
                    input = any(),
                    tools = listOf(createNoteTool),
                    apiKey = any(),
                    modelName = any(),
                )
            }
        }

    private val createNoteTool by lazy { createMockTool(NOTES_PKG, CREATE_NOTE) }
    private val expandToolDescriptionTool by lazy {
        createMockTool(AgentInternalTools.INTERNAL_TOOL_PACKAGE, EXPAND_TOOL_DESCRIPTION)
    }
    private val expandFunctionDeclarationsTool by lazy {
        createMockTool(AgentInternalTools.INTERNAL_TOOL_PACKAGE, EXPAND_FUNCTION_DECLARATIONS)
    }

    /** Progressive requests get the discovery tools; others get the app functions. */
    private fun mockProgressiveTools() {
        every { getAppFunctionsUseCase(match { it.progressiveToolDisclosure }) } returns
            flowOf(
                listOf(expandToolDescriptionTool, expandFunctionDeclarationsTool)
                    .groupBy(AppFunctionMetadata::packageMetadata),
            )
        every { getAppFunctionsUseCase(match { !it.progressiveToolDisclosure }) } returns
            flowOf(listOf(createNoteTool).groupBy(AppFunctionMetadata::packageMetadata))
    }

    private fun mockToolExecution() {
        every {
            convertInputToAppFunctionDataUseCase(any(), any(), any())
        } returns Result.success(AppFunctionData.EMPTY)
        coEvery {
            executeAppFunctionUseCase(function = expandToolDescriptionTool, parameters = any(), threadId = any())
        } returns ExecuteAppFunctionResult.Data(AppFunctionData.EMPTY, "{\"instructions\":\"Notes app\"}")
        coEvery {
            executeAppFunctionUseCase(function = expandFunctionDeclarationsTool, parameters = any(), threadId = any())
        } returns
            ExecuteAppFunctionResult.ToolsDiscovered("{\"loaded\":[\"$CREATE_NOTE\"]}", listOf(createNoteTool))
        coEvery {
            executeAppFunctionUseCase(function = createNoteTool, parameters = any(), threadId = any())
        } returns ExecuteAppFunctionResult.Data(AppFunctionData.EMPTY, "{\"ok\":true}")
    }

    private fun mockLlmResponses(
        llmProvider: LlmProvider,
        capturedTools: MutableList<List<AppFunctionMetadata>>,
        capturedInputs: MutableList<LlmInput>,
        statusesAtCall: MutableList<AgentStatus>,
        vararg responses: LlmResponse,
    ) {
        val queue = ArrayDeque(responses.toList())
        coEvery {
            llmProvider.generateResponse(
                any(),
                capture(capturedInputs),
                capture(capturedTools),
                any(),
                any(),
                any(),
            )
        } answers {
            statusesAtCall.add(agentOrchestrator.status.value)
            queue.removeFirst()
        }
    }

    private fun success(
        interactionId: String,
        part: LlmResponsePart,
    ) = LlmResponse.Success(interactionId, listOf(part))

    private fun expandToolDescriptionCall() =
        LlmResponsePart.ToolCall(
            packageName = AgentInternalTools.INTERNAL_TOOL_PACKAGE,
            functionId = EXPAND_TOOL_DESCRIPTION,
            arguments = mapOf(ToolDiscoveryTools.PARAM_PACKAGE_NAME to NOTES_PKG),
            callId = "c1",
        )

    private fun expandFunctionDeclarationsCall() =
        LlmResponsePart.ToolCall(
            packageName = AgentInternalTools.INTERNAL_TOOL_PACKAGE,
            functionId = EXPAND_FUNCTION_DECLARATIONS,
            arguments =
                mapOf(
                    ToolDiscoveryTools.PARAM_PACKAGE_NAME to NOTES_PKG,
                    ToolDiscoveryTools.PARAM_FUNCTION_IDS to listOf(CREATE_NOTE),
                ),
            callId = "c2",
        )

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
        llmModel: LlmModel = LlmModel(LlmProviderName.GEMINI, "gemini-2.5-flash"),
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
    ): AppFunctionMetadata {
        val components = AppFunctionComponentsMetadata()
        val pkgMetadata =
            AppFunctionPackageMetadata(
                packageName = packageName,
                components = components,
            )
        val tool = mockk<AppFunctionMetadata>(relaxed = true)
        every { tool.packageName } returns packageName
        every { tool.id } returns id
        every { tool.name } returns AppFunctionName(packageName, id)
        every { tool.parameters } returns emptyList()
        every { tool.components } returns components
        every { tool.packageMetadata } returns pkgMetadata
        return tool
    }

    private fun mockAppFunctions(tools: List<AppFunctionMetadata>) {
        every { getAppFunctionsUseCase(any()) } returns
            flowOf(tools.groupBy(AppFunctionMetadata::packageMetadata))
    }

    private fun setupDefaultMocks(
        threadId: String,
        message: MessageEntity,
        thread: ThreadEntity,
        apiKey: String? = "dummy_key",
        llmProvider: LlmProvider = mockk(),
        progressiveToolDisclosure: Boolean = false,
    ) {
        coEvery { observePendingMessagesUseCase(threadId) } returns
            flow {
                delay(10)
                emit(message)
            }
        coEvery { manageThreadsUseCase.getThread(threadId) } returns flowOf(thread)
        coEvery { settingsRepository.geminiApiKey } returns flowOf(apiKey)
        coEvery { settingsRepository.serviceTier } returns flowOf(ServiceTier.STANDARD)
        coEvery { settingsRepository.progressiveToolDisclosure } returns
            flowOf(progressiveToolDisclosure)
        coEvery { llmProviderFactory.getProvider(LlmProviderName.GEMINI) } returns llmProvider
    }

    private fun setupTwoStepToolCallAndExecution(
        llmProvider: LlmProvider,
        toolCall: LlmResponsePart.ToolCall,
        secondResponseText: String,
        toolResultJson: String,
    ) {
        val firstResponse =
            LlmResponse.Success(
                interactionId = "interaction_1",
                parts = listOf(toolCall),
            )
        val secondResponse =
            LlmResponse.Success(
                interactionId = "interaction_2",
                parts = listOf(LlmResponsePart.Text(secondResponseText)),
            )

        coEvery {
            llmProvider.generateResponse(
                previousInteractionId = null,
                input = any(),
                tools = any(),
                apiKey = any(),
                modelName = any(),
            )
        } returns firstResponse

        coEvery {
            llmProvider.generateResponse(
                previousInteractionId = "interaction_1",
                input = any(),
                tools = any(),
                apiKey = any(),
                modelName = any(),
            )
        } returns secondResponse

        every {
            convertInputToAppFunctionDataUseCase(any(), any(), any())
        } returns Result.success(AppFunctionData.EMPTY)

        coEvery {
            executeAppFunctionUseCase(any(), any(), any())
        } returns
            ExecuteAppFunctionResult.Data(
                data = AppFunctionData.EMPTY,
                formattedJson = toolResultJson,
            )
    }

    private companion object {
        const val NOTES_PKG = "com.example.notes"
        const val CREATE_NOTE = "createNote"
    }
}
