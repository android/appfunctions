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
package com.example.chatapp.appfunctions

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.appfunctions.AppFunctionStringValueConstraint
import com.example.chatapp.data.CallManager
import com.example.chatapp.data.DisplayMessage
import com.example.chatapp.data.MessageRepository
import com.example.chatapp.data.RecipientsRepository
import com.example.chatapp.data.WallpaperRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Service entry point for chat-related AppFunctions such as searching contacts, sending messages, and making calls.
 */
@RequiresApi(36)
@AndroidEntryPoint
@AppFunctionServiceEntryPoint(
    serviceName = "ChatAppFunctionService",
    appFunctionXmlFileName = "chat_app_function_service",
)
abstract class BaseChatAppFunctionService : AppFunctionService() {
    @Inject lateinit var messageRepository: MessageRepository

    @Inject lateinit var recipientsRepository: RecipientsRepository

    @Inject lateinit var callManager: CallManager

    @Inject lateinit var wallpaperRepository: WallpaperRepository

    /**
     * Search for message recipients or chat groups by name or email.
     * Required workflow: Call this before "send" or "makeCall" to obtain a valid endpointValue (unique ID).
     *
     * @param query Search string for contact name, email, or group name. Throws [AppFunctionInvalidArgumentException] if empty.
     * @param contactType Filter results by entity type. Accepts "INDIVIDUAL", "GROUP", or "ANY".
     * @return List of [ContactSearchResult] objects matching the query.
     * @throws AppFunctionInvalidArgumentException If query is empty or blank, an invalid contactType is provided, or if no matching contact or group is found. If thrown, suggest the user clarify the recipient or group name.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchContacts(
        query: String,
        @AppFunctionStringValueConstraint(enumValues = ["INDIVIDUAL", "GROUP", "ANY"])
        contactType: String,
    ): List<ContactSearchResult> {
        if (query.isBlank()) {
            throw AppFunctionInvalidArgumentException("Query cannot be empty")
        }
        val recipients =
            when (contactType) {
                "INDIVIDUAL" -> {
                    recipientsRepository.searchRecipients(query, 3)
                }
                "GROUP" -> {
                    recipientsRepository.searchGroups(query, 3).map {
                        ContactSearchResult(
                            contactDisplayName = it.name,
                            contactType = "GROUP",
                            endpointValue = it.id,
                            endpointDisplayName = it.name,
                        )
                    }
                }
                "ANY" -> {
                    recipientsRepository.searchAny(query, maxCount = 3)
                }
                else -> {
                    throw AppFunctionInvalidArgumentException(
                        "Invalid contactType: $contactType. Must be INDIVIDUAL, GROUP, or ANY.",
                    )
                }
            }

        if (recipients.isEmpty()) {
            throw AppFunctionInvalidArgumentException(
                "$contactType with name $query not found. Ask the user to clarify the name",
            )
        }
        return recipients
    }

    /**
     * Send a text message with optional image attachments to an individual contact or group.
     * Required workflow: Call "searchContacts" first to obtain a valid endpointValue.
     *
     * @param endpointValue The unique identifier for the recipient or group obtained from searchContacts.
     * @param messageBody The text content of the message to send. Cannot be empty or blank.
     * @param imageUris Optional list of image URIs to attach to the message.
     * @return A human-readable message indicating the error or confirmation.
     * @throws AppFunctionInvalidArgumentException If messageBody is empty or blank. If thrown, ask the user to provide the message content to send.
     * @throws AppFunctionElementNotFoundException If no contact or group matches endpointValue. If thrown, call "searchContacts" to find the correct ID.
     * @throws AppFunctionAppUnknownException If sending fails due to a repository error. If thrown, suggest the user retry later.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun sendMessage(
        endpointValue: String,
        messageBody: String,
        imageUris: List<Uri>? = null,
    ): String {
        if (messageBody.isBlank()) {
            throw AppFunctionInvalidArgumentException("Message body cannot be empty")
        }
        val displayName =
            recipientsRepository.getRecipientById(endpointValue)?.name
                ?: recipientsRepository.getGroupById(endpointValue)?.name
                ?: throw AppFunctionElementNotFoundException(
                    "No contact or group found for endpointValue: $endpointValue",
                )

        try {
            messageRepository.send(
                text = messageBody,
                recipientIds = listOf(endpointValue),
                imageUris = imageUris?.map { it.toString() },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppFunctionAppUnknownException("Failed to send message: ${e.message}")
        }

        return "Message sent to: $displayName."
    }

    /**
     * Initiate a voice call with an individual contact or group.
     * Required workflow: Call "searchContacts" first to obtain a valid endpointValue.
     *
     * @param endpointValue The unique identifier for the recipient or group obtained from searchContacts.
     * @return A [PendingIntent] that launches the call activity in the application UI.
     * @throws AppFunctionElementNotFoundException If no recipient exists for endpointValue. If thrown, call "searchContacts" to find the correct ID.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun makeCall(endpointValue: String): PendingIntent {
        val recipient =
            recipientsRepository.getRecipientById(endpointValue)
                ?: throw AppFunctionElementNotFoundException(
                    "No recipient exists for endpointValue: $endpointValue",
                )

        callManager.startCall(recipient.id)

        return PendingIntent.getActivity(
            this,
            0,
            Intent().setClassName(this, "com.example.chatapp.MainActivity")
                .apply { putExtra("nav_route", "call/${recipient.id}") },
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Updates the wallpaper image for a specific chat conversation.
     *
     * @param chatId The unique identifier for the recipient or chat group.
     * @param wallpaperUri The URI of the image file to set as the chat wallpaper.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun updateChatWallpaper(
        chatId: String,
        wallpaperUri: Uri,
    ): Boolean {
        val resolvedId =
            recipientsRepository.getRecipientById(chatId)?.id
                ?: recipientsRepository.getGroupById(chatId)?.id
                ?: recipientsRepository.searchAny(chatId, maxCount = 1).firstOrNull()?.endpointValue
                ?: chatId
        val inputStream =
            try {
                contentResolver.openInputStream(wallpaperUri)
            } catch (e: Exception) {
                throw AppFunctionInvalidArgumentException("Cannot open wallpaper stream: ${e.message}")
            } ?: throw AppFunctionInvalidArgumentException("Cannot open wallpaper stream")
        return inputStream.use { stream ->
            wallpaperRepository.setWallpaper(resolvedId, stream)
        }
    }

    /**
     * Search for messages containing a query string, optionally filtered by a specific recipient.
     *
     * @param query The text to search for within message bodies. Cannot be empty.
     * @param endpointValue Optional unique identifier of the contact or group to restrict the search to.
     * @return List of [MessagesSearchResult] matching the query.
     * @throws AppFunctionInvalidArgumentException If the query is blank.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchMessages(
        query: String,
        endpointValue: String? = null,
    ): List<MessagesSearchResult> {
        if (query.isBlank()) {
            throw AppFunctionInvalidArgumentException("Query cannot be empty")
        }
        val targetIds =
            if (endpointValue != null) {
                listOf(endpointValue)
            } else {
                val individuals = recipientsRepository.getAllRecipients().map { it.id }
                val groups = recipientsRepository.getAllGroups().map { it.id }
                individuals + groups
            }

        val results = mutableListOf<MessagesSearchResult>()

        for (id in targetIds) {
            val messages = messageRepository.getMessages(id).first()
            val matchingMessages =
                messages.filter { it.content.contains(query, ignoreCase = true) }
            if (matchingMessages.isNotEmpty()) {
                results.add(
                    MessagesSearchResult(
                        endpointValue = id,
                        messages =
                            matchingMessages.map {
                                val senderDisplayName = getSenderDisplayName(it, id)
                                Message(
                                    messageBody = it.content,
                                    timestamp = it.sentAt,
                                    senderDisplayName = senderDisplayName,
                                )
                            },
                    ),
                )
            }
        }

        return results
    }

    private fun getSenderDisplayName(
        message: DisplayMessage,
        endpointId: String,
    ): String {
        return message.senderName
            ?: if (message.isInbound) {
                recipientsRepository.getRecipientById(endpointId)?.name
                    ?: recipientsRepository.getGroupById(endpointId)?.name
                    ?: "Other"
            } else {
                "Me"
            }
    }
}
