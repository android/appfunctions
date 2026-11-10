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

/** Sealed class representing the response from an LLM. */
sealed class LlmResponse {
    /**
     * Represents a successful response from the LLM.
     *
     * @property interactionId The unique ID for this conversation turn, required for follow-ups.
     * @property parts The ordered list of outputs (text, tool calls, etc.) from the model.
     */
    data class Success(
        val interactionId: String,
        val parts: List<LlmResponsePart>,
    ) : LlmResponse()

    /** Represents an error response (API error, network failure, etc.). */
    data class Error(
        val errorMessage: String,
    ) : LlmResponse()
}

/** Represents a specific component of a model's output turn. */
sealed class LlmResponsePart {
    data class Text(val text: String) : LlmResponsePart()

    data class ToolCall(
        val packageName: String,
        val functionId: String,
        val arguments: Map<String, Any?>,
        val callId: String,
    ) : LlmResponsePart()

    data class Thought(val thought: String) : LlmResponsePart()

    /**
     * The model called a function that was not declared in the request's tools (for example a
     * function it saw earlier in the conversation but that is not loaded in this turn).
     *
     * @property name The function name as reported by the model.
     * @property callId The ID of the call, used to send back a result.
     */
    data class UnknownToolCall(
        val name: String,
        val callId: String,
    ) : LlmResponsePart()
}
