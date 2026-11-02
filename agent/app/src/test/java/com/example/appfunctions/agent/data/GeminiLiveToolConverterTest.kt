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
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeminiLiveToolConverterTest {
    private lateinit var converter: GeminiLiveToolConverter

    @Before
    fun setUp() {
        converter = GeminiLiveToolConverter(GeminiToolConverter())
    }

    @Test
    fun convert_normalizesStringEnumsAndStripsFileReferenceFormat() {
        val enumParam =
            AppFunctionParameterMetadata(
                name = "mode",
                isRequired = true,
                dataType =
                    AppFunctionStringTypeMetadata(isNullable = false, enumValues = setOf("A", "B")),
                description = "Mode parameter",
            )
        val uriParam =
            AppFunctionParameterMetadata(
                name = "wallpaperUri",
                isRequired = false,
                dataType = AppFunctionStringTypeMetadata(isNullable = true),
                description = "Wallpaper URI",
            )
        val tool = createTool(listOf(enumParam, uriParam))

        val liveSchema = converter.convert(tool)

        val expectedJson =
            Json.parseToJsonElement(
                """
            {
                "name": "com_example_my_function",
                "description": "Test function",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "mode": {
                            "type": "string",
                            "enum": ["A", "B"],
                            "description": "Mode parameter"
                        },
                        "wallpaperUri": {
                            "type": "string",
                            "description": "Wallpaper URI"
                        }
                    },
                    "required": ["mode"]
                }
            }
        """,
            )

        assertEquals(expectedJson, liveSchema)
    }

    @Test
    fun convert_omitsEnumForIntParameterWithEnumValues() {
        val intEnumParam =
            AppFunctionParameterMetadata(
                name = "priority",
                isRequired = true,
                dataType =
                    AppFunctionIntTypeMetadata(isNullable = false, enumValues = setOf(1, 2, 3)),
                description = "Priority level",
            )
        val tool = createTool(listOf(intEnumParam))

        val liveSchema = converter.convert(tool)

        val expectedJson =
            Json.parseToJsonElement(
                """
            {
                "name": "com_example_my_function",
                "description": "Test function",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "priority": {
                            "type": "integer",
                            "description": "Priority level"
                        }
                    },
                    "required": ["priority"]
                }
            }
        """,
            )

        assertEquals(expectedJson, liveSchema)
    }

    @Test
    fun convert_omitsFileReferenceFormatForUriObjectAndReferenceParameters() {
        val uriObjectParam =
            AppFunctionParameterMetadata(
                name = "directUri",
                isRequired = true,
                dataType =
                    AppFunctionObjectTypeMetadata(
                        properties = emptyMap(),
                        required = emptyList(),
                        qualifiedName = "android.net.Uri",
                        isNullable = false,
                    ),
                description = "Direct Uri object parameter",
            )
        val uriRefParam =
            AppFunctionParameterMetadata(
                name = "refUri",
                isRequired = false,
                dataType =
                    AppFunctionReferenceTypeMetadata(
                        referenceDataType = "android.net.Uri",
                        isNullable = true,
                    ),
                description = "Reference Uri parameter",
            )
        val tool = createTool(listOf(uriObjectParam, uriRefParam))

        val liveSchema = converter.convert(tool)

        val expectedJson =
            Json.parseToJsonElement(
                """
            {
                "name": "com_example_my_function",
                "description": "Test function",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "directUri": {
                            "type": "string",
                            "description": "Direct Uri object parameter"
                        },
                        "refUri": {
                            "type": "string",
                            "description": "Reference Uri parameter"
                        }
                    },
                    "required": ["directUri"]
                }
            }
        """,
            )

        assertEquals(expectedJson, liveSchema)
    }

    @Test
    fun getToolName_delegatesToGeminiToolConverter() {
        val tool = createTool(emptyList())
        assertEquals("com_example_my_function", converter.getToolName(tool))
    }

    private fun createTool(parameters: List<AppFunctionParameterMetadata>): AppFunctionMetadata =
        AppFunctionMetadata(
            id = "com.example.my_function",
            packageName = "com.example",
            isEnabled = true,
            schema = null,
            parameters = parameters,
            response =
                AppFunctionResponseMetadata(
                    valueType = AppFunctionStringTypeMetadata(isNullable = false),
                    description = "",
                ),
            components = AppFunctionComponentsMetadata(emptyMap()),
            description = "Test function",
            deprecation = null,
        )
}
