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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.domain.appfunction.ConnectedAppInfo
import com.example.appfunctions.agent.ui.contracts.ConnectedAppsScreenLayout
import com.example.appfunctions.agent.ui.layout.FormFactor
import com.example.appfunctions.agent.ui.layout.rememberFormFactor
import com.example.appfunctions.agent.ui.mobile.agentdemo.MobileConnectedAppsLayout
import com.example.appfunctions.agent.ui.tv.agentdemo.TvConnectedAppsLayout

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
    val formFactor = rememberFormFactor()
    val layout: ConnectedAppsScreenLayout = when (formFactor) {
        FormFactor.TV -> TvConnectedAppsLayout
        FormFactor.WEAR, FormFactor.AUTO, FormFactor.XR, FormFactor.MOBILE -> MobileConnectedAppsLayout
    }

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
