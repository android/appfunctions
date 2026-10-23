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
import android.widget.Toast
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.ui.contracts.DebuggingScreenLayout
import com.example.appfunctions.agent.ui.layout.rememberFormFactor
import com.example.appfunctions.agent.ui.layout.resolveDebuggingLayout
import com.example.appfunctions.agent.ui.theme.AppFunctionsAgentTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebuggingScreen(viewModel: DebuggingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.onToastShown()
        }
    }

    DebuggingScreenContent(
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onAppSelected = viewModel::onAppSelected,
        onClearSelectedApp = viewModel::onClearSelectedApp,
        onFunctionInputsChange = viewModel::onFunctionInputsChange,
        onInvoke = viewModel::invokeFunction,
        onClearResult = viewModel::clearResult,
        onFunctionExpandedChange = viewModel::onFunctionExpandedChange,
        onLaunchPendingIntent = viewModel::launchPendingIntent,
        onTogglePin = viewModel::onTogglePin,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebuggingScreenContent(
    uiState: DebuggingUiState,
    onSearchQueryChanged: (String) -> Unit,
    onAppSelected: (AppInfo) -> Unit,
    onClearSelectedApp: () -> Unit,
    onFunctionInputsChange: (String, Map<String, Any>) -> Unit,
    onInvoke: (AppFunctionMetadata) -> Unit,
    onClearResult: () -> Unit,
    onFunctionExpandedChange: (String, Boolean) -> Unit,
    onLaunchPendingIntent: (PendingIntent) -> Unit,
    onTogglePin: (AppInfo) -> Unit,
) {
    val layout: DebuggingScreenLayout = rememberFormFactor().resolveDebuggingLayout()

    layout.Content(
        uiState = uiState,
        onSearchQueryChanged = onSearchQueryChanged,
        onAppSelected = onAppSelected,
        onClearSelectedApp = onClearSelectedApp,
        onFunctionInputsChange = onFunctionInputsChange,
        onInvoke = onInvoke,
        onClearResult = onClearResult,
        onFunctionExpandedChange = onFunctionExpandedChange,
        onLaunchPendingIntent = onLaunchPendingIntent,
        onTogglePin = onTogglePin,
        modifier = Modifier,
    )
}

@Preview(showBackground = true)
@Composable
fun DebuggingScreenPreview() {
    val dummyState =
        DebuggingUiState(
            filteredApps = AppsGroupState(),
            selectedApp = null,
        )
    AppFunctionsAgentTheme {
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
