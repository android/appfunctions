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
package com.example.appfunctions.agent.domain.appfunction

import android.content.Context
import android.content.Intent
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import com.example.appfunctions.agent.data.AgentInternalTools
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExecuteAppFunctionUseCaseTest {
    private val context = mockk<Context>(relaxed = true)
    private val appFunctionManager = mockk<AppFunctionManager>()
    private val convertAppFunctionDataToJsonUseCase = mockk<ConvertAppFunctionDataToJsonUseCase>()
    private val agentInternalTools = mockk<AgentInternalTools>(relaxed = true)
    private val useCase =
        ExecuteAppFunctionUseCase(
            context = context,
            appFunctionManager = appFunctionManager,
            convertAppFunctionDataToJsonUseCase = convertAppFunctionDataToJsonUseCase,
            agentInternalTools = agentInternalTools,
        )

    @Test
    fun `invoke returns Error with original AppFunctionException when response is Error`() =
        runTest {
            val function = mockk<AppFunctionMetadata>(relaxed = true)
            every { function.packageName } returns "com.example.app"
            every { function.id } returns "test_function"
            every { function.parameters } returns emptyList()
            val parameters = mockk<AppFunctionData>()

            val appException = AppFunctionPermissionRequiredException("Permission needed")
            coEvery { appFunctionManager.executeAppFunction(any()) } returns
                ExecuteAppFunctionResponse.Error(appException)

            val result = useCase(function, parameters)

            assertTrue(result is ExecuteAppFunctionResult.Error)
            val errorResult = result as ExecuteAppFunctionResult.Error
            assertEquals(appException, errorResult.exception)
        }

    @Test
    fun `invoke returns Error when executeAppFunction throws exception`() =
        runTest {
            val function = mockk<AppFunctionMetadata>(relaxed = true)
            every { function.packageName } returns "com.example.app"
            every { function.id } returns "test_function"
            every { function.parameters } returns emptyList()
            val parameters = mockk<AppFunctionData>()

            val runtimeException = RuntimeException("Unexpected crash")
            coEvery { appFunctionManager.executeAppFunction(any()) } throws runtimeException

            val result = useCase(function, parameters)

            assertTrue(result is ExecuteAppFunctionResult.Error)
            val errorResult = result as ExecuteAppFunctionResult.Error
            assertEquals(runtimeException, errorResult.exception)
        }

    @Test
    fun `invoke grants recursive read URI permissions for content URIs in AppFunctionData`() =
        runTest {
            val uriObjectType =
                AppFunctionObjectTypeMetadata(
                    properties = mapOf("uri" to AppFunctionStringTypeMetadata(false)),
                    required = listOf("uri"),
                    qualifiedName = "android.net.Uri",
                    isNullable = false,
                )
            val payloadObjectType =
                AppFunctionObjectTypeMetadata(
                    properties =
                        mapOf(
                            "nestedUris" to
                                AppFunctionArrayTypeMetadata(
                                    itemType = AppFunctionStringTypeMetadata(false),
                                    isNullable = false,
                                ),
                        ),
                    required = listOf("nestedUris"),
                    qualifiedName = null,
                    isNullable = false,
                )
            val parametersMeta =
                listOf(
                    AppFunctionParameterMetadata(
                        name = "directUri",
                        isRequired = true,
                        dataType = AppFunctionStringTypeMetadata(false),
                    ),
                    AppFunctionParameterMetadata(
                        name = "uriObj",
                        isRequired = true,
                        dataType = uriObjectType,
                    ),
                    AppFunctionParameterMetadata(
                        name = "payload",
                        isRequired = true,
                        dataType = payloadObjectType,
                    ),
                    AppFunctionParameterMetadata(
                        name = "plainText",
                        isRequired = true,
                        dataType = AppFunctionStringTypeMetadata(false),
                    ),
                )
            val components = AppFunctionComponentsMetadata()
            val function =
                AppFunctionMetadata(
                    name = AppFunctionName("com.example.notes", "attachMedia"),
                    schema = null,
                    parameters = parametersMeta,
                    response =
                        AppFunctionResponseMetadata(
                            valueType = AppFunctionStringTypeMetadata(false),
                            description = "",
                        ),
                    description = "Attach media",
                    deprecation = null,
                    packageMetadata =
                        AppFunctionPackageMetadata(
                            packageName = "com.example.notes",
                            components = components,
                        ),
                )

            val directUri = "content://com.example.appfunctions.agent.fileprovider/cache/direct.png"
            val objectUri = "content://com.example.appfunctions.agent.fileprovider/cache/object.png"
            val nestedUri = "content://com.example.appfunctions.agent.fileprovider/cache/nested.jpg"

            val uriObjData =
                AppFunctionData.Builder(uriObjectType, components)
                    .setString("uri", objectUri)
                    .build()
            val payloadData =
                AppFunctionData.Builder(payloadObjectType, components)
                    .setStringList("nestedUris", listOf(nestedUri, "https://example.com/ignore"))
                    .build()
            val inputData =
                AppFunctionData.Builder(parametersMeta, components)
                    .setString("directUri", directUri)
                    .setAppFunctionData("uriObj", uriObjData)
                    .setAppFunctionData("payload", payloadData)
                    .setString("plainText", "hello world")
                    .build()

            coEvery { appFunctionManager.executeAppFunction(any()) } returns
                ExecuteAppFunctionResponse.Success(AppFunctionData.EMPTY)
            every {
                convertAppFunctionDataToJsonUseCase(any(), any(), any(), any())
            } returns """{"ok":true}"""

            val result = useCase(function, inputData)

            assertTrue(result is ExecuteAppFunctionResult.Data)
            assertEquals("""{"ok":true}""", (result as ExecuteAppFunctionResult.Data).formattedJson)

            for (expectedUri in listOf(directUri, objectUri, nestedUri)) {
                verify(exactly = 1) {
                    context.grantUriPermission(
                        eq("com.example.notes"),
                        match { it.toString() == expectedUri },
                        eq(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    )
                }
            }
        }

    @Test
    fun `invoke dispatches internal generateImage tool even when AppFunctionManager is null`() =
        runTest {
            val nullManagerUseCase =
                ExecuteAppFunctionUseCase(
                    context = context,
                    appFunctionManager = null,
                    convertAppFunctionDataToJsonUseCase = convertAppFunctionDataToJsonUseCase,
                    agentInternalTools = agentInternalTools,
                )
            val imageResult =
                AgentInternalTools.GeneratedImageResult(
                    imageUri = "content://com.example.appfunctions.agent.fileprovider/cache/cat.png",
                    mimeType = "image/png",
                    prompt = "a cat",
                )
            coEvery { agentInternalTools.generateImage("a cat", "1:1") } returns imageResult

            val parametersMeta =
                listOf(
                    AppFunctionParameterMetadata(
                        name = "prompt",
                        isRequired = true,
                        dataType = AppFunctionStringTypeMetadata(false),
                    ),
                    AppFunctionParameterMetadata(
                        name = "aspectRatio",
                        isRequired = false,
                        dataType = AppFunctionStringTypeMetadata(true),
                    ),
                )
            val components = AppFunctionComponentsMetadata()
            val imageResultType =
                AppFunctionObjectTypeMetadata(
                    properties =
                        mapOf(
                            "imageUri" to AppFunctionStringTypeMetadata(false),
                            "mimeType" to AppFunctionStringTypeMetadata(false),
                            "prompt" to AppFunctionStringTypeMetadata(false),
                        ),
                    required = listOf("imageUri", "mimeType", "prompt"),
                    isNullable = false,
                    qualifiedName =
                        "com.example.appfunctions.agent.data.AgentInternalTools.GeneratedImageResult",
                )
            val function =
                AppFunctionMetadata(
                    name =
                        AppFunctionName(
                            AgentInternalTools.INTERNAL_TOOL_PACKAGE,
                            "generateImage",
                        ),
                    schema = null,
                    parameters = parametersMeta,
                    response =
                        AppFunctionResponseMetadata(
                            valueType = imageResultType,
                            description = "",
                        ),
                    description = "Generate image",
                    deprecation = null,
                    packageMetadata =
                        AppFunctionPackageMetadata(
                            packageName = AgentInternalTools.INTERNAL_TOOL_PACKAGE,
                            components = components,
                        ),
                )
            val inputData =
                AppFunctionData.Builder(parametersMeta, components)
                    .setString("prompt", "a cat")
                    .setString("aspectRatio", "1:1")
                    .build()

            val result = nullManagerUseCase(function, inputData)

            assertTrue(result is ExecuteAppFunctionResult.Data)
            val json = JSONObject((result as ExecuteAppFunctionResult.Data).formattedJson)
            assertEquals(imageResult.imageUri, json.getString("imageUri"))
            assertEquals("image/png", json.getString("mimeType"))
            assertEquals("a cat", json.getString("prompt"))
        }

    @Test
    fun `invoke dispatches internal geocodeAddress tool and returns Error on missing required parameter`() =
        runTest {
            coEvery { agentInternalTools.geocodeAddress("London") } returns
                AgentInternalTools.LatLng(51.5074, -0.1278)

            val parametersMeta =
                listOf(
                    AppFunctionParameterMetadata(
                        name = "address",
                        isRequired = true,
                        dataType = AppFunctionStringTypeMetadata(false),
                    ),
                )
            val components = AppFunctionComponentsMetadata()
            val function =
                AppFunctionMetadata(
                    name =
                        AppFunctionName(
                            AgentInternalTools.INTERNAL_TOOL_PACKAGE,
                            "geocodeAddress",
                        ),
                    schema = null,
                    parameters = parametersMeta,
                    response =
                        AppFunctionResponseMetadata(
                            valueType = AppFunctionStringTypeMetadata(true),
                            description = "",
                        ),
                    description = "Geocode",
                    deprecation = null,
                    packageMetadata =
                        AppFunctionPackageMetadata(
                            packageName = AgentInternalTools.INTERNAL_TOOL_PACKAGE,
                            components = components,
                        ),
                )
            val validData =
                AppFunctionData.Builder(parametersMeta, components)
                    .setString("address", "London")
                    .build()

            val okResult = useCase(function, validData)
            assertTrue(okResult is ExecuteAppFunctionResult.Data)
            val json = JSONObject((okResult as ExecuteAppFunctionResult.Data).formattedJson)
            assertEquals(51.5074, json.getDouble("latitude"), 0.0001)
            assertEquals(-0.1278, json.getDouble("longitude"), 0.0001)

            val missingResult = useCase(function, AppFunctionData.EMPTY)
            assertTrue(missingResult is ExecuteAppFunctionResult.Error)
            assertEquals(
                "Missing required parameter 'address'",
                (missingResult as ExecuteAppFunctionResult.Error).exception.message,
            )
        }
}
