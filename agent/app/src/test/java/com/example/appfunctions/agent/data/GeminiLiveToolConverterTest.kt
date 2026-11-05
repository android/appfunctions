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

import androidx.appfunctions.metadata.AppFunctionMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GeminiLiveToolConverterTest {
    private lateinit var geminiToolConverter: GeminiToolConverter
    private lateinit var converter: GeminiLiveToolConverter
    private lateinit var tool: AppFunctionMetadata

    @Before
    fun setUp() {
        geminiToolConverter = mockk()
        converter = GeminiLiveToolConverter(geminiToolConverter)
        tool = mockk()
    }

    @Test
    fun convert_stringEnum_replacesEnumsKeyWithEnum() {
        every { geminiToolConverter.convert(tool) } returns
            Json.parseToJsonElement(
                """
                {
                    "name": "com_example_fn",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "mode": {
                                "type": "string",
                                "enums": ["A", "B"]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ).jsonObject

        val expected =
            Json.parseToJsonElement(
                """
                {
                    "name": "com_example_fn",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "mode": {
                                "type": "string",
                                "enum": ["A", "B"]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ).jsonObject

        assertEquals(expected, converter.convert(tool))
    }

    @Test
    fun convert_nonStringEnum_omitsEnumsKey() {
        every { geminiToolConverter.convert(tool) } returns
            Json.parseToJsonElement(
                """
                {
                    "name": "com_example_fn",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "priority": {
                                "type": "integer",
                                "enums": [1, 2, 3]
                            }
                        }
                    }
                }
                """.trimIndent(),
            ).jsonObject

        val expected =
            Json.parseToJsonElement(
                """
                {
                    "name": "com_example_fn",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "priority": {
                                "type": "integer"
                            }
                        }
                    }
                }
                """.trimIndent(),
            ).jsonObject

        assertEquals(expected, converter.convert(tool))
    }

    @Test
    fun convert_fileReferenceFormat_stripsFileReferenceRecursivelyAndKeepsOtherFormats() {
        every { geminiToolConverter.convert(tool) } returns
            Json.parseToJsonElement(
                """
                {
                    "name": "com_example_fn",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "wallpaperUri": {
                                "type": "string",
                                "format": "file_reference"
                            },
                            "nested": {
                                "type": "object",
                                "properties": {
                                    "attachmentUri": {
                                        "type": "string",
                                        "format": "file_reference"
                                    },
                                    "createdAt": {
                                        "type": "string",
                                        "format": "date-time"
                                    }
                                }
                            }
                        }
                    }
                }
                """.trimIndent(),
            ).jsonObject

        val expected =
            Json.parseToJsonElement(
                """
                {
                    "name": "com_example_fn",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "wallpaperUri": {
                                "type": "string"
                            },
                            "nested": {
                                "type": "object",
                                "properties": {
                                    "attachmentUri": {
                                        "type": "string"
                                    },
                                    "createdAt": {
                                        "type": "string",
                                        "format": "date-time"
                                    }
                                }
                            }
                        }
                    }
                }
                """.trimIndent(),
            ).jsonObject

        assertEquals(expected, converter.convert(tool))
    }

    @Test
    fun getToolName_delegatesToGeminiToolConverter() {
        every { geminiToolConverter.getToolName(tool) } returns "delegated_tool_name"

        assertEquals("delegated_tool_name", converter.getToolName(tool))
        verify(exactly = 1) { geminiToolConverter.getToolName(tool) }
    }
}
