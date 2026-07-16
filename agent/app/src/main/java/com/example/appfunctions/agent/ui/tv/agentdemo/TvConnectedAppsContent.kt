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
package com.example.appfunctions.agent.ui.tv.agentdemo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.ui.contracts.ConnectedAppsScreenLayout
import com.example.appfunctions.agent.ui.screens.agentdemo.ConnectedAppsUiState

object TvConnectedAppsLayout : ConnectedAppsScreenLayout {
    @Composable
    override fun Content(
        uiState: ConnectedAppsUiState,
        onBack: () -> Unit,
        onToggleApp: (String, Boolean) -> Unit,
        modifier: Modifier,
    ) {
        TvConnectedAppsContent(
            uiState = uiState,
            onBack = onBack,
            onToggleApp = onToggleApp,
            modifier = modifier,
        )
    }
}

@Composable
fun TvConnectedAppsContent(
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
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
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
                    var isRowFocused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { onToggleApp(app.packageName, !app.isConnected) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .onFocusChanged { isRowFocused = it.isFocused },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = if (isRowFocused) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else null,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                onCheckedChange = null,
                            )
                        }
                    }
                }
            }
        }
    }
}
