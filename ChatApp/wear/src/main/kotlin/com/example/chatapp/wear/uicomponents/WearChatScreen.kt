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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import com.example.chatapp.ChatViewModel
import com.example.chatapp.util.linkifyString

@Composable
fun WearChatScreen(
    recipientId: String,
    viewModel: ChatViewModel =
        hiltViewModel(
            key = recipientId,
            creationCallback = { factory: ChatViewModel.Factory ->
                factory.create(recipientId)
            },
        ),
    onCallClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recipient = viewModel.recipient

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Button(
                onClick = onCallClick,
            ) {
                Text(text = "📞")
            }
        }

        item {
            ListHeader {
                Text(text = recipient.name)
            }
        }

        items(uiState.messages.reversed()) { message ->
            TitleCard(
                onClick = { },
                title = { Text(text = if (message.isInbound) message.senderName ?: "Sender" else "Me") },
            ) {
                val linkColor = MaterialTheme.colorScheme.primary
                val annotatedText = remember(message.content, linkColor) {
                    linkifyString(text = message.content, linkColor = linkColor)
                }
                Text(text = annotatedText)
            }
        }
    }
}
