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

import android.content.Context
import com.example.appfunctions.agent.ui.screens.agentdemo.FakeSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetConnectedAppsUseCaseTest {
    private lateinit var useCase: GetConnectedAppsUseCase
    private val mockGetAppFunctionsUseCase: GetAppFunctionsUseCase = mockk()
    private val mockGetInstalledAppsUseCase: GetInstalledAppsUseCase = mockk()
    private lateinit var fakeRepository: FakeSettingsRepository

    @Before
    fun setUp() {
        fakeRepository = FakeSettingsRepository()
        val mockContext: Context = mockk(relaxed = true)
        useCase =
            GetConnectedAppsUseCase(
                mockContext,
                mockGetAppFunctionsUseCase,
                mockGetInstalledAppsUseCase,
                fakeRepository,
            )
    }

    @Test
    fun invoke_filtersDisconnectedApps() =
        runTest {
            val app1Metadata =
                mockk<androidx.appfunctions.metadata.AppFunctionPackageMetadata>(relaxed = true)
            every { app1Metadata.packageName } returns "com.example.app1"
            val app2Metadata =
                mockk<androidx.appfunctions.metadata.AppFunctionPackageMetadata>(relaxed = true)
            every { app2Metadata.packageName } returns "com.example.app2"

            val appFunctionsMap =
                mapOf(
                    app1Metadata to emptyList<androidx.appfunctions.metadata.AppFunctionMetadata>(),
                    app2Metadata to emptyList<androidx.appfunctions.metadata.AppFunctionMetadata>(),
                )

            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns flowOf(appFunctionsMap)
            fakeRepository.setAppConnected("com.example.app2", false)

            val appInfo1 = AppInfo("com.example.app1", "App 1", null)
            val appInfo2 = AppInfo("com.example.app2", "App 2", null)
            every { mockGetInstalledAppsUseCase() } returns listOf(appInfo1, appInfo2)

            val result = useCase().first()

            assertEquals(2, result.size)
            assertEquals("com.example.app1", result[0].packageName)
            assertEquals(true, result[0].isConnected)
            assertEquals("com.example.app2", result[1].packageName)
            assertEquals(false, result[1].isConnected)
        }

    @Test
    fun invoke_fallsBackToPackageNameWhenAppInfoNotFound() =
        runTest {
            val app1Metadata =
                mockk<androidx.appfunctions.metadata.AppFunctionPackageMetadata>(relaxed = true)
            every { app1Metadata.packageName } returns "com.example.app1"

            val appFunctionsMap =
                mapOf(app1Metadata to emptyList<androidx.appfunctions.metadata.AppFunctionMetadata>())

            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns flowOf(appFunctionsMap)
            fakeRepository.setAppConnected("com.example.app1", true)

            every { mockGetInstalledAppsUseCase() } returns emptyList()

            val result = useCase().first()

            assertEquals(1, result.size)
            assertEquals("com.example.app1", result[0].packageName)
            assertEquals("com.example.app1", result[0].label)
            assertEquals(null, result[0].icon)
            assertEquals(true, result[0].isConnected)
        }
}
