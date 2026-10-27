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

import android.app.PendingIntent
import android.util.Log
import androidx.appfunctions.metadata.AppFunctionMetadata
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.data.db.entities.MessageAttachment
import com.example.appfunctions.agent.data.db.entities.MessageEntity
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import com.example.appfunctions.agent.domain.appfunction.AppFunctionExceptionFormatter
import com.example.appfunctions.agent.domain.appfunction.ConvertInputToAppFunctionDataUseCase
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionResult
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsParams
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.chat.ManageThreadsUseCase
import com.example.appfunctions.agent.domain.chat.ObservePendingMessagesUseCase
import com.example.appfunctions.agent.domain.chat.SendMessageUseCase
import com.example.appfunctions.agent.domain.chat.UpdateMessageParams
import com.example.appfunctions.agent.domain.chat.UpdateMessageUseCase
import com.example.appfunctions.agent.domain.chat.UpdateThreadParams
import com.example.appfunctions.agent.domain.chat.UpdateThreadUseCase
import com.example.appfunctions.agent.domain.pendingintent.SavePendingIntentUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Orchestrates the interaction between the LLM, AppFunctions, and the chat repository. */
@Singleton
class AgentOrchestrator
    @Inject
    constructor(
        private val manageThreadsUseCase: ManageThreadsUseCase,
        private val observePendingMessagesUseCase: ObservePendingMessagesUseCase,
        private val sendMessageUseCase: SendMessageUseCase,
        private val updateMessageUseCase: UpdateMessageUseCase,
        private val updateThreadUseCase: UpdateThreadUseCase,
        private val llmProviderFactory: LlmProviderFactory,
        private val settingsRepository: SettingsRepository,
        private val getAppFunctionsUseCase: GetAppFunctionsUseCase,
        private val convertInputToAppFunctionDataUseCase: ConvertInputToAppFunctionDataUseCase,
        private val executeAppFunctionUseCase: ExecuteAppFunctionUseCase,
        private val savePendingIntentUseCase: SavePendingIntentUseCase,
    ) {
        private val _status = MutableStateFlow<AgentStatus>(AgentStatus.Idle)

        /** The current status of the agent. */
        val status: StateFlow<AgentStatus> = _status.asStateFlow()

        /**
         * Observes messages for a specific thread and processes any pending agent responses. This
         * method suspends and collects messages until cancelled.
         */
        suspend fun observeAndProcessMessages(threadId: String) =
            coroutineScope {
                val threadStateFlow =
                    manageThreadsUseCase.getThread(
                        threadId,
                    ).stateIn(this, SharingStarted.Eagerly, null)

                observePendingMessagesUseCase(threadId).collect { message ->
                    if (message != null) {
                        val thread = threadStateFlow.filterNotNull().first()
                        processMessage(message, thread)
                    }
                }
            }

        private suspend fun processMessage(
            message: MessageEntity,
            thread: ThreadEntity,
        ) {
            _status.value = AgentStatus.Thinking

            try {
                val provider = thread.llmModel.providerName
                val apiKey = getApiKey(provider)
                if (apiKey == null) {
                    completeMessageWithError(
                        message.messageId,
                        message.threadId,
                        "API key is missing for ${provider.name}",
                    )
                    _status.value = AgentStatus.Idle
                    return
                }

                val tools =
                    getAppFunctionsUseCase(
                        GetAppFunctionsParams(
                            targetPackageNames = message.targetPackageName?.let { setOf(it) },
                            isEnabled = true,
                            isConnected = true,
                            includeInternalTools = true,
                        ),
                    ).first().values.flatten()

                runInteractionLoop(
                    message = message,
                    thread = thread,
                    apiKey = apiKey,
                    tools = tools,
                    initialInput = message.textContent,
                )

                _status.value = AgentStatus.Idle
            } catch (e: CancellationException) {
                _status.value = AgentStatus.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
                completeMessageWithError(
                    message.messageId,
                    message.threadId,
                    e.message ?: "Unknown error occurred",
                )
                _status.value = AgentStatus.Idle
            }
        }

        private suspend fun runInteractionLoop(
            message: MessageEntity,
            thread: ThreadEntity,
            apiKey: String,
            tools: List<AppFunctionMetadata>,
            initialInput: String,
        ) {
            val provider = thread.llmModel.providerName
            val modelName = thread.llmModel.modelName
            val llmProvider = llmProviderFactory.getProvider(provider)
            val serviceTier = settingsRepository.serviceTier.first()

            var previousInteractionId = thread.latestInteractionId
            var currentToolOutputs = emptyList<ToolOutput>()
            var continueLoop = true
            val currentInput = initialInput
            val capturedAttachments = mutableListOf<MessageAttachment>()

            while (continueLoop) {
                val llmInput = prepareLlmInput(currentToolOutputs, currentInput)

                currentToolOutputs = emptyList()
                val response =
                    llmProvider.generateResponse(
                        previousInteractionId = previousInteractionId,
                        input = llmInput,
                        tools = tools,
                        apiKey = apiKey,
                        modelName = modelName,
                        serviceTier = serviceTier,
                    )

                when (
                    val handleResult =
                        handleLlmResponse(response, message, tools, capturedAttachments)
                ) {
                    is HandleResult.Continue -> {
                        currentToolOutputs = handleResult.toolOutputs
                        previousInteractionId = handleResult.interactionId
                    }

                    is HandleResult.Stop -> {
                        continueLoop = false
                    }
                }
            }
        }

        private suspend fun getApiKey(provider: LlmProviderName): String? {
            return when (provider) {
                LlmProviderName.GEMINI -> settingsRepository.geminiApiKey.first()
            }
        }

        private fun prepareLlmInput(
            currentToolOutputs: List<ToolOutput>,
            currentInput: String,
        ): LlmInput {
            return if (currentToolOutputs.isNotEmpty()) {
                LlmInput.ToolResponse(currentToolOutputs)
            } else {
                LlmInput.UserMessage(currentInput)
            }
        }

        private sealed class HandleResult {
            data class Continue(val toolOutputs: List<ToolOutput>, val interactionId: String) :
                HandleResult()

            object Stop : HandleResult()
        }

        private sealed class ExecuteToolCallsResult {
            data class Success(val toolOutputs: List<ToolOutput>) : ExecuteToolCallsResult()

            data class PendingIntentAction(
                val pendingIntentId: String,
                val pendingIntent: PendingIntent,
            ) : ExecuteToolCallsResult()

            object Error : ExecuteToolCallsResult()
        }

        private suspend fun handleLlmResponse(
            response: LlmResponse,
            message: MessageEntity,
            tools: List<AppFunctionMetadata>,
            capturedAttachments: MutableList<MessageAttachment>,
        ): HandleResult {
            return when (response) {
                is LlmResponse.Success -> {
                    updateThreadUseCase(
                        message.threadId,
                        UpdateThreadParams(interactionId = response.interactionId),
                    )
                    updateMessageUseCase(
                        message.messageId,
                        UpdateMessageParams(status = MessageProcessingStatus.PROCESSED),
                    )

                    val toolCalls = response.parts.filterIsInstance<LlmResponsePart.ToolCall>()
                    val textParts = response.parts.filterIsInstance<LlmResponsePart.Text>()

                    val textContent = textParts.joinToString("\n") { it.text }

                    if (toolCalls.isNotEmpty()) {
                        when (
                            val toolResult =
                                executeToolCalls(toolCalls, tools, message, capturedAttachments)
                        ) {
                            is ExecuteToolCallsResult.Success -> {
                                if (textContent.isNotEmpty()) {
                                    sendMessageUseCase(
                                        threadId = message.threadId,
                                        role = MessageRole.ASSISTANT,
                                        textContent = textContent,
                                        processingStatus = MessageProcessingStatus.PROCESSED,
                                    )
                                }
                                HandleResult.Continue(
                                    toolResult.toolOutputs,
                                    response.interactionId,
                                )
                            }

                            is ExecuteToolCallsResult.PendingIntentAction -> {
                                savePendingIntentUseCase(
                                    toolResult.pendingIntentId,
                                    toolResult.pendingIntent,
                                )
                                sendMessageUseCase(
                                    threadId = message.threadId,
                                    role = MessageRole.ASSISTANT,
                                    textContent = textContent,
                                    processingStatus = MessageProcessingStatus.PROCESSED,
                                    pendingIntentId = toolResult.pendingIntentId,
                                )
                                HandleResult.Stop
                            }

                            is ExecuteToolCallsResult.Error -> {
                                HandleResult.Stop
                            }
                        }
                    } else {
                        if (textContent.isNotEmpty() || capturedAttachments.isNotEmpty()) {
                            sendMessageUseCase(
                                threadId = message.threadId,
                                role = MessageRole.ASSISTANT,
                                textContent = textContent,
                                processingStatus = MessageProcessingStatus.PROCESSED,
                                attachments = capturedAttachments,
                            )
                        }
                        HandleResult.Stop
                    }
                }

                is LlmResponse.Error -> {
                    Log.e(TAG, "LLM Error: ${response.errorMessage}")
                    completeMessageWithError(
                        message.messageId,
                        message.threadId,
                        response.errorMessage,
                    )
                    _status.value = AgentStatus.Idle
                    HandleResult.Stop
                }
            }
        }

        private suspend fun executeToolCalls(
            toolCalls: List<LlmResponsePart.ToolCall>,
            tools: List<AppFunctionMetadata>,
            message: MessageEntity,
            capturedAttachments: MutableList<MessageAttachment>,
        ): ExecuteToolCallsResult {
            val results = mutableListOf<ToolOutput>()
            for (toolCall in toolCalls) {
                _status.value = AgentStatus.InvokingTool(toolCall.functionId, toolCall.packageName)

                val matchingTool =
                    tools.find {
                        it.packageName == toolCall.packageName && it.id == toolCall.functionId
                    }
                if (matchingTool == null) {
                    completeMessageWithError(
                        message.messageId,
                        message.threadId,
                        "Tool not found: ${toolCall.functionId}",
                    )
                    return ExecuteToolCallsResult.Error
                }

                val inputs = nonNullArguments(toolCall.arguments)
                val appFunctionDataResult =
                    convertInputToAppFunctionDataUseCase(
                        parameters = matchingTool.parameters,
                        components = matchingTool.components,
                        inputs = inputs,
                    )
                if (appFunctionDataResult.isFailure) {
                    val exception = appFunctionDataResult.exceptionOrNull()
                    if (exception is CancellationException) {
                        throw exception
                    }
                    val errorMessage =
                        "Failed to convert arguments for ${toolCall.functionId}\n $exception"
                    if (exception != null) {
                        Log.e(TAG, errorMessage, exception)
                    }
                    completeMessageWithError(
                        message.messageId,
                        message.threadId,
                        errorMessage,
                    )
                    return ExecuteToolCallsResult.Error
                }

                when (
                    val executionResult =
                        executeAppFunctionUseCase(
                            function = matchingTool,
                            parameters = appFunctionDataResult.getOrThrow(),
                            threadId = message.threadId,
                        )
                ) {
                    is ExecuteAppFunctionResult.Data -> {
                        results.add(
                            ToolOutput(
                                functionId = toolCall.functionId,
                                callId = toolCall.callId,
                                result = executionResult.formattedJson,
                            ),
                        )
                        extractMessageAttachment(executionResult.formattedJson)?.let {
                            capturedAttachments.add(it)
                        }
                    }

                    is ExecuteAppFunctionResult.PendingIntentAction -> {
                        return ExecuteToolCallsResult.PendingIntentAction(
                            pendingIntentId = UUID.randomUUID().toString(),
                            pendingIntent = executionResult.pendingIntent,
                        )
                    }

                    is ExecuteAppFunctionResult.Error -> {
                        val exception = executionResult.exception
                        if (exception is CancellationException) {
                            throw exception
                        }
                        val appFunctionException =
                            AppFunctionExceptionFormatter.getAppFunctionException(exception)
                        if (appFunctionException != null) {
                            results.add(
                                ToolOutput(
                                    functionId = toolCall.functionId,
                                    callId = toolCall.callId,
                                    result =
                                        AppFunctionExceptionFormatter.format(
                                            appFunctionException,
                                            toolCall.functionId,
                                        ),
                                ),
                            )
                        } else {
                            val errorMessage =
                                "Tool execution failed for ${toolCall.functionId}: ${exception.message}"
                            Log.e(TAG, errorMessage, exception)
                            completeMessageWithError(
                                message.messageId,
                                message.threadId,
                                errorMessage,
                            )
                            return ExecuteToolCallsResult.Error
                        }
                    }
                }
            }
            return ExecuteToolCallsResult.Success(results)
        }

        private suspend fun completeMessageWithError(
            messageId: String,
            threadId: String,
            reason: String,
        ) {
            updateMessageUseCase(
                messageId,
                UpdateMessageParams(status = MessageProcessingStatus.PROCESSED),
            )
            sendMessageUseCase(
                threadId = threadId,
                role = MessageRole.ASSISTANT,
                textContent = reason,
                processingStatus = MessageProcessingStatus.FAILED,
            )
        }

        private companion object {
            const val TAG = "AgentOrchestrator"
        }
    }

internal fun nonNullArguments(arguments: Map<String, Any?>): Map<String, Any> =
    buildMap {
        for ((key, value) in arguments) {
            if (value != null) {
                put(key, value)
            }
        }
    }

internal fun extractMessageAttachment(jsonString: String): MessageAttachment? {
    return runCatching {
        val json = Json.parseToJsonElement(jsonString) as? JsonObject ?: return null
        val uri = json["imageUri"]?.jsonPrimitive?.contentOrNull
        if (!uri.isNullOrBlank()) {
            val mimeType =
                json["mimeType"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: "image/png"
            MessageAttachment(uri = uri, mimeType = mimeType)
        } else {
            null
        }
    }.getOrNull()
}
