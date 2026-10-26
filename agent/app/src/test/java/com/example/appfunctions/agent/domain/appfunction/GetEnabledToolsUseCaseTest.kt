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
import androidx.appfunctions.AppFunctionState
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import com.example.appfunctions.agent.data.AgentInternalTools
import com.example.appfunctions.agent.data.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetEnabledToolsUseCaseTest {
    private val settingsRepository: SettingsRepository = mockk()
    private val getAppFunctionsUseCase: GetAppFunctionsUseCase = mockk()
    private val getAppFunctionStatesUseCase: GetAppFunctionStatesUseCase = mockk()
    private val agentInternalTools: AgentInternalTools = mockk()

    private lateinit var useCase: GetEnabledToolsUseCase

    @Before
    fun setUp() {
        every { settingsRepository.disconnectedApps } returns flowOf(emptySet())
        every { agentInternalTools.getInternalToolsMetadata() } returns emptyList()
        coEvery { getAppFunctionStatesUseCase(any()) } answers {
            firstArg<List<AppFunctionName>>().map { name ->
                createState(name, isEnabled = true)
            }
        }

        useCase =
            GetEnabledToolsUseCase(
                settingsRepository = settingsRepository,
                getAppFunctionsUseCase = getAppFunctionsUseCase,
                getAppFunctionStatesUseCase = getAppFunctionStatesUseCase,
                agentInternalTools = agentInternalTools,
            )
    }

    @Test
    fun `invoke excludes disabled tools and disconnected apps while keeping internal tools`() =
        runTest {
            val enabledTool = createTool("com.example.notes", "com.example.notes.createNote")
            val disabledTool = createTool("com.example.notes", "com.example.notes.deleteNote")
            val disconnectedTool = createTool("com.example.chat", "com.example.chat.sendMessage")
            val internalTool =
                createTool(AgentInternalTools.INTERNAL_TOOL_PACKAGE, "generateImage")
            val internalState = createState(internalTool.name, isEnabled = true)

            val pkgMeta = mockk<AppFunctionPackageMetadata>(relaxed = true)
            every { getAppFunctionsUseCase() } returns
                flowOf(mapOf(pkgMeta to listOf(enabledTool, disabledTool, disconnectedTool)))
            every { settingsRepository.disconnectedApps } returns flowOf(setOf("com.example.chat"))
            every { agentInternalTools.getInternalToolsMetadata() } returns
                listOf(internalTool to internalState)
            coEvery { getAppFunctionStatesUseCase(any()) } answers {
                firstArg<List<AppFunctionName>>().map { name ->
                    createState(name, isEnabled = name != disabledTool.name)
                }
            }

            val result = useCase()

            assertEquals(listOf(enabledTool, internalTool), result)
        }

    @Test
    fun `invoke scopes returned tools to targetPackageName when specified`() =
        runTest {
            val notesTool = createTool("com.example.notes", "createNote")
            val calendarTool = createTool("com.example.calendar", "createEvent")
            val pkgMeta = mockk<AppFunctionPackageMetadata>(relaxed = true)
            every { getAppFunctionsUseCase() } returns
                flowOf(mapOf(pkgMeta to listOf(notesTool, calendarTool)))

            val result = useCase(targetPackageName = "com.example.calendar")

            assertEquals(listOf(calendarTool), result)
        }

    private fun createTool(
        packageName: String,
        id: String,
    ): AppFunctionMetadata =
        mockk<AppFunctionMetadata>(relaxed = true).apply {
            every { this@apply.packageName } returns packageName
            every { this@apply.id } returns id
            every { this@apply.name } returns AppFunctionName(packageName, id)
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
