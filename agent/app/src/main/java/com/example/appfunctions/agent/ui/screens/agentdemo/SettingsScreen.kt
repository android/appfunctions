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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.data.ServiceTier
import com.example.appfunctions.agent.ui.contracts.SettingsScreenLayout
import com.example.appfunctions.agent.ui.layout.rememberFormFactor
import com.example.appfunctions.agent.ui.layout.resolveSettingsLayout
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity

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
@Composable
fun SettingsScreenContent(
    geminiApiKeyState: TextFieldState,
    serviceTier: ServiceTier,
    onServiceTierSelected: (ServiceTier) -> Unit,
    onOpenLicenses: () -> Unit,
    onNavigateToConnectedApps: () -> Unit,
) {
    val layout: SettingsScreenLayout = rememberFormFactor().resolveSettingsLayout()

    layout.Content(
        geminiApiKeyState = geminiApiKeyState,
        serviceTier = serviceTier,
        onServiceTierSelected = onServiceTierSelected,
        onOpenLicenses = onOpenLicenses,
        onNavigateToConnectedApps = onNavigateToConnectedApps,
        modifier = Modifier,
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceTierDropdown(
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
