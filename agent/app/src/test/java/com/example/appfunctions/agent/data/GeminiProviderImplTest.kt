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
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import com.example.appfunctions.agent.domain.LlmInput
import com.example.appfunctions.agent.domain.LlmResponse
import com.example.appfunctions.agent.domain.LlmResponsePart
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
    fun generateResponse_unknownFunction_returnsError() =
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

            assertTrue(response is LlmResponse.Error)
            val error = response as LlmResponse.Error
            assertTrue(error.errorMessage.contains("Called unknown function"))
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
}
