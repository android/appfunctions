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

import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import com.example.appfunctions.agent.data.AgentInternalTools
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExecuteAppFunctionUseCaseTest {
    private val appFunctionManager = mockk<AppFunctionManager>()
    private val convertAppFunctionDataToJsonUseCase = mockk<ConvertAppFunctionDataToJsonUseCase>()
    private val agentInternalTools = mockk<AgentInternalTools>(relaxed = true)
    private val grantAppFunctionUriPermissionsUseCase =
        mockk<GrantAppFunctionUriPermissionsUseCase>(relaxed = true)
    private val useCase =
        ExecuteAppFunctionUseCase(
            appFunctionManager = appFunctionManager,
            convertAppFunctionDataToJsonUseCase = convertAppFunctionDataToJsonUseCase,
            agentInternalTools = agentInternalTools,
            grantAppFunctionUriPermissionsUseCase = grantAppFunctionUriPermissionsUseCase,
        )

    @Test
    fun `invoke returns Error with original AppFunctionException when response is Error`() =
        runTest {
            val function = mockk<AppFunctionMetadata>(relaxed = true)
            every { function.packageName } returns "com.example.app"
            every { function.id } returns "test_function"
            every { function.parameters } returns emptyList()
            val parameters = mockk<AppFunctionData>()

            val appException = AppFunctionPermissionRequiredException("Permission needed")
            coEvery { appFunctionManager.executeAppFunction(any()) } returns
                ExecuteAppFunctionResponse.Error(appException)

            val result = useCase(function, parameters)

            assertTrue(result is ExecuteAppFunctionResult.Error)
            val errorResult = result as ExecuteAppFunctionResult.Error
            assertEquals(appException, errorResult.exception)
            verify(exactly = 1) { grantAppFunctionUriPermissionsUseCase(function, parameters) }
        }

    @Test
    fun `invoke returns Error when executeAppFunction throws exception`() =
        runTest {
            val function = mockk<AppFunctionMetadata>(relaxed = true)
            every { function.packageName } returns "com.example.app"
            every { function.id } returns "test_function"
            every { function.parameters } returns emptyList()
            val parameters = mockk<AppFunctionData>()

            val runtimeException = RuntimeException("Unexpected crash")
            coEvery { appFunctionManager.executeAppFunction(any()) } throws runtimeException

            val result = useCase(function, parameters)

            assertTrue(result is ExecuteAppFunctionResult.Error)
            val errorResult = result as ExecuteAppFunctionResult.Error
            assertEquals(runtimeException, errorResult.exception)
        }

    @Test
    fun `invoke grants URI permissions before executing external AppFunction`() =
        runTest {
            val function = mockk<AppFunctionMetadata>(relaxed = true)
            every { function.packageName } returns "com.example.notes"
            every { function.id } returns "attachMedia"
            every { function.response.valueType } returns AppFunctionStringTypeMetadata(false)
            val parameters = AppFunctionData.EMPTY

            coEvery { appFunctionManager.executeAppFunction(any()) } returns
                ExecuteAppFunctionResponse.Success(AppFunctionData.EMPTY)
            every {
                convertAppFunctionDataToJsonUseCase(any(), any(), any(), any())
            } returns """{"ok":true}"""

            val result = useCase(function, parameters)

            assertTrue(result is ExecuteAppFunctionResult.Data)
            assertEquals("""{"ok":true}""", (result as ExecuteAppFunctionResult.Data).formattedJson)
            verify(exactly = 1) { grantAppFunctionUriPermissionsUseCase(function, parameters) }
        }

    @Test
    fun `invoke delegates internal tools to AgentInternalTools even when AppFunctionManager is null`() =
        runTest {
            val nullManagerUseCase =
                ExecuteAppFunctionUseCase(
                    appFunctionManager = null,
                    convertAppFunctionDataToJsonUseCase = convertAppFunctionDataToJsonUseCase,
                    agentInternalTools = agentInternalTools,
                    grantAppFunctionUriPermissionsUseCase = grantAppFunctionUriPermissionsUseCase,
                )
            val function = mockk<AppFunctionMetadata>(relaxed = true)
            every { function.packageName } returns AgentInternalTools.INTERNAL_TOOL_PACKAGE
            every { function.id } returns "generateImage"
            val parameters = AppFunctionData.EMPTY
            val expectedResult =
                ExecuteAppFunctionResult.Data(
                    data = AppFunctionData.EMPTY,
                    formattedJson = """{"imageUri":"content://test/cat.png"}""",
                )
            coEvery {
                agentInternalTools.executeInternalTool(function, parameters)
            } returns expectedResult

            val result = nullManagerUseCase(function, parameters)

            assertEquals(expectedResult, result)
            coVerify(exactly = 1) { agentInternalTools.executeInternalTool(function, parameters) }
            verify(exactly = 0) { grantAppFunctionUriPermissionsUseCase(any(), any()) }
        }
}
