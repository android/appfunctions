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

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.appfunctions.agent.data.ServiceTier

/** Screenshot test for [SettingsScreen]. */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun SettingsScreenScreenshotPreview() {
    SettingsScreenContent(
        geminiApiKeyState = rememberTextFieldState("AIzaSy..."),
        serviceTier = ServiceTier.STANDARD,
        onServiceTierSelected = {
            // no-op
        },
        onOpenLicenses = {
            // no-op
        },
        onNavigateToConnectedApps = {
            // no-op
        },
    )
}
