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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
    private val useCase = ExecuteAppFunctionUseCase(appFunctionManager, convertAppFunctionDataToJsonUseCase)


    @Test
    fun `invoke returns Error with original AppFunctionException when response is Error`() = runTest {
        val function = mockk<AppFunctionMetadata>(relaxed = true)
        every { function.packageName } returns "com.example.app"
        every { function.id } returns "test_function"
        val parameters = mockk<AppFunctionData>()

        val appException = AppFunctionPermissionRequiredException("Permission needed")
        coEvery { appFunctionManager.executeAppFunction(any()) } returns ExecuteAppFunctionResponse.Error(appException)

        val result = useCase(function, parameters)

        assertTrue(result is ExecuteAppFunctionResult.Error)
        val errorResult = result as ExecuteAppFunctionResult.Error
        assertEquals(appException, errorResult.exception)
    }

    @Test
    fun `invoke returns Error when executeAppFunction throws exception`() = runTest {
        val function = mockk<AppFunctionMetadata>(relaxed = true)
        every { function.packageName } returns "com.example.app"
        every { function.id } returns "test_function"
        val parameters = mockk<AppFunctionData>()

        val runtimeException = RuntimeException("Unexpected crash")
        coEvery { appFunctionManager.executeAppFunction(any()) } throws runtimeException

        val result = useCase(function, parameters)

        assertTrue(result is ExecuteAppFunctionResult.Error)
        val errorResult = result as ExecuteAppFunctionResult.Error
        assertEquals(runtimeException, errorResult.exception)
    }
}
