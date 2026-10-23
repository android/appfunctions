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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.appfunctions.agent.BuildConfig
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.data.ServiceTier
import com.example.appfunctions.agent.ui.contracts.SettingsScreenLayout

object MobileSettingsLayout : SettingsScreenLayout {
    @Composable
    override fun Content(
        geminiApiKeyState: TextFieldState,
        serviceTier: ServiceTier,
        onServiceTierSelected: (ServiceTier) -> Unit,
        onOpenLicenses: () -> Unit,
        onNavigateToConnectedApps: () -> Unit,
        modifier: Modifier,
    ) {
        MobileSettingsContent(
            geminiApiKeyState = geminiApiKeyState,
            serviceTier = serviceTier,
            onServiceTierSelected = onServiceTierSelected,
            onOpenLicenses = onOpenLicenses,
            onNavigateToConnectedApps = onNavigateToConnectedApps,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSettingsContent(
    geminiApiKeyState: TextFieldState,
    serviceTier: ServiceTier,
    onServiceTierSelected: (ServiceTier) -> Unit,
    onOpenLicenses: () -> Unit,
    onNavigateToConnectedApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Unspecified,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.nav_settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
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
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_agent),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp).semantics { heading() },
                )
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_gemini_api_key),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                if (BuildConfig.IS_RETAIL) {
                    OutlinedTextField(
                        value = geminiApiKeyState.text.toString(),
                        onValueChange = {},
                        placeholder = { Text(stringResource(id = R.string.settings_gemini_api_key)) },
                        visualTransformation = PasswordVisualTransformation(),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = geminiApiKeyState.text.toString(),
                        onValueChange = { geminiApiKeyState.setTextAndPlaceCursorAtEnd(it) },
                        placeholder = { Text(stringResource(id = R.string.settings_gemini_api_key)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_service_tier),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                com.example.appfunctions.agent.ui.screens.agentdemo.ServiceTierDropdown(
                    selectedTier = serviceTier,
                    onTierSelected = onServiceTierSelected,
                )
                Text(
                    text = stringResource(id = R.string.settings_service_tier_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

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
                modifier =
                    Modifier.clickable(role = Role.Button) { onNavigateToConnectedApps() },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_about),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp).semantics { heading() },
                )
            }

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
                modifier = Modifier.clickable(role = Role.Button) { onOpenLicenses() },
            )
        }
    }
}
