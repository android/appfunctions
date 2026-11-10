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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.metadata.AppFunctionAppMetadata
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import androidx.test.core.app.ApplicationProvider
import com.example.appfunctions.agent.data.AgentInternalTools.Companion.EXPAND_FUNCTION_DECLARATIONS
import com.example.appfunctions.agent.data.AgentInternalTools.Companion.EXPAND_TOOL_DESCRIPTION
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ToolDiscoveryToolsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notesPackage = packageMetadata(NOTES_PKG)
    private val chatPackage = packageMetadata(CHAT_PKG)
    private val createNote = function(notesPackage, "createNote", "Creates a new note. Supports checklists.")
    private val deleteNote = function(notesPackage, "deleteNote", "Deletes a note.\nIrreversible.")
    private val sendMessage = function(chatPackage, "sendMessage", "Sends a message.")
    private val apps = mapOf(notesPackage to listOf(createNote, deleteNote), chatPackage to listOf(sendMessage))

    private val toolDiscoveryTools = ToolDiscoveryTools(context)

    @Before
    fun setUp() {
        mockApp(
            packageMetadata = notesPackage,
            label = "Notes",
            instructions =
                "Manage notes, e.g. shopping lists. Use createNote to add a note and " +
                    "deleteNote to remove one; always confirm with the user before deleting " +
                    "anything because deletion cannot be undone.",
        )
        mockApp(packageMetadata = chatPackage, label = "Chat", instructions = null)
    }

    @Test
    fun `buildTools returns the two discovery tools in the internal package`() =
        runTest {
            val tools = toolDiscoveryTools.buildTools(apps)

            assertEquals(listOf(EXPAND_TOOL_DESCRIPTION, EXPAND_FUNCTION_DECLARATIONS), tools.map { it.id })
            assertTrue(tools.all { it.packageName == AgentInternalTools.INTERNAL_TOOL_PACKAGE })
        }

    @Test
    fun `catalog lists label, package and summary per app with a fallback for missing instructions`() =
        runTest {
            val description = toolDiscoveryTools.buildTools(apps).first().description

            assertTrue(description.contains("- Notes ($NOTES_PKG): Manage notes, e.g. shopping lists."))
            // The rest of the long instructions is not disclosed at L0.
            assertFalse(description.contains("deletion cannot be undone"))
            assertTrue(description.contains("- Chat ($CHAT_PKG): Functions: sendMessage"))
            // Usage guidance lives in the tool description rather than in the provider.
            assertTrue(description.contains(EXPAND_FUNCTION_DECLARATIONS))
        }

    @Test
    fun `expand_function_declarations is scoped to one app`() =
        runTest {
            val expandFunctions = toolDiscoveryTools.buildTools(apps)[1]

            assertEquals(
                listOf(ToolDiscoveryTools.PARAM_PACKAGE_NAME, ToolDiscoveryTools.PARAM_FUNCTION_IDS),
                expandFunctions.parameters.map { it.name },
            )
            assertTrue(expandFunctions.parameters[1].dataType is AppFunctionArrayTypeMetadata)
        }

    @Test
    fun `buildTools returns nothing when there are no apps`() =
        runTest {
            assertEquals(emptyList<AppFunctionMetadata>(), toolDiscoveryTools.buildTools(emptyMap()))
        }

    @Test
    fun `expand_tool_description returns full instructions and one-line functions without schemas`() =
        runTest {
            val tool = toolDiscoveryTools.buildTools(apps)[0]

            val result = toolDiscoveryTools.execute(tool, data(tool, packageName = NOTES_PKG), apps)

            val json = (result as ExecuteAppFunctionResult.Data).json()
            assertEquals("Notes", json["app_name"]!!.jsonPrimitive.content)
            assertTrue(json["instructions"]!!.jsonPrimitive.content.contains("cannot be undone"))
            val functions = json["functions"]!!.jsonArray.map { it.jsonObject }
            assertEquals(
                listOf("createNote", "deleteNote"),
                functions.map { it["function_id"]!!.jsonPrimitive.content },
            )
            assertEquals(
                listOf("Creates a new note. Supports checklists.", "Deletes a note. Irreversible."),
                functions.map { it["description"]!!.jsonPrimitive.content },
            )
            assertTrue(functions.all { it.keys == setOf("function_id", "description") })
        }

    @Test
    fun `expand_tool_description for an unknown app returns a recoverable error`() =
        runTest {
            val tool = toolDiscoveryTools.buildTools(apps)[0]

            val result = toolDiscoveryTools.execute(tool, data(tool, packageName = "com.unknown"), apps)

            assertNotNull((result as ExecuteAppFunctionResult.Data).json()["error"])
        }

    @Test
    fun `expand_function_declarations returns the matched tools of that app only`() =
        runTest {
            val tool = toolDiscoveryTools.buildTools(apps)[1]

            val result =
                toolDiscoveryTools.execute(
                    tool,
                    data(
                        tool,
                        packageName = NOTES_PKG,
                        functionIds = listOf("createNote", "sendMessage", "missing"),
                    ),
                    apps,
                )

            val discovered = result as ExecuteAppFunctionResult.ToolsDiscovered
            // sendMessage belongs to another app, so it is not loaded for NOTES_PKG.
            assertEquals(listOf(createNote), discovered.tools)
            val json = Json.parseToJsonElement(discovered.formattedJson).jsonObject
            assertEquals(
                listOf("sendMessage", "missing"),
                json["not_found"]!!.jsonArray.map { it.jsonPrimitive.content },
            )
        }

    private fun ExecuteAppFunctionResult.Data.json(): JsonObject = Json.parseToJsonElement(formattedJson).jsonObject

    private fun mockApp(
        packageMetadata: AppFunctionPackageMetadata,
        label: String,
        instructions: String?,
    ) {
        val packageName = packageMetadata.packageName
        Shadows.shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo =
                    ApplicationInfo().apply {
                        this.packageName = packageName
                        nonLocalizedLabel = label
                    }
            },
        )
        every { packageMetadata.resolveAppFunctionAppMetadata(context) } returns
            instructions?.let { AppFunctionAppMetadata(instructions = it, displayDescription = label) }
    }

    private fun data(
        tool: AppFunctionMetadata,
        packageName: String,
        functionIds: List<String>? = null,
    ): AppFunctionData {
        val builder =
            AppFunctionData.Builder(tool.parameters, AppFunctionComponentsMetadata())
                .setString(ToolDiscoveryTools.PARAM_PACKAGE_NAME, packageName)
        if (functionIds != null) {
            builder.setStringList(ToolDiscoveryTools.PARAM_FUNCTION_IDS, functionIds)
        }
        return builder.build()
    }

    private fun packageMetadata(packageName: String): AppFunctionPackageMetadata =
        mockk<AppFunctionPackageMetadata>(relaxed = true).apply {
            every { this@apply.packageName } returns packageName
            every { this@apply.components } returns AppFunctionComponentsMetadata()
        }

    private fun function(
        packageMetadata: AppFunctionPackageMetadata,
        id: String,
        description: String,
    ) = AppFunctionMetadata(
        name = AppFunctionName(packageMetadata.packageName, id),
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
        packageMetadata = packageMetadata,
    )

    private companion object {
        const val NOTES_PKG = "com.example.notes"
        const val CHAT_PKG = "com.example.chat"
    }
}
