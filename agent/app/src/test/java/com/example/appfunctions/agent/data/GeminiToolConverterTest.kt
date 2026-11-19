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
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
    fun getToolName_functionsOfOneService_returnsSharedServicePrefix() {
        val names =
            listOf("addNote", "updateNoteSettings", "getNoteFieldDescriptions").map { function ->
                converter.getToolName(createTool("$NOTES_SERVICE#$function"))
            }

        assertEquals(
            listOf(
                "ExampleNotesAppFunctionService_addNote",
                "ExampleNotesAppFunctionService_updateNoteSettings",
                "ExampleNotesAppFunctionService_getNoteFieldDescriptions",
            ),
            names,
        )
    }

    @Test
    fun getToolName_chatAppFunctions_returnsSharedServicePrefix() {
        val functions =
            listOf(
                "searchContacts",
                "fetchSendMessageDetails",
                "sendMessage",
                "makeCall",
                "updateChatWallpaper",
                "searchMessages",
            )

        val names = functions.map { converter.getToolName(createTool("$CHAT_SERVICE#$it")) }

        assertEquals(functions.map { "BaseChatAppFunctionService_$it" }, names)
    }

    @Test
    fun getToolName_otherToolsConvertedFirst_returnsSameName() {
        val tools =
            listOf(
                createTool("$NOTES_SERVICE#addNote"),
                createTool("$NOTES_SERVICE#getNoteFieldDescriptions"),
                createTool("$CHAT_SERVICE#getConversationParticipantsWithPresenceDetails"),
                createTool("generateImage"),
            )
        val namesOfToolsAlone = tools.map { GeminiToolConverter().getToolName(it) }

        tools.reversed().forEach { converter.convert(it) }
        val namesAfterOtherTools = tools.map { converter.getToolName(it) }

        assertEquals(namesOfToolsAlone, namesAfterOtherTools)
    }

    @Test
    fun getToolName_functionNameFitsAfterPrefix_keepsFullFunctionName() {
        // 26-character prefix + "_" + 37-character function name = 64 characters.
        val tool = createTool("$CHAT_SERVICE#getConversationParticipantsWithStatus")

        val name = converter.getToolName(tool)

        assertEquals("BaseChatAppFunctionService_getConversationParticipantsWithStatus", name)
    }

    @Test
    fun getToolName_functionNameTooLongToFit_returnsCutNameWithHash() {
        val id = "$CHAT_SERVICE#getConversationParticipantsWithPresenceDetails"

        val name = converter.getToolName(createTool(id))

        assertEquals(64, name.length)
        assertEquals("BaseChatAppFunctionService_getConversationParticipantsW_", name.dropLast(8))
        assertTrue(name.takeLast(8).matches(Regex("[0-9a-f]{8}")))
        assertEquals(name, GeminiToolConverter().getToolName(createTool(id)))
    }

    @Test
    fun getToolName_longFunctionNamesWithSameStart_returnsDistinctNames() {
        val presenceDetailsName =
            converter.getToolName(
                createTool("$CHAT_SERVICE#getConversationParticipantsWithPresenceDetails"),
            )
        val presenceHistoryName =
            converter.getToolName(
                createTool("$CHAT_SERVICE#getConversationParticipantsWithPresenceHistory"),
            )

        assertNotEquals(presenceDetailsName, presenceHistoryName)
        assertEquals(presenceDetailsName.dropLast(8), presenceHistoryName.dropLast(8))
    }

    @Test
    fun getToolName_hashWithLeadingZero_padsHashToEightDigits() {
        // The hash code of this ID is 0x045b61e5.
        val tool = createTool("$CHAT_SERVICE#getConversationParticipantsWithDeliveryStatus")

        val name = converter.getToolName(tool)

        assertEquals("BaseChatAppFunctionService_getConversationParticipantsW_045b61e5", name)
    }

    @Test
    fun getToolName_classNameLongerThanPrefixLimit_usesClassNameAsPrefix() {
        val service = "com.example.verbose.ExampleVeryLongNotesAppFunctionServiceName"

        val names =
            listOf("addNote", "updateNoteSettings", "getNoteFieldDescriptions").map { function ->
                converter.getToolName(createTool("$service#$function"))
            }

        assertEquals("ExampleVeryLongNotesAppFunctionServiceName_addNote", names[0])
        assertEquals("ExampleVeryLongNotesAppFunctionServiceName_updateNoteSettings", names[1])
        assertEquals(64, names[2].length)
        assertTrue(names[2].startsWith("ExampleVeryLongNotesAppFunctionServiceName_getNoteField_"))
    }

    @Test
    fun getToolName_variousIds_returnsDistinctValidNames() {
        val longClassNameService =
            "com.example.huge.NotesAppFunctionServiceWithAnExtremelyLongClassNameExceedingTheLimit"
        val ids =
            listOf(
                "$NOTES_SERVICE#addNote",
                "$NOTES_SERVICE#updateNoteSettings",
                "$NOTES_SERVICE#getNoteFieldDescriptions",
                "$CHAT_SERVICE#sendMessage",
                "$CHAT_SERVICE#getConversationParticipantsWithPresenceDetails",
                "$CHAT_SERVICE#getConversationParticipantsWithPresenceHistory",
                "$CHAT_SERVICE#getConversationParticipantsWithDeliveryStatus",
                "com.example.verbose.ExampleVeryLongNotesAppFunctionServiceName#getNoteFieldDescriptions",
                "$longClassNameService#addNote",
                "$longClassNameService#getNoteFieldDescriptions",
                "com.example.notes.NotesApp\$FunctionService#add-note",
                "com.example.notes.NoteFunctions#createNote",
                "generateImage",
                "com.example.verylongpackagename.that.exceeds.sixtyfour.chars.my_function",
            )

        val names = ids.map { converter.getToolName(createTool(it)) }

        ids.zip(names).forEach { (id, name) ->
            assertTrue("Invalid name $name for $id", name.matches(VALID_NAME))
        }
        assertEquals(ids.size, names.toSet().size)
    }

    @Test
    fun getToolName_servicePartFitsInPrefix_keepsFullName() {
        val shortServiceName = converter.getToolName(createTool("com.example.notes.NoteFunctions#createNote"))
        // The service part has exactly 32 characters, the maximum prefix length.
        val maxServiceName = converter.getToolName(createTool("com.example.notes.NotesFunctions#createNote"))

        assertEquals("com_example_notes_NoteFunctions_createNote", shortServiceName)
        assertEquals("com_example_notes_NotesFunctions_createNote", maxServiceName)
    }

    @Test
    fun getToolName_idWithoutSeparatorAndLastPartTooLong_returnsLast64Chars() {
        val tool = createTool("com.example.${"a".repeat(70)}")

        val name = converter.getToolName(tool)

        assertEquals("a".repeat(64), name)
    }

    @Test
    fun convert_serviceFunctionId_usesToolNameAsName() {
        val tool = createTool("$CHAT_SERVICE#getConversationParticipantsWithPresenceDetails")

        val schema = converter.convert(tool)

        assertEquals(converter.getToolName(tool), schema["name"]!!.jsonPrimitive.content)
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

    private fun createTool(id: String): AppFunctionMetadata =
        AppFunctionMetadata(
            id = id,
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

    private companion object {
        const val NOTES_SERVICE = "com.example.app.feature.notes.ExampleNotesAppFunctionService"
        const val CHAT_SERVICE = "com.example.chatapp.appfunctions.BaseChatAppFunctionService"
        val VALID_NAME = Regex("[a-zA-Z_][a-zA-Z0-9_]{0,63}")
    }
}
