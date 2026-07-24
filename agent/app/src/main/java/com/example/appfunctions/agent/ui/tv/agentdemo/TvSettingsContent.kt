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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.appfunctions.agent.BuildConfig
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.ui.components.TvSurfaceTextField
import com.example.appfunctions.agent.ui.contracts.SettingsScreenLayout

object TvSettingsLayout : SettingsScreenLayout {
    @Composable
    override fun Content(
        geminiApiKeyState: TextFieldState,
        onOpenLicenses: () -> Unit,
        onNavigateToConnectedApps: () -> Unit,
        modifier: Modifier,
    ) {
        TvSettingsContent(
            geminiApiKeyState = geminiApiKeyState,
            onOpenLicenses = onOpenLicenses,
            onNavigateToConnectedApps = onNavigateToConnectedApps,
            modifier = modifier,
        )
    }
}

@Composable
fun TvSettingsContent(
    geminiApiKeyState: TextFieldState,
    onOpenLicenses: () -> Unit,
    onNavigateToConnectedApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Unspecified,
        topBar = {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(id = R.string.nav_settings),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_agent),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp).semantics { heading() },
                )
            }
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_gemini_api_key),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                if (BuildConfig.IS_RETAIL) {
                    TvSurfaceTextField(
                        value = geminiApiKeyState.text.toString(),
                        onValueChange = {},
                        placeholder = stringResource(id = R.string.settings_gemini_api_key),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    TvSurfaceTextField(
                        value = geminiApiKeyState.text.toString(),
                        onValueChange = { geminiApiKeyState.setTextAndPlaceCursorAtEnd(it) },
                        placeholder = stringResource(id = R.string.settings_gemini_api_key),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            var isAppsFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = onNavigateToConnectedApps,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .onFocusChanged { isAppsFocused = it.isFocused },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (isAppsFocused) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else null,
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(id = R.string.manage_connected_apps),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(imageVector = Icons.Default.Apps, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_about),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp).semantics { heading() },
                )
            }

            var isLicensesFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = onOpenLicenses,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .onFocusChanged { isLicensesFocused = it.isFocused },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (isLicensesFocused) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else null,
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(id = R.string.settings_open_source_licenses),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
