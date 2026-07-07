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
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionStringValueConstraint
import androidx.appfunctions.service.AppFunction
import com.example.chatapp.data.CallManager
import com.example.chatapp.data.MessageRepository
import com.example.chatapp.data.RecipientsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Provides functions for chat-related operations such as retrieving contacts and sending messages.
 */
class AppFunctions
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val recipientsRepository: RecipientsRepository,
        private val callManager: CallManager,
    ) {
        /**
         * Search for contacts or groups by name.
         *
         * @param appFunctionContext The context of this app function call.
         * @param query Search string for the contact or group name. Can be partial or full names.
         *   If blank, returns the most recently contacted entities.
         * @param contactType Filter results by entity type. Accepts "INDIVIDUAL", "GROUP", or "ANY".
         */
        @AppFunction(isDescribedByKDoc = true)
        suspend fun searchContacts(
            appFunctionContext: AppFunctionContext,
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
                                endpointType = "GROUP",
                                endpoints = listOf(
                                    Endpoint(
                                        endpointValue = it.id,
                                        endpointDisplayName = it.name,
                                    )
                                )
                            )
                        }
                    }
                    "ANY" -> {
                        recipientsRepository.searchAny(query, maxCount = 3)
                    }
                    else -> {
                        throw AppFunctionInvalidArgumentException(
                            "Invalid contactType: $contactType",
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
         * Send a text message with optional image attachments.
         *
         * @param appFunctionContext The context of this app function call.
         * @param endpointValue The unique identifier for the recipient or group.
         * @param messageBody The text content of the message. Cannot be empty.
         * @param imageUris List of URIs for images to attach.
         * @return A human-readable message indicating the error or confirmation.
         */
        @AppFunction(isDescribedByKDoc = true)
        suspend fun sendMessage(
            appFunctionContext: AppFunctionContext,
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

            val sentMessageId =
                try {
                    messageRepository.send(
                        text = messageBody,
                        recipientIds = listOf(endpointValue),
                        imageUris = imageUris?.map { it.toString() },
                    )
                } catch (e: Exception) {
                    throw AppFunctionAppUnknownException("Failed to send message: ${e.message}")
                }

            // 3. RETURN: Provide a confirmation string
            // The bot may use this string or the fact that it didn't throw to confirm success.
            return "Message sent to: $displayName."
        }

        /**
         * Initiate a voice call.
         *
         * @param appFunctionContext The context of this app function call.
         * @param endpointValue The unique identifier for the recipient.
         */
        @AppFunction(isDescribedByKDoc = true)
        suspend fun makeCall(
            appFunctionContext: AppFunctionContext,
            endpointValue: String,
        ): PendingIntent {
            val recipient =
                recipientsRepository.getRecipientById(endpointValue)
                    ?: throw AppFunctionElementNotFoundException(
                        "No recipient exists for endpointValue: $endpointValue",
                    )

            // Call manager should technically also record it here depending on app architecture,
            // but we will launch the intent to handle it in the UI.
            callManager.startCall(recipient.id)

            // Create a pending intent to the MainActivity, initiating the call
            return PendingIntent.getActivity(
                appFunctionContext.context,
                0,
                Intent(appFunctionContext.context, Class.forName("com.example.chatapp.MainActivity"))
                    .apply { putExtra("nav_route", "call/${recipient.id}") },
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Search for messages containing a query string, optionally filtered by a specific recipient.
         *
         * @param appFunctionContext The context of this app function call.
         * @param query The text to search for within message bodies.
         * @param endpointValue Optional unique identifier of the contact or group to restrict the search to.
         */
        @AppFunction(isDescribedByKDoc = true)
        suspend fun searchMessages(
            appFunctionContext: AppFunctionContext,
            query: String,
            endpointValue: String? = null,
        ): List<MessagesSearchResult> {
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
                                    val senderDisplayName = it.senderName
                                        ?: if (it.isInbound) {
                                            recipientsRepository.getRecipientById(id)?.name
                                                ?: recipientsRepository.getGroupById(id)?.name
                                                ?: "Other"
                                        } else {
                                            "Me"
                                        }
                                    Message(
                                        messageBody = it.content,
                                        timestamp = it.sentAt,
                                        senderDisplayName = senderDisplayName,
                                    )
                                },
                        )
                    )
                }
            }

            return results
        }

        /** Represents a result from a contact or group search. */
        @AppFunctionSerializable(isDescribedByKDoc = true)
        data class ContactSearchResult(
            /** The human-readable name of the contact or group. */
            val contactDisplayName: String,
            /** The type of the found entity, either "INDIVIDUAL" or "GROUP". */
            val endpointType: String,
            /** The list of endpoints associated with this contact or group. */
            val endpoints: List<Endpoint>,
        )

        /** Represents an endpoint of a contact or group. */
        @AppFunctionSerializable(isDescribedByKDoc = true)
        data class Endpoint(
            /** The unique identifier of the endpoint. */
            val endpointValue: String,
            /** The human-readable label/display name of the endpoint. */
            val endpointDisplayName: String,
        )



        /** Represents a recipient of a message. */
        @AppFunctionSerializable(isDescribedByKDoc = true)
        data class Recipient(
            /** Unique identifier for the contact. */
            val id: String,
            /** Human-readable name. */
            val name: String,
            /** Email address of the contact. */
            val email: String,
        )

        /** Represents a group of recipients. */
        @AppFunctionSerializable(isDescribedByKDoc = true)
        data class ChatGroup(
            /** Unique identifier for the group. */
            val id: String,
            /** Name of the group. */
            val name: String,
            /** List of members belonging to the group. */
            val recipients: List<Recipient>,
        )

        /** Represents a message returned in search results. */
        @AppFunctionSerializable(isDescribedByKDoc = true)
        data class Message(
            /** The text content of the message. */
            val messageBody: String,
            /** The timestamp when the message was sent or received. */
            val timestamp: Long,
            /** The human-readable name of the sender. */
            val senderDisplayName: String,
        )

        /** Represents the search results for a specific chat endpoint. */
        @AppFunctionSerializable(isDescribedByKDoc = true)
        data class MessagesSearchResult(
            /** The unique identifier of the contact or group. */
            val endpointValue: String,
            /** The list of relevant messages found in this chat. */
            val messages: List<Message>,
        )
    }
