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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.chatapp.RecipientsViewModel
import com.example.chatapp.wear.R

@Composable
fun WearRecipientsScreen(
    viewModel: RecipientsViewModel = hiltViewModel(),
    onRecipientClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val transformationSpec = rememberTransformationSpec()
    val scrollState = rememberTransformingLazyColumnState()
    val recipients = viewModel.recipients
    val groups = viewModel.groups

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
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                ListHeaderDefaults.minimumTopListContentPadding,
                            ),
                    transformation = SurfaceTransformation(transformationSpec),
                ) { Text(text = stringResource(R.string.chats)) }
            }

            items(groups) { group ->
                Button(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    onClick = { onRecipientClick(group.id) },
                    label = { Text(text = group.name) },
                    secondaryLabel = { Text(text = stringResource(R.string.group)) },
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }

            items(recipients) { recipient ->
                Button(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    onClick = { onRecipientClick(recipient.id) },
                    label = { Text(text = recipient.name) },
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }

            item {
                Button(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    onClick = onSettingsClick,
                    label = { Text(text = stringResource(R.string.settings)) },
                )
            }
        }
    }
}
