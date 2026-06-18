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
package com.example.appfunctions.agent.ui.screens.agentdemo

import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.db.entities.MessageEntity
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import com.example.appfunctions.agent.domain.AgentStatus
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.ui.screens.debugging.LazyExposedDropdownMenu
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AgentDemoScreen(viewModel: AgentDemoViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AgentDemoContent(uiState = uiState, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDemoContent(
    uiState: AgentUiState,
    onEvent: (AgentUiEvent) -> Unit,
    initialSidePanelVisible: Boolean = false,
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
                    AgentDemoLoadedScreen(
                        uiState = uiState,
                        onEvent = onEvent,
                        isWideScreen = isWideScreen,
                        drawerState = drawerState,
                        scope = scope,
                        packageManager = packageManager,
                        initialSidePanelVisible = initialSidePanelVisible,
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

@Composable
fun AgentDemoLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDemoLoadedScreen(
    uiState: AgentUiState.Loaded,
    onEvent: (AgentUiEvent) -> Unit,
    isWideScreen: Boolean,
    drawerState: DrawerState,
    scope: CoroutineScope,
    packageManager: PackageManager,
    initialSidePanelVisible: Boolean = false,
) {
    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    var isSidePanelVisible by remember { mutableStateOf(initialSidePanelVisible) }
    var selectedAppPackageName by remember { mutableStateOf<String?>(null) }

    val chipBgColor = MaterialTheme.colorScheme.primaryContainer
    val chipTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val visualTransformation =
        remember(uiState.installedApps, chipTextColor) {
            InlineAppScopingVisualTransformation(uiState.installedApps, chipTextColor)
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Unspecified,
        topBar = {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModelDropdown(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    currentThread = uiState.currentThread,
                    onModelSelected = { onEvent(AgentUiEvent.OnModelSelected(it)) },
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
                    .imePadding()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                    ),
        ) {
            // Side Panel (only for wide screens)
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

            // Main Chat Area
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Messages List
                LazyColumn(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                    reverseLayout = true,
                ) {
                    // Status item at the bottom (above input) if not
                    // idle
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
                            onConfirmAction = { onEvent(AgentUiEvent.OnConfirmAction(it)) },
                        )
                    }
                }

                val sendMessage = {
                    val textStr = messageText.text
                    if (textStr.isNotBlank() && uiState.status == AgentStatus.Idle) {
                        onEvent(AgentUiEvent.OnSendMessage(textStr, selectedAppPackageName))
                        messageText = TextFieldValue("")
                        selectedAppPackageName = null
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

                // Input area
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { newValue ->
                            messageText = newValue
                            val currentText = newValue.text
                            if (selectedAppPackageName != null && appMentionRegex != null) {
                                if (!appMentionRegex.containsMatchIn(currentText)) {
                                    selectedAppPackageName = null
                                }
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (
                                        (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) &&
                                        keyEvent.type == KeyEventType.KeyDown
                                    ) {
                                        sendMessage()
                                        true
                                    } else {
                                        false
                                    }
                                },
                        enabled = uiState.status == AgentStatus.Idle,
                        shape = CircleShape,
                        placeholder = { Text(stringResource(R.string.agent_demo_ask_agent)) },
                        visualTransformation = visualTransformation,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                            ),
                        trailingIcon = {
                            IconButton(
                                onClick = sendMessage,
                                enabled =
                                    messageText.text.isNotBlank() &&
                                        uiState.status == AgentStatus.Idle,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription =
                                        stringResource(R.string.agent_demo_send),
                                )
                            }
                        },
                    )

                    if (showAutocomplete && filteredApps.isNotEmpty()) {
                        Popup(
                            popupPositionProvider = popupPositionProvider,
                            onDismissRequest = {},
                            properties = PopupProperties(focusable = false),
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(0.9f),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceBright,
                                    ),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    filteredApps.take(5).forEach { app ->
                                        DropdownMenuItem(
                                            text = { Text(app.label) },
                                            onClick = {
                                                val currentText = messageText.text
                                                val selectionStart = messageText.selection.start
                                                val textBeforeCursor =
                                                    currentText.take(
                                                        selectionStart,
                                                    )
                                                val textAfterCursor =
                                                    currentText.drop(
                                                        selectionStart,
                                                    )
                                                val mentionIndex = textBeforeCursor.lastIndexOf('@')
                                                if (mentionIndex >= 0) {
                                                    val textBeforeMention =
                                                        textBeforeCursor.substring(
                                                            0,
                                                            mentionIndex,
                                                        )
                                                    val newText =
                                                        "$textBeforeMention@${app.label} $textAfterCursor"
                                                    val newCursorPosition =
                                                        mentionIndex + app.label.length + 2
                                                    messageText =
                                                        TextFieldValue(
                                                            text = newText,
                                                            selection =
                                                                TextRange(
                                                                    newCursorPosition,
                                                                ),
                                                        )
                                                    selectedAppPackageName = app.packageName
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDropdown(
    modifier: Modifier = Modifier,
    currentThread: ThreadEntity?,
    onModelSelected: (LlmModel) -> Unit,
    onMenuClick: () -> Unit,
) {
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
                IconButton(onClick = onMenuClick) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
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
            val models =
                listOf(
                    LlmModel.GEMINI_3_1_PRO_PREVIEW,
                    LlmModel.GEMINI_3_FLASH_PREVIEW,
                    LlmModel.GEMINI_3_1_FLASH_LITE_PREVIEW,
                )
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

@Composable
fun MessageBubble(
    message: MessageEntity,
    isValidAction: Boolean,
    installedApps: List<AppInfo>,
    onConfirmAction: (String) -> Unit,
) {
    val alignment = if (message.role == MessageRole.USER) Alignment.End else Alignment.Start
    val isError = message.processingStatus == MessageProcessingStatus.FAILED
    val backgroundColor =
        when {
            isError -> MaterialTheme.colorScheme.errorContainer
            message.role == MessageRole.USER -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceBright
        }
    val textColor =
        when {
            isError -> MaterialTheme.colorScheme.onErrorContainer
            message.role == MessageRole.USER -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = alignment,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = backgroundColor,
            shadowElevation = if (message.role == MessageRole.ASSISTANT) 1.dp else 0.dp,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                SelectionContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isError) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = stringResource(R.string.debugging_error),
                                tint = textColor,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        val contentText =
                            if (message.textContent.isEmpty() &&
                                message.pendingIntentId != null
                            ) {
                                stringResource(R.string.agent_demo_action_confirmation_needed)
                            } else {
                                message.textContent
                            }
                        if (message.role != MessageRole.USER) {
                            Markdown(content = contentText)
                        } else {
                            val chipBgColor = MaterialTheme.colorScheme.primary
                            val chipTextColor = MaterialTheme.colorScheme.onPrimary
                            val formattedText =
                                remember(contentText, installedApps) {
                                    formatMessageText(contentText, installedApps)
                                }
                            val textMeasurer = rememberTextMeasurer()
                            val typographyStyle = MaterialTheme.typography.bodyLarge
                            val density = LocalDensity.current

                            val inlineContentMap =
                                remember(
                                    contentText,
                                    installedApps,
                                    chipBgColor,
                                    chipTextColor,
                                    density,
                                ) {
                                    val map = mutableMapOf<String, InlineTextContent>()
                                    if (installedApps.isNotEmpty() && contentText.contains("@")) {
                                        val appLabelsPattern =
                                            installedApps.joinToString(
                                                "|",
                                            ) { Regex.escape(it.label) }
                                        val regex =
                                            Regex("@($appLabelsPattern)\\b", RegexOption.IGNORE_CASE)
                                        regex.findAll(contentText).forEachIndexed { index, match ->
                                            val id = "chip_$index"
                                            val appName = match.value
                                            val measured =
                                                textMeasurer.measure(
                                                    text = appName,
                                                    style =
                                                        typographyStyle.copy(
                                                            fontWeight = FontWeight.Bold,
                                                        ),
                                                )
                                            val widthSp =
                                                with(
                                                    density,
                                                ) { (measured.size.width + 8.dp.roundToPx()).toSp() }
                                            val heightSp =
                                                with(
                                                    density,
                                                ) { (measured.size.height + 2.dp.roundToPx()).toSp() }

                                            map[id] =
                                                InlineTextContent(
                                                    Placeholder(
                                                        width = widthSp,
                                                        height = heightSp,
                                                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                                                    ),
                                                ) {
                                                    Surface(
                                                        shape =
                                                            androidx.compose.foundation.shape.RoundedCornerShape(
                                                                6.dp,
                                                            ),
                                                        color = chipBgColor,
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = appName,
                                                                color = chipTextColor,
                                                                style =
                                                                    typographyStyle.copy(
                                                                        fontWeight = FontWeight.Bold,
                                                                    ),
                                                                modifier =
                                                                    Modifier.padding(
                                                                        horizontal = 4.dp,
                                                                        vertical = 1.dp,
                                                                    ),
                                                            )
                                                        }
                                                    }
                                                }
                                        }
                                    }
                                    map
                                }

                            Text(
                                text = formattedText,
                                inlineContent = inlineContentMap,
                                color = textColor,
                                style = typographyStyle,
                            )
                        }
                    }
                }

                if (message.pendingIntentId != null) {
                    Spacer(modifier = Modifier.padding(vertical = 8.dp))
                    androidx.compose.material3.Button(
                        onClick = { onConfirmAction(message.pendingIntentId) },
                        enabled = isValidAction,
                        shape = CircleShape,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    ) {
                        Text(
                            if (isValidAction) {
                                stringResource(R.string.agent_demo_confirm_action)
                            } else {
                                stringResource(R.string.agent_demo_action_expired)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(
    status: AgentStatus,
    packageManager: PackageManager,
) {
    when (status) {
        AgentStatus.Thinking -> {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.agent_demo_thinking),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        is AgentStatus.InvokingTool -> {
            val appName =
                try {
                    val appInfo = packageManager.getApplicationInfo(status.packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    status.packageName
                }
            val appIcon =
                try {
                    packageManager.getApplicationIcon(status.packageName)
                } catch (e: Exception) {
                    null
                }

            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceBright,
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    appIcon?.let {
                        Image(
                            bitmap = it.toBitmap().asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        Text(appName, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.agent_demo_connecting),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        AgentStatus.Idle -> {
            // Nothing to show
        }
    }
}

@Composable
fun ChatHistorySidePanel(
    threads: List<ThreadEntity>,
    currentThread: ThreadEntity?,
    onEvent: (AgentUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(280.dp)
                .fillMaxHeight()
                .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.agent_demo_chat_history),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = threads,
                key = { thread -> thread.threadId },
            ) { thread ->
                val isSelected = thread.threadId == currentThread?.threadId
                val backgroundColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                val textColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                onEvent(AgentUiEvent.OnThreadSelected(thread.threadId))
                            },
                    shape = MaterialTheme.shapes.medium,
                    color = backgroundColor,
                    contentColor = textColor,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = thread.llmModel.modelName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                        )
                        Text(
                            text = "ID: ${thread.threadId.take(8)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

class InlineAppScopingVisualTransformation(
    private val installedApps: List<AppInfo>,
    private val chipTextColor: Color,
) : VisualTransformation {
    private val regex: Regex? =
        if (installedApps.isNotEmpty()) {
            val appLabelsPattern = installedApps.joinToString("|") { Regex.escape(it.label) }
            Regex("@($appLabelsPattern)\\b", RegexOption.IGNORE_CASE)
        } else {
            null
        }

    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text
        val currentRegex = regex
        if (currentRegex == null || !rawText.contains("@")) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val matches = currentRegex.findAll(rawText)

        val annotatedString =
            buildAnnotatedString {
                var lastIndex = 0
                matches.forEach { match ->
                    append(rawText.substring(lastIndex, match.range.first))
                    pushStringAnnotation(tag = "mention", annotation = match.value)
                    withStyle(
                        SpanStyle(
                            color = chipTextColor,
                            fontWeight = FontWeight.Bold,
                        ),
                    ) {
                        append(match.value)
                    }
                    pop()
                    lastIndex = match.range.last + 1
                }
                if (lastIndex < rawText.length) {
                    append(rawText.substring(lastIndex))
                }
            }
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}

fun formatMessageText(
    text: String,
    installedApps: List<AppInfo>,
): AnnotatedString {
    if (installedApps.isEmpty() || !text.contains("@")) {
        return AnnotatedString(text)
    }
    val appLabelsPattern = installedApps.joinToString("|") { Regex.escape(it.label) }
    val regex = Regex("@($appLabelsPattern)\\b", RegexOption.IGNORE_CASE)
    val matches = regex.findAll(text)

    return buildAnnotatedString {
        var lastIndex = 0
        matches.forEachIndexed { index, match ->
            val precedingText = text.substring(lastIndex, match.range.first)
            if (precedingText.isNotEmpty()) {
                append(precedingText)
            }
            appendInlineContent(id = "chip_$index", alternateText = match.value)
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}
