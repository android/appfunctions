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
package com.example.appfunctions.agent.ui.layout

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

enum class FormFactor {
    MOBILE,
    TV,
    WEAR,
    AUTO,
    XR,
}

@Composable
fun rememberFormFactor(): FormFactor {
    val context = LocalContext.current
    return remember(context) {
        val pm = context.packageManager
        when {
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) -> FormFactor.TV
            pm.hasSystemFeature(PackageManager.FEATURE_WATCH) -> FormFactor.WEAR
            pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) -> FormFactor.AUTO
            pm.hasSystemFeature("android.hardware.xr.display") -> FormFactor.XR
            else -> FormFactor.MOBILE
        }
    }
}

@Composable
fun isTvFormFactor(): Boolean = rememberFormFactor() == FormFactor.TV
