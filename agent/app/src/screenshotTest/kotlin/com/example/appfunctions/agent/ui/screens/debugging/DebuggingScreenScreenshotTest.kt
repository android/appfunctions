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

import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionBooleanTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDoubleTypeMetadata
import androidx.appfunctions.metadata.AppFunctionFloatTypeMetadata
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionLongTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.domain.troubleshoot.StepStatus
import com.example.appfunctions.agent.domain.troubleshoot.TroubleshootReport
import com.example.appfunctions.agent.domain.troubleshoot.TroubleshootStep
import com.example.appfunctions.agent.ui.theme.AppFunctionsAgentTheme

/** Screenshot tests for [DebuggingScreen]. */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun DebuggingScreenInitialScreenshotPreview() {
    val dummyState =
        DebuggingUiState(
            filteredApps = AppsGroupState(),
            selectedApp = null,
        )
    AppFunctionsAgentTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            DebuggingScreenContent(
                uiState = dummyState,
                onSearchQueryChanged = {},
                onAppSelected = {},
                onClearSelectedApp = {},
                onFunctionInputsChange = { _, _ -> },
                onInvoke = {},
                onClearResult = {},
                onFunctionExpandedChange = { _, _ -> },
                onLaunchPendingIntent = {},
                onTogglePin = {},
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun DebuggingScreenAppSelectedScreenshotPreview() {
    val app = AppInfo("com.example.test", "Test App", null)
    val function = createFakeFunction()
    val dummyState =
        DebuggingUiState(
            filteredApps =
                AppsGroupState(
                    sections = listOf(AppSection(R.string.debugging_supported, listOf(app), true)),
                ),
            selectedApp = app,
            searchAppResultState =
                SearchAppResultState.FunctionsFoundState(
                    functions = listOf(function),
                    enabledState = mapOf(function.name to true),
                ),
        )
    AppFunctionsAgentTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            DebuggingScreenContent(
                uiState = dummyState,
                onSearchQueryChanged = {},
                onAppSelected = {},
                onClearSelectedApp = {},
                onFunctionInputsChange = { _, _ -> },
                onInvoke = {},
                onClearResult = {},
                onFunctionExpandedChange = { _, _ -> },
                onLaunchPendingIntent = {},
                onTogglePin = {},
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun DebuggingScreenFunctionExpandedScreenshotPreview() {
    val app = AppInfo("com.example.test", "Test App", null)
    val function = createFakeFunction()
    val dummyState =
        DebuggingUiState(
            filteredApps =
                AppsGroupState(
                    sections = listOf(AppSection(R.string.debugging_supported, listOf(app), true)),
                ),
            selectedApp = app,
            searchAppResultState =
                SearchAppResultState.FunctionsFoundState(
                    functions = listOf(function),
                    enabledState = mapOf(function.name to true),
                    expandedFunctions = setOf(function.id),
                ),
        )
    AppFunctionsAgentTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            DebuggingScreenContent(
                uiState = dummyState,
                onSearchQueryChanged = {},
                onAppSelected = {},
                onClearSelectedApp = {},
                onFunctionInputsChange = { _, _ -> },
                onInvoke = {},
                onClearResult = {},
                onFunctionExpandedChange = { _, _ -> },
                onLaunchPendingIntent = {},
                onTogglePin = {},
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun DebuggingScreenFunctionExpandedWithInputsScreenshotPreview() {
    val app = AppInfo("com.example.test", "Test App", null)
    val function = createFakeFunction()
    val dummyState =
        DebuggingUiState(
            filteredApps =
                AppsGroupState(
                    sections = listOf(AppSection(R.string.debugging_supported, listOf(app), true)),
                ),
            selectedApp = app,
            searchAppResultState =
                SearchAppResultState.FunctionsFoundState(
                    functions = listOf(function),
                    enabledState = mapOf(function.name to true),
                    expandedFunctions = setOf(function.id),
                    functionInputs =
                        mapOf(
                            function.id to
                                mapOf(
                                    "param_string" to "pre-filled string",
                                    "param_int" to "123",
                                    "param_long" to "456",
                                    "param_boolean" to true,
                                    "param_double" to "3.14",
                                    "param_float" to "1.5",
                                    "param_object" to mapOf("prop1" to "obj_val"),
                                    "param_array" to listOf("arr1", "arr2"),
                                    "param_reference" to mapOf("prop1" to "ref_val"),
                                ),
                        ),
                ),
        )
    AppFunctionsAgentTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            DebuggingScreenContent(
                uiState = dummyState,
                onSearchQueryChanged = {},
                onAppSelected = {},
                onClearSelectedApp = {},
                onFunctionInputsChange = { _, _ -> },
                onInvoke = {},
                onClearResult = {},
                onFunctionExpandedChange = { _, _ -> },
                onLaunchPendingIntent = {},
                onTogglePin = {},
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun DebuggingScreenFunctionWithEnumScreenshotPreview() {
    val app = AppInfo("com.example.test", "Test App", null)
    val stringEnumType =
        AppFunctionStringTypeMetadata(
            isNullable = false,
            enumValues = setOf("Option 1", "Option 2", "Option 3"),
        )
    val intEnumType = AppFunctionIntTypeMetadata(isNullable = false, enumValues = setOf(1, 2, 3))
    val function =
        AppFunctionMetadata(
            id = "enum_function",
            packageName = "com.example.test",
            isEnabled = true,
            schema = null,
            parameters =
                listOf(
                    AppFunctionParameterMetadata(
                        name = "param_string_enum",
                        isRequired = true,
                        dataType = stringEnumType,
                        description = "String Enum param",
                    ),
                    AppFunctionParameterMetadata(
                        name = "param_int_enum",
                        isRequired = true,
                        dataType = intEnumType,
                        description = "Int Enum param",
                    ),
                ),
            response =
                AppFunctionResponseMetadata(
                    valueType = AppFunctionStringTypeMetadata(isNullable = false),
                    description = "Returns a string",
                ),
            components = AppFunctionComponentsMetadata(emptyMap()),
            description = "Function with enum parameters",
            deprecation = null,
        )
    val dummyState =
        DebuggingUiState(
            filteredApps =
                AppsGroupState(
                    sections = listOf(AppSection(R.string.debugging_supported, listOf(app), true)),
                ),
            selectedApp = app,
            searchAppResultState =
                SearchAppResultState.FunctionsFoundState(
                    functions = listOf(function),
                    enabledState = mapOf(function.name to true),
                    expandedFunctions = setOf(function.id),
                ),
        )
    AppFunctionsAgentTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            DebuggingScreenContent(
                uiState = dummyState,
                onSearchQueryChanged = {},
                onAppSelected = {},
                onClearSelectedApp = {},
                onFunctionInputsChange = { _, _ -> },
                onInvoke = {},
                onClearResult = {},
                onFunctionExpandedChange = { _, _ -> },
                onLaunchPendingIntent = {},
                onTogglePin = {},
            )
        }
    }
}

private fun createFakeFunction(): AppFunctionMetadata {
    val stringType = AppFunctionStringTypeMetadata(isNullable = false)
    val intType = AppFunctionIntTypeMetadata(isNullable = false)
    val longType = AppFunctionLongTypeMetadata(isNullable = false)
    val booleanType = AppFunctionBooleanTypeMetadata(isNullable = false)
    val doubleType = AppFunctionDoubleTypeMetadata(isNullable = false)
    val floatType = AppFunctionFloatTypeMetadata(isNullable = false)

    val objectType =
        AppFunctionObjectTypeMetadata(
            properties = mapOf("prop1" to stringType),
            required = listOf("prop1"),
            qualifiedName = null,
            isNullable = false,
        )

    val arrayType = AppFunctionArrayTypeMetadata(itemType = stringType, isNullable = false)

    val referenceType =
        AppFunctionReferenceTypeMetadata(referenceDataType = "CustomType", isNullable = false)

    val response =
        AppFunctionResponseMetadata(
            valueType = stringType,
            description = "Returns a string",
        )

    val components = AppFunctionComponentsMetadata(mapOf("CustomType" to objectType))

    val parameters =
        listOf(
            AppFunctionParameterMetadata(
                name = "param_string",
                isRequired = true,
                dataType = stringType,
                description = "String param",
            ),
            AppFunctionParameterMetadata(
                name = "param_int",
                isRequired = true,
                dataType = intType,
                description = "Int param",
            ),
            AppFunctionParameterMetadata(
                name = "param_long",
                isRequired = true,
                dataType = longType,
                description = "Long param",
            ),
            AppFunctionParameterMetadata(
                name = "param_boolean",
                isRequired = true,
                dataType = booleanType,
                description = "Boolean param",
            ),
            AppFunctionParameterMetadata(
                name = "param_double",
                isRequired = true,
                dataType = doubleType,
                description = "Double param",
            ),
            AppFunctionParameterMetadata(
                name = "param_float",
                isRequired = true,
                dataType = floatType,
                description = "Float param",
            ),
            AppFunctionParameterMetadata(
                name = "param_object",
                isRequired = true,
                dataType = objectType,
                description = "Object param",
            ),
            AppFunctionParameterMetadata(
                name = "param_array",
                isRequired = true,
                dataType = arrayType,
                description = "Array param",
            ),
            AppFunctionParameterMetadata(
                name = "param_reference",
                isRequired = true,
                dataType = referenceType,
                description = "Reference param",
            ),
        )

    return AppFunctionMetadata(
        id = "test_function",
        packageName = "com.example.test",
        isEnabled = true,
        schema = null,
        parameters = parameters,
        response = response,
        components = components,
        description = "Test function description",
        deprecation = null,
    )
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ObjectTypeInputScreenshotPreview() {
    val stringType = AppFunctionStringTypeMetadata(isNullable = false)
    val intType = AppFunctionIntTypeMetadata(isNullable = false)

    val objectType =
        AppFunctionObjectTypeMetadata(
            properties =
                mapOf(
                    "prop_string" to stringType,
                    "prop_int" to intType,
                    "prop_long" to AppFunctionLongTypeMetadata(isNullable = false),
                    "prop_boolean" to AppFunctionBooleanTypeMetadata(isNullable = false),
                    "prop_double" to AppFunctionDoubleTypeMetadata(isNullable = false),
                    "prop_float" to AppFunctionFloatTypeMetadata(isNullable = false),
                    "prop_array" to
                        AppFunctionArrayTypeMetadata(itemType = stringType, isNullable = false),
                ),
            required = listOf("prop_string"),
            qualifiedName = null,
            isNullable = false,
        )
    val components = AppFunctionComponentsMetadata(emptyMap())
    AppFunctionsAgentTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            ObjectTypeInput(
                dataType = objectType,
                value = mapOf("prop_string" to "value1", "prop_int" to 42),
                onValueChange = {},
                components = components,
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ObjectTypeInputWithInputsScreenshotPreview() {
    val stringType = AppFunctionStringTypeMetadata(isNullable = false)
    val intType = AppFunctionIntTypeMetadata(isNullable = false)

    val objectType =
        AppFunctionObjectTypeMetadata(
            properties =
                mapOf(
                    "prop_string" to stringType,
                    "prop_int" to intType,
                    "prop_long" to AppFunctionLongTypeMetadata(isNullable = false),
                    "prop_boolean" to AppFunctionBooleanTypeMetadata(isNullable = false),
                    "prop_double" to AppFunctionDoubleTypeMetadata(isNullable = false),
                    "prop_float" to AppFunctionFloatTypeMetadata(isNullable = false),
                    "prop_array" to
                        AppFunctionArrayTypeMetadata(itemType = stringType, isNullable = false),
                ),
            required = listOf("prop_string"),
            qualifiedName = null,
            isNullable = false,
        )
    val components = AppFunctionComponentsMetadata(emptyMap())
    AppFunctionsAgentTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            ObjectTypeInput(
                dataType = objectType,
                value =
                    mapOf(
                        "prop_string" to "value1",
                        "prop_int" to "42",
                        "prop_long" to "100",
                        "prop_boolean" to true,
                        "prop_double" to "2.718",
                        "prop_float" to "0.5",
                        "prop_array" to listOf("a", "b"),
                    ),
                onValueChange = {},
                components = components,
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun TroubleshootContentScreenshotPreview() {
    val app = AppInfo("com.example.app", "Example App", null)
    val dummyState =
        DebuggingUiState(
            filteredApps =
                AppsGroupState(
                    sections = listOf(AppSection(R.string.debugging_supported, listOf(app), true)),
                ),
            selectedApp = app,
            searchAppResultState =
                SearchAppResultState.TroubleshootUiState(
                    report =
                        TroubleshootReport(
                            "com.example.app",
                            listOf(
                                TroubleshootStep(
                                    R.string.troubleshoot_step_service_check,
                                    StepStatus.PASS,
                                ),
                            ),
                        ),
                    isLoading = false,
                ),
        )
    AppFunctionsAgentTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            DebuggingScreenContent(
                uiState = dummyState,
                onSearchQueryChanged = {},
                onAppSelected = {},
                onClearSelectedApp = {},
                onFunctionInputsChange = { _, _ -> },
                onInvoke = {},
                onClearResult = {},
                onFunctionExpandedChange = { _, _ -> },
                onLaunchPendingIntent = {},
                onTogglePin = {},
            )
        }
    }
}
