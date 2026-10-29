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

import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import androidx.test.core.app.ApplicationProvider
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionResult
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AgentInternalToolsTest {
    private val agentInternalTools =
        spyk(
            AgentInternalTools(
                context = ApplicationProvider.getApplicationContext(),
                settingsDataStore = mockk(relaxed = true),
            ),
        )

    @Test
    fun `executeInternalTool dispatches generateImage and maps result`() =
        runTest {
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

            val result = agentInternalTools.executeInternalTool(function, inputData)

            assertTrue(result is ExecuteAppFunctionResult.Data)
            val json = JSONObject((result as ExecuteAppFunctionResult.Data).formattedJson)
            assertEquals(imageResult.imageUri, json.getString("imageUri"))
            assertEquals("image/png", json.getString("mimeType"))
            assertEquals("a cat", json.getString("prompt"))
        }

    @Test
    fun `executeInternalTool dispatches geocodeAddress and returns Error on missing required parameter`() =
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

            val okResult = agentInternalTools.executeInternalTool(function, validData)
            assertTrue(okResult is ExecuteAppFunctionResult.Data)
            val json = JSONObject((okResult as ExecuteAppFunctionResult.Data).formattedJson)
            assertEquals(51.5074, json.getDouble("latitude"), 0.0001)
            assertEquals(-0.1278, json.getDouble("longitude"), 0.0001)

            val missingResult =
                agentInternalTools.executeInternalTool(function, AppFunctionData.EMPTY)
            assertTrue(missingResult is ExecuteAppFunctionResult.Error)
            assertEquals(
                "Missing required parameter 'address'",
                (missingResult as ExecuteAppFunctionResult.Error).exception.message,
            )
        }
}
