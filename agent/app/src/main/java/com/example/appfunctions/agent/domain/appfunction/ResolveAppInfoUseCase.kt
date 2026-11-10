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
package com.example.appfunctions.agent.domain.appfunction

import android.content.Context
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Agent-facing information about an app that exposes AppFunctions.
 *
 * @property label The user-visible app label, or the package name if it cannot be resolved.
 * @property instructions `AppFunctionAppMetadata.instructions`, or null if the app declares none.
 */
data class ResolvedAppInfo(
    val label: String,
    val instructions: String?,
)

/** Use case to resolve the label and agent-facing instructions of an app. */
class ResolveAppInfoUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend operator fun invoke(packageMetadata: AppFunctionPackageMetadata): ResolvedAppInfo =
            withContext(Dispatchers.IO) {
                val packageName = packageMetadata.packageName
                val label =
                    try {
                        val packageManager = context.packageManager
                        packageManager
                            .getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
                            .toString()
                    } catch (e: Exception) {
                        packageName
                    }
                val instructions =
                    packageMetadata
                        .resolveAppFunctionAppMetadata(context)
                        ?.instructions
                        ?.takeIf { it.isNotBlank() }
                ResolvedAppInfo(label = label, instructions = instructions)
            }
    }
