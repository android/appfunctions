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
package com.example.chatapp.uicomponents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.chatapp.BotMessageState
import com.example.chatapp.ChatViewModel
import com.example.chatapp.InputBar
import com.example.chatapp.R
import com.example.chatapp.data.DisplayMessage
import com.example.chatapp.util.linkifyString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    recipientId: String,
    viewModel: ChatViewModel =
        hiltViewModel(
            key = recipientId,
            creationCallback = { factory: ChatViewModel.Factory ->
                factory.create(recipientId)
            },
        ),
    onCallClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topAppBarState)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var message by rememberSaveable { mutableStateOf("") }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors =
                    topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                title = {
                    Text(text = viewModel.recipient.name)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onCallClick) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call",
                        )
                    }
                },
            )
        },
        bottomBar = {
            InputBar(
                value = message,
                placeholder = stringResource(R.string.input_placeholder),
                onInputChanged = {
                    message = it
                },
                onSendClick = { uris ->
                    viewModel.sendMessage(message, uris)
                    message = ""
                },
                sendEnabled = uiState.botMessageState !is BotMessageState.Generating,
                modifier =
                    Modifier
                        .navigationBarsPadding()
                        .imePadding(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
        ) {
            MessageList(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .weight(1f),
                messages = uiState.messages,
                contentPadding = PaddingValues(bottom = 8.dp),
            )

            when (val state = uiState.botMessageState) {
                is BotMessageState.Generating -> {
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .padding(vertical = 8.dp)
                                .align(Alignment.CenterHorizontally),
                    )
                }

                is BotMessageState.Error -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissError() },
                        title = { Text(text = stringResource(R.string.error)) },
                        text = { Text(text = state.errorMessage) },
                        confirmButton = {
                            Button(onClick = { viewModel.dismissError() }) {
                                Text(text = stringResource(R.string.dismiss_button))
                            }
                        },
                    )
                }

                else -> { // No additional UI for waiting state
                }
            }
        }
    }
}

@Composable
fun MessageList(
    messages: List<DisplayMessage>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
    ) {
        items(items = messages) { message ->
            MessageBubble(
                message = message,
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: DisplayMessage,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isInbound) Alignment.Start else Alignment.End,
    ) {
        if (message.isInbound && message.senderName != null) {
            Text(
                text = message.senderName ?: "???",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            color =
                if (message.isInbound) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
            shape = MaterialTheme.shapes.large,
        ) {
            Column {
                if (message.images.isNotEmpty()) {
                    if (message.images.size == 1) {
                        AsyncImage(
                            model = message.images[0],
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(MaterialTheme.shapes.large),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(message.images) { imageUri ->
                                AsyncImage(
                                    model = imageUri,
                                    contentDescription = null,
                                    modifier =
                                        Modifier
                                            .size(150.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
                if (message.content.isNotEmpty()) {
                    val linkColor = if (message.isInbound) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.inversePrimary
                    }
                    val annotatedText = remember(message.content, linkColor) {
                        linkifyString(message.content, linkColor = linkColor)
                    }
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = annotatedText,
                    )
                }
            }
        }
    }
}
