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

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appfunctions.agent.BuildConfig
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.data.ServiceTier
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import kotlin.OptIn

/** Stateful composable for the Settings screen. */
@Composable
fun SettingsScreen(
    onNavigateToConnectedApps: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    DisposableEffect(Unit) { onDispose { viewModel.saveSettings() } }

    val context = LocalContext.current
    val onOpenLicensesClick =
        remember(context) {
            { context.startActivity(Intent(context, OssLicensesMenuActivity::class.java)) }
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreenContent(
        geminiApiKeyState = viewModel.geminiApiKeyState,
        serviceTier = uiState.serviceTier,
        onServiceTierSelected = viewModel::setServiceTier,
        onOpenLicenses = onOpenLicensesClick,
        onNavigateToConnectedApps = onNavigateToConnectedApps,
    )
}

/** Stateless composable for the Settings screen, allowing for previews and easier testing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    geminiApiKeyState: TextFieldState,
    serviceTier: ServiceTier,
    onServiceTierSelected: (ServiceTier) -> Unit,
    onOpenLicenses: () -> Unit,
    onNavigateToConnectedApps: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Unspecified,
        topBar = {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
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

                // NOTE: We use the value/onValueChange overload here because the new state
                // overload might not support simple PasswordVisualTransformation as
                // easily in this version.
                if (BuildConfig.IS_RETAIL) {
                    OutlinedTextField(
                        value = geminiApiKeyState.text.toString(),
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = CircleShape,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor =
                                    MaterialTheme.colorScheme.surfaceBright,
                                focusedContainerColor =
                                    MaterialTheme.colorScheme.surfaceBright,
                            ),
                    )
                } else {
                    OutlinedTextField(
                        value = geminiApiKeyState.text.toString(),
                        onValueChange = { geminiApiKeyState.setTextAndPlaceCursorAtEnd(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = CircleShape,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor =
                                    MaterialTheme.colorScheme.surfaceBright,
                                focusedContainerColor =
                                    MaterialTheme.colorScheme.surfaceBright,
                            ),
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_service_tier),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ServiceTierDropdown(
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

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    SettingsScreenContent(
        geminiApiKeyState = rememberTextFieldState("AIzaSy..."),
        serviceTier = ServiceTier.STANDARD,
        onServiceTierSelected = {},
        onOpenLicenses = {},
        onNavigateToConnectedApps = {},
    )
}

/** Dropdown allowing the user to pick the Gemini [ServiceTier] for agent requests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceTierDropdown(
    selectedTier: ServiceTier,
    onTierSelected: (ServiceTier) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = stringResource(id = selectedTier.labelRes()),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            shape = CircleShape,
            colors =
                ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                ),
            modifier =
                Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ServiceTier.entries.forEach { tier ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = tier.labelRes())) },
                    onClick = {
                        onTierSelected(tier)
                        expanded = false
                    },
                )
            }
        }
    }
}

@StringRes
private fun ServiceTier.labelRes(): Int =
    when (this) {
        ServiceTier.STANDARD -> R.string.settings_service_tier_standard
        ServiceTier.PRIORITY -> R.string.settings_service_tier_priority
    }
