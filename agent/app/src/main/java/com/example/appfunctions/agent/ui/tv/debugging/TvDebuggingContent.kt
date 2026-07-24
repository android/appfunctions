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
package com.example.appfunctions.agent.ui.tv.debugging

import android.app.PendingIntent
import android.content.res.Resources
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.ui.components.TvSurfaceTextField
import com.example.appfunctions.agent.ui.contracts.DebuggingScreenLayout
import com.example.appfunctions.agent.ui.screens.debugging.AppDropdownItem
import com.example.appfunctions.agent.ui.screens.debugging.AppsGroupState
import com.example.appfunctions.agent.ui.screens.debugging.DebuggingUiState
import com.example.appfunctions.agent.ui.screens.debugging.FunctionsFoundContent
import com.example.appfunctions.agent.ui.screens.debugging.SearchAppResultState
import com.example.appfunctions.agent.ui.screens.debugging.TroubleshootResult
import kotlinx.coroutines.android.awaitFrame

object TvDebuggingLayout : DebuggingScreenLayout {
    @Composable
    override fun Content(
        uiState: DebuggingUiState,
        onSearchQueryChanged: (String) -> Unit,
        onAppSelected: (AppInfo) -> Unit,
        onClearSelectedApp: () -> Unit,
        onFunctionInputsChange: (String, Map<String, Any>) -> Unit,
        onInvoke: (AppFunctionMetadata) -> Unit,
        onClearResult: () -> Unit,
        onFunctionExpandedChange: (String, Boolean) -> Unit,
        onLaunchPendingIntent: (PendingIntent) -> Unit,
        onTogglePin: (AppInfo) -> Unit,
        modifier: Modifier,
    ) {
        TvDebuggingContent(
            uiState = uiState,
            onSearchQueryChanged = onSearchQueryChanged,
            onAppSelected = onAppSelected,
            onClearSelectedApp = onClearSelectedApp,
            onFunctionInputsChange = onFunctionInputsChange,
            onInvoke = onInvoke,
            onClearResult = onClearResult,
            onFunctionExpandedChange = onFunctionExpandedChange,
            onLaunchPendingIntent = onLaunchPendingIntent,
            onTogglePin = onTogglePin,
            modifier = modifier,
        )
    }
}

@Composable
fun TvDebuggingContent(
    uiState: DebuggingUiState,
    onSearchQueryChanged: (String) -> Unit,
    onAppSelected: (AppInfo) -> Unit,
    onClearSelectedApp: () -> Unit,
    onFunctionInputsChange: (String, Map<String, Any>) -> Unit,
    onInvoke: (AppFunctionMetadata) -> Unit,
    onClearResult: () -> Unit,
    onFunctionExpandedChange: (String, Boolean) -> Unit,
    onLaunchPendingIntent: (PendingIntent) -> Unit,
    onTogglePin: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Unspecified,
        topBar = {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvAppDropdown(
                    modifier = Modifier.weight(1f),
                    appGroups = uiState.filteredApps,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChanged = onSearchQueryChanged,
                    onAppSelected = onAppSelected,
                    onClearSelectedApp = onClearSelectedApp,
                    onTogglePin = onTogglePin,
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()),
        ) {
            when (val searchAppResultState = uiState.searchAppResultState) {
                is SearchAppResultState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.debugging_select_app_prompt),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                is SearchAppResultState.FunctionsFoundState -> {
                    FunctionsFoundContent(
                        state = searchAppResultState,
                        onFunctionExpandedChange = onFunctionExpandedChange,
                        onFunctionInputsChange = onFunctionInputsChange,
                        onInvoke = onInvoke,
                        onClearResult = onClearResult,
                        onLaunchPendingIntent = onLaunchPendingIntent,
                    )
                }
                is SearchAppResultState.TroubleshootUiState -> {
                    TroubleshootResult(
                        state = searchAppResultState,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TvAppDropdown(
    appGroups: AppsGroupState,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onAppSelected: (AppInfo) -> Unit,
    onClearSelectedApp: () -> Unit,
    onTogglePin: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val sections = appGroups.sections
    val pinnedPackageNames =
        remember(sections) {
            sections
                .find { it.titleRes == Resources.ID_NULL }
                ?.apps
                ?.map { it.packageName }
                ?.toSet() ?: emptySet()
        }

    val dropdownFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        awaitFrame()
        dropdownFocusRequester.requestFocus()
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvSurfaceTextField(
                value = searchQuery,
                placeholder = stringResource(R.string.debugging_search_app),
                onValueChange = onSearchQueryChanged,
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onClearSelectedApp() }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                } else null,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .focusRequester(dropdownFocusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (expanded && keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                            expanded = false
                            true
                        } else false
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceBright,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Apps",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Toggle Dropdown",
                    )
                }
            }
        }

        if (expanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                            expanded = false
                            true
                        } else false
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    sections.forEachIndexed { index, section ->
                        if (index > 0) {
                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp),
                                )
                            }
                        }

                        if (index != 0) {
                            item {
                                Text(
                                    text = stringResource(id = section.titleRes),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                        }

                        items(
                            items = section.apps,
                            key = { app -> "${section.titleRes}_${app.packageName}" },
                        ) { app ->
                            AppDropdownItem(
                                app = app,
                                isPinned = pinnedPackageNames.contains(app.packageName),
                                onAppSelected = {
                                    onAppSelected(it)
                                    expanded = false
                                    focusManager.clearFocus()
                                },
                                onSearchQueryChanged = onSearchQueryChanged,
                                onTogglePin = onTogglePin,
                                onExpandedChange = { expanded = it },
                                showPin = section.showPin,
                            )
                        }
                    }
                }
            }
        }
    }
}
