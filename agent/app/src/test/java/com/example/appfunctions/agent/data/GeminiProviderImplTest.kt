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
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import com.example.appfunctions.agent.domain.LlmInput
import com.example.appfunctions.agent.domain.LlmResponse
import com.example.appfunctions.agent.domain.LlmResponsePart
import com.example.appfunctions.agent.domain.ToolOutput
import com.example.appfunctions.agent.domain.ToolOutputImage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

class GeminiProviderImplTest {
    private val toolConverter = GeminiToolConverter()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    private fun createCapturingHttpClient(
        status: HttpStatusCode,
        responseBody: String,
        capturedRequestBodies: MutableList<String>,
    ): HttpClient {
        val mockEngine =
            MockEngine { request ->
                capturedRequestBodies.add((request.body as TextContent).text)
                respond(
                    content = responseBody,
                    status = status,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
        }
    }

    private fun createMockHttpClient(
        status: HttpStatusCode,
        responseBody: String,
    ): HttpClient {
        val mockEngine =
            MockEngine { _ ->
                respond(
                    content = responseBody,
                    status = status,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
        }
    }

    /** Sends [input] with [tools] to [modelName] and returns the body of the request. */
    private suspend fun sendInput(
        input: LlmInput,
        modelName: String = "test_model",
        tools: List<AppFunctionMetadata> = emptyList(),
    ): String {
        val responseJson = buildJsonObject { put("id", "interaction_123") }.toString()
        val capturedRequestBodies = mutableListOf<String>()
        val httpClient =
            createCapturingHttpClient(HttpStatusCode.OK, responseJson, capturedRequestBodies)
        val provider = GeminiProviderImpl(httpClient, toolConverter)

        provider.generateResponse(
            previousInteractionId = "interaction_1",
            input = input,
            tools = tools,
            apiKey = "test_key",
            modelName = modelName,
        )

        return capturedRequestBodies.single()
    }

    /** Sends [output] to [modelName] and returns the `function_result` input of the request. */
    private suspend fun sendToolOutput(
        output: ToolOutput,
        modelName: String = "test_model",
    ): JsonObject {
        val requestBody = Json.parseToJsonElement(sendInput(LlmInput.ToolResponse(listOf(output)), modelName)).jsonObject
        return requestBody["input"]!!.jsonArray.single().jsonObject
    }

    /** Captures the messages logged with [Log.d] from now on. */
    private fun captureDebugLogs(): List<String> {
        val messages = mutableListOf<String>()
        every { Log.d(any(), capture(messages)) } returns 0
        return messages
    }

    /** Returns the request body logged among the debug log [messages]. */
    private fun loggedRequestBody(messages: List<String>): JsonElement {
        val prefix = "Gemini Request Body: "
        return Json.parseToJsonElement(messages.single { it.startsWith(prefix) }.removePrefix(prefix))
    }

    private fun textContent(text: String): JsonObject =
        buildJsonObject {
            put("type", "text")
            put("text", text)
        }

    private fun assertImageContent(
        mimeType: String,
        data: ByteArray,
        content: JsonObject,
    ) {
        assertEquals(setOf("type", "mime_type", "data"), content.keys)
        assertEquals("image", content["type"]!!.jsonPrimitive.content)
        assertEquals(mimeType, content["mime_type"]!!.jsonPrimitive.content)
        assertArrayEquals(data, Base64.getDecoder().decode(content["data"]!!.jsonPrimitive.content))
    }

    /** Returns a models.list response body that lists [modelIds], each of which supports `generateContent`. */
    private fun modelsListResponse(modelIds: List<String>): String =
        buildJsonObject {
            put(
                "models",
                buildJsonArray {
                    for (modelId in modelIds) {
                        add(
                            buildJsonObject {
                                put("name", "models/$modelId")
                                put(
                                    "supportedGenerationMethods",
                                    buildJsonArray { add(JsonPrimitive("generateContent")) },
                                )
                            },
                        )
                    }
                },
            )
        }
            .toString()

    /** Returns the names of the models [GeminiProviderImpl.getAvailableModels] returns when the API lists [apiModelIds]. */
    private suspend fun availableModelNames(apiModelIds: List<String>): List<String> {
        val httpClient = createMockHttpClient(HttpStatusCode.OK, modelsListResponse(apiModelIds))
        val provider = GeminiProviderImpl(httpClient, toolConverter)
        return provider.getAvailableModels("test_key").map { it.modelName }
    }

    @Test
    fun generateResponse_apiError_returnsError() =
        runBlocking {
            val httpClient = createMockHttpClient(HttpStatusCode.BadRequest, "Bad Request")
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            val response =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Hi"),
                    tools = emptyList(),
                    apiKey = "test_key",
                    modelName = "test_model",
                )

            assertTrue(response is LlmResponse.Error)
            val error = response as LlmResponse.Error
            assertTrue(error.errorMessage.contains("Gemini API error"))
        }

    @Test
    fun generateResponse_networkError_returnsError() =
        runBlocking {
            val mockEngine = MockEngine { _ -> throw Exception("Network connection failed") }
            val httpClient = HttpClient(mockEngine)
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            val response =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Hi"),
                    tools = emptyList(),
                    apiKey = "test_key",
                    modelName = "test_model",
                )

            assertTrue(response is LlmResponse.Error)
            val error = response as LlmResponse.Error
            assertTrue(error.errorMessage.contains("Network error"))
        }

    @Test
    fun generateResponse_missingFunctionName_returnsError() =
        runBlocking {
            val responseJson =
                buildJsonObject {
                    put("id", "interaction_123")
                    put(
                        "steps",
                        kotlinx.serialization.json.buildJsonArray {
                            add(buildJsonObject { put("type", "function_call") })
                        },
                    )
                }
                    .toString()

            val httpClient = createMockHttpClient(HttpStatusCode.OK, responseJson)
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            val response =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Hi"),
                    tools = emptyList(),
                    apiKey = "test_key",
                    modelName = "test_model",
                )

            assertTrue(response is LlmResponse.Error)
            val error = response as LlmResponse.Error
            assertTrue(error.errorMessage.contains("Called function without a name"))
        }

    @Test
    fun generateResponse_unknownFunction_returnsUnknownToolCall() =
        runBlocking {
            val responseJson =
                buildJsonObject {
                    put("id", "interaction_123")
                    put(
                        "steps",
                        kotlinx.serialization.json.buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "function_call")
                                    put("name", "unknown_function")
                                    put("id", "call_456")
                                },
                            )
                        },
                    )
                }
                    .toString()

            val httpClient = createMockHttpClient(HttpStatusCode.OK, responseJson)
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            val response =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Hi"),
                    tools = emptyList(),
                    apiKey = "test_key",
                    modelName = "test_model",
                )

            assertTrue(response is LlmResponse.Success)
            assertEquals(
                listOf(LlmResponsePart.UnknownToolCall(name = "unknown_function", callId = "call_456")),
                (response as LlmResponse.Success).parts,
            )
        }

    @Test
    fun generateResponse_serviceFunctionIds_roundTripsDeclaredToolNames() =
        runBlocking {
            val notesService = "com.example.app.feature.notes.ExampleNotesAppFunctionService"
            val tools =
                listOf("addNote", "updateNoteSettings", "getNoteFieldDescriptions").map { function ->
                    AppFunctionMetadata(
                        id = "$notesService#$function",
                        packageName = "com.example.app",
                        isEnabled = true,
                        schema = null,
                        parameters = emptyList(),
                        response =
                            AppFunctionResponseMetadata(
                                valueType = AppFunctionStringTypeMetadata(isNullable = false),
                                description = "",
                            ),
                        components = AppFunctionComponentsMetadata(emptyMap()),
                        description = "",
                        deprecation = null,
                    )
                }
            val responseJson =
                buildJsonObject {
                    put("id", "interaction_123")
                    put(
                        "steps",
                        kotlinx.serialization.json.buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "function_call")
                                    put("name", "com_example_app_feature_notes_ExampleNotesAppFunctionService_getNoteFieldDescriptions")
                                    put("id", "call_456")
                                },
                            )
                        },
                    )
                }
                    .toString()
            val capturedRequestBodies = mutableListOf<String>()
            val httpClient =
                createCapturingHttpClient(HttpStatusCode.OK, responseJson, capturedRequestBodies)
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            val response =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Which fields does a note have?"),
                    tools = tools,
                    apiKey = "test_key",
                    modelName = "test_model",
                )
            provider.generateResponse(
                previousInteractionId = "interaction_123",
                input =
                    LlmInput.ToolResponse(
                        listOf(
                            ToolOutput(
                                functionId = "$notesService#getNoteFieldDescriptions",
                                callId = "call_456",
                                result = """{"fields":["title","body"]}""",
                            ),
                        ),
                    ),
                tools = tools,
                apiKey = "test_key",
                modelName = "test_model",
            )

            val declaredNames =
                Json
                    .parseToJsonElement(capturedRequestBodies[0])
                    .jsonObject["tools"]!!
                    .jsonArray
                    .map { it.jsonObject["name"]!!.jsonPrimitive.content }
            assertEquals(
                listOf(
                    "com_example_app_feature_notes_ExampleNotesAppFunctionService_addNote",
                    "com_example_app_feature_notes_ExampleNotesAppFunctionService_updateNoteSettings",
                    "com_example_app_feature_notes_ExampleNotesAppFunctionService_getNoteFieldDescriptions",
                ),
                declaredNames,
            )
            assertEquals(
                LlmResponse.Success(
                    interactionId = "interaction_123",
                    parts =
                        listOf(
                            LlmResponsePart.ToolCall(
                                packageName = "com.example.app",
                                functionId = "$notesService#getNoteFieldDescriptions",
                                arguments = emptyMap(),
                                callId = "call_456",
                            ),
                        ),
                ),
                response,
            )
            val functionResult =
                Json
                    .parseToJsonElement(capturedRequestBodies[1])
                    .jsonObject["input"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("function_result", functionResult["type"]!!.jsonPrimitive.content)
            assertEquals(
                "com_example_app_feature_notes_ExampleNotesAppFunctionService_getNoteFieldDescriptions",
                functionResult["name"]!!.jsonPrimitive.content,
            )
            assertEquals("call_456", functionResult["call_id"]!!.jsonPrimitive.content)
        }

    @Test
    fun generateResponse_systemInstruction_hasNoToolDiscoveryGuidance() =
        runBlocking {
            val responseJson = buildJsonObject { put("id", "interaction_123") }.toString()
            val capturedRequestBodies = mutableListOf<String>()
            val httpClient =
                createCapturingHttpClient(HttpStatusCode.OK, responseJson, capturedRequestBodies)
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            provider.generateResponse(
                previousInteractionId = null,
                input = LlmInput.UserMessage("Hi"),
                tools = emptyList(),
                apiKey = "test_key",
                modelName = "test_model",
            )

            // Tool discovery guidance lives in the discovery tools' descriptions, not in the
            // provider, so every LlmProvider stays agnostic of progressive disclosure.
            val requestBody = Json.parseToJsonElement(capturedRequestBodies.first()).jsonObject
            val systemInstruction = requestBody["system_instruction"]!!.jsonPrimitive.content
            assertFalse(systemInstruction.contains("expand_tool_description"))
        }

    @Test
    fun generateResponse_missingInteractionId_returnsError() =
        runBlocking {
            val responseJson =
                buildJsonObject {
                    // Missing "id"
                    put(
                        "steps",
                        kotlinx.serialization.json.buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "model_output")
                                    put(
                                        "content",
                                        kotlinx.serialization.json.buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("type", "text")
                                                    put("text", "Hello from Gemini")
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
                    .toString()

            val httpClient = createMockHttpClient(HttpStatusCode.OK, responseJson)
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            val response =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Hi"),
                    tools = emptyList(),
                    apiKey = "test_key",
                    modelName = "test_model",
                )

            assertTrue(response is LlmResponse.Error)
            val error = response as LlmResponse.Error
            assertTrue(error.errorMessage.contains("Missing interaction ID in response"))
        }

    @Test
    fun generateResponse_withComplexObjectArgument_returnsSuccessWithParsedMap() =
        runBlocking {
            val responseJson =
                buildJsonObject {
                    put("id", "interaction_123")
                    put(
                        "steps",
                        kotlinx.serialization.json.buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "function_call")
                                    put("name", "my_function")
                                    put("id", "call_456")
                                    put(
                                        "arguments",
                                        buildJsonObject {
                                            put(
                                                "request",
                                                buildJsonObject {
                                                    put(
                                                        "label",
                                                        buildJsonObject { put("value", "Pasta") },
                                                    )
                                                    put(
                                                        "id",
                                                        "53cd7fe9-570f-4595-8156-262ce908a915",
                                                    )
                                                    put(
                                                        "durationMillis",
                                                        buildJsonObject { put("value", 900000) },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
                    .toString()

            val httpClient = createMockHttpClient(HttpStatusCode.OK, responseJson)
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            val tool =
                AppFunctionMetadata(
                    id = "my_function",
                    packageName = "com.example",
                    isEnabled = true,
                    schema = null,
                    parameters = emptyList(),
                    response =
                        AppFunctionResponseMetadata(
                            valueType = AppFunctionStringTypeMetadata(isNullable = false),
                            description = "",
                        ),
                    components = AppFunctionComponentsMetadata(emptyMap()),
                    description = "",
                    deprecation = null,
                )

            val response =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Hi"),
                    tools = listOf(tool),
                    apiKey = "test_key",
                    modelName = "test_model",
                )

            assertTrue(response is LlmResponse.Success)
            val success = response as LlmResponse.Success
            val toolCall = success.parts.filterIsInstance<LlmResponsePart.ToolCall>().first()

            val args = toolCall.arguments["request"]
            assertTrue("Argument 'request' should be a Map", args is Map<*, *>)
            val requestMap = args as Map<String, Any?>

            val label = requestMap["label"] as Map<String, Any?>
            assertEquals("Pasta", label["value"])

            assertEquals("53cd7fe9-570f-4595-8156-262ce908a915", requestMap["id"])

            val duration = requestMap["durationMillis"] as Map<String, Any?>
            assertEquals(900000L, (duration["value"] as Number).toLong())
        }

    @Test
    fun generateResponse_simpleTextResponse_returnsSuccess() =
        runBlocking {
            val responseJson =
                buildJsonObject {
                    put("id", "interaction_123")
                    put(
                        "steps",
                        kotlinx.serialization.json.buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "model_output")
                                    put(
                                        "content",
                                        kotlinx.serialization.json.buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("type", "text")
                                                    put("text", "Hello from the new steps schema!")
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
                    .toString()

            val httpClient = createMockHttpClient(HttpStatusCode.OK, responseJson)
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            val response =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Hi"),
                    tools = emptyList(),
                    apiKey = "test_key",
                    modelName = "test_model",
                )

            assertTrue(response is LlmResponse.Success)
            val success = response as LlmResponse.Success
            assertEquals("interaction_123", success.interactionId)
            assertEquals(1, success.parts.size)
            val textPart = success.parts.first() as LlmResponsePart.Text
            assertEquals("Hello from the new steps schema!", textPart.text)
        }

    @Test
    fun generateResponse_priorityTier_includesServiceTierInRequestBody() =
        runBlocking {
            val responseJson =
                buildJsonObject {
                    put("id", "interaction_123")
                    put("service_tier", "priority")
                }
                    .toString()
            val capturedRequestBodies = mutableListOf<String>()
            val httpClient =
                createCapturingHttpClient(HttpStatusCode.OK, responseJson, capturedRequestBodies)
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            provider.generateResponse(
                previousInteractionId = null,
                input = LlmInput.UserMessage("Hi"),
                tools = emptyList(),
                apiKey = "test_key",
                modelName = "test_model",
                serviceTier = ServiceTier.PRIORITY,
            )

            val requestBody = Json.parseToJsonElement(capturedRequestBodies.first()).jsonObject
            assertEquals("priority", requestBody["service_tier"]?.jsonPrimitive?.content)
        }

    @Test
    fun generateResponse_standardTier_omitsServiceTierFromRequestBody() =
        runBlocking {
            val responseJson = buildJsonObject { put("id", "interaction_123") }.toString()
            val capturedRequestBodies = mutableListOf<String>()
            val httpClient =
                createCapturingHttpClient(HttpStatusCode.OK, responseJson, capturedRequestBodies)
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            provider.generateResponse(
                previousInteractionId = null,
                input = LlmInput.UserMessage("Hi"),
                tools = emptyList(),
                apiKey = "test_key",
                modelName = "test_model",
                serviceTier = ServiceTier.STANDARD,
            )

            val requestBody = Json.parseToJsonElement(capturedRequestBodies.first()).jsonObject
            assertNull(requestBody["service_tier"])
        }

    @Test
    fun generateResponse_priorityDowngradedToStandard_returnsSuccess() =
        runBlocking {
            val responseJson =
                buildJsonObject {
                    put("id", "interaction_123")
                    put("service_tier", "standard")
                    put(
                        "steps",
                        kotlinx.serialization.json.buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "model_output")
                                    put(
                                        "content",
                                        kotlinx.serialization.json.buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("type", "text")
                                                    put("text", "Served on standard")
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
                    .toString()

            val httpClient = createMockHttpClient(HttpStatusCode.OK, responseJson)
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            val response =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Hi"),
                    tools = emptyList(),
                    apiKey = "test_key",
                    modelName = "test_model",
                    serviceTier = ServiceTier.PRIORITY,
                )

            assertTrue(response is LlmResponse.Success)
            val success = response as LlmResponse.Success
            val textPart = success.parts.first() as LlmResponsePart.Text
            assertEquals("Served on standard", textPart.text)
        }

    @Test
    fun generateResponse_toolOutputWithImages_sendsResultTextFollowedByImages() =
        runBlocking {
            val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
            val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

            val functionResult =
                sendToolOutput(
                    ToolOutput(
                        functionId = "getPhotos",
                        callId = "call_1",
                        result = """{"count":2}""",
                        images =
                            listOf(
                                ToolOutputImage(mimeType = "image/png", data = png),
                                ToolOutputImage(mimeType = "image/jpeg", data = jpeg),
                            ),
                    ),
                )

            assertEquals("function_result", functionResult["type"]!!.jsonPrimitive.content)
            assertEquals("call_1", functionResult["call_id"]!!.jsonPrimitive.content)
            val content = functionResult["result"]!!.jsonArray.map { it.jsonObject }
            assertEquals(3, content.size)
            assertEquals(textContent("""{"count":2}"""), content[0])
            assertImageContent("image/png", png, content[1])
            assertImageContent("image/jpeg", jpeg, content[2])
        }

    @Test
    fun generateResponse_toolOutputWithUnsupportedImage_sendsNoteInsteadOfImage() =
        runBlocking {
            val gif = "GIF89a".toByteArray()
            val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

            val functionResult =
                sendToolOutput(
                    ToolOutput(
                        functionId = "getPhotos",
                        callId = "call_1",
                        result = "Found 2 photos",
                        images =
                            listOf(
                                ToolOutputImage(mimeType = "image/gif", data = gif),
                                ToolOutputImage(mimeType = "image/png", data = png),
                            ),
                    ),
                )

            val content = functionResult["result"]!!.jsonArray.map { it.jsonObject }
            assertEquals(3, content.size)
            assertEquals(textContent("Found 2 photos"), content[0])
            assertEquals(
                textContent("An image of type image/gif was not attached because the model doesn't support it."),
                content[1],
            )
            assertImageContent("image/png", png, content[2])
        }

    @Test
    fun generateResponse_toolOutputWithoutImages_sendsResultAsString() =
        runBlocking {
            val functionResult =
                sendToolOutput(ToolOutput(functionId = "createNote", callId = "call_1", result = """{"ok":true}"""))

            assertEquals(JsonPrimitive("""{"ok":true}"""), functionResult["result"])
        }

    @Test
    fun generateResponse_toolOutputWithHeicImages_sendsNotesInsteadOfImages() =
        runBlocking {
            val functionResult =
                sendToolOutput(
                    ToolOutput(
                        functionId = "getPhotos",
                        callId = "call_1",
                        result = "Found 2 photos",
                        images =
                            listOf(
                                ToolOutputImage(mimeType = "image/heic", data = byteArrayOf(1)),
                                ToolOutputImage(mimeType = "image/heif", data = byteArrayOf(2)),
                            ),
                    ),
                )

            val content = functionResult["result"]!!.jsonArray.map { it.jsonObject }
            assertEquals(
                listOf(
                    textContent("Found 2 photos"),
                    textContent("An image of type image/heic was not attached because the model doesn't support it."),
                    textContent("An image of type image/heif was not attached because the model doesn't support it."),
                ),
                content,
            )
        }

    @Test
    fun generateResponse_toolOutputWithImagesForGemini1Or2Model_sendsResultStringWithNotesInsteadOfImages() =
        runBlocking {
            val output =
                ToolOutput(
                    functionId = "getPhotos",
                    callId = "call_1",
                    result = "Found 2 photos",
                    images =
                        listOf(
                            ToolOutputImage(mimeType = "image/png", data = byteArrayOf(1)),
                            ToolOutputImage(mimeType = "image/jpeg", data = byteArrayOf(2)),
                        ),
                )

            for (modelName in listOf("gemini-2.5-flash", "gemini-2.0-flash-001", "gemini-1.5-pro")) {
                val functionResult = sendToolOutput(output, modelName)

                assertEquals(
                    modelName,
                    JsonPrimitive(
                        "Found 2 photos" +
                            "\nAn image of type image/png was not attached because the model doesn't support images in function results." +
                            "\nAn image of type image/jpeg was not attached because the model doesn't support images in function results.",
                    ),
                    functionResult["result"],
                )
            }
        }

    @Test
    fun generateResponse_toolOutputWithImagesForGemini3OrLaterModel_sendsImages() =
        runBlocking {
            val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
            val output =
                ToolOutput(
                    functionId = "getPhoto",
                    callId = "call_1",
                    result = "Found a photo",
                    images = listOf(ToolOutputImage(mimeType = "image/png", data = png)),
                )

            for (modelName in listOf("gemini-3-pro-preview", "gemini-3.1-flash-lite-preview", "gemini-flash-latest")) {
                val content = sendToolOutput(output, modelName)["result"]!!.jsonArray.map { it.jsonObject }

                assertEquals(modelName, 2, content.size)
                assertEquals(modelName, textContent("Found a photo"), content[0])
                assertImageContent("image/png", png, content[1])
            }
        }

    @Test
    fun generateResponse_toolOutputWithImages_logsRequestBodyWithImageDataLengthInsteadOfData() =
        runBlocking {
            val loggedMessages = captureDebugLogs()
            val image = ByteArray(3000) { it.toByte() }
            val imageData = Base64.getEncoder().encodeToString(image)

            val requestBody =
                sendInput(
                    LlmInput.ToolResponse(
                        listOf(
                            ToolOutput(
                                functionId = "getPhoto",
                                callId = "call_1",
                                result = "Found a photo",
                                images = listOf(ToolOutputImage(mimeType = "image/png", data = image)),
                            ),
                        ),
                    ),
                )

            assertTrue(requestBody.contains(imageData))
            assertEquals(
                Json.parseToJsonElement(requestBody.replace(imageData, "<${imageData.length} base64 characters>")),
                loggedRequestBody(loggedMessages),
            )
        }

    @Test
    fun generateResponse_requestWithoutImages_logsRequestBodyAsSent() =
        runBlocking {
            val loggedMessages = captureDebugLogs()
            val tool =
                AppFunctionMetadata(
                    id = "createNote",
                    packageName = "com.example.notes",
                    isEnabled = true,
                    schema = null,
                    parameters =
                        listOf(
                            AppFunctionParameterMetadata(
                                name = "title",
                                isRequired = true,
                                dataType = AppFunctionStringTypeMetadata(isNullable = false),
                                description = "The title of the note",
                            ),
                        ),
                    response =
                        AppFunctionResponseMetadata(
                            valueType = AppFunctionStringTypeMetadata(isNullable = false),
                            description = "",
                        ),
                    components = AppFunctionComponentsMetadata(emptyMap()),
                    description = "Creates a note",
                    deprecation = null,
                )

            val requestBody = sendInput(LlmInput.UserMessage("Create a note"), tools = listOf(tool))

            assertEquals(Json.parseToJsonElement(requestBody), loggedRequestBody(loggedMessages))
        }

    @Test
    fun getAvailableModels_returnsFilteredGeminiModels() =
        runBlocking {
            val responseJson =
                buildJsonObject {
                    put(
                        "models",
                        kotlinx.serialization.json.buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("name", "models/gemini-2.5-pro")
                                    put(
                                        "supportedGenerationMethods",
                                        kotlinx.serialization.json.buildJsonArray {
                                            add(
                                                kotlinx.serialization.json.JsonPrimitive(
                                                    "generateContent",
                                                ),
                                            )
                                        },
                                    )
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("name", "models/gemini-embedding-001")
                                    put(
                                        "supportedGenerationMethods",
                                        kotlinx.serialization.json.buildJsonArray {
                                            add(
                                                kotlinx.serialization.json.JsonPrimitive(
                                                    "embedContent",
                                                ),
                                            )
                                        },
                                    )
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("name", "models/imagen-3.0-generate-002")
                                    put(
                                        "supportedGenerationMethods",
                                        kotlinx.serialization.json.buildJsonArray {
                                            add(
                                                kotlinx.serialization.json.JsonPrimitive(
                                                    "predict",
                                                ),
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
                    .toString()

            val httpClient = createMockHttpClient(HttpStatusCode.OK, responseJson)
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            val models = provider.getAvailableModels("test_key")

            assertEquals(listOf(LlmModel(LlmProviderName.GEMINI, "gemini-2.5-pro")), models)
        }

    @Test
    fun getAvailableModels_blankApiKey_returnsEmptyList() =
        runBlocking {
            val httpClient = createMockHttpClient(HttpStatusCode.OK, "{}")
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            val models = provider.getAvailableModels("")

            assertEquals(emptyList<LlmModel>(), models)
        }

    @Test
    fun getAvailableModels_apiError_returnsEmptyList() =
        runBlocking {
            val httpClient = createMockHttpClient(HttpStatusCode.InternalServerError, "Error")
            val provider = GeminiProviderImpl(httpClient, toolConverter)

            val models = provider.getAvailableModels("test_key")

            assertEquals(emptyList<LlmModel>(), models)
        }

    @Test
    fun getAvailableModels_realModelsList_returnsChatModelsWithDefaultFirst() =
        runBlocking {
            // The Gemini models the API listed on 2026-11-23, in API order. All of them list generateContent.
            val apiModelIds =
                listOf(
                    "gemini-2.5-flash-preview-tts",
                    "gemini-2.5-pro-preview-tts",
                    "gemini-4-cobalt-eap",
                    "gemini-flash-latest",
                    "gemini-flash-lite-latest",
                    "gemini-pro-latest",
                    "gemini-2.5-flash-lite",
                    "gemini-2.5-flash-image",
                    "gemini-3-flash-preview",
                    "gemini-3.1-pro-preview",
                    "gemini-3.1-pro-preview-customtools",
                    "gemini-3.1-flash-lite-preview",
                    "gemini-3.1-flash-lite",
                    "gemini-3-pro-image-preview",
                    "gemini-3-pro-image",
                    "gemini-3.1-flash-image-preview",
                    "gemini-3.1-flash-image",
                    "gemini-3.1-flash-lite-image",
                    "gemini-nano-banana-2.1",
                    "gemini-3.5-flash",
                    "gemini-3.5-flash-lite",
                    "gemini-omni-flash-preview",
                    "gemini-omni-1.1-flash",
                    "gemini-omni-1.2-flash",
                    "gemini-3.5-transcribe",
                    "gemini-3.6-flash",
                    "gemini-3.7-flash",
                    "gemini-3.8-flash",
                    "gemini-4-argon",
                    "gemini-4-carbon",
                    "gemini-4-hydrogen",
                    "gemini-4-neon",
                    "gemini-3.1-flash-tts-preview",
                    "gemini-3.8-flash-tts",
                    "gemini-3.8-flash-lite-tts",
                    "gemini-robotics-er-2-preview",
                    "gemini-2.5-computer-use-preview-10-2025",
                )

            val modelNames = availableModelNames(apiModelIds)

            assertEquals(
                listOf(
                    "gemini-3.1-flash-lite",
                    "gemini-4-cobalt-eap",
                    "gemini-flash-latest",
                    "gemini-flash-lite-latest",
                    "gemini-pro-latest",
                    "gemini-2.5-flash-lite",
                    "gemini-3-flash-preview",
                    "gemini-3.1-pro-preview",
                    "gemini-3.1-pro-preview-customtools",
                    "gemini-3.1-flash-lite-preview",
                    "gemini-3.5-flash",
                    "gemini-3.5-flash-lite",
                    "gemini-3.6-flash",
                    "gemini-3.7-flash",
                    "gemini-3.8-flash",
                    "gemini-4-argon",
                    "gemini-4-carbon",
                    "gemini-4-hydrogen",
                    "gemini-4-neon",
                ),
                modelNames,
            )
        }

    @Test
    fun getAvailableModels_withoutGemini31FlashLite_returnsFlashLiteLatestFirst() =
        runBlocking {
            val modelNames =
                availableModelNames(
                    listOf(
                        "gemini-flash-latest",
                        "gemini-pro-latest",
                        "gemini-3.1-flash-lite-preview",
                        "gemini-flash-lite-latest",
                        "gemini-3.5-flash",
                    ),
                )

            assertEquals(
                listOf(
                    "gemini-flash-lite-latest",
                    "gemini-flash-latest",
                    "gemini-pro-latest",
                    "gemini-3.1-flash-lite-preview",
                    "gemini-3.5-flash",
                ),
                modelNames,
            )
        }

    @Test
    fun getAvailableModels_withoutFlashLiteDefaults_returnsFlashLatestFirst() =
        runBlocking {
            val modelNames =
                availableModelNames(
                    listOf("gemini-pro-latest", "gemini-3.5-flash-lite", "gemini-flash-latest", "gemini-3.5-flash"),
                )

            assertEquals(
                listOf("gemini-flash-latest", "gemini-pro-latest", "gemini-3.5-flash-lite", "gemini-3.5-flash"),
                modelNames,
            )
        }

    @Test
    fun getAvailableModels_withoutDefaultModels_keepsApiOrder() =
        runBlocking {
            val apiModelIds =
                listOf("gemini-pro-latest", "gemini-3.5-flash", "gemini-3.1-flash-lite-preview", "gemini-2.5-flash-lite")

            val modelNames = availableModelNames(apiModelIds)

            assertEquals(apiModelIds, modelNames)
        }

    @Test
    fun getAvailableModels_newModels_keepsGeneralModelsAndDropsSpecializedModels() =
        runBlocking {
            val modelNames =
                availableModelNames(
                    listOf(
                        "gemini-3.9-flash",
                        "gemini-4-neon-tts",
                        "gemini-nano-banana-3",
                        "gemini-5-xenon",
                        "gemini-3.5-transcribe-live",
                        "gemini-2.5-flash-native-audio-preview-09-2025",
                        "gemini-live-2.5-flash-preview",
                    ),
                )

            assertEquals(listOf("gemini-3.9-flash", "gemini-5-xenon"), modelNames)
        }

    @Test
    fun getAvailableModels_specializedPartInsideLongerNamePart_keepsModels() =
        runBlocking {
            // "live" in "olive" and "image" in "imagery" aren't whole parts of the names.
            val apiModelIds = listOf("gemini-3.1-pro-preview-customtools", "gemini-5-olive", "gemini-5-imagery")

            val modelNames = availableModelNames(apiModelIds)

            assertEquals(apiModelIds, modelNames)
        }
}
