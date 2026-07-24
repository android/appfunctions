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
import com.example.appfunctions.agent.data.ServiceTier

/** Interface for LLM providers to generate responses. */
interface LlmProvider {
    /**
     * Generates a response from the LLM.
     *
     * @param previousInteractionId The ID of the previous interaction to continue the conversation.
     *   Pass null for the first turn.
     * @param input The new input (user message or tool response).
     * @param tools The list of available tools (AppFunctions).
     * @param apiKey The API key for the LLM provider.
     * @param modelName The name of the model to use.
     * @param serviceTier The processing tier to request for this call.
     * @return The response from the LLM, including the new interaction ID.
     */
    suspend fun generateResponse(
        previousInteractionId: String?,
        input: LlmInput,
        tools: List<AppFunctionMetadata>,
        apiKey: String,
        modelName: String,
        serviceTier: ServiceTier = ServiceTier.STANDARD,
    ): LlmResponse
}

/** Represents the input to the LLM, which can be a user message or a response from a tool. */
sealed class LlmInput {
    /** Represents a new message from the user. */
    data class UserMessage(val text: String) : LlmInput()

    /** Represents the results of tool executions to be sent back to the LLM. */
    data class ToolResponse(val outputs: List<ToolOutput>) : LlmInput()
}

/** Represents the result of a tool execution to be sent back to the LLM. */
data class ToolOutput(
    val functionId: String,
    val callId: String,
    val result: String,
)
