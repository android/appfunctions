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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsParams
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Use case to run troubleshooting checks on a target app. */
class TroubleshootAppUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val getAppFunctionsUseCase: GetAppFunctionsUseCase,
    ) {
        /**
         * Executes the use case.
         *
         * @param packageName The package name of the app to troubleshoot.
         * @return A TroubleshootReport containing the results of the checks.
         */
        suspend operator fun invoke(packageName: String): TroubleshootReport {
            val steps = mutableListOf<TroubleshootStep>()
            val pm = context.packageManager

            // 1. Check if already visible
            val appFunctionsMap =
                getAppFunctionsUseCase(
                    GetAppFunctionsParams(
                        targetPackageNames = setOf(packageName),
                        includeInternalTools = false,
                    ),
                ).first()
            val isVisible = appFunctionsMap.keys.any { it.packageName == packageName }
            if (isVisible) {
                steps.add(
                    TroubleshootStep(
                        R.string.troubleshoot_step_app_support,
                        StepStatus.PASS,
                        R.string.troubleshoot_app_already_supported_desc,
                    ),
                )
                return TroubleshootReport(packageName, steps)
            } else {
                steps.add(
                    TroubleshootStep(
                        R.string.troubleshoot_step_app_support,
                        StepStatus.WARNING,
                        R.string.troubleshoot_app_not_listed,
                    ),
                )
            }

            // 2. Check for Service
            val intent = Intent("android.app.appfunctions.AppFunctionService")
            intent.setPackage(packageName)
            val services = pm.queryIntentServices(intent, 0)
            val hasService = services.isNotEmpty()

            if (!hasService) {
                steps.add(
                    TroubleshootStep(
                        R.string.troubleshoot_step_service_check,
                        StepStatus.FAIL,
                        R.string.troubleshoot_no_service_desc,
                    ),
                )
                return TroubleshootReport(packageName, steps)
            } else {
                steps.add(
                    TroubleshootStep(
                        R.string.troubleshoot_step_service_check,
                        StepStatus.PASS,
                        R.string.troubleshoot_has_service_desc,
                    ),
                )
            }

            // 3. Check for Property
            val serviceInfo = services.first().serviceInfo
            var propertyValue: String? = null
            val hasProperty =
                try {
                    val property =
                        pm.getProperty(
                            "android.app.appfunctions.v2",
                            ComponentName(packageName, serviceInfo.name),
                        )
                    propertyValue = property.getString()
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }

            if (!hasProperty) {
                steps.add(
                    TroubleshootStep(
                        R.string.troubleshoot_step_property_check,
                        StepStatus.FAIL,
                        R.string.troubleshoot_no_property_desc,
                    ),
                )
                return TroubleshootReport(packageName, steps)
            } else {
                steps.add(
                    TroubleshootStep(
                        R.string.troubleshoot_step_property_check,
                        StepStatus.PASS,
                        R.string.troubleshoot_has_property_desc,
                    ),
                )
            }

            // 4. Check for Asset
            val hasAsset =
                if (propertyValue != null) {
                    try {
                        val packageContext = context.createPackageContext(packageName, 0)
                        val assets = packageContext.assets
                        assets.open(propertyValue).close()
                        true
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    false
                }

            if (!hasAsset) {
                steps.add(
                    TroubleshootStep(
                        R.string.troubleshoot_step_asset_check,
                        StepStatus.FAIL,
                        R.string.troubleshoot_no_asset_desc,
                        listOf(propertyValue ?: ""),
                    ),
                )
                return TroubleshootReport(packageName, steps)
            } else {
                steps.add(
                    TroubleshootStep(
                        R.string.troubleshoot_step_asset_check,
                        StepStatus.PASS,
                        R.string.troubleshoot_has_asset_desc,
                    ),
                )
            }

            // 5. Final check
            steps.add(
                TroubleshootStep(
                    R.string.troubleshoot_step_final_check,
                    StepStatus.WARNING,
                    R.string.troubleshoot_fallback_desc,
                ),
            )

            return TroubleshootReport(packageName, steps)
        }
    }
