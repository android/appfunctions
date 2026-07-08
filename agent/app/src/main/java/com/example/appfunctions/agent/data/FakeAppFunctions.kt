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

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import dagger.hilt.android.AndroidEntryPoint

/** Fake AppFunction service used for testing and verification. */
@RequiresApi(36)
@AndroidEntryPoint
@AppFunctionServiceEntryPoint(
    serviceName = "FakeAppFunctionService",
    appFunctionXmlFileName = "fake_app_function_service",
)
abstract class BaseFakeAppFunctionService : AppFunctionService() {
    /**
     * Execute a fake function for testing purposes.
     *
     * @param params The test parameters containing input data.
     * @return The test response containing output data.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun fakeFunction(
        params: FakeParams,
    ): FakeResponse {
        return FakeResponse("success")
    }

    /** Input parameters for fake function testing. */
    @AppFunctionSerializable(isDescribedByKDoc = true)
    data class FakeParams(
        /** The test input string. */
        val input: String,
    )

    /** Response data from fake function testing. */
    @AppFunctionSerializable(isDescribedByKDoc = true)
    data class FakeResponse(
        /** The test output string. */
        val output: String,
    )
}
