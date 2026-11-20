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
        val longPackageName =
            "com.example.verylongpackagename.that.exceeds.onehundredtwentyeight.chars.which.is.the.maximum.length.of.gemini.function.names"
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
        val expectedGeminiName =
            "verylongpackagename_that_exceeds_onehundredtwentyeight_chars_which_is_the_maximum_length_of_gemini_function_names_my_function"
        assertEquals(expectedGeminiName, name)
    }

    @Test
    fun getToolName_functionsOfOneService_returnsSharedServicePrefix() {
        val names =
            listOf("addNote", "getNoteFieldDescriptions", "getConversationParticipantsWithPresenceDetails").map { function ->
                converter.getToolName(createTool("$LONG_SERVICE#$function"))
            }

        assertEquals(
            listOf(
                "${LONG_SERVICE_PREFIX}_addNote",
                "${LONG_SERVICE_PREFIX}_getNoteFieldDescriptions",
                "${LONG_SERVICE_PREFIX}_getConversationPartici_4a3868d1",
            ),
            names,
        )
    }

    @Test
    fun getToolName_serviceThatFitsInPrefix_returnsFullSanitizedIds() {
        val names =
            listOf("addNote", "updateNoteSettings", "getNoteFieldDescriptions").map { function ->
                converter.getToolName(createTool("$NOTES_SERVICE#$function"))
            }

        assertEquals(
            listOf(
                "com_example_app_feature_notes_ExampleNotesAppFunctionService_addNote",
                "com_example_app_feature_notes_ExampleNotesAppFunctionService_updateNoteSettings",
                "com_example_app_feature_notes_ExampleNotesAppFunctionService_getNoteFieldDescriptions",
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

        assertEquals(functions.map { "com_example_chatapp_appfunctions_BaseChatAppFunctionService_$it" }, names)
    }

    @Test
    fun getToolName_otherToolsConvertedFirst_returnsSameName() {
        val tools =
            listOf(
                createTool("$NOTES_SERVICE#addNote"),
                createTool("$NOTES_SERVICE#getNoteFieldDescriptions"),
                createTool("$LONG_SERVICE#getConversationParticipantsWithPresenceDetails"),
                createTool("generateImage"),
            )
        val namesOfToolsAlone = tools.map { GeminiToolConverter().getToolName(it) }

        tools.reversed().forEach { converter.convert(it) }
        val namesAfterOtherTools = tools.map { converter.getToolName(it) }

        assertEquals(namesOfToolsAlone, namesAfterOtherTools)
    }

    @Test
    fun getToolName_functionNameFitsAfterPrefix_keepsFullFunctionName() {
        // 96-character prefix + "_" + 31-character function name = 128 characters.
        val tool = createTool("$LONG_SERVICE#getConversationParticipantsList")

        val name = converter.getToolName(tool)

        assertEquals("${LONG_SERVICE_PREFIX}_getConversationParticipantsList", name)
    }

    @Test
    fun getToolName_functionNameTooLongToFit_returnsCutNameWithHash() {
        val id = "$LONG_SERVICE#getConversationParticipantsWithPresenceDetails"

        val name = converter.getToolName(createTool(id))

        assertEquals(128, name.length)
        assertEquals("${LONG_SERVICE_PREFIX}_getConversationPartici_", name.dropLast(8))
        assertTrue(name.takeLast(8).matches(Regex("[0-9a-f]{8}")))
        assertEquals(name, GeminiToolConverter().getToolName(createTool(id)))
    }

    @Test
    fun getToolName_longFunctionNamesWithSameStart_returnsDistinctNames() {
        val presenceDetailsName =
            converter.getToolName(
                createTool("$LONG_SERVICE#getConversationParticipantsWithPresenceDetails"),
            )
        val presenceHistoryName =
            converter.getToolName(
                createTool("$LONG_SERVICE#getConversationParticipantsWithPresenceHistory"),
            )

        assertNotEquals(presenceDetailsName, presenceHistoryName)
        assertEquals(presenceDetailsName.dropLast(8), presenceHistoryName.dropLast(8))
    }

    @Test
    fun getToolName_hashWithLeadingZero_padsHashToEightDigits() {
        // The hash code of this ID is 0x009e03f6.
        val tool = createTool("$LONG_SERVICE#fetchChatParticipantsWithNicknames")

        val name = converter.getToolName(tool)

        assertEquals("${LONG_SERVICE_PREFIX}_fetchChatParticipantsW_009e03f6", name)
    }

    @Test
    fun getToolName_classNameLongerThanPrefixLimit_usesClassNameAsPrefix() {
        // The class name has 106 characters, more than the 96 kept of a service name.
        val className = "Example${"VeryLong".repeat(12)}Svc"
        val service = "com.example.verbose.$className"

        val names =
            listOf("addNote", "updateNoteSettings", "getNoteFieldDescriptions").map { function ->
                converter.getToolName(createTool("$service#$function"))
            }

        assertEquals("${className}_addNote", names[0])
        assertEquals("${className}_updateNoteSettings", names[1])
        assertEquals(128, names[2].length)
        assertTrue(names[2].startsWith("${className}_getNoteField_"))
    }

    @Test
    fun getToolName_classNameTooLongToFitBeforeHash_usesCutClassNameAsPrefix() {
        // The class name has 121 characters, 2 more than fit before the hash of a cut name.
        val cutClassName = "Example${"VeryLong".repeat(14)}"
        val service = "com.example.verbose.${cutClassName}Ab"

        val names =
            listOf("share", "shareNote").map { function ->
                converter.getToolName(createTool("$service#$function"))
            }

        assertEquals("${cutClassName}_share", names[0])
        assertEquals(128, names[1].length)
        assertTrue(names[1].startsWith("${cutClassName}_"))
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
                "$LONG_SERVICE#getConversationParticipantsWithPresenceDetails",
                "$LONG_SERVICE#getConversationParticipantsWithPresenceHistory",
                "$LONG_SERVICE#fetchChatParticipantsWithNicknames",
                "com.example.verbose.ExampleVeryLongNotesAppFunctionServiceName#getNoteFieldDescriptions",
                "com.example.verbose.Example${"VeryLong".repeat(12)}Svc#getNoteFieldDescriptions",
                "com.example.verbose.Example${"VeryLong".repeat(14)}Ab#shareNote",
                "$longClassNameService#addNote",
                "$longClassNameService#getNoteFieldDescriptions",
                "com.example.notes.NotesApp\$FunctionService#add-note",
                "com.example.notes.NoteFunctions#createNote",
                "generateImage",
                "com.example.verylongpackagename.that.exceeds.sixtyfour.chars.my_function",
                "com.example.${"a".repeat(134)}",
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
        // The service part has exactly 96 characters, the maximum prefix length.
        val maxService = "com.example.enterprise.productivity.workspace.notes.sync.appfunctionz.ExampleNotesAppFunctionSvc"
        val maxServiceName = converter.getToolName(createTool("$maxService#createNote"))

        assertEquals("com_example_notes_NoteFunctions_createNote", shortServiceName)
        assertEquals(
            "com_example_enterprise_productivity_workspace_notes_sync_appfunctionz_ExampleNotesAppFunctionSvc_createNote",
            maxServiceName,
        )
    }

    @Test
    fun getToolName_idWithoutSeparatorAndLastPartTooLong_returnsLast128Chars() {
        val tool = createTool("com.example.${"a".repeat(134)}")

        val name = converter.getToolName(tool)

        assertEquals("a".repeat(128), name)
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

        // 100 characters, more than the 96 kept whole, so the prefix drops its first part.
        const val LONG_SERVICE = "com.example.enterprise.productivity.workspace.notes.sync.appfunctions.ExampleNotesAppFunctionService"
        const val LONG_SERVICE_PREFIX = "example_enterprise_productivity_workspace_notes_sync_appfunctions_ExampleNotesAppFunctionService"
        val VALID_NAME = Regex("[a-zA-Z_][a-zA-Z0-9_]{0,127}")
    }
}
