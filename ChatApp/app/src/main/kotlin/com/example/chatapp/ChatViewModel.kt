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
package com.example.chatapp

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.appfunctions.AppFunctions.Recipient
import com.example.chatapp.data.CallManager
import com.example.chatapp.data.MessageRepository
import com.example.chatapp.data.RecipientsRepository
import com.example.chatapp.uicomponents.DisplayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Represents the current state of the bot's response generation process.
 */
sealed interface BotMessageState {
    /**
     * Represents the idle state where the bot is ready to receive a new message
     * or has finished processing the previous one.
     */
    data object WaitingForMessage : BotMessageState

    /**
     * State indicating the bot is actively generating a response to the user's message.
     */
    data object Generating : BotMessageState

    /**
     * Represents an error state encountered while communicating with the bot.
     *
     * @property errorMessage A descriptive message detailing the nature of the failure.
     */
    data class Error(val errorMessage: String) : BotMessageState
}

/**
 * Represents the UI state for the chatbot screen.
 */
data class ChatbotUiState(
    /** The list of messages to be displayed in the chat history. */
    val messages: List<DisplayMessage> = listOf(),
    /** The current state of the bot's response generation. */
    val botMessageState: BotMessageState = BotMessageState.WaitingForMessage,
)

@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val messageRepository: MessageRepository,
        private val callManager: CallManager,
        private val recipientsRepository: RecipientsRepository,
    ) : ViewModel() {
        private val recipientId: String = savedStateHandle.get<String>("recipientId") ?: "bot"

        val recipient: Recipient =
            recipientsRepository.getRecipientById(recipientId)
                ?: recipientsRepository.getGroupById(recipientId)?.let {
                    Recipient(it.id, it.name, "")
                }
                ?: recipientsRepository.getRecipientByName(recipientId)
                ?: if (recipientId == "bot") {
                    Recipient("bot", "Assistant", "bot@example.com")
                } else {
                    Recipient(recipientId, recipientId, "")
                }

        private val _uiState = MutableStateFlow(ChatbotUiState())
        val uiState: StateFlow<ChatbotUiState> = _uiState.asStateFlow()

        val activeCall: StateFlow<Recipient?> = callManager.activeCall

        init {
            viewModelScope.launch {
                messageRepository.getMessages(recipientId).collect { msgs ->
                    _uiState.update { it.copy(messages = msgs) }
                }
            }
        }

        fun startCall() {
            callManager.startCall(recipientId)
        }

        fun endCall() {
            val activeRecipient = callManager.activeCall.value
            callManager.endCall()

            if (activeRecipient != null) {
                val callLogMessage =
                    DisplayMessage(
                        content = "📞 Call ended",
                        sentAt = System.currentTimeMillis(),
                        isInbound = false,
                        senderName = "Me",
                    )
                messageRepository.saveMessage(activeRecipient.id, callLogMessage)
            }
        }

        fun sendMessage(
            message: String,
            images: List<Uri> = emptyList(),
        ) {
            viewModelScope.launch {
                try {
                    _uiState.update { it.copy(botMessageState = BotMessageState.Generating) }

                    val userMessage =
                        DisplayMessage(
                            content = message,
                            sentAt = System.currentTimeMillis(),
                            senderName = "Me",
                            images = images,
                        )
                    messageRepository.saveMessage(recipientId, userMessage)

                    val response = messageRepository.sendToBot(message)
                    val senderName =
                        if (recipientId.startsWith("g")) {
                            recipientsRepository.getGroupById(recipientId)?.recipients?.randomOrNull()?.name ?: recipient.name
                        } else {
                            recipient.name
                        }
                    val botMessage =
                        DisplayMessage(
                            content = response.trim(),
                            sentAt = System.currentTimeMillis(),
                            isInbound = true,
                            senderName = senderName,
                        )
                    messageRepository.saveMessage(recipientId, botMessage)

                    _uiState.update { it.copy(botMessageState = BotMessageState.WaitingForMessage) }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            botMessageState =
                                BotMessageState.Error(
                                    e.localizedMessage ?: "Something went wrong, try again",
                                ),
                        )
                    }
                }
            }
        }

        fun dismissError() {
            _uiState.update {
                it.copy(botMessageState = BotMessageState.WaitingForMessage)
            }
        }
    }
