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
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.AppFunctionState
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import androidx.test.core.app.ApplicationProvider
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.domain.appfunction.ConvertInputToAppFunctionDataUseCase
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionResult
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionStatesUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsParams
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.appfunction.GetInstalledAppsUseCase
import com.example.appfunctions.agent.domain.pendingintent.LaunchPendingIntentUseCase
import com.example.appfunctions.agent.domain.troubleshoot.TroubleshootAppUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val testAppPackage =
        AppFunctionPackageMetadata(
            packageName = TEST_PACKAGE_NAME,
            components = AppFunctionComponentsMetadata(),
        )
    private val testApp = AppInfo(TEST_PACKAGE_NAME, TEST_PACKAGE_NAME, null)

    /** The functions of all apps. Emit again to change them at runtime. */
    private val appFunctionsFlow =
        MutableSharedFlow<Map<AppFunctionPackageMetadata, List<AppFunctionMetadata>>>(replay = 1)

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
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns
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
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns
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
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns
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
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns
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
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns
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
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns
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
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns
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
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns
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
                mapOf(
                    enabledFunctionName to AppFunctionStatus(isEnabled = true),
                    disabledFunctionName to AppFunctionStatus(isEnabled = false),
                ),
                functionsState.functionStatuses,
            )
        }

    @Test
    fun `function disabled at runtime shows as disabled without selecting the app again`() =
        runTest {
            val function = createFunction("test_function")
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns appFunctionsFlow
            appFunctionsFlow.emit(mapOf(testAppPackage to listOf(function)))
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onAppSelected(testApp)
            assertEquals(
                mapOf(function.name to AppFunctionStatus(isEnabled = true)),
                functionsFoundState().functionStatuses,
            )

            coEvery { mockGetAppFunctionStatesUseCase(any()) } returns
                listOf(createState(function.name, isEnabled = false))
            appFunctionsFlow.emit(mapOf(testAppPackage to listOf(function)))
            advanceUntilIdle()

            assertEquals(
                mapOf(function.name to AppFunctionStatus(isEnabled = false)),
                functionsFoundState().functionStatuses,
            )
        }

    @Test
    fun `functions added and removed at runtime are shown and inputs and expanded functions are kept`() =
        runTest {
            val keptFunction = createFunction("kept_function")
            val removedFunction = createFunction("removed_function")
            val addedFunction = createFunction("added_function")
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns appFunctionsFlow
            appFunctionsFlow.emit(mapOf(testAppPackage to listOf(keptFunction, removedFunction)))
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onAppSelected(testApp)
            val inputs = mapOf("param1" to "value1")
            viewModel.onFunctionInputsChange(keptFunction.id, inputs)
            viewModel.onFunctionExpandedChange(keptFunction.id, expanded = true)

            appFunctionsFlow.emit(mapOf(testAppPackage to listOf(keptFunction, addedFunction)))
            advanceUntilIdle()

            val functionsState = functionsFoundState()
            assertEquals(listOf(keptFunction, addedFunction), functionsState.functions)
            assertEquals(
                mapOf(
                    keptFunction.name to AppFunctionStatus(isEnabled = true),
                    addedFunction.name to AppFunctionStatus(isEnabled = true),
                ),
                functionsState.functionStatuses,
            )
            assertEquals(mapOf(keptFunction.id to inputs), functionsState.functionInputs)
            assertEquals(setOf(keptFunction.id), functionsState.expandedFunctions)
        }

    @Test
    fun `update without the selected app keeps its functions and statuses`() =
        runTest {
            val function = createFunction("test_function")
            val otherAppPackage =
                AppFunctionPackageMetadata(
                    packageName = "com.example.other",
                    components = AppFunctionComponentsMetadata(),
                )
            val otherAppFunction = createFunction("other_function", otherAppPackage)
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns appFunctionsFlow
            appFunctionsFlow.emit(mapOf(testAppPackage to listOf(function)))
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onAppSelected(testApp)

            // For example while the selected app is being reinstalled.
            appFunctionsFlow.emit(mapOf(otherAppPackage to listOf(otherAppFunction)))
            advanceUntilIdle()

            val functionsState = functionsFoundState()
            assertEquals(listOf(function), functionsState.functions)
            assertEquals(
                mapOf(function.name to AppFunctionStatus(isEnabled = true)),
                functionsState.functionStatuses,
            )
        }

    @Test
    fun `invokeFunction result keeps the statuses refreshed during the execution`() =
        runTest {
            val function = createFunction("test_function")
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns appFunctionsFlow
            appFunctionsFlow.emit(mapOf(testAppPackage to listOf(function)))
            every {
                mockConvertInputToAppFunctionDataUseCase(any(), any(), any())
            } returns Result.success(AppFunctionData.EMPTY)
            val executionStarted = CompletableDeferred<Unit>()
            val pendingResult = CompletableDeferred<ExecuteAppFunctionResult>()
            coEvery { mockExecuteAppFunctionUseCase(any(), any(), any()) } coAnswers {
                executionStarted.complete(Unit)
                pendingResult.await()
            }
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onAppSelected(testApp)

            viewModel.invokeFunction(function)
            // Inputs are converted on Dispatchers.Default, so wait for the execution to start.
            executionStarted.await()
            coEvery { mockGetAppFunctionStatesUseCase(any()) } returns
                listOf(createState(function.name, isEnabled = false))
            appFunctionsFlow.emit(mapOf(testAppPackage to listOf(function)))
            advanceUntilIdle()
            val result = ExecuteAppFunctionResult.Data(AppFunctionData.EMPTY, "{}")
            pendingResult.complete(result)
            advanceUntilIdle()

            val functionsState = functionsFoundState()
            assertEquals(result, functionsState.executionResult)
            assertEquals(function, functionsState.executedFunction)
            assertEquals(
                mapOf(function.name to AppFunctionStatus(isEnabled = false)),
                functionsState.functionStatuses,
            )
        }

    @Test
    fun `invokeFunction shows input conversion error`() =
        runTest {
            val function = createFunction("test_function")
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns flowOf(mapOf(testAppPackage to listOf(function)))
            every {
                mockConvertInputToAppFunctionDataUseCase(any(), any(), any())
            } returns Result.failure(IllegalArgumentException("Invalid input"))
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onAppSelected(testApp)
            val inputs = mapOf("param1" to "value1")
            viewModel.onFunctionInputsChange(function.id, inputs)

            viewModel.invokeFunction(function)
            // Inputs are converted on Dispatchers.Default, so wait for the result.
            val state =
                viewModel.uiState.first {
                    (it.searchAppResultState as? SearchAppResultState.FunctionsFoundState)
                        ?.executionResult != null
                }

            val functionsState = state.searchAppResultState as SearchAppResultState.FunctionsFoundState
            val error = functionsState.executionResult as ExecuteAppFunctionResult.Error
            assertEquals("Invalid input", error.exception.message)
            assertEquals(function, functionsState.executedFunction)
            assertEquals(inputs, functionsState.executedInputs)
        }

    @Test
    fun `onAppSelected shows activity-scoped function as dynamic and not runnable`() =
        runTest {
            val dynamicFunction =
                createFunction("dynamic_function", scope = AppFunctionMetadata.SCOPE_ACTIVITY)
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns flowOf(mapOf(testAppPackage to listOf(dynamicFunction)))
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onAppSelected(testApp)

            val status = functionsFoundState().functionStatuses.getValue(dynamicFunction.name)
            assertTrue(status.isDynamic)
            assertFalse(status.canRun)
        }

    @Test
    fun `refresh after clearing the selected app keeps the idle state`() =
        runTest {
            val function = createFunction("test_function")
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns appFunctionsFlow
            appFunctionsFlow.emit(mapOf(testAppPackage to listOf(function)))
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onAppSelected(testApp)
            viewModel.onClearSelectedApp()

            appFunctionsFlow.emit(mapOf(testAppPackage to listOf(function)))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.selectedApp)
            assertEquals(SearchAppResultState.Idle, state.searchAppResultState)
        }

    @Test
    fun `launchPendingIntent shows error on failure and clears it on success`() =
        runTest {
            val function = createFunction("test_function")
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns flowOf(mapOf(testAppPackage to listOf(function)))
            val pendingIntent = mockk<PendingIntent>()
            every { mockLaunchPendingIntentUseCase(pendingIntent) } returns
                Result.failure(IllegalStateException("No activity found"))
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onAppSelected(testApp)

            viewModel.launchPendingIntent(pendingIntent)
            advanceUntilIdle()

            val error = functionsFoundState().executionResult as ExecuteAppFunctionResult.Error
            assertEquals("No activity found", error.exception.message)

            every { mockLaunchPendingIntentUseCase(pendingIntent) } returns Result.success(Unit)
            viewModel.launchPendingIntent(pendingIntent)
            advanceUntilIdle()

            assertNull(functionsFoundState().executionResult)
        }

    @Test
    fun `onFunctionExpandedChange collapses an expanded function`() =
        runTest {
            val firstFunction = createFunction("first_function")
            val secondFunction = createFunction("second_function")
            every {
                mockGetAppFunctionsUseCase(GetAppFunctionsParams(includeInternalTools = false))
            } returns flowOf(mapOf(testAppPackage to listOf(firstFunction, secondFunction)))
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onAppSelected(testApp)
            viewModel.onFunctionExpandedChange(firstFunction.id, expanded = true)
            viewModel.onFunctionExpandedChange(secondFunction.id, expanded = true)

            viewModel.onFunctionExpandedChange(firstFunction.id, expanded = false)

            assertEquals(setOf(secondFunction.id), functionsFoundState().expandedFunctions)
        }

    private fun createViewModel(): DebuggingViewModel =
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

    private fun createFunction(
        id: String,
        packageMetadata: AppFunctionPackageMetadata = testAppPackage,
        scope: Int = AppFunctionMetadata.SCOPE_GLOBAL,
    ): AppFunctionMetadata =
        AppFunctionMetadata(
            name = AppFunctionName(packageMetadata.packageName, id),
            schema = null,
            parameters = emptyList(),
            response = AppFunctionResponseMetadata(valueType = AppFunctionStringTypeMetadata(isNullable = false)),
            packageMetadata = packageMetadata,
            scope = scope,
        )

    private fun createState(
        name: AppFunctionName,
        isEnabled: Boolean,
    ): AppFunctionState =
        mockk<AppFunctionState>().apply {
            every { functionName } returns name
            every { this@apply.isEnabled } returns isEnabled
        }

    private fun functionsFoundState(): SearchAppResultState.FunctionsFoundState =
        viewModel.uiState.value.searchAppResultState as SearchAppResultState.FunctionsFoundState

    private companion object {
        const val TEST_PACKAGE_NAME = "com.example.app"
    }
}
