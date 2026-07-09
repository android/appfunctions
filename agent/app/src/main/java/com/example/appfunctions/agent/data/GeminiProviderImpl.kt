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
package com.example.appfunctions.agent.data

import android.util.Log
import androidx.appfunctions.metadata.AppFunctionMetadata
import com.example.appfunctions.agent.domain.LlmInput
import com.example.appfunctions.agent.domain.LlmProvider
import com.example.appfunctions.agent.domain.LlmResponse
import com.example.appfunctions.agent.domain.LlmResponsePart
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class GeminiProviderImpl
@Inject
constructor(
    private val httpClient: HttpClient,
    private val toolConverter: GeminiToolConverter
) : LlmProvider {
    override suspend fun generateResponse(
        previousInteractionId: String?,
        input: LlmInput,
        tools: List<AppFunctionMetadata>,
        apiKey: String,
        modelName: String,
    ): LlmResponse {
        val sortedTools = tools.sortedByDescending { it.id.startsWith(it.packageName) }
        val uniqueTools = sortedTools.distinctBy { toolConverter.getToolName(it) }
        val convertedTools =
            uniqueTools.mapNotNull { tool ->
                try {
                    buildJsonObject {
                        put(KEY_TYPE, JsonPrimitive(VALUE_FUNCTION))
                        val functionSchema = toolConverter.convert(tool)
                        functionSchema.forEach { (key, value) -> put(key, value) }
                    }
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Failed to convert tool ${tool.id}: ${e.message}", e)
                    null
                }
            }

        val requestBody =
            buildJsonObject {
                put(KEY_MODEL, JsonPrimitive(modelName))

                put(KEY_SYSTEM_INSTRUCTION, JsonPrimitive(getSystemInstruction()))

                when (input) {
                    is LlmInput.ToolResponse -> {
                        val inputElement =
                            kotlinx.serialization.json.buildJsonArray {
                                input.outputs.forEach { output ->
                                    val matchingTool = tools.find { it.id == output.functionId }
                                    val mappedName =
                                        if (matchingTool != null) {
                                            toolConverter.getToolName(matchingTool)
                                        } else {
                                            output.functionId
                                        }

                                    add(
                                        buildJsonObject {
                                            put("type", "function_result")
                                            put("name", mappedName)
                                            if (output.callId.isNotEmpty()) {
                                                put("call_id", output.callId)
                                            }
                                            put("result", output.result)
                                        }
                                    )
                                }
                            }
                        put(KEY_INPUT, inputElement)
                    }
                    is LlmInput.UserMessage -> {
                        put(KEY_INPUT, JsonPrimitive(input.text))
                    }
                }

                if (convertedTools.isNotEmpty()) {
                    put(KEY_TOOLS, kotlinx.serialization.json.JsonArray(convertedTools))
                }

                if (previousInteractionId != null) {
                    put(KEY_PREVIOUS_INTERACTION_ID, JsonPrimitive(previousInteractionId))
                }
            }

        Log.d(TAG, "Gemini Request Body: $requestBody")

        val response: HttpResponse =
            try {
                httpClient.post(GEMINI_INTERACTIONS_URL) {
                    contentType(ContentType.Application.Json)
                    header(HEADER_API_KEY, apiKey)
                    header(HEADER_API_REVISION, VALUE_API_REVISION)
                    setBody(requestBody)
                }
            } catch (e: Exception) {
                return LlmResponse.Error("Network error: ${e.message}")
            }

        val responseBodyText = response.bodyAsText()
        Log.d(TAG, "Gemini Response Body: $responseBodyText")

        if (response.status.value !in 200..299) {
            return LlmResponse.Error("Gemini API error: ${response.status} - $responseBodyText")
        }

        val jsonResponse = Json.parseToJsonElement(responseBodyText).jsonObject

        val newInteractionId =
            jsonResponse[KEY_ID]?.jsonPrimitive?.content
                ?: return LlmResponse.Error("Missing interaction ID in response")
        val steps = jsonResponse[KEY_STEPS]?.jsonArray

        val parts = mutableListOf<LlmResponsePart>()

        if (steps != null) {
            for (step in steps) {
                val stepObj = step.jsonObject
                val stepType = stepObj[KEY_TYPE]?.jsonPrimitive?.content

                if (stepType == VALUE_MODEL_OUTPUT) {
                    val content = stepObj[KEY_CONTENT]?.jsonArray
                    if (content != null) {
                        for (part in content) {
                            val partObj = part.jsonObject
                            val partType = partObj[KEY_TYPE]?.jsonPrimitive?.content
                            if (partType == VALUE_TEXT) {
                                val text = partObj[KEY_TEXT]?.jsonPrimitive?.content
                                if (text != null) {
                                    parts.add(LlmResponsePart.Text(text))
                                }
                            }
                        }
                    }
                } else if (stepType == VALUE_FUNCTION_CALL) {
                    val name =
                        stepObj[KEY_NAME]?.jsonPrimitive?.content
                            ?: return LlmResponse.Error("Called function without a name")
                    val matchingTool =
                        tools.find { tool -> toolConverter.getToolName(tool) == name }
                            ?: return LlmResponse.Error("Called unknown function: $name")

                    val packageName = matchingTool.packageName
                    val functionId = matchingTool.id

                    val args = stepObj[KEY_ARGUMENTS]?.jsonObject
                    val argumentsMap = mutableMapOf<String, Any?>()
                    if (args != null) {
                        for ((key, value) in args) {
                            argumentsMap[key] = value.toPrimitive()
                        }
                    }
                    val callId =
                        stepObj["id"]?.jsonPrimitive?.content
                            ?: return LlmResponse.Error(
                                "Function call missing call_id in response"
                            )
                    parts.add(
                        LlmResponsePart.ToolCall(
                            packageName = packageName,
                            functionId = functionId,
                            arguments = argumentsMap,
                            callId = callId
                        )
                    )
                } else {
                    Log.d(TAG, "Unsupported step type: $stepType")
                }
            }
        }

        return LlmResponse.Success(
            interactionId = newInteractionId,
            parts = parts
        )
    }

    private fun JsonElement.toPrimitive(): Any? {
        return when (this) {
            is JsonPrimitive -> {
                if (this.isString) return this.content
                return this.content.toBooleanStrictOrNull()
                    ?: this.content.toLongOrNull()
                    ?: this.content.toDoubleOrNull()
            }
            is JsonObject -> this.mapValues { it.value.toPrimitive() }
            is JsonArray -> this.map { it.toPrimitive() }
        }
    }

    private fun getSystemInstruction(): String {
        val currentDate = LocalDate.now().toString()
        return """
                You are an AI assistant running on Android. Today's date is $currentDate.
                Always reply to the user using concise, friendly, natural human language. Never respond to the user with raw JSON or structured code blocks unless explicitly asked to write code.
                When a user asks you to generate an image, call the generateImage tool. After generateImage completes, confirm to the user in a natural sentence (for example: "I have generated that image for you.").
                When a user asks you to generate an image and use it in an app (for example, setting a chat wallpaper or attaching an image to a note), first call generateImage, then call the target app function passing the returned imageUri.
        """.trimIndent()
    }

    companion object {
        private const val GEMINI_INTERACTIONS_URL =
            "https://generativelanguage.googleapis.com/v1beta/interactions"
        private const val TAG = "GeminiProvider"

        private const val KEY_MODEL = "model"
        private const val KEY_INPUT = "input"
        private const val KEY_TOOLS = "tools"
        private const val KEY_TYPE = "type"
        private const val VALUE_FUNCTION = "function"
        private const val KEY_PREVIOUS_INTERACTION_ID = "previous_interaction_id"
        private const val KEY_SYSTEM_INSTRUCTION = "system_instruction"
        private const val HEADER_API_KEY = "x-goog-api-key"
        private const val KEY_ID = "id"
        private const val KEY_STEPS = "steps"
        private const val KEY_CONTENT = "content"
        private const val VALUE_MODEL_OUTPUT = "model_output"
        private const val VALUE_TEXT = "text"
        private const val KEY_TEXT = "text"
        private const val VALUE_FUNCTION_CALL = "function_call"
        private const val KEY_NAME = "name"
        private const val KEY_ARGUMENTS = "arguments"
        private const val HEADER_API_REVISION = "Api-Revision"
        private const val VALUE_API_REVISION = "2026-05-20"
    }
}
