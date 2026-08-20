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
package com.example.appfunctions.agent.ui.screens.agentdemo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appfunctions.agent.domain.appfunction.ConnectedAppInfo
import com.example.appfunctions.agent.ui.contracts.ConnectedAppsScreenLayout
import com.example.appfunctions.agent.ui.layout.rememberFormFactor
import com.example.appfunctions.agent.ui.layout.resolveConnectedAppsLayout

/** Stateful composable for the Connected Apps screen. */
@Composable
fun ConnectedAppsScreen(
    onBack: () -> Unit,
    viewModel: ConnectedAppsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ConnectedAppsScreenContent(
        uiState = uiState,
        onBack = onBack,
        onToggleApp = { packageName, connected ->
            viewModel.setAppConnected(packageName, connected)
        },
    )
}

/** Stateless composable for the Connected Apps screen. */
@Composable
fun ConnectedAppsScreenContent(
    uiState: ConnectedAppsUiState,
    onBack: () -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
) {
    val layout: ConnectedAppsScreenLayout = rememberFormFactor().resolveConnectedAppsLayout()

    layout.Content(
        uiState = uiState,
        onBack = onBack,
        onToggleApp = onToggleApp,
        modifier = Modifier,
    )
}

@Preview(showBackground = true)
@Composable
fun ConnectedAppsScreenPreview() {
    ConnectedAppsScreenContent(
        uiState =
            ConnectedAppsUiState(
                connectedApps =
                    listOf(
                        ConnectedAppInfo("com.example.app1", "App 1", null, true),
                        ConnectedAppInfo("com.example.app2", "App 2", null, false),
                    ),
            ),
        onBack = {},
        onToggleApp = { _, _ -> },
    )
}
