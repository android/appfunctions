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
package com.example.appfunctions.agent.ui.mobile.agentdemo

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.ui.contracts.ConnectedAppsScreenLayout
import com.example.appfunctions.agent.ui.screens.agentdemo.ConnectedAppsUiState

object MobileConnectedAppsLayout : ConnectedAppsScreenLayout {
    @Composable
    override fun Content(
        uiState: ConnectedAppsUiState,
        onBack: () -> Unit,
        onToggleApp: (String, Boolean) -> Unit,
        modifier: Modifier,
    ) {
        MobileConnectedAppsContent(
            uiState = uiState,
            onBack = onBack,
            onToggleApp = onToggleApp,
            modifier = modifier,
        )
    }
}

@Composable
fun MobileConnectedAppsContent(
    uiState: ConnectedAppsUiState,
    onBack: () -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Unspecified,
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.clickable { onBack() },
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(id = R.string.connected_apps_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 24.dp),
        ) {
            LazyColumn {
                items(
                    items = uiState.connectedApps,
                    key = { app -> app.packageName },
                ) { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        if (app.icon != null) {
                            Image(
                                painter = rememberAsyncImagePainter(app.icon),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        } else {
                            Box(modifier = Modifier.size(40.dp).background(Color.Gray))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (!app.description.isNullOrEmpty()) {
                                Text(
                                    text = app.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = app.isConnected,
                            onCheckedChange = { connected ->
                                onToggleApp(app.packageName, connected)
                            },
                        )
                    }
                }
            }
        }
    }
}
