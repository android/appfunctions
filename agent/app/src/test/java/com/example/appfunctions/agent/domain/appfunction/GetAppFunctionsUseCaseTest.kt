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

import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.AppFunctionState
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import com.example.appfunctions.agent.data.AgentInternalTools
import com.example.appfunctions.agent.data.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetAppFunctionsUseCaseTest {
    private val appFunctionManager: AppFunctionManager = mockk()
    private val getAppFunctionStatesUseCase: GetAppFunctionStatesUseCase = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val agentInternalTools: AgentInternalTools = mockk()

    private val disconnectedAppsFlow = MutableStateFlow<Set<String>>(emptySet())

    private lateinit var useCase: GetAppFunctionsUseCase

    @Before
    fun setUp() {
        every { appFunctionManager.observeAppFunctions() } returns emptyFlow()
        every { settingsRepository.disconnectedApps } returns disconnectedAppsFlow
        every { agentInternalTools.getInternalToolsMetadata() } returns emptyList()
        coEvery { getAppFunctionStatesUseCase(any()) } answers {
            firstArg<List<AppFunctionName>>().map { name ->
                createState(name, isEnabled = true)
            }
        }

        useCase =
            GetAppFunctionsUseCase(
                appFunctionManager = appFunctionManager,
                getAppFunctionStatesUseCase = getAppFunctionStatesUseCase,
                settingsRepository = settingsRepository,
                agentInternalTools = agentInternalTools,
            )
    }

    @Test
    fun `default params return all external functions and internal tools without state or connection filtering`() =
        runTest {
            val notesPkg = createPackage("com.example.notes")
            val chatPkg = createPackage("com.example.chat")
            val internalPkg = createPackage(AgentInternalTools.INTERNAL_TOOL_PACKAGE)

            val enabledTool = createTool(notesPkg, "com.example.notes.createNote")
            val disabledTool = createTool(notesPkg, "com.example.notes.deleteNote")
            val disconnectedTool = createTool(chatPkg, "com.example.chat.sendMessage")
            val internalTool = createTool(internalPkg, "generateImage")

            disconnectedAppsFlow.value = setOf("com.example.chat")
            coEvery { appFunctionManager.searchAppFunctions(any()) } returns
                listOf(enabledTool, disabledTool, disconnectedTool)
            every { agentInternalTools.getInternalToolsMetadata() } returns
                listOf(internalTool to createState(internalTool.name, isEnabled = true))

            val result = useCase().first()

            assertEquals(listOf(enabledTool, disabledTool), result[notesPkg])
            assertEquals(listOf(disconnectedTool), result[chatPkg])
            assertEquals(listOf(internalTool), result[internalPkg])
            coVerify(exactly = 0) { getAppFunctionStatesUseCase(any()) }
        }

    @Test
    fun `includeInternalTools false excludes internal tools`() =
        runTest {
            val notesPkg = createPackage("com.example.notes")
            val internalPkg = createPackage(AgentInternalTools.INTERNAL_TOOL_PACKAGE)
            val notesTool = createTool(notesPkg, "createNote")
            val internalTool = createTool(internalPkg, "generateImage")

            coEvery { appFunctionManager.searchAppFunctions(any()) } returns listOf(notesTool)
            every { agentInternalTools.getInternalToolsMetadata() } returns
                listOf(internalTool to createState(internalTool.name, isEnabled = true))

            val result =
                useCase(GetAppFunctionsParams(includeInternalTools = false)).first()

            assertEquals(mapOf(notesPkg to listOf(notesTool)), result)
        }

    @Test
    fun `isEnabled and isConnected filters exclude disabled tools and disconnected apps while keeping internal tools`() =
        runTest {
            val notesPkg = createPackage("com.example.notes")
            val chatPkg = createPackage("com.example.chat")
            val internalPkg = createPackage(AgentInternalTools.INTERNAL_TOOL_PACKAGE)

            val enabledTool = createTool(notesPkg, "com.example.notes.createNote")
            val disabledTool = createTool(notesPkg, "com.example.notes.deleteNote")
            val disconnectedTool = createTool(chatPkg, "com.example.chat.sendMessage")
            val internalTool = createTool(internalPkg, "generateImage")

            disconnectedAppsFlow.value = setOf("com.example.chat")
            coEvery { appFunctionManager.searchAppFunctions(any()) } returns
                listOf(enabledTool, disabledTool, disconnectedTool)
            every { agentInternalTools.getInternalToolsMetadata() } returns
                listOf(internalTool to createState(internalTool.name, isEnabled = true))
            coEvery { getAppFunctionStatesUseCase(any()) } answers {
                firstArg<List<AppFunctionName>>().map { name ->
                    createState(name, isEnabled = name != disabledTool.name)
                }
            }

            val enabledConnected =
                useCase(
                    GetAppFunctionsParams(
                        isEnabled = true,
                        isConnected = true,
                        includeInternalTools = true,
                    ),
                ).first()

            assertEquals(
                mapOf(
                    notesPkg to listOf(enabledTool),
                    internalPkg to listOf(internalTool),
                ),
                enabledConnected,
            )

            val disabledOnly =
                useCase(GetAppFunctionsParams(isEnabled = false)).first()
            assertEquals(mapOf(notesPkg to listOf(disabledTool)), disabledOnly)

            val disconnectedOnly =
                useCase(GetAppFunctionsParams(isConnected = false)).first()
            assertEquals(mapOf(chatPkg to listOf(disconnectedTool)), disconnectedOnly)
        }

    @Test
    fun `targetPackageNames scopes returned functions strictly to requested packages`() =
        runTest {
            val notesPkg = createPackage("com.example.notes")
            val calendarPkg = createPackage("com.example.calendar")
            val internalPkg = createPackage(AgentInternalTools.INTERNAL_TOOL_PACKAGE)

            val notesTool = createTool(notesPkg, "createNote")
            val calendarTool = createTool(calendarPkg, "createEvent")
            val internalTool = createTool(internalPkg, "geocodeAddress")

            coEvery {
                appFunctionManager.searchAppFunctions(
                    match { it.packageNames == setOf("com.example.calendar") },
                )
            } returns listOf(calendarTool)
            every { agentInternalTools.getInternalToolsMetadata() } returns
                listOf(internalTool to createState(internalTool.name, isEnabled = true))

            val result =
                useCase(
                    GetAppFunctionsParams(
                        targetPackageNames = setOf("com.example.calendar"),
                        isEnabled = true,
                        isConnected = true,
                        includeInternalTools = true,
                    ),
                ).first()

            assertEquals(mapOf(calendarPkg to listOf(calendarTool)), result)
        }

    @Test
    fun `null AppFunctionManager still emits internal tools when includeInternalTools is true`() =
        runTest {
            val internalPkg = createPackage(AgentInternalTools.INTERNAL_TOOL_PACKAGE)
            val internalTool = createTool(internalPkg, "getCurrentLocation")
            every { agentInternalTools.getInternalToolsMetadata() } returns
                listOf(internalTool to createState(internalTool.name, isEnabled = true))

            val nullManagerUseCase =
                GetAppFunctionsUseCase(
                    appFunctionManager = null,
                    getAppFunctionStatesUseCase = getAppFunctionStatesUseCase,
                    settingsRepository = settingsRepository,
                    agentInternalTools = agentInternalTools,
                )

            val withInternal = nullManagerUseCase().first()
            assertEquals(mapOf(internalPkg to listOf(internalTool)), withInternal)

            val withoutInternal =
                nullManagerUseCase(GetAppFunctionsParams(includeInternalTools = false)).first()
            assertTrue(withoutInternal.isEmpty())
        }

    private fun createPackage(packageName: String): AppFunctionPackageMetadata =
        AppFunctionPackageMetadata(
            packageName = packageName,
            components = AppFunctionComponentsMetadata(),
        )

    private fun createTool(
        packageMetadata: AppFunctionPackageMetadata,
        id: String,
    ): AppFunctionMetadata =
        mockk<AppFunctionMetadata>(relaxed = true).apply {
            every { this@apply.packageName } returns packageMetadata.packageName
            every { this@apply.id } returns id
            every { this@apply.name } returns AppFunctionName(packageMetadata.packageName, id)
            every { this@apply.packageMetadata } returns packageMetadata
        }

    private fun createState(
        name: AppFunctionName,
        isEnabled: Boolean,
    ): AppFunctionState =
        mockk<AppFunctionState>().apply {
            every { functionName } returns name
            every { this@apply.isEnabled } returns isEnabled
        }
}
