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

import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import com.example.appfunctions.agent.domain.LlmInput
import com.example.appfunctions.agent.domain.LlmProvider
import com.example.appfunctions.agent.domain.LlmResponse
import com.example.appfunctions.agent.domain.LlmResponsePart
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Base class for integration tests for [LlmProvider] implementations.
 *
 * Subclasses must provide the specific provider instance and model name.
 */
abstract class LlmProviderIntegrationTestBase {
    protected lateinit var provider: LlmProvider
    protected lateinit var apiKey: String

    /** The name of the model to use for testing. */
    protected abstract val modelName: String

    /** Creates the specific [LlmProvider] instance. */
    protected abstract fun createProvider(httpClient: HttpClient): LlmProvider

    /** Returns the argument key used to retrieve the API key from instrumentation arguments. */
    protected abstract val apiKeyArgumentKey: String

    @Before
    fun setUp() {
        val arguments = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        apiKey = arguments.getString(apiKeyArgumentKey) ?: ""
        assumeTrue(
            "API key is required. Please provide it via instrumentation arguments.",
            apiKey.isNotEmpty() && apiKey != "PLACEHOLDER",
        )

        val httpClient =
            HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            prettyPrint = true
                            isLenient = true
                        },
                    )
                }
            }
        provider = createProvider(httpClient)
    }

    @Test
    fun generateResponse_realTest() =
        runBlocking {
            val response =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Tell me a short joke about programming."),
                    tools = emptyList(),
                    apiKey = apiKey,
                    modelName = modelName,
                )

            assertTrue(response is LlmResponse.Success)
            val successResponse = response as LlmResponse.Success
            val textPart = successResponse.parts.filterIsInstance<LlmResponsePart.Text>().firstOrNull()
            println("Real Test Response: ${textPart?.text}")
            assertNotNull(textPart)
            assertTrue(textPart!!.text.isNotEmpty())
        }

    @Test
    fun generateResponse_statefulTest() =
        runBlocking {
            val response1 =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("My name is Phil."),
                    tools = emptyList(),
                    apiKey = apiKey,
                    modelName = modelName,
                )

            assertTrue("Expected Success but got $response1", response1 is LlmResponse.Success)
            val interactionId = (response1 as LlmResponse.Success).interactionId
            assertNotNull(interactionId)

            val response2 =
                provider.generateResponse(
                    previousInteractionId = interactionId,
                    input = LlmInput.UserMessage("What is my name?"),
                    tools = emptyList(),
                    apiKey = apiKey,
                    modelName = modelName,
                )

            assertTrue(response2 is LlmResponse.Success)
            val textPart =
                (response2 as LlmResponse.Success)
                    .parts
                    .filterIsInstance<LlmResponsePart.Text>()
                    .firstOrNull()
            println("Stateful Test Response 2: ${textPart?.text}")
            assertNotNull(textPart)
            assertTrue(textPart!!.text.contains("Phil"))
        }

    @Test
    fun generateResponse_toolCallTest() =
        runBlocking {
            val stringType = AppFunctionStringTypeMetadata(isNullable = false)
            val responseMetadata =
                AppFunctionResponseMetadata(
                    valueType = stringType,
                    description = "Returns a string",
                )
            val components = AppFunctionComponentsMetadata(emptyMap())
            val parameter =
                AppFunctionParameterMetadata(
                    name = "location",
                    isRequired = true,
                    dataType = stringType,
                    description = "The location to get weather for",
                )

            val realTool =
                AppFunctionMetadata(
                    id = "com.example.appfunctions.agent.get_weather",
                    packageName = "com.example.appfunctions.agent",
                    isEnabled = true,
                    schema = null,
                    parameters = listOf(parameter),
                    response = responseMetadata,
                    components = components,
                    description = "Gets the weather for a given location.",
                    deprecation = null,
                )

            val response =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Tell me the weather in Mountain View"),
                    tools = listOf(realTool),
                    apiKey = apiKey,
                    modelName = modelName,
                )

            assertTrue(response is LlmResponse.Success)
            val successResponse = response as LlmResponse.Success
            val toolCalls = successResponse.parts.filterIsInstance<LlmResponsePart.ToolCall>()
            println("Tool Calls: $toolCalls")

            assertTrue(toolCalls.isNotEmpty())
            val toolCall = toolCalls[0]
            assertEquals("com.example.appfunctions.agent", toolCall.packageName)
            assertEquals("com.example.appfunctions.agent.get_weather", toolCall.functionId)
        }

    @Test
    fun generateResponse_toolCallWithLongNameTest() =
        runBlocking {
            val stringType = AppFunctionStringTypeMetadata(isNullable = false)
            val responseMetadata =
                AppFunctionResponseMetadata(
                    valueType = stringType,
                    description = "Returns a string",
                )
            val components = AppFunctionComponentsMetadata(emptyMap())
            val parameter =
                AppFunctionParameterMetadata(
                    name = "param1",
                    isRequired = true,
                    dataType = stringType,
                    description = "A parameter",
                )

            val longPackageName = "com.example.verylongpackagename.that.exceeds.sixtyfour.chars"
            val functionId = "my_function"

            val realTool =
                AppFunctionMetadata(
                    id = functionId,
                    packageName = longPackageName,
                    isEnabled = true,
                    schema = null,
                    parameters = listOf(parameter),
                    response = responseMetadata,
                    components = components,
                    description = "Greets the user with a message.",
                    deprecation = null,
                )

            val response =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Greet me with 'hello'"),
                    tools = listOf(realTool),
                    apiKey = apiKey,
                    modelName = modelName,
                )

            assertTrue(response is LlmResponse.Success)
            val successResponse = response as LlmResponse.Success
            val toolCalls = successResponse.parts.filterIsInstance<LlmResponsePart.ToolCall>()
            println("Long Tool Calls: $toolCalls")

            assertTrue(toolCalls.isNotEmpty())
            val toolCall = toolCalls[0]
            assertEquals(longPackageName, toolCall.packageName)
            assertEquals(functionId, toolCall.functionId)
            assertEquals("hello", toolCall.arguments["param1"])
        }

    @Test
    fun generateResponse_toolCallWithPendingIntentResponseTest() =
        runBlocking {
            val stringType = AppFunctionStringTypeMetadata(isNullable = false)
            val parcelableType =
                androidx.appfunctions.metadata.AppFunctionParcelableTypeMetadata(
                    qualifiedName = "android.app.PendingIntent",
                    isNullable = false,
                )
            val responseMetadata =
                AppFunctionResponseMetadata(
                    valueType = parcelableType,
                    description = "Returns a PendingIntent",
                )
            val components = AppFunctionComponentsMetadata(emptyMap())
            val parameter =
                AppFunctionParameterMetadata(
                    name = "recipient",
                    isRequired = true,
                    dataType = stringType,
                    description = "The recipient of the email",
                )

            val realTool =
                AppFunctionMetadata(
                    id = "com.example.appfunctions.agent.open_email",
                    packageName = "com.example.appfunctions.agent",
                    isEnabled = true,
                    schema = null,
                    parameters = listOf(parameter),
                    response = responseMetadata,
                    components = components,
                    description = "Opens the email app with a dynamic recipient.",
                    deprecation = null,
                )

            val response =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Open email for Phil"),
                    tools = listOf(realTool),
                    apiKey = apiKey,
                    modelName = modelName,
                )

            assertTrue(response is LlmResponse.Success)
            val successResponse = response as LlmResponse.Success
            val toolCalls = successResponse.parts.filterIsInstance<LlmResponsePart.ToolCall>()
            assertTrue(toolCalls.isNotEmpty())
            val toolCall = toolCalls[0]
            assertEquals("com.example.appfunctions.agent", toolCall.packageName)
            assertEquals("com.example.appfunctions.agent.open_email", toolCall.functionId)
            assertEquals("Phil", toolCall.arguments["recipient"])
        }

    @Test
    fun generateResponse_invalidApiKeyTest() =
        runBlocking {
            val response =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Hello"),
                    tools = emptyList(),
                    apiKey = "INVALID_KEY",
                    modelName = modelName,
                )

            assertTrue(response is LlmResponse.Error)
            val errorResponse = response as LlmResponse.Error
            assertTrue(errorResponse.errorMessage.contains("Gemini API error"))
        }

    @Test
    fun generateResponse_toolResponseTest() =
        runBlocking {
            val stringType = AppFunctionStringTypeMetadata(isNullable = false)
            val responseMetadata =
                AppFunctionResponseMetadata(
                    valueType = stringType,
                    description = "Returns a string",
                )
            val components = AppFunctionComponentsMetadata(emptyMap())
            val parameter =
                AppFunctionParameterMetadata(
                    name = "location",
                    isRequired = true,
                    dataType = stringType,
                    description = "The location to get weather for",
                )

            val realTool =
                AppFunctionMetadata(
                    id = "com.example.appfunctions.agent.get_weather",
                    packageName = "com.example.appfunctions.agent",
                    isEnabled = true,
                    schema = null,
                    parameters = listOf(parameter),
                    response = responseMetadata,
                    components = components,
                    description = "Gets the weather for a given location.",
                    deprecation = null,
                )

            val response1 =
                provider.generateResponse(
                    previousInteractionId = null,
                    input = LlmInput.UserMessage("Tell me the weather in Mountain View"),
                    tools = listOf(realTool),
                    apiKey = apiKey,
                    modelName = modelName,
                )

            assertTrue(response1 is LlmResponse.Success)
            val successResponse1 = response1 as LlmResponse.Success
            val toolCalls = successResponse1.parts.filterIsInstance<LlmResponsePart.ToolCall>()
            assertTrue(toolCalls.isNotEmpty())
            val toolCall = toolCalls[0]
            assertNotNull(toolCall.callId)

            val toolOutput =
                com.example.appfunctions.agent.domain.ToolOutput(
                    functionId = toolCall.functionId,
                    callId = toolCall.callId,
                    result = "It is sunny in Mountain View",
                )

            val response2 =
                provider.generateResponse(
                    previousInteractionId = successResponse1.interactionId,
                    input = LlmInput.ToolResponse(listOf(toolOutput)),
                    tools = listOf(realTool),
                    apiKey = apiKey,
                    modelName = modelName,
                )

            assertTrue(response2 is LlmResponse.Success)
            val successResponse2 = response2 as LlmResponse.Success
            val textPart = successResponse2.parts.filterIsInstance<LlmResponsePart.Text>().firstOrNull()
            println("Tool Response Test Response: ${textPart?.text}")
            assertNotNull(textPart)
            assertTrue(textPart!!.text.contains("sunny"))
        }
}
