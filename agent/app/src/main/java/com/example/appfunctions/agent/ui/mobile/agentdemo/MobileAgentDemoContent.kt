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
package com.example.appfunctions.agent.ui.mobile.agentdemo

import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.core.graphics.drawable.toBitmap
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import com.example.appfunctions.agent.domain.AgentStatus
import com.example.appfunctions.agent.ui.contracts.AgentDemoScreenLayout
import com.example.appfunctions.agent.ui.screens.agentdemo.AgentDemoLoadingScreen
import com.example.appfunctions.agent.ui.screens.agentdemo.AgentUiEvent
import com.example.appfunctions.agent.ui.screens.agentdemo.AgentUiState
import com.example.appfunctions.agent.ui.screens.agentdemo.ChatHistorySidePanel
import com.example.appfunctions.agent.ui.screens.agentdemo.MessageBubble
import com.example.appfunctions.agent.ui.screens.agentdemo.StatusIndicator
import com.example.appfunctions.agent.ui.screens.debugging.LazyExposedDropdownMenu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object MobileAgentDemoLayout : AgentDemoScreenLayout {
    @Composable
    override fun Content(
        uiState: AgentUiState,
        onEvent: (AgentUiEvent) -> Unit,
        initialSidePanelVisible: Boolean,
        modifier: Modifier,
    ) {
        MobileAgentDemoContent(
            uiState = uiState,
            onEvent = onEvent,
            initialSidePanelVisible = initialSidePanelVisible,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileAgentDemoContent(
    uiState: AgentUiState,
    onEvent: (AgentUiEvent) -> Unit,
    initialSidePanelVisible: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val focusManager = LocalFocusManager.current

    val containerSize = LocalConfiguration.current.screenWidthDp
    val isWideScreen = containerSize >= 600

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { focusManager.clearFocus() }

    val content =
        @Composable {
            when (uiState) {
                is AgentUiState.Loading -> {
                    AgentDemoLoadingScreen()
                }

                is AgentUiState.Loaded -> {
                    MobileAgentDemoLoadedScreen(
                        uiState = uiState,
                        onEvent = onEvent,
                        isWideScreen = isWideScreen,
                        drawerState = drawerState,
                        scope = scope,
                        packageManager = packageManager,
                        initialSidePanelVisible = initialSidePanelVisible,
                        modifier = modifier,
                    )
                }
            }
        }

    if (isWideScreen) {
        content()
    } else {
        val currentThread = (uiState as? AgentUiState.Loaded)?.currentThread
        val threads = (uiState as? AgentUiState.Loaded)?.threads ?: emptyList()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                ) {
                    ChatHistorySidePanel(
                        threads = threads,
                        currentThread = currentThread,
                        onEvent = { event ->
                            onEvent(event)
                            scope.launch { drawerState.close() }
                        },
                    )
                }
            },
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileAgentDemoLoadedScreen(
    uiState: AgentUiState.Loaded,
    onEvent: (AgentUiEvent) -> Unit,
    isWideScreen: Boolean,
    drawerState: DrawerState,
    scope: CoroutineScope,
    packageManager: PackageManager,
    modifier: Modifier = Modifier,
    initialSidePanelVisible: Boolean = false,
) {
    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    var isSidePanelVisible by remember { mutableStateOf(initialSidePanelVisible) }
    var selectedAppPackageName by remember { mutableStateOf<String?>(null) }

    val inputFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val currentThreadId = uiState.currentThread.threadId
    var hasInitiallyScrolled by remember(currentThreadId) { mutableStateOf(false) }

    LaunchedEffect(uiState.messages, currentThreadId) {
        if (uiState.messages.isNotEmpty() && !hasInitiallyScrolled) {
            hasInitiallyScrolled = true
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Unspecified,
        topBar = {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MobileModelDropdown(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    currentThread = uiState.currentThread,
                    onModelSelected = { onEvent(AgentUiEvent.OnModelSelected(it)) },
                    isAppFunctionDebuggingEnabled = uiState.isAppFunctionDebuggingEnabled,
                    onToggleAppFunctionDebugging = {
                        onEvent(AgentUiEvent.OnToggleAppFunctionDebugging(it))
                    },
                    onMenuClick = {
                        if (isWideScreen) {
                            isSidePanelVisible = !isSidePanelVisible
                        } else {
                            scope.launch { drawerState.open() }
                        }
                    },
                )
                IconButton(
                    onClick = {
                        onEvent(AgentUiEvent.OnCreateThread(uiState.currentThread.llmModel))
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create Thread")
                }
            }
        },
    ) { paddingValues ->
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .consumeWindowInsets(paddingValues)
                    .imePadding(),
        ) {
            if (isWideScreen) {
                AnimatedVisibility(
                    visible = isSidePanelVisible,
                    enter = slideInHorizontally() + expandHorizontally(),
                    exit = slideOutHorizontally() + shrinkHorizontally(),
                ) {
                    ChatHistorySidePanel(
                        threads = uiState.threads,
                        currentThread = uiState.currentThread,
                        onEvent = onEvent,
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                    reverseLayout = true,
                ) {
                    if (uiState.status != AgentStatus.Idle) {
                        item {
                            StatusIndicator(
                                status = uiState.status,
                                packageManager = packageManager,
                            )
                        }
                    }

                    items(
                        items = uiState.messages.reversed(),
                        key = { message -> message.messageId },
                    ) { message ->
                        MessageBubble(
                            message = message,
                            isValidAction =
                                message.pendingIntentId in uiState.activePendingActionIds,
                            installedApps = uiState.installedApps,
                            showAppFunctionDebugDetails = uiState.isAppFunctionDebuggingEnabled,
                            onConfirmAction = { onEvent(AgentUiEvent.OnConfirmAction(it)) },
                        )
                    }
                }

                val sendMessage = {
                    val textStr = messageText.text
                    if (textStr.isNotBlank()) {
                        onEvent(AgentUiEvent.OnSendMessage(textStr, selectedAppPackageName))
                        messageText = TextFieldValue("")
                        selectedAppPackageName = null
                        inputFocusRequester.requestFocus()
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }
                }

                val textStr = messageText.text
                val lastAtIndex = textStr.lastIndexOf('@')
                val showAutocomplete =
                    lastAtIndex >= 0 &&
                        (lastAtIndex == 0 || textStr[lastAtIndex - 1].isWhitespace()) &&
                        selectedAppPackageName == null
                val autocompleteQuery =
                    if (showAutocomplete) {
                        textStr.substring(lastAtIndex + 1)
                    } else {
                        ""
                    }
                val filteredApps =
                    remember(autocompleteQuery, uiState.installedApps) {
                        if (autocompleteQuery.isEmpty()) {
                            uiState.installedApps
                        } else {
                            uiState.installedApps.filter {
                                it.label.contains(autocompleteQuery, ignoreCase = true)
                            }
                        }
                    }

                val density = LocalDensity.current
                val popupPositionProvider =
                    remember(density) {
                        object : PopupPositionProvider {
                            override fun calculatePosition(
                                anchorBounds: IntRect,
                                windowSize: IntSize,
                                layoutDirection: LayoutDirection,
                                popupContentSize: IntSize,
                            ): IntOffset {
                                val gap = with(density) { 2.dp.roundToPx() }
                                return IntOffset(
                                    x = anchorBounds.left,
                                    y = anchorBounds.top - popupContentSize.height - gap,
                                )
                            }
                        }
                    }

                val appMentionRegex =
                    remember(uiState.installedApps) {
                        if (uiState.installedApps.isNotEmpty()) {
                            val appLabelsPattern =
                                uiState.installedApps.joinToString("|") { Regex.escape(it.label) }
                            Regex("@($appLabelsPattern)\\b", RegexOption.IGNORE_CASE)
                        } else {
                            null
                        }
                    }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                    ) {
                        if (showAutocomplete && filteredApps.isNotEmpty()) {
                            Popup(
                                popupPositionProvider = popupPositionProvider,
                                onDismissRequest = {},
                            ) {
                                Card(
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceBright,
                                        ),
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                ) {
                                    LazyColumn(
                                        modifier =
                                            Modifier
                                                .height(
                                                    if (filteredApps.size > 3) 200.dp else androidx.compose.ui.unit.Dp.Unspecified,
                                                ),
                                    ) {
                                        items(filteredApps) { app ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        app.icon?.let {
                                                            Image(
                                                                bitmap = it.toBitmap().asImageBitmap(),
                                                                contentDescription = null,
                                                                modifier = Modifier.size(24.dp),
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                        }
                                                        Text(text = app.label)
                                                    }
                                                },
                                                onClick = {
                                                    val prefix = textStr.substring(0, lastAtIndex)
                                                    val newText = "$prefix@${app.label} "
                                                    messageText =
                                                        TextFieldValue(
                                                            text = newText,
                                                            selection =
                                                                androidx.compose.ui.text.TextRange(
                                                                    newText.length,
                                                                ),
                                                        )
                                                    selectedAppPackageName = app.packageName
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val primaryColor = MaterialTheme.colorScheme.primary
                        val styledTextFieldValue =
                            remember(messageText, appMentionRegex, primaryColor) {
                                if (appMentionRegex != null) {
                                    val annotatedString =
                                        buildAnnotatedString {
                                            var lastIndex = 0
                                            appMentionRegex.findAll(messageText.text).forEach { matchResult ->
                                                append(messageText.text.substring(lastIndex, matchResult.range.first))
                                                pushStyle(
                                                    SpanStyle(
                                                        color = primaryColor,
                                                        fontWeight = FontWeight.Bold,
                                                    ),
                                                )
                                                append(matchResult.value)
                                                pop()
                                                lastIndex = matchResult.range.last + 1
                                            }
                                            if (lastIndex < messageText.text.length) {
                                                append(messageText.text.substring(lastIndex))
                                            }
                                        }
                                    messageText.copy(annotatedString = annotatedString)
                                } else {
                                    messageText
                                }
                            }

                        OutlinedTextField(
                            value = styledTextFieldValue,
                            onValueChange = { newValue ->
                                messageText = newValue
                                if (selectedAppPackageName != null) {
                                    val currentMention = "@"
                                    if (!newValue.text.contains(currentMention)) {
                                        selectedAppPackageName = null
                                    }
                                }
                            },
                            placeholder = { Text(stringResource(R.string.agent_demo_ask_agent)) },
                            leadingIcon =
                                if (selectedAppPackageName != null) {
                                    val pkg = selectedAppPackageName!!
                                    val app = uiState.installedApps.find { it.packageName == pkg }
                                    val iconComposable: @Composable () -> Unit = {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = CircleShape,
                                            modifier = Modifier.padding(start = 8.dp),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            ) {
                                                app?.icon?.let {
                                                    Image(
                                                        bitmap = it.toBitmap().asImageBitmap(),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = app?.label ?: pkg,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = { selectedAppPackageName = null },
                                                    modifier = Modifier.size(16.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove app target",
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    iconComposable
                                } else {
                                    null
                                },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(inputFocusRequester)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown &&
                                            (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                                        ) {
                                            if (keyEvent.isCtrlPressed || keyEvent.isShiftPressed) {
                                                val selection = messageText.selection
                                                val text = messageText.text
                                                val newText =
                                                    text.substring(0, selection.min) + "\n" + text.substring(selection.max)
                                                val newSelection = TextRange(selection.min + 1)
                                                messageText =
                                                    messageText.copy(
                                                        text = newText,
                                                        selection = newSelection,
                                                    )
                                                true
                                            } else {
                                                sendMessage()
                                                true
                                            }
                                        } else {
                                            false
                                        }
                                    },
                            maxLines = 5,
                            shape = RoundedCornerShape(24.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                                ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { sendMessage() },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = stringResource(R.string.agent_demo_send),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileModelDropdown(
    modifier: Modifier = Modifier,
    currentThread: ThreadEntity?,
    onModelSelected: (LlmModel) -> Unit,
    isAppFunctionDebuggingEnabled: Boolean = true,
    onToggleAppFunctionDebugging: (Boolean) -> Unit = {},
    onMenuClick: (() -> Unit)? = null,
) {
    val models =
        listOf(
            LlmModel.GEMINI_3_1_PRO_PREVIEW,
            LlmModel.GEMINI_3_FLASH_PREVIEW,
            LlmModel.GEMINI_3_1_FLASH_LITE_PREVIEW,
        )

    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        Surface(
            modifier = Modifier.padding(bottom = 8.dp),
            shadowElevation = 2.dp,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceBright,
        ) {
            val text =
                currentThread?.llmModel?.modelName
                    ?: stringResource(R.string.agent_demo_select_model_to_create_thread)
            val textColor =
                if (currentThread != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onMenuClick != null) {
                    IconButton(onClick = onMenuClick) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                    }
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .menuAnchor(
                                ExposedDropdownMenuAnchorType.PrimaryEditable,
                                enabled = true,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.agent_demo_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
        }

        LazyExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
            containerColor = MaterialTheme.colorScheme.surfaceBright,
            shape = RoundedCornerShape(28.dp),
        ) {
            item {
                Text(
                    "--- Gemini ---",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            items(models) { model ->
                DropdownMenuItem(
                    text = { Text(model.modelName) },
                    onClick = {
                        onModelSelected(model)
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}
