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

import androidx.appfunctions.AppFunctionSerializable

/**
 * Represents a result from a contact or group search.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ContactSearchResult(
    /** The human-readable name of the contact or group. */
    val contactDisplayName: String,
    /** The type of the found entity, either "INDIVIDUAL" or "GROUP". */
    val contactType: String,
    /** The unique identifier of the endpoint. */
    val endpointValue: String,
    /** The human-readable label/display name of the endpoint. */
    val endpointDisplayName: String,
)

/**
 * Represents an individual recipient or contact.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class Recipient(
    /** Unique identifier for the contact. */
    val id: String,
    /** Human-readable name of the contact. */
    val name: String,
    /** Email address of the contact. */
    val email: String,
)

/**
 * Represents a group of chat recipients.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ChatGroup(
    /** Unique identifier for the group. */
    val id: String,
    /** Name of the group. */
    val name: String,
    /** List of members belonging to the group. */
    val recipients: List<Recipient>,
)

/**
 * Represents a message returned in search results.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class Message(
    /** The text content of the message. */
    val messageBody: String,
    /** The timestamp when the message was sent or received. */
    val timestamp: Long,
    /** The human-readable name of the sender. */
    val senderDisplayName: String,
)

/**
 * Represents the search results for a specific chat endpoint.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MessagesSearchResult(
    /** The unique identifier of the contact or group. */
    val endpointValue: String,
    /** The list of relevant messages found in this chat. */
    val messages: List<Message>,
)
