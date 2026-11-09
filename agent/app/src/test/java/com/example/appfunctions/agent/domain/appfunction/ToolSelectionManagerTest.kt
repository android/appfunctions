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

import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import com.example.appfunctions.agent.data.GeminiToolConverter
import com.example.appfunctions.agent.domain.appfunction.ToolSelectionManager.Companion.EXPAND_FUNCTION_DECLARATIONS
import com.example.appfunctions.agent.domain.appfunction.ToolSelectionManager.Companion.EXPAND_TOOL_DESCRIPTION
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ToolSelectionManagerTest {
    private val manager = ToolSelectionManager()

    private val createNote =
        createFunction(NOTES_PKG, "createNote", "Creates a new note. Supports checklists.")
    private val deleteNote = createFunction(NOTES_PKG, "deleteNote", "Deletes a note.\nIrreversible.")
    private val sendMessage = createFunction(CHAT_PKG, "sendMessage", "Sends a message.")
    private val internalTool = createFunction(INTERNAL_PKG, "getCurrentLocation", "Gets location.")

    private val catalog =
        ToolCatalog(
            apps =
                listOf(
                    AppToolGroup(
                        packageName = NOTES_PKG,
                        instructions = "Create and manage notes. Supports tags and reminders.",
                        functions = listOf(createNote, deleteNote),
                    ),
                    AppToolGroup(
                        packageName = CHAT_PKG,
                        instructions = "Chat with friends\nSecond line.",
                        functions = listOf(sendMessage),
                    ),
                ),
            alwaysAvailableTools = listOf(internalTool),
        )

    @Test
    fun `initial tools contain only meta tools and always available tools`() {
        val tools = manager.getActiveTools(THREAD, catalog)

        assertEquals(
            listOf(EXPAND_TOOL_DESCRIPTION, EXPAND_FUNCTION_DECLARATIONS, "getCurrentLocation"),
            tools.map { it.id },
        )
    }

    @Test
    fun `expand_tool_description lists the first line of app instructions`() {
        val expandTool = manager.getActiveTools(THREAD, catalog).first()

        assertTrue(expandTool.description.contains("- $NOTES_PKG: Create and manage notes."))
        assertTrue(expandTool.description.contains("- $CHAT_PKG: Chat with friends"))
        assertFalse(expandTool.description.contains("Supports tags"))
        assertFalse(expandTool.description.contains("Second line"))
        val packageParam = expandTool.parameters.single().dataType as AppFunctionStringTypeMetadata
        assertEquals(setOf(NOTES_PKG, CHAT_PKG), packageParam.enumValues)
    }

    @Test
    fun `expand_function_declarations accepts a list of strings`() {
        val expandFunctions = manager.getActiveTools(THREAD, catalog)[1]

        val param = expandFunctions.parameters.single()
        assertEquals(ToolSelectionManager.PARAM_FUNCTION_IDS, param.name)
        assertTrue(param.dataType is AppFunctionArrayTypeMetadata)
    }

    @Test
    fun `expanding an app returns full instructions and one-line function descriptions`() {
        val result =
            Json.parseToJsonElement(
                manager.handleMetaToolCall(
                    THREAD,
                    EXPAND_TOOL_DESCRIPTION,
                    mapOf("package_name" to NOTES_PKG),
                    catalog,
                ),
            ).jsonObject

        assertEquals(
            "Create and manage notes. Supports tags and reminders.",
            result["instructions"]!!.jsonPrimitive.content,
        )
        val functions = result["functions"]!!.jsonArray.map { it.jsonObject }
        assertEquals(
            listOf("createNote", "deleteNote"),
            functions.map { it["function_id"]!!.jsonPrimitive.content },
        )
        assertEquals(
            listOf("Creates a new note.", "Deletes a note."),
            functions.map { it["description"]!!.jsonPrimitive.content },
        )
        // Schemas are not disclosed at this level.
        assertTrue(functions.all { it.keys == setOf("function_id", "description") })
        // Expanding an app does not load any function declarations.
        assertEquals(3, manager.getActiveTools(THREAD, catalog).size)
    }

    @Test
    fun `expanding an unknown app returns an error`() {
        val result =
            manager.handleMetaToolCall(
                THREAD,
                EXPAND_TOOL_DESCRIPTION,
                mapOf("package_name" to "com.unknown"),
                catalog,
            )

        assertNotNull(Json.parseToJsonElement(result).jsonObject["error"])
    }

    @Test
    fun `expanding functions adds their declarations to active tools`() {
        val result =
            Json.parseToJsonElement(
                manager.handleMetaToolCall(
                    THREAD,
                    EXPAND_FUNCTION_DECLARATIONS,
                    mapOf("function_ids" to listOf("createNote", "missing")),
                    catalog,
                ),
            ).jsonObject

        assertEquals(
            listOf("createNote"),
            result["loaded"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("missing"),
            result["not_found"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        val activeTools = manager.getActiveTools(THREAD, catalog)
        assertTrue(createNote in activeTools)
        assertFalse(deleteNote in activeTools)
        assertFalse(sendMessage in activeTools)
    }

    @Test
    fun `expansions accumulate within a thread and are isolated across threads`() {
        manager.handleMetaToolCall(
            THREAD,
            EXPAND_FUNCTION_DECLARATIONS,
            mapOf("function_ids" to listOf("createNote")),
            catalog,
        )
        manager.handleMetaToolCall(
            THREAD,
            EXPAND_FUNCTION_DECLARATIONS,
            mapOf("function_ids" to listOf("sendMessage")),
            catalog,
        )

        val threadTools = manager.getActiveTools(THREAD, catalog)
        assertTrue(createNote in threadTools)
        assertTrue(sendMessage in threadTools)
        val otherThreadTools = manager.getActiveTools("other_thread", catalog)
        assertFalse(createNote in otherThreadTools)
        assertFalse(sendMessage in otherThreadTools)
    }

    @Test
    fun `meta tools convert to Gemini function declarations`() {
        val converter = GeminiToolConverter()
        val (expandTool, expandFunctions) = manager.getActiveTools(THREAD, catalog)

        val expandToolSchema = converter.convert(expandTool)
        assertEquals(EXPAND_TOOL_DESCRIPTION, expandToolSchema["name"]!!.jsonPrimitive.content)
        val packageNameSchema =
            expandToolSchema["parameters"]!!.jsonObject["properties"]!!.jsonObject["package_name"]!!
                .jsonObject
        assertEquals("string", packageNameSchema["type"]!!.jsonPrimitive.content)

        val expandFunctionsSchema = converter.convert(expandFunctions)
        assertEquals(
            EXPAND_FUNCTION_DECLARATIONS,
            expandFunctionsSchema["name"]!!.jsonPrimitive.content,
        )
        val functionIdsSchema =
            expandFunctionsSchema["parameters"]!!.jsonObject["properties"]!!.jsonObject[
                "function_ids",
            ]!!.jsonObject
        assertEquals("array", functionIdsSchema["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `no meta tools when there are no apps`() {
        val tools =
            manager.getActiveTools(
                THREAD,
                ToolCatalog(apps = emptyList(), alwaysAvailableTools = listOf(internalTool)),
            )

        assertEquals(listOf(internalTool), tools)
    }

    private fun createFunction(
        packageName: String,
        id: String,
        description: String,
    ) = AppFunctionMetadata(
        name = AppFunctionName(packageName, id),
        schema = null,
        parameters =
            listOf(
                AppFunctionParameterMetadata(
                    name = "title",
                    isRequired = true,
                    dataType = AppFunctionStringTypeMetadata(isNullable = false),
                    description = "The title.",
                ),
            ),
        response =
            AppFunctionResponseMetadata(
                valueType = AppFunctionStringTypeMetadata(isNullable = false),
                description = "The result.",
            ),
        description = description,
        deprecation = null,
        packageMetadata =
            AppFunctionPackageMetadata(
                packageName = packageName,
                components = AppFunctionComponentsMetadata(),
            ),
    )

    private companion object {
        const val THREAD = "thread_1"
        const val NOTES_PKG = "com.example.notes"
        const val CHAT_PKG = "com.example.chat"
        const val INTERNAL_PKG = "com.example.appfunctions.agent.internal"
    }
}
