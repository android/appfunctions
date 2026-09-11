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
package com.example.appfunctions.agent.ui.screens.debugging

import android.app.PendingIntent
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.appfunctions.AppFunctionState
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionName
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.test.core.app.ApplicationProvider
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.domain.appfunction.ConvertInputToAppFunctionDataUseCase
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionStatesUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.appfunction.GetInstalledAppsUseCase
import com.example.appfunctions.agent.domain.pendingintent.LaunchPendingIntentUseCase
import com.example.appfunctions.agent.domain.troubleshoot.TroubleshootAppUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.collections.emptyList

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebuggingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockGetAppFunctionsUseCase: GetAppFunctionsUseCase
    private lateinit var mockGetAppFunctionStatesUseCase: GetAppFunctionStatesUseCase
    private lateinit var mockConvertInputToAppFunctionDataUseCase:
        ConvertInputToAppFunctionDataUseCase
    private lateinit var mockExecuteAppFunctionUseCase: ExecuteAppFunctionUseCase
    private lateinit var mockGetInstalledAppsUseCase: GetInstalledAppsUseCase
    private lateinit var mockTroubleshootAppUseCase: TroubleshootAppUseCase
    private lateinit var mockLaunchPendingIntentUseCase: LaunchPendingIntentUseCase
    private lateinit var mockSettingsRepository: SettingsRepository
    private lateinit var viewModel: DebuggingViewModel
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockGetAppFunctionsUseCase = mockk()
        mockGetAppFunctionStatesUseCase = mockk()
        mockConvertInputToAppFunctionDataUseCase = mockk()
        mockExecuteAppFunctionUseCase = mockk()
        mockGetInstalledAppsUseCase = mockk()
        mockTroubleshootAppUseCase = mockk()
        mockLaunchPendingIntentUseCase = mockk()
        mockSettingsRepository = mockk()
        context = ApplicationProvider.getApplicationContext()

        coEvery { mockGetAppFunctionStatesUseCase(any()) } answers {
            val names = firstArg<List<AppFunctionName>>()
            names.map { name ->
                val state = mockk<AppFunctionState>()
                every { state.functionName } returns name
                every { state.isEnabled } returns true
                state
            }
        }
        every { mockGetInstalledAppsUseCase() } returns emptyList()
        every { mockSettingsRepository.pinnedApps } returns flowOf(emptySet())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads apps`() =
        runTest {
            val mockMetadata =
                mockk<AppFunctionMetadata>(relaxed = true) {
                    every { name } returns AppFunctionName("com.example.app", "test_function")
                    every { id } returns "test_function"
                }
            val mockPackageMetadata = mockk<AppFunctionPackageMetadata>()
            every { mockPackageMetadata.packageName } returns "com.example.app"
            val expectedAppInfo = AppInfo("com.example.app", "com.example.app", null)
            every { mockGetAppFunctionsUseCase() } returns
                flowOf(mapOf(mockPackageMetadata to listOf(mockMetadata)))
            every { mockGetInstalledAppsUseCase() } returns listOf(expectedAppInfo)

            viewModel =
                DebuggingViewModel(
                    mockGetAppFunctionsUseCase,
                    mockGetAppFunctionStatesUseCase,
                    mockConvertInputToAppFunctionDataUseCase,
                    mockExecuteAppFunctionUseCase,
                    mockGetInstalledAppsUseCase,
                    mockTroubleshootAppUseCase,
                    mockLaunchPendingIntentUseCase,
                    mockSettingsRepository,
                    context,
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(
                listOf(expectedAppInfo),
                state.filteredApps.sections.find { it.titleRes == R.string.debugging_supported }?.apps,
            )
        }

    @Test
    fun `initial state loads apps with resolved metadata`() =
        runTest {
            val mockMetadata =
                mockk<AppFunctionMetadata>(relaxed = true) {
                    every { name } returns AppFunctionName("com.example.app", "test_function")
                    every { id } returns "test_function"
                }
            val mockPackageMetadata = mockk<AppFunctionPackageMetadata>()
            val packageName = "com.example.app.resolved"
            every { mockPackageMetadata.packageName } returns packageName
            every { mockGetAppFunctionsUseCase() } returns
                flowOf(mapOf(mockPackageMetadata to listOf(mockMetadata)))

            val pm = context.packageManager
            val shadowPm = Shadows.shadowOf(pm)

            val appInfo =
                ApplicationInfo().apply {
                    this.packageName = packageName
                    this.nonLocalizedLabel = "Resolved App Name"
                }
            val packageInfo =
                PackageInfo().apply {
                    this.packageName = packageName
                    this.applicationInfo = appInfo
                }
            shadowPm.addPackage(packageInfo)

            val drawable = ColorDrawable(Color.RED)
            shadowPm.setApplicationIcon(packageName, drawable)

            val expectedAppInfo = AppInfo(packageName, "Resolved App Name", ColorDrawable(Color.RED))
            every { mockGetInstalledAppsUseCase() } returns listOf(expectedAppInfo)

            viewModel =
                DebuggingViewModel(
                    mockGetAppFunctionsUseCase,
                    mockGetAppFunctionStatesUseCase,
                    mockConvertInputToAppFunctionDataUseCase,
                    mockExecuteAppFunctionUseCase,
                    mockGetInstalledAppsUseCase,
                    mockTroubleshootAppUseCase,
                    mockLaunchPendingIntentUseCase,
                    mockSettingsRepository,
                    context,
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val supportedApps =
                state.filteredApps.sections.find { it.titleRes == R.string.debugging_supported }?.apps
                    ?: emptyList()
            assertEquals(1, supportedApps.size)
            val resolvedAppInfo = supportedApps.first()
            assertEquals(packageName, resolvedAppInfo.packageName)
            assertEquals("Resolved App Name", resolvedAppInfo.label)

            val resolvedIcon = checkNotNull(resolvedAppInfo.icon as? ColorDrawable)
            assertEquals(Color.RED, resolvedIcon.color)
        }

    @Test
    fun `onSearchQueryChanged filters apps`() =
        runTest {
            val mockMetadata =
                mockk<AppFunctionMetadata>(relaxed = true) {
                    every { name } returns AppFunctionName("com.example.app", "test_function")
                    every { id } returns "test_function"
                }
            val mockPackageMetadata1 = mockk<AppFunctionPackageMetadata>()
            every { mockPackageMetadata1.packageName } returns "com.example.app1"
            val mockPackageMetadata2 = mockk<AppFunctionPackageMetadata>()
            every { mockPackageMetadata2.packageName } returns "com.example.app2"
            every { mockGetAppFunctionsUseCase() } returns
                flowOf(
                    mapOf(
                        mockPackageMetadata1 to listOf(mockMetadata),
                        mockPackageMetadata2 to listOf(mockMetadata),
                    ),
                )

            val expectedAppInfo1 = AppInfo("com.example.app1", "com.example.app1", null)
            val expectedAppInfo2 = AppInfo("com.example.app2", "com.example.app2", null)
            every { mockGetInstalledAppsUseCase() } returns listOf(expectedAppInfo1, expectedAppInfo2)

            viewModel =
                DebuggingViewModel(
                    mockGetAppFunctionsUseCase,
                    mockGetAppFunctionStatesUseCase,
                    mockConvertInputToAppFunctionDataUseCase,
                    mockExecuteAppFunctionUseCase,
                    mockGetInstalledAppsUseCase,
                    mockTroubleshootAppUseCase,
                    mockLaunchPendingIntentUseCase,
                    mockSettingsRepository,
                    context,
                )
            advanceUntilIdle()

            viewModel.onSearchQueryChanged("app1")

            val state = viewModel.uiState.value
            assertEquals(
                listOf(expectedAppInfo1),
                state.filteredApps.sections.find { it.titleRes == R.string.debugging_supported }?.apps,
            )
        }

    @Test
    fun `onAppSelected updates selected app and functions`() =
        runTest {
            val mockMetadata =
                mockk<AppFunctionMetadata>(relaxed = true) {
                    every { name } returns AppFunctionName("com.example.app", "test_function")
                    every { id } returns "test_function"
                }
            val mockPackageMetadata = mockk<AppFunctionPackageMetadata>()
            every { mockPackageMetadata.packageName } returns "com.example.app"
            every { mockGetAppFunctionsUseCase() } returns
                flowOf(mapOf(mockPackageMetadata to listOf(mockMetadata)))

            viewModel =
                DebuggingViewModel(
                    mockGetAppFunctionsUseCase,
                    mockGetAppFunctionStatesUseCase,
                    mockConvertInputToAppFunctionDataUseCase,
                    mockExecuteAppFunctionUseCase,
                    mockGetInstalledAppsUseCase,
                    mockTroubleshootAppUseCase,
                    mockLaunchPendingIntentUseCase,
                    mockSettingsRepository,
                    context,
                )
            advanceUntilIdle()

            val appInfo = AppInfo("com.example.app", "com.example.app", null)
            viewModel.onAppSelected(appInfo)

            val state = viewModel.uiState.value
            assertEquals(appInfo, state.selectedApp)
            val functionsState = state.searchAppResultState as SearchAppResultState.FunctionsFoundState
            assertEquals(listOf(mockMetadata), functionsState.functions)
        }

    @Test
    fun `onClearSelectedApp clears selected app and functions`() =
        runTest {
            val mockMetadata =
                mockk<AppFunctionMetadata>(relaxed = true) {
                    every { name } returns AppFunctionName("com.example.app", "test_function")
                    every { id } returns "test_function"
                }
            val mockPackageMetadata = mockk<AppFunctionPackageMetadata>()
            every { mockPackageMetadata.packageName } returns "com.example.app"
            every { mockGetAppFunctionsUseCase() } returns
                flowOf(mapOf(mockPackageMetadata to listOf(mockMetadata)))

            viewModel =
                DebuggingViewModel(
                    mockGetAppFunctionsUseCase,
                    mockGetAppFunctionStatesUseCase,
                    mockConvertInputToAppFunctionDataUseCase,
                    mockExecuteAppFunctionUseCase,
                    mockGetInstalledAppsUseCase,
                    mockTroubleshootAppUseCase,
                    mockLaunchPendingIntentUseCase,
                    mockSettingsRepository,
                    context,
                )
            advanceUntilIdle()

            val appInfo = AppInfo("com.example.app", "com.example.app", null)
            viewModel.onAppSelected(appInfo)

            val state = viewModel.uiState.value
            assertEquals(appInfo, state.selectedApp)
            val functionsState = state.searchAppResultState as SearchAppResultState.FunctionsFoundState
            assertEquals(listOf(mockMetadata), functionsState.functions)

            viewModel.onClearSelectedApp()

            val newState = viewModel.uiState.value
            assertEquals("", newState.searchQuery)

            assertEquals(null, newState.selectedApp)
            assertEquals(SearchAppResultState.Idle, newState.searchAppResultState)
        }

    @Test
    fun `onFunctionInputsChange updates state`() =
        runTest {
            val mockMetadata =
                mockk<AppFunctionMetadata>(relaxed = true) {
                    every { name } returns AppFunctionName("com.example.app", "test_function")
                    every { id } returns "test_function"
                }
            val mockPackageMetadata = mockk<AppFunctionPackageMetadata>()
            every { mockPackageMetadata.packageName } returns "com.example.app"
            every { mockGetAppFunctionsUseCase() } returns
                flowOf(mapOf(mockPackageMetadata to listOf(mockMetadata)))

            viewModel =
                DebuggingViewModel(
                    mockGetAppFunctionsUseCase,
                    mockGetAppFunctionStatesUseCase,
                    mockConvertInputToAppFunctionDataUseCase,
                    mockExecuteAppFunctionUseCase,
                    mockGetInstalledAppsUseCase,
                    mockTroubleshootAppUseCase,
                    mockLaunchPendingIntentUseCase,
                    mockSettingsRepository,
                    context,
                )
            advanceUntilIdle()

            val appInfo = AppInfo("com.example.app", "com.example.app", null)
            viewModel.onAppSelected(appInfo)

            val functionId = "test_function"
            val inputs = mapOf("param1" to "value1")
            viewModel.onFunctionInputsChange(functionId, inputs)

            val state = viewModel.uiState.value
            val functionsState = state.searchAppResultState as SearchAppResultState.FunctionsFoundState
            assertEquals(mapOf(functionId to inputs), functionsState.functionInputs)
        }

    @Test
    fun `launchPendingIntent calls use case and clears result on success`() =
        runTest {
            val mockMetadata =
                mockk<AppFunctionMetadata>(relaxed = true) {
                    every { name } returns AppFunctionName("com.example.app", "test_function")
                    every { id } returns "test_function"
                }
            val mockPackageMetadata = mockk<AppFunctionPackageMetadata>()
            every { mockPackageMetadata.packageName } returns "com.example.app"
            every { mockGetAppFunctionsUseCase() } returns
                flowOf(mapOf(mockPackageMetadata to listOf(mockMetadata)))

            val pendingIntent = mockk<PendingIntent>()
            every { mockLaunchPendingIntentUseCase(pendingIntent) } returns Result.success(Unit)

            viewModel =
                DebuggingViewModel(
                    mockGetAppFunctionsUseCase,
                    mockGetAppFunctionStatesUseCase,
                    mockConvertInputToAppFunctionDataUseCase,
                    mockExecuteAppFunctionUseCase,
                    mockGetInstalledAppsUseCase,
                    mockTroubleshootAppUseCase,
                    mockLaunchPendingIntentUseCase,
                    mockSettingsRepository,
                    context,
                )
            advanceUntilIdle()

            val appInfo = AppInfo("com.example.app", "com.example.app", null)
            viewModel.onAppSelected(appInfo)

            viewModel.launchPendingIntent(pendingIntent)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val functionsState = state.searchAppResultState as SearchAppResultState.FunctionsFoundState
            assertEquals(null, functionsState.executionResult)
        }

    @Test
    fun `onAppSelected populates enabled states from use case`() =
        runTest {
            val enabledFunctionName = AppFunctionName("com.example.app", "enabled_function")
            val disabledFunctionName = AppFunctionName("com.example.app", "disabled_function")
            val missingFunctionName = AppFunctionName("com.example.app", "missing_function")

            val mockEnabledMetadata =
                mockk<AppFunctionMetadata>(relaxed = true) {
                    every { name } returns enabledFunctionName
                    every { id } returns "enabled_function"
                }
            val mockDisabledMetadata =
                mockk<AppFunctionMetadata>(relaxed = true) {
                    every { name } returns disabledFunctionName
                    every { id } returns "disabled_function"
                }
            val mockMissingMetadata =
                mockk<AppFunctionMetadata>(relaxed = true) {
                    every { name } returns missingFunctionName
                    every { id } returns "missing_function"
                }

            val mockPackageMetadata = mockk<AppFunctionPackageMetadata>()
            every { mockPackageMetadata.packageName } returns "com.example.app"
            every { mockGetAppFunctionsUseCase() } returns
                flowOf(
                    mapOf(
                        mockPackageMetadata to
                            listOf(mockEnabledMetadata, mockDisabledMetadata, mockMissingMetadata),
                    ),
                )

            coEvery { mockGetAppFunctionStatesUseCase(any()) } answers {
                val names = firstArg<List<AppFunctionName>>()
                names.mapNotNull { name ->
                    when (name) {
                        enabledFunctionName ->
                            mockk<AppFunctionState> {
                                every { functionName } returns name
                                every { isEnabled } returns true
                            }
                        disabledFunctionName ->
                            mockk<AppFunctionState> {
                                every { functionName } returns name
                                every { isEnabled } returns false
                            }
                        else -> null
                    }
                }
            }

            viewModel =
                DebuggingViewModel(
                    mockGetAppFunctionsUseCase,
                    mockGetAppFunctionStatesUseCase,
                    mockConvertInputToAppFunctionDataUseCase,
                    mockExecuteAppFunctionUseCase,
                    mockGetInstalledAppsUseCase,
                    mockTroubleshootAppUseCase,
                    mockLaunchPendingIntentUseCase,
                    mockSettingsRepository,
                    context,
                )
            advanceUntilIdle()

            viewModel.onAppSelected(AppInfo("com.example.app", "com.example.app", null))

            val state = viewModel.uiState.value
            val functionsState = state.searchAppResultState as SearchAppResultState.FunctionsFoundState

            assertEquals(
                mapOf(enabledFunctionName to true, disabledFunctionName to false),
                functionsState.enabledState,
            )
        }
}
