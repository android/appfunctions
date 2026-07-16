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
package com.example.appfunctions.agent.ui.screens.debugging

import android.app.PendingIntent
import android.content.res.Resources
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.ui.contracts.DebuggingScreenLayout
import com.example.appfunctions.agent.ui.layout.rememberFormFactor
import com.example.appfunctions.agent.ui.layout.FormFactor
import com.example.appfunctions.agent.ui.mobile.debugging.MobileDebuggingLayout
import com.example.appfunctions.agent.ui.theme.AppFunctionsAgentTheme
import com.example.appfunctions.agent.ui.tv.debugging.TvDebuggingLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebuggingScreen(viewModel: DebuggingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DebuggingScreenContent(
        uiState = uiState,
        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
        onAppSelected = { viewModel.onAppSelected(it) },
        onClearSelectedApp = { viewModel.onClearSelectedApp() },
        onFunctionInputsChange = { functionId, inputs ->
            viewModel.onFunctionInputsChange(functionId, inputs)
        },
        onInvoke = { viewModel.invokeFunction(it) },
        onClearResult = { viewModel.clearResult() },
        onFunctionExpandedChange = { functionId, expanded ->
            viewModel.onFunctionExpandedChange(functionId, expanded)
        },
        onLaunchPendingIntent = { viewModel.launchPendingIntent(it) },
        onTogglePin = { viewModel.onTogglePin(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebuggingScreenContent(
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
) {
    val formFactor = rememberFormFactor()
    val layout: DebuggingScreenLayout = when (formFactor) {
        FormFactor.TV -> TvDebuggingLayout
        FormFactor.WEAR, FormFactor.AUTO, FormFactor.XR, FormFactor.MOBILE -> MobileDebuggingLayout
    }

    layout.Content(
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
        modifier = Modifier,
    )
}

@Composable
internal fun AppDropdownItem(
    app: AppInfo,
    isPinned: Boolean,
    onAppSelected: (AppInfo) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onTogglePin: (AppInfo) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    showPin: Boolean = true,
) {
    DropdownMenuItem(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                app.icon?.let {
                    Image(
                        bitmap = it.toBitmap().asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = app.label, modifier = Modifier.weight(1f))
                if (showPin) {
                    IconButton(onClick = { onTogglePin(app) }) {
                        Icon(
                            imageVector =
                                if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (isPinned) "Unpin" else "Pin",
                            tint =
                                if (isPinned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                } else {
                    Box(Modifier.minimumInteractiveComponentSize())
                }
            }
        },
        onClick = {
            onSearchQueryChanged(app.label)
            onAppSelected(app)
            onExpandedChange(false)
        },
    )
}

@Preview(showBackground = true)
@Composable
fun DebuggingScreenPreview() {
    val dummyState =
        DebuggingUiState(
            filteredApps = AppsGroupState(),
            selectedApp = null,
        )
    AppFunctionsAgentTheme {
        DebuggingScreenContent(
            uiState = dummyState,
            onSearchQueryChanged = {},
            onAppSelected = {},
            onClearSelectedApp = {},
            onFunctionInputsChange = { _, _ -> },
            onInvoke = {},
            onClearResult = {},
            onFunctionExpandedChange = { _, _ -> },
            onLaunchPendingIntent = {},
            onTogglePin = {},
        )
    }
}
