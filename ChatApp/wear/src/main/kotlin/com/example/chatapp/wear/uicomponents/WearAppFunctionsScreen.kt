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
package com.example.chatapp.wear.uicomponents

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.chatapp.AppFunctionsViewModel
import com.example.chatapp.wear.R

@Composable
fun WearAppFunctionsScreen(viewModel: AppFunctionsViewModel = hiltViewModel()) {
    val transformationSpec = rememberTransformationSpec()
    val scrollState = rememberTransformingLazyColumnState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(
        scrollState = scrollState,
    ) { contentPadding ->
        TransformingLazyColumn(
            state = scrollState,
            contentPadding = contentPadding,
        ) {
            item {
                ListHeader(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(stringResource(R.string.appfunctions))
                }
            }

            if (uiState.functions.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_functions_found),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(uiState.functions) { function ->
                    SwitchButton(
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                        checked = function.isEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.toggleFunction(function.id, enabled)
                        },
                        label = {
                            val shortName = function.id.substringAfterLast("#").substringAfterLast(".")
                            Text(text = shortName)
                        },
                        secondaryLabel = {
                            Text(text = function.description ?: function.id)
                        },
                    )
                }
            }
        }
    }
}
