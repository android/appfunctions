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
package com.example.appfunctions.agent.domain.troubleshoot

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CheckMainlineVersionUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Executes the check.
         *
         * @return True if sufficient, false otherwise.
         */
        operator fun invoke(): Boolean {
            val pm = context.packageManager
            for (moduleName in listOf(APPSEARCH_MODULE_NAME, "com.android.appsearch")) {
                try {
                    val packageInfo =
                        pm.getPackageInfo(
                            moduleName,
                            PackageManager.PackageInfoFlags.of(PackageManager.MATCH_APEX.toLong()),
                        )
                    val versionCode = packageInfo.longVersionCode
                    if (versionCode > REQUIRED_VERSION) return true
                } catch (e: PackageManager.NameNotFoundException) {
                    // Try next package or fallback
                }
            }
            // Fallback to true if running on API Level >= 36
            return android.os.Build.VERSION.SDK_INT >= 36
        }

        companion object {
            const val APPSEARCH_MODULE_NAME = "com.google.android.appsearch"
            const val REQUIRED_VERSION = 360743060L
        }
    }
