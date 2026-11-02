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

import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
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

class GeminiToolConverterTest {
    private lateinit var converter: GeminiToolConverter

    @Before
    fun setUp() {
        converter = GeminiToolConverter()
    }

    @Test
    fun getToolName_shortName_returnsCombinedName() {
        val tool =
            AppFunctionMetadata(
                id = "com.example.my_function",
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

        val name = converter.getToolName(tool)
        assertEquals("com_example_my_function", name)
    }

    @Test
    fun getToolName_longName_returnsTruncatedName() {
        val longPackageName = "com.example.verylongpackagename.that.exceeds.sixtyfour.chars"
        val tool =
            AppFunctionMetadata(
                id = "$longPackageName.my_function",
                packageName = longPackageName,
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

        val name = converter.getToolName(tool)
        val expectedGeminiName = "verylongpackagename_that_exceeds_sixtyfour_chars_my_function"
        assertEquals(expectedGeminiName, name)
    }

    @Test
    fun convert_basicTypes_returnsCorrectSchema() {
        val parameter =
            AppFunctionParameterMetadata(
                name = "param1",
                isRequired = true,
                dataType = AppFunctionStringTypeMetadata(isNullable = false),
                description = "A string parameter",
            )
        val tool =
            AppFunctionMetadata(
                id = "com.example.my_function",
                packageName = "com.example",
                isEnabled = true,
                schema = null,
                parameters = listOf(parameter),
                response =
                    AppFunctionResponseMetadata(
                        valueType = AppFunctionStringTypeMetadata(isNullable = false),
                        description = "",
                    ),
                components = AppFunctionComponentsMetadata(emptyMap()),
                description = "Test function",
                deprecation = null,
            )

        val schema = converter.convert(tool)

        val expectedJson =
            Json.parseToJsonElement(
                """
            {
                "name": "com_example_my_function",
                "description": "Test function",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "param1": {
                            "type": "string",
                            "description": "A string parameter"
                        }
                    },
                    "required": ["param1"]
                }
            }
        """,
            )

        assertEquals(expectedJson, schema)
    }

    @Test
    fun convert_arrayType_returnsCorrectSchema() {
        val arrayType =
            AppFunctionArrayTypeMetadata(
                itemType = AppFunctionStringTypeMetadata(isNullable = false),
                isNullable = false,
            )
        val parameter =
            AppFunctionParameterMetadata(
                name = "param1",
                isRequired = true,
                dataType = arrayType,
                description = "An array parameter",
            )
        val tool =
            AppFunctionMetadata(
                id = "com.example.my_function",
                packageName = "com.example",
                isEnabled = true,
                schema = null,
                parameters = listOf(parameter),
                response =
                    AppFunctionResponseMetadata(
                        valueType = AppFunctionStringTypeMetadata(isNullable = false),
                        description = "",
                    ),
                components = AppFunctionComponentsMetadata(emptyMap()),
                description = "",
                deprecation = null,
            )

        val schema = converter.convert(tool)

        val expectedJson =
            Json.parseToJsonElement(
                """
            {
                "name": "com_example_my_function",
                "description": "",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "param1": {
                            "type": "array",
                            "items": {
                                "type": "string"
                            },
                            "description": "An array parameter"
                        }
                    },
                    "required": ["param1"]
                }
            }
        """,
            )

        assertEquals(expectedJson, schema)
    }

    @Test
    fun convert_objectType_returnsCorrectSchema() {
        val objectType =
            AppFunctionObjectTypeMetadata(
                properties = mapOf("prop1" to AppFunctionStringTypeMetadata(isNullable = false)),
                required = listOf("prop1"),
                isNullable = false,
                qualifiedName = "TestType",
            )
        val parameter =
            AppFunctionParameterMetadata(
                name = "param1",
                isRequired = true,
                dataType = objectType,
                description = "An object parameter",
            )
        val tool =
            AppFunctionMetadata(
                id = "com.example.my_function",
                packageName = "com.example",
                isEnabled = true,
                schema = null,
                parameters = listOf(parameter),
                response =
                    AppFunctionResponseMetadata(
                        valueType = AppFunctionStringTypeMetadata(isNullable = false),
                        description = "",
                    ),
                components = AppFunctionComponentsMetadata(emptyMap()),
                description = "",
                deprecation = null,
            )

        val schema = converter.convert(tool)

        val expectedJson =
            Json.parseToJsonElement(
                """
            {
                "name": "com_example_my_function",
                "description": "",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "param1": {
                            "type": "object",
                            "properties": {
                                "prop1": {
                                    "type": "string"
                                }
                            },
                            "required": ["prop1"],
                            "description": "An object parameter"
                        }
                    },
                    "required": ["param1"]
                }
            }
        """,
            )

        assertEquals(expectedJson, schema)
    }

    @Test
    fun convert_referenceType_returnsCorrectSchema() {
        val referenceType =
            AppFunctionReferenceTypeMetadata(referenceDataType = "MyType", isNullable = false)
        val parameter =
            AppFunctionParameterMetadata(
                name = "param1",
                isRequired = true,
                dataType = referenceType,
                description = "A reference parameter",
            )
        val objectType =
            AppFunctionObjectTypeMetadata(
                properties = mapOf("prop1" to AppFunctionStringTypeMetadata(isNullable = false)),
                required = listOf("prop1"),
                isNullable = false,
                qualifiedName = "MyType",
            )
        val components = AppFunctionComponentsMetadata(dataTypes = mapOf("MyType" to objectType))
        val tool =
            AppFunctionMetadata(
                id = "com.example.my_function",
                packageName = "com.example",
                isEnabled = true,
                schema = null,
                parameters = listOf(parameter),
                response =
                    AppFunctionResponseMetadata(
                        valueType = AppFunctionStringTypeMetadata(isNullable = false),
                        description = "",
                    ),
                components = components,
                description = "",
                deprecation = null,
            )

        val schema = converter.convert(tool)

        val expectedJson =
            Json.parseToJsonElement(
                """
            {
                "name": "com_example_my_function",
                "description": "",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "param1": {
                            "type": "object",
                            "properties": {
                                "prop1": {
                                    "type": "string"
                                }
                            },
                            "required": ["prop1"],
                            "description": "A reference parameter"
                        }
                    },
                    "required": ["param1"]
                }
            }
        """,
            )

        assertEquals(expectedJson, schema)
    }

    @Test(expected = IllegalArgumentException::class)
    fun convert_missingReference_throwsException() {
        val referenceType =
            AppFunctionReferenceTypeMetadata(referenceDataType = "MissingType", isNullable = false)
        val parameter =
            AppFunctionParameterMetadata(
                name = "param1",
                isRequired = true,
                dataType = referenceType,
                description = "A parameter with missing reference",
            )
        val tool =
            AppFunctionMetadata(
                id = "com.example.my_function",
                packageName = "com.example",
                isEnabled = true,
                schema = null,
                parameters = listOf(parameter),
                response =
                    AppFunctionResponseMetadata(
                        valueType = AppFunctionStringTypeMetadata(isNullable = false),
                        description = "",
                    ),
                components = AppFunctionComponentsMetadata(emptyMap()),
                description = "",
                deprecation = null,
            )

        converter.convert(tool)
    }

    @Test
    fun convert_enumString_returnCorrectSchema() {
        val parameter =
            AppFunctionParameterMetadata(
                name = "param1",
                isRequired = true,
                dataType =
                    AppFunctionStringTypeMetadata(isNullable = false, enumValues = setOf("1", "2")),
                description = "A string parameter",
            )
        val tool =
            AppFunctionMetadata(
                id = "com.example.my_function",
                packageName = "com.example",
                isEnabled = true,
                schema = null,
                parameters = listOf(parameter),
                response =
                    AppFunctionResponseMetadata(
                        valueType = AppFunctionStringTypeMetadata(isNullable = false),
                        description = "",
                    ),
                components = AppFunctionComponentsMetadata(emptyMap()),
                description = "Test function",
                deprecation = null,
            )

        val schema = converter.convert(tool)

        val expectedJson =
            Json.parseToJsonElement(
                """
            {
                "name": "com_example_my_function",
                "description": "Test function",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "param1": {
                            "type": "string",
                            "enums":["1","2"],
                            "description": "A string parameter"
                        }
                    },
                    "required": ["param1"]
                }
            }
        """,
            )

        assertEquals(expectedJson, schema)
    }

    @Test
    fun convert_stringParameterEndingWithUri_injectsFileReferenceFormat() {
        val parameter =
            AppFunctionParameterMetadata(
                name = "wallpaperUri",
                isRequired = true,
                dataType = AppFunctionStringTypeMetadata(isNullable = false),
                description = "A URI parameter",
            )
        val tool =
            AppFunctionMetadata(
                id = "com.example.my_function",
                packageName = "com.example",
                isEnabled = true,
                schema = null,
                parameters = listOf(parameter),
                response =
                    AppFunctionResponseMetadata(
                        valueType = AppFunctionStringTypeMetadata(isNullable = false),
                        description = "",
                    ),
                components = AppFunctionComponentsMetadata(emptyMap()),
                description = "Test function",
                deprecation = null,
            )

        val schema = converter.convert(tool)

        val expectedJson =
            Json.parseToJsonElement(
                """
            {
                "name": "com_example_my_function",
                "description": "Test function",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "wallpaperUri": {
                            "type": "string",
                            "format": "file_reference",
                            "description": "A URI parameter"
                        }
                    },
                    "required": ["wallpaperUri"]
                }
            }
        """,
            )

        assertEquals(expectedJson, schema)
    }

    @Test
    fun convert_uriObjectType_injectsFileReferenceFormat() {
        val uriObjectMetadata =
            AppFunctionObjectTypeMetadata(
                properties = emptyMap(),
                required = emptyList(),
                qualifiedName = "android.net.Uri",
                isNullable = false,
                description = "Uri object",
            )
        val parameter =
            AppFunctionParameterMetadata(
                name = "customUri",
                isRequired = true,
                dataType = uriObjectMetadata,
                description = "A Uri object parameter",
            )
        val tool =
            AppFunctionMetadata(
                id = "com.example.my_function",
                packageName = "com.example",
                isEnabled = true,
                schema = null,
                parameters = listOf(parameter),
                response =
                    AppFunctionResponseMetadata(
                        valueType = AppFunctionStringTypeMetadata(isNullable = false),
                        description = "",
                    ),
                components = AppFunctionComponentsMetadata(emptyMap()),
                description = "Test function",
                deprecation = null,
            )

        val schema = converter.convert(tool)

        val expectedJson =
            Json.parseToJsonElement(
                """
            {
                "name": "com_example_my_function",
                "description": "Test function",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "customUri": {
                            "type": "string",
                            "format": "file_reference",
                            "description": "A Uri object parameter"
                        }
                    },
                    "required": ["customUri"]
                }
            }
        """,
            )

        assertEquals(expectedJson, schema)
    }
}
