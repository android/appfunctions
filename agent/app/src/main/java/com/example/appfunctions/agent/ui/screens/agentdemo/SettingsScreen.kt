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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.appfunctions.agent.BuildConfig
import com.example.appfunctions.agent.R
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import kotlin.OptIn

/** Stateful composable for the Settings screen. */
import com.example.appfunctions.agent.ui.contracts.SettingsScreenLayout
import com.example.appfunctions.agent.ui.layout.FormFactor
import com.example.appfunctions.agent.ui.layout.rememberFormFactor
import com.example.appfunctions.agent.ui.mobile.agentdemo.MobileSettingsLayout
import com.example.appfunctions.agent.ui.tv.agentdemo.TvSettingsLayout

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

    SettingsScreenContent(
        geminiApiKeyState = viewModel.geminiApiKeyState,
        onOpenLicenses = onOpenLicensesClick,
        onNavigateToConnectedApps = onNavigateToConnectedApps,
    )
}

/** Stateless composable for the Settings screen, allowing for previews and easier testing. */
@Composable
fun SettingsScreenContent(
    geminiApiKeyState: TextFieldState,
    onOpenLicenses: () -> Unit,
    onNavigateToConnectedApps: () -> Unit,
) {
    val formFactor = rememberFormFactor()
    val layout: SettingsScreenLayout = when (formFactor) {
        FormFactor.TV -> TvSettingsLayout
        FormFactor.WEAR, FormFactor.AUTO, FormFactor.XR, FormFactor.MOBILE -> MobileSettingsLayout
    }

    layout.Content(
        geminiApiKeyState = geminiApiKeyState,
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
        onOpenLicenses = {},
        onNavigateToConnectedApps = {},
    )
}
