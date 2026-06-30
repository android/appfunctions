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

class FakeLlmProvider : LlmProvider {
    var responseToReturn: LlmResponse = LlmResponse.Error("Default fake error")
    private val enqueuedResponses = mutableListOf<LlmResponse>()
    var capturedPreviousInteractionId: String? = null
    var capturedInput: String? = null
    var capturedTools: List<AppFunctionMetadata>? = null
    var capturedToolOutputs: List<ToolOutput>? = null

    fun enqueueResponse(response: LlmResponse) {
        enqueuedResponses.add(response)
    }

    override suspend fun generateResponse(
        previousInteractionId: String?,
        input: LlmInput,
        tools: List<AppFunctionMetadata>,
        apiKey: String,
        modelName: String,
        serviceTier: ServiceTier,
    ): LlmResponse {
        capturedPreviousInteractionId = previousInteractionId
        if (input is LlmInput.UserMessage) {
            capturedInput = input.text
        }
        capturedTools = tools
        if (input is LlmInput.ToolResponse) {
            capturedToolOutputs = input.outputs
        }
        return if (enqueuedResponses.isNotEmpty()) {
            enqueuedResponses.removeAt(0)
        } else {
            responseToReturn
        }
    }
}
