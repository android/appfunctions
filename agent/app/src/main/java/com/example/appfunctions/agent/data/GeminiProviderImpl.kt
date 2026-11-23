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
import com.example.appfunctions.agent.domain.ToolOutput
import com.example.appfunctions.agent.domain.appfunction.findToolByName
import com.example.appfunctions.agent.domain.appfunction.resolveToolName
import com.example.appfunctions.agent.domain.appfunction.selectDistinctTools
import com.example.appfunctions.agent.domain.toArgumentsMap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiProviderImpl
    @Inject
    constructor(
        private val httpClient: HttpClient,
        private val toolConverter: GeminiToolConverter,
    ) : LlmProvider {
        override suspend fun getAvailableModels(apiKey: String?): List<LlmModel> {
            if (apiKey.isNullOrBlank()) {
                return emptyList()
            }

            val response: HttpResponse =
                try {
                    httpClient.get(GEMINI_MODELS_URL) {
                        header(HEADER_API_KEY, apiKey)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch Gemini models: ${e.message}")
                    return emptyList()
                }

            val responseBodyText = response.bodyAsText()
            if (response.status.value !in 200..299) {
                Log.w(
                    TAG,
                    "Gemini models API error: ${response.status} - $responseBodyText",
                )
                return emptyList()
            }

            return try {
                val jsonResponse = Json.parseToJsonElement(responseBodyText).jsonObject
                val modelsArray = jsonResponse[KEY_MODELS]?.jsonArray ?: return emptyList()

                modelsArray
                    .mapNotNull { element ->
                        val modelObj = element.jsonObject
                        val rawName =
                            modelObj[KEY_NAME]?.jsonPrimitive?.contentOrNull
                                ?: return@mapNotNull null
                        val modelId = rawName.removePrefix(MODEL_NAME_PREFIX)
                        val supportedMethods =
                            modelObj[KEY_SUPPORTED_GENERATION_METHODS]
                                ?.jsonArray
                                ?.mapNotNull { it.jsonPrimitive.contentOrNull }

                        // Require "generateContent" to filter out non-generative models (e.g. embeddings).
                        val supportsGenerateContent =
                            supportedMethods == null ||
                                VALUE_GENERATE_CONTENT in supportedMethods

                        if (modelId.startsWith(GEMINI_MODEL_PREFIX) &&
                            supportsGenerateContent &&
                            !isSpecializedModel(modelId)
                        ) {
                            LlmModel(LlmProviderName.GEMINI, modelId)
                        } else {
                            null
                        }
                    }
                    .distinct()
                    .withDefaultModelFirst()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse Gemini models response", e)
                emptyList()
            }
        }

        /**
         * Whether [modelId] belongs to one of [SPECIALIZED_MODEL_NAME_PARTS]: models that support
         * `generateContent` but aren't meant for chat with function calling.
         */
        private fun isSpecializedModel(modelId: String): Boolean =
            SPECIALIZED_MODEL_NAME_PARTS.any { part -> "-$modelId-".contains("-$part-") }

        /** Moves the first model of [DEFAULT_MODEL_PREFERENCE] in this list to the front. */
        private fun List<LlmModel>.withDefaultModelFirst(): List<LlmModel> {
            val defaultModel =
                DEFAULT_MODEL_PREFERENCE.firstNotNullOfOrNull { name -> find { it.modelName == name } }
                    ?: return this
            return listOf(defaultModel) + (this - defaultModel)
        }

        override suspend fun generateResponse(
            previousInteractionId: String?,
            input: LlmInput,
            tools: List<AppFunctionMetadata>,
            apiKey: String,
            modelName: String,
            serviceTier: ServiceTier,
        ): LlmResponse {
            val convertedTools =
                toolConverter
                    .selectDistinctTools(tools)
                    .mapNotNull { tool ->
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

                    serviceTier.apiValue?.let { tierValue ->
                        put(KEY_SERVICE_TIER, JsonPrimitive(tierValue))
                    }

                    put(KEY_SYSTEM_INSTRUCTION, JsonPrimitive(getSystemInstruction()))

                    when (input) {
                        is LlmInput.ToolResponse -> {
                            val inputElement =
                                buildJsonArray {
                                    input.outputs.forEach { output ->
                                        val mappedName =
                                            toolConverter.resolveToolName(output.functionId, tools)

                                        add(
                                            buildJsonObject {
                                                put("type", "function_result")
                                                put("name", mappedName)
                                                if (output.callId.isNotEmpty()) {
                                                    put("call_id", output.callId)
                                                }
                                                put("result", functionResult(output, modelName))
                                            },
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
                        put(KEY_TOOLS, JsonArray(convertedTools))
                    }

                    if (previousInteractionId != null) {
                        put(KEY_PREVIOUS_INTERACTION_ID, JsonPrimitive(previousInteractionId))
                    }
                }

            Log.d(TAG, "Gemini Request Body: ${requestBody.redactImageData()}")

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

            // The Interactions API reports the served tier in the response body
            // (KEY_SERVICE_TIER), not in a response header.
            val servedServiceTier = (jsonResponse[KEY_SERVICE_TIER] as? JsonPrimitive)?.contentOrNull
            Log.d(
                TAG,
                "Requested service_tier=${serviceTier.apiValue ?: "standard"}, " +
                    "served ($KEY_SERVICE_TIER)=${servedServiceTier ?: "unknown"}",
            )
            if (serviceTier == ServiceTier.PRIORITY &&
                servedServiceTier != null &&
                servedServiceTier != ServiceTier.PRIORITY.apiValue
            ) {
                Log.w(
                    TAG,
                    "Priority request was downgraded to '$servedServiceTier' " +
                        "(priority quota likely exhausted).",
                )
            }

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
                        val callId =
                            stepObj["id"]?.jsonPrimitive?.content
                                ?: return LlmResponse.Error(
                                    "Function call missing call_id in response",
                                )
                        val matchingTool = toolConverter.findToolByName(name, tools)
                        if (matchingTool == null) {
                            // Let the caller answer with a recoverable error instead of failing the
                            // whole turn (e.g. the model called a function it saw earlier in the
                            // conversation that is not declared in this request).
                            parts.add(LlmResponsePart.UnknownToolCall(name = name, callId = callId))
                            continue
                        }

                        val packageName = matchingTool.packageName
                        val functionId = matchingTool.id

                        val argumentsMap = stepObj[KEY_ARGUMENTS]?.jsonObject.toArgumentsMap()
                        parts.add(
                            LlmResponsePart.ToolCall(
                                packageName = packageName,
                                functionId = functionId,
                                arguments = argumentsMap,
                                callId = callId,
                            ),
                        )
                    } else {
                        Log.d(TAG, "Unsupported step type: $stepType")
                    }
                }
            }

            return LlmResponse.Success(
                interactionId = newInteractionId,
                parts = parts,
            )
        }

        /**
         * Builds the result of a `function_result`: the result text, followed by the images the
         * function returned. An image the model doesn't take in function results is replaced by a
         * note, because it would make the API reject the whole request: Gemini takes images in
         * function results only from Gemini 3 on, and only as PNG, JPEG or WebP.
         */
        private fun functionResult(
            output: ToolOutput,
            modelName: String,
        ): JsonElement {
            if (output.images.isEmpty()) {
                return JsonPrimitive(output.result)
            }
            if (PRE_GEMINI_3_MODEL_NAME.containsMatchIn(modelName)) {
                // Older models get the plain string result that every model takes, with a note per image.
                val notes =
                    output.images.joinToString(separator = "") { image ->
                        "\nAn image of type ${image.mimeType} was not attached because the model doesn't support images " +
                            "in function results."
                    }
                return JsonPrimitive(output.result + notes)
            }
            return buildJsonArray {
                add(textContent(output.result))
                for (image in output.images) {
                    if (image.mimeType in SUPPORTED_IMAGE_MIME_TYPES) {
                        add(
                            buildJsonObject {
                                put(KEY_TYPE, VALUE_IMAGE)
                                put(KEY_MIME_TYPE, image.mimeType)
                                put(KEY_DATA, Base64.getEncoder().encodeToString(image.data))
                            },
                        )
                    } else {
                        add(textContent("An image of type ${image.mimeType} was not attached because the model doesn't support it."))
                    }
                }
            }
        }

        private fun textContent(text: String): JsonObject =
            buildJsonObject {
                put(KEY_TYPE, VALUE_TEXT)
                put(KEY_TEXT, text)
            }

        /**
         * Returns a copy of this JSON to log, with the base64 data of each image replaced by its
         * length, so that logging a request doesn't copy megabytes of image data.
         */
        private fun JsonElement.redactImageData(): JsonElement =
            when (this) {
                is JsonArray -> JsonArray(map { it.redactImageData() })
                is JsonObject -> {
                    val data = this[KEY_DATA]
                    if (this[KEY_TYPE] == JsonPrimitive(VALUE_IMAGE) && data is JsonPrimitive) {
                        JsonObject(this + (KEY_DATA to JsonPrimitive("<${data.content.length} base64 characters>")))
                    } else {
                        JsonObject(mapValues { (_, value) -> value.redactImageData() })
                    }
                }
                else -> this
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
            private const val GEMINI_MODELS_URL =
                "https://generativelanguage.googleapis.com/v1beta/models?pageSize=100"
            private const val TAG = "GeminiProvider"

            private const val KEY_MODELS = "models"
            private const val KEY_SUPPORTED_GENERATION_METHODS = "supportedGenerationMethods"
            private const val VALUE_GENERATE_CONTENT = "generateContent"
            private const val MODEL_NAME_PREFIX = "models/"
            private const val GEMINI_MODEL_PREFIX = "gemini-"

            /**
             * Name parts of Gemini model families that support `generateContent` but aren't meant for
             * chat with function calling: speech generation, image generation, transcription, Omni
             * media generation, robotics, computer use, and Live and native audio. A model is hidden
             * when one of them appears as whole `-`-separated parts of its name, so that new general
             * models keep appearing without code changes.
             */
            private val SPECIALIZED_MODEL_NAME_PARTS =
                listOf("tts", "image", "nano-banana", "transcribe", "omni", "robotics", "computer-use", "live", "audio")

            /**
             * Defaults for new threads, in order of preference. gemini-3.1-flash-lite is fast and calls
             * functions reliably; the -latest aliases take over once it is retired.
             */
            private val DEFAULT_MODEL_PREFERENCE =
                listOf("gemini-3.1-flash-lite", "gemini-flash-lite-latest", "gemini-flash-latest")

            private const val KEY_MODEL = "model"
            private const val KEY_SERVICE_TIER = "service_tier"
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
            private const val VALUE_IMAGE = "image"
            private const val KEY_MIME_TYPE = "mime_type"
            private const val KEY_DATA = "data"

            /** Image formats Gemini accepts in function results. */
            private val SUPPORTED_IMAGE_MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp")

            /** Names of Gemini 1 and 2 models, which don't accept images in function results. */
            private val PRE_GEMINI_3_MODEL_NAME = Regex("^gemini-[12][.-]")
        }
    }
