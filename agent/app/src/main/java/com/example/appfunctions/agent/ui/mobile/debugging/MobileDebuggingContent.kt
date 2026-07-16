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
package com.example.appfunctions.agent.ui.mobile.debugging

import android.app.PendingIntent
import android.content.res.Resources
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.ui.contracts.DebuggingScreenLayout
import com.example.appfunctions.agent.ui.screens.debugging.AppDropdownItem
import com.example.appfunctions.agent.ui.screens.debugging.AppsGroupState
import com.example.appfunctions.agent.ui.screens.debugging.DebuggingUiState
import com.example.appfunctions.agent.ui.screens.debugging.FunctionsFoundContent
import com.example.appfunctions.agent.ui.screens.debugging.SearchAppResultState
import com.example.appfunctions.agent.ui.screens.debugging.TroubleshootResult

object MobileDebuggingLayout : DebuggingScreenLayout {
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
        MobileDebuggingContent(
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
fun MobileDebuggingContent(
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
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MobileAppDropdown(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    appGroups = uiState.filteredApps,
                    selectedApp = uiState.selectedApp,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileAppDropdown(
    appGroups: AppsGroupState,
    selectedApp: AppInfo?,
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

    OutlinedCard(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = selectedApp?.label ?: stringResource(R.string.debugging_select_app_prompt),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            supportingContent = if (selectedApp?.packageName != null) {
                { Text(text = selectedApp.packageName) }
            } else null,
            leadingContent = if (selectedApp?.icon != null) {
                {
                    Image(
                        bitmap = selectedApp.icon.toBitmap().asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else null,
            trailingContent = {
                if (selectedApp != null) {
                    IconButton(onClick = { onClearSelectedApp() }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }

    if (expanded) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text(stringResource(R.string.debugging_search_app)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    singleLine = true,
                )

                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    sections.forEach { section ->
                        item {
                            if (section.titleRes != Resources.ID_NULL) {
                                Text(
                                    text = stringResource(section.titleRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        items(section.apps) { app ->
                            AppDropdownItem(
                                app = app,
                                isPinned = pinnedPackageNames.contains(app.packageName),
                                onAppSelected = {
                                    onAppSelected(it)
                                    expanded = false
                                    focusManager.clearFocus()
                                },
                                onSearchQueryChanged = onSearchQueryChanged,
                                onTogglePin = { onTogglePin(app) },
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
