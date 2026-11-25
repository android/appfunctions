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
package com.example.appfunctions.agent.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.appfunctions.agent.FakeAppFunctionRule
import com.example.appfunctions.agent.awaitUntil
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionStatesUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GetAppFunctionStatesUseCaseTest {
    @get:Rule
    val fakeFunction = FakeAppFunctionRule()

    private val useCase by lazy { GetAppFunctionStatesUseCase(fakeFunction.appFunctionManager) }

    @Test
    fun invoke_returnsFakeFunctionState() =
        runBlocking {
            val result = useCase(listOf(fakeFunction.functionName))

            val found = result.find { it.functionName == fakeFunction.functionName }
            assertNotNull("Should find fakeFunction from ${fakeFunction.functionName}", found)
            assertTrue("The function should be enabled", checkNotNull(found).isEnabled)
        }

    @Test
    fun invoke_functionDisabledAtRuntime_returnsDisabledState() =
        runBlocking {
            fakeFunction.setEnabled(false)

            awaitUntil("the use case to report fakeFunction as disabled") {
                !useCase(listOf(fakeFunction.functionName)).single().isEnabled
            }
        }

    @Test
    fun invoke_functionReEnabledAtRuntime_returnsEnabledState() =
        runBlocking {
            fakeFunction.setEnabled(false)
            awaitUntil("the use case to report fakeFunction as disabled") {
                !useCase(listOf(fakeFunction.functionName)).single().isEnabled
            }

            fakeFunction.setEnabled(true)

            awaitUntil("the use case to report fakeFunction as enabled") {
                useCase(listOf(fakeFunction.functionName)).single().isEnabled
            }
        }
}
