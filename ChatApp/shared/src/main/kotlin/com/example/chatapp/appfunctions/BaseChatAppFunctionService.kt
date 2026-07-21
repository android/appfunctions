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
import com.example.chatapp.data.MessageRepository
import com.example.chatapp.data.RecipientsRepository
import com.example.chatapp.data.WallpaperRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
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
     * @param query Search string for contact name, email, or group name. Can be partial or full names. If blank, returns the most recently contacted entities.
     * @param filterType Filter results by entity type. Accepts "INDIVIDUAL" or "GROUP".
     * @return List of [ContactSearchResult] objects matching the query.
     * @throws AppFunctionInvalidArgumentException If an invalid filterType is provided or if no matching contact or group is found. If thrown, suggest the user clarify the recipient or group name.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchContacts(
        query: String,
        @AppFunctionStringValueConstraint(enumValues = ["INDIVIDUAL", "GROUP"])
        filterType: String,
    ): List<ContactSearchResult> {
        val recipients =
            when (filterType) {
                "INDIVIDUAL" -> {
                    recipientsRepository.searchRecipients(query, 3).map {
                        ContactSearchResult(
                            endpointValue = it.id,
                            endpointType = "INDIVIDUAL",
                            displayName = it.name,
                        )
                    }
                }
                "GROUP" -> {
                    recipientsRepository.searchGroups(query, 3).map {
                        ContactSearchResult(
                            endpointValue = it.id,
                            endpointType = "GROUP",
                            displayName = it.name,
                        )
                    }
                }
                else -> {
                    recipientsRepository.searchAny(query, maxCount = 3)
                        .ifEmpty {
                            throw AppFunctionInvalidArgumentException(
                                "Only INDIVIDUAL or GROUP are accepted filter arguments.",
                            )
                        }
                }
            }

        if (recipients.isEmpty()) {
            throw AppFunctionInvalidArgumentException(
                "$filterType with name $query not found. Ask the user to clarify the name",
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
     * @return A [Result] object containing the messageId and a human-readable success confirmation.
     * @throws AppFunctionInvalidArgumentException If messageBody is empty or blank. If thrown, ask the user to provide the message content to send.
     * @throws AppFunctionElementNotFoundException If no contact or group matches endpointValue. If thrown, call "searchContacts" to find the correct ID.
     * @throws AppFunctionAppUnknownException If sending fails due to a repository error. If thrown, suggest the user retry later.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun send(
        endpointValue: String,
        messageBody: String,
        imageUris: List<Uri>? = null,
    ): Result {
        if (messageBody.isBlank()) {
            throw AppFunctionInvalidArgumentException("Message body cannot be empty")
        }
        val displayName =
            recipientsRepository.getRecipientById(endpointValue)?.name
                ?: recipientsRepository.getGroupById(endpointValue)?.name
                ?: throw AppFunctionElementNotFoundException(
                    "No contact or group found for endpointValue: $endpointValue",
                )

        val sentMessageId =
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

        return Result(sentMessageId, "Message sent to: $displayName.")
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
}
