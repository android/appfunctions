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

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.LlmProviderName
import com.example.appfunctions.agent.data.db.entities.MessageEntity
import com.example.appfunctions.agent.data.db.entities.MessageProcessingStatus
import com.example.appfunctions.agent.data.db.entities.MessageRole
import com.example.appfunctions.agent.data.db.entities.ThreadEntity
import com.example.appfunctions.agent.domain.AgentStatus

private val previewFlashModel = LlmModel(LlmProviderName.GEMINI, "gemini-3-flash-preview")
private val previewProModel = LlmModel(LlmProviderName.GEMINI, "gemini-3.1-pro-preview")

/** Screenshot test for [AgentDemoScreen]. */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun AgentDemoScreenLoadingPreview() {
    AgentDemoContent(uiState = AgentUiState.Loading, onEvent = {})
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun AgentDemoScreenNoThreadPreview() {
    AgentDemoContent(
        uiState =
            AgentUiState.Loaded(
                messages = emptyList(),
                status = AgentStatus.Idle,
                currentThread = null,
            ),
        onEvent = {},
    )
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun AgentDemoScreenScreenshotPreview() {
    val thread =
        ThreadEntity("thread-1", System.currentTimeMillis(), previewFlashModel, null)
    val messages =
        listOf(
            MessageEntity(
                "1",
                "thread-1",
                MessageRole.USER,
                "Hi Agent!",
                System.currentTimeMillis(),
                MessageProcessingStatus.PROCESSED,
            ),
            MessageEntity(
                "2",
                "thread-1",
                MessageRole.ASSISTANT,
                "Hello! I can help you with:\n- **Bold** text\n- *Italic* text\n- [Links](https://example.com)",
                System.currentTimeMillis(),
                MessageProcessingStatus.PROCESSED,
            ),
        )
    AgentDemoContent(
        uiState =
            AgentUiState.Loaded(
                messages = messages,
                status = AgentStatus.Idle,
                currentThread = thread,
            ),
        onEvent = {},
    )
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun AgentDemoScreenThinkingPreview() {
    val thread =
        ThreadEntity("thread-1", System.currentTimeMillis(), previewFlashModel, null)
    val messages =
        listOf(
            MessageEntity(
                "1",
                "thread-1",
                MessageRole.USER,
                "Hi Agent!",
                System.currentTimeMillis(),
                MessageProcessingStatus.PROCESSED,
            ),
        )
    AgentDemoContent(
        uiState =
            AgentUiState.Loaded(
                messages = messages,
                status = AgentStatus.Thinking,
                currentThread = thread,
            ),
        onEvent = {},
    )
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun AgentDemoScreenInvokingToolPreview() {
    val thread =
        ThreadEntity("thread-1", System.currentTimeMillis(), previewFlashModel, null)
    val messages =
        listOf(
            MessageEntity(
                "1",
                "thread-1",
                MessageRole.USER,
                "Hi Agent!",
                System.currentTimeMillis(),
                MessageProcessingStatus.PROCESSED,
            ),
        )
    AgentDemoContent(
        uiState =
            AgentUiState.Loaded(
                messages = messages,
                status = AgentStatus.InvokingTool("testFunction", "com.example.app"),
                currentThread = thread,
            ),
        onEvent = {},
    )
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun AgentDemoScreenConfirmActionPreview() {
    val thread =
        ThreadEntity("thread-1", System.currentTimeMillis(), previewFlashModel, null)
    val messages =
        listOf(
            MessageEntity(
                "1",
                "thread-1",
                MessageRole.USER,
                "Run action",
                System.currentTimeMillis(),
                MessageProcessingStatus.PROCESSED,
            ),
            MessageEntity(
                "2",
                "thread-1",
                MessageRole.ASSISTANT,
                "",
                System.currentTimeMillis(),
                MessageProcessingStatus.PROCESSED,
                pendingIntentId = "action-1",
            ),
        )
    AgentDemoContent(
        uiState =
            AgentUiState.Loaded(
                messages = messages,
                status = AgentStatus.Idle,
                currentThread = thread,
                activePendingActionIds = setOf("action-1"),
            ),
        onEvent = {},
    )
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun AgentDemoScreenActionExpiredPreview() {
    val thread =
        ThreadEntity("thread-1", System.currentTimeMillis(), previewFlashModel, null)
    val messages =
        listOf(
            MessageEntity(
                "1",
                "thread-1",
                MessageRole.USER,
                "Run action",
                System.currentTimeMillis(),
                MessageProcessingStatus.PROCESSED,
            ),
            MessageEntity(
                "2",
                "thread-1",
                MessageRole.ASSISTANT,
                "",
                System.currentTimeMillis(),
                MessageProcessingStatus.PROCESSED,
                pendingIntentId = "action-1",
            ),
        )
    AgentDemoContent(
        uiState =
            AgentUiState.Loaded(
                messages = messages,
                status = AgentStatus.Idle,
                currentThread = thread,
                activePendingActionIds = emptySet(),
            ),
        onEvent = {},
    )
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun AgentDemoScreenErrorPreview() {
    val thread =
        ThreadEntity("thread-1", System.currentTimeMillis(), previewFlashModel, null)
    val messages =
        listOf(
            MessageEntity(
                "1",
                "thread-1",
                MessageRole.USER,
                "Hi Agent!",
                System.currentTimeMillis(),
                MessageProcessingStatus.PROCESSED,
            ),
            MessageEntity(
                "2",
                "thread-1",
                MessageRole.ASSISTANT,
                "API key is missing for GEMINI",
                System.currentTimeMillis(),
                MessageProcessingStatus.FAILED,
            ),
        )
    AgentDemoContent(
        uiState =
            AgentUiState.Loaded(
                messages = messages,
                status = AgentStatus.Idle,
                currentThread = thread,
            ),
        onEvent = {},
    )
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun AgentDemoScreenSidePanelPreview() {
    val thread1 =
        ThreadEntity("thread-1", System.currentTimeMillis(), previewFlashModel, null)
    val thread2 =
        ThreadEntity("thread-2", System.currentTimeMillis(), previewProModel, null)
    val threads = listOf(thread1, thread2)

    val messages =
        listOf(
            MessageEntity(
                "1",
                "thread-1",
                MessageRole.USER,
                "Hi Agent!",
                System.currentTimeMillis(),
                MessageProcessingStatus.PROCESSED,
            ),
            MessageEntity(
                "2",
                "thread-1",
                MessageRole.ASSISTANT,
                "Hello! How can I help you today?",
                System.currentTimeMillis(),
                MessageProcessingStatus.PROCESSED,
            ),
        )

    AgentDemoContent(
        uiState =
            AgentUiState.Loaded(
                messages = messages,
                status = AgentStatus.Idle,
                currentThread = thread1,
                threads = threads,
            ),
        onEvent = {},
        initialSidePanelVisible = true,
    )
}
