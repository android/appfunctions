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

import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolConverterTest {
    private val fakeConverter =
        object : ToolConverter<String> {
            override fun convert(tool: AppFunctionMetadata): String = getToolName(tool)

            override fun getToolName(tool: AppFunctionMetadata): String = tool.id.substringAfterLast('.')
        }

    @Test
    fun selectDistinctTools_prioritizesIdStartingWithPackageNameAndDeduplicatesByToolName() {
        val legacyTool = createTool(packageName = "com.example.notes", id = "legacy.createNote")
        val canonicalTool =
            createTool(packageName = "com.example.notes", id = "com.example.notes.createNote")
        val otherTool =
            createTool(packageName = "com.example.notes", id = "com.example.notes.deleteNote")

        val distinct =
            fakeConverter.selectDistinctTools(listOf(legacyTool, canonicalTool, otherTool))

        assertEquals(listOf(canonicalTool, otherTool), distinct)
    }

    @Test
    fun findToolByName_returnsMatchingToolOrNull() {
        val createNote =
            createTool(packageName = "com.example.notes", id = "com.example.notes.createNote")
        val deleteNote =
            createTool(packageName = "com.example.notes", id = "com.example.notes.deleteNote")
        val tools = listOf(createNote, deleteNote)

        assertEquals(createNote, fakeConverter.findToolByName("createNote", tools))
        assertEquals(deleteNote, fakeConverter.findToolByName("deleteNote", tools))
        assertNull(fakeConverter.findToolByName("unknownTool", tools))
    }

    @Test
    fun resolveToolName_returnsConvertedNameWhenMatchedOrFallsBackToFunctionId() {
        val createNote =
            createTool(packageName = "com.example.notes", id = "com.example.notes.createNote")
        val tools = listOf(createNote)

        assertEquals("createNote", fakeConverter.resolveToolName(createNote.id, tools))
        assertEquals("unregistered.id", fakeConverter.resolveToolName("unregistered.id", tools))
    }

    private fun createTool(
        packageName: String,
        id: String,
    ): AppFunctionMetadata =
        AppFunctionMetadata(
            id = id,
            packageName = packageName,
            isEnabled = true,
            schema = null,
            parameters = emptyList(),
            response =
                AppFunctionResponseMetadata(
                    valueType = AppFunctionStringTypeMetadata(isNullable = false),
                    description = "",
                ),
            components = AppFunctionComponentsMetadata(emptyMap()),
            description = "Test tool",
            deprecation = null,
        )
}
