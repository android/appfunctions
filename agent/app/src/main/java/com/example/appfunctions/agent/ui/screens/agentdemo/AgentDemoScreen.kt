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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.db.entities.MessageEntity
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import com.example.appfunctions.agent.domain.AgentStatus
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.ui.contracts.AgentDemoScreenLayout
import com.example.appfunctions.agent.ui.layout.rememberFormFactor
import com.example.appfunctions.agent.ui.layout.resolveAgentDemoLayout

@Composable
fun AgentDemoScreen(viewModel: AgentDemoViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AgentDemoContent(uiState = uiState, onEvent = viewModel::onEvent)
}

@Composable
fun AgentDemoContent(
    uiState: AgentUiState,
    onEvent: (AgentUiEvent) -> Unit,
    initialSidePanelVisible: Boolean = false,
) {
    val layout: AgentDemoScreenLayout = rememberFormFactor().resolveAgentDemoLayout()

    layout.Content(
        uiState = uiState,
        onEvent = onEvent,
        initialSidePanelVisible = initialSidePanelVisible,
        modifier = Modifier,
    )
}

@Composable
fun AgentDemoLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isValidAction: Boolean,
    installedApps: List<AppInfo>,
    showAppFunctionDebugDetails: Boolean = true,
    enableTextSelection: Boolean = true,
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

    val parsedData =
        remember(message.textContent) {
            parseMessageContent(message.textContent)
        }
    val cleanContentText = parsedData.first
    val parsedCalls = parsedData.second

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalAlignment = alignment,
    ) {
        if (cleanContentText.isNotEmpty() || message.attachments.isNotEmpty()) {
            Surface(
                color = backgroundColor,
                shape =
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (message.role == MessageRole.USER) 20.dp else 4.dp,
                        bottomEnd = if (message.role == MessageRole.USER) 4.dp else 20.dp,
                    ),
                shadowElevation = 1.dp,
                modifier =
                    Modifier.fillMaxWidth(
                        if (message.role == MessageRole.USER) 0.85f else 0.95f,
                    ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (message.attachments.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            message.attachments.forEach { _ ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier =
                                            Modifier.padding(
                                                horizontal = 12.dp,
                                                vertical = 8.dp,
                                            ),
                                    ) {
                                        Text(
                                            text = "Attachment",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (cleanContentText.isNotEmpty()) {
                        val typographyStyle =
                            if (message.role == MessageRole.USER) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.bodyMedium
                            }

                        if (enableTextSelection) {
                            val annotatedText =
                                remember(cleanContentText, installedApps) {
                                    formatMessageText(cleanContentText, installedApps)
                                }

                            SelectionContainer {
                                Text(
                                    text = annotatedText,
                                    color = textColor,
                                    style = typographyStyle,
                                )
                            }
                        } else {
                            Text(
                                text = cleanContentText,
                                color = textColor,
                                style = typographyStyle,
                            )
                        }
                    }
                }
            }
        }

        if (showAppFunctionDebugDetails && parsedCalls.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            parsedCalls.forEach { call ->
                AppFunctionCallHintCard(call, installedApps)
            }
        }

        if (message.pendingIntentId != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(0.95f),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text =
                            if (isValidAction) {
                                stringResource(R.string.agent_demo_action_confirmation_needed)
                            } else {
                                stringResource(R.string.agent_demo_action_expired)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    if (isValidAction) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { onConfirmAction(message.pendingIntentId) },
                        ) {
                            Text(stringResource(R.string.agent_demo_confirm_action))
                        }
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
                } catch (_: Exception) {
                    status.packageName
                }
            val appIcon =
                try {
                    packageManager.getApplicationIcon(status.packageName)
                } catch (_: Exception) {
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
                .fillMaxHeight()
                .width(280.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.agent_demo_chat_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Surface(
                onClick = { onEvent(AgentUiEvent.OnCreateThread(LlmModel.GEMINI_3_1_PRO_PREVIEW)) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "New",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(threads) { thread ->
                val isSelected = thread.threadId == currentThread?.threadId
                Surface(
                    onClick = { onEvent(AgentUiEvent.OnThreadSelected(thread.threadId)) },
                    shape = RoundedCornerShape(12.dp),
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = thread.llmModel.modelName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                            Text(
                                text = "ID: ${thread.threadId.take(8)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatMessageText(
    text: String,
    installedApps: List<AppInfo>,
): AnnotatedString =
    buildAnnotatedString {
        if (installedApps.isEmpty()) {
            append(text)
            return@buildAnnotatedString
        }

        val appLabelsPattern =
            installedApps.joinToString("|") { Regex.escape(it.label) }
        val regex = Regex("@($appLabelsPattern)\\b", RegexOption.IGNORE_CASE)

        var lastIndex = 0
        regex.findAll(text).forEach { matchResult ->
            append(text.substring(lastIndex, matchResult.range.first))

            val appName = matchResult.groupValues[1]
            val matchedApp =
                installedApps.find { it.label.equals(appName, ignoreCase = true) }

            if (matchedApp != null) {
                appendInlineContent(matchedApp.label, "@${matchedApp.label}")
            } else {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(matchResult.value)
                }
            }

            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }

data class ParsedAppFunctionCall(
    val packageName: String,
    val functionId: String,
    val arguments: Map<String, Any?>,
    val response: String?,
)

fun parseMessageContent(text: String): Pair<String, List<ParsedAppFunctionCall>> {
    val callRegex = Regex("@@AppFunctionCall:(.*?)@@")
    val matches = callRegex.findAll(text)
    val calls = mutableListOf<ParsedAppFunctionCall>()

    var cleanText = text
    matches.forEach { match ->
        cleanText = cleanText.replace(match.value, "")
    }

    return Pair(cleanText.trim(), calls)
}

@Composable
fun AppFunctionCallHintCard(
    call: ParsedAppFunctionCall,
    installedApps: List<AppInfo>,
    modifier: Modifier = Modifier,
) {
    val appInfo =
        remember(call.packageName, installedApps) {
            installedApps.find { it.packageName == call.packageName }
        }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            appInfo?.icon?.let {
                Image(
                    bitmap = it.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${appInfo?.label ?: call.packageName} → ${call.functionId}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
