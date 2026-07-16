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
package com.example.appfunctions.agent.ui.tv.agentdemo

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import com.example.appfunctions.agent.ui.contracts.AgentDemoScreenLayout
import com.example.appfunctions.agent.ui.screens.agentdemo.AgentDemoLoadedScreen
import com.example.appfunctions.agent.ui.screens.agentdemo.AgentDemoLoadingScreen
import com.example.appfunctions.agent.ui.screens.agentdemo.AgentUiEvent
import com.example.appfunctions.agent.ui.screens.agentdemo.AgentUiState

object TvAgentDemoLayout : AgentDemoScreenLayout {
    @Composable
    override fun Content(
        uiState: AgentUiState,
        onEvent: (AgentUiEvent) -> Unit,
        initialSidePanelVisible: Boolean,
        modifier: Modifier,
    ) {
        TvAgentDemoContent(
            uiState = uiState,
            onEvent = onEvent,
            initialSidePanelVisible = initialSidePanelVisible,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvAgentDemoContent(
    uiState: AgentUiState,
    onEvent: (AgentUiEvent) -> Unit,
    initialSidePanelVisible: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val focusManager = LocalFocusManager.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { focusManager.clearFocus() }

    when (uiState) {
        is AgentUiState.Loading -> {
            AgentDemoLoadingScreen()
        }

        is AgentUiState.Loaded -> {
            AgentDemoLoadedScreen(
                uiState = uiState,
                onEvent = onEvent,
                isWideScreen = true, // Force landscape dual-pane for TV D-Pad navigation
                drawerState = drawerState,
                scope = scope,
                packageManager = packageManager,
                initialSidePanelVisible = initialSidePanelVisible,
            )
        }
    }
}
