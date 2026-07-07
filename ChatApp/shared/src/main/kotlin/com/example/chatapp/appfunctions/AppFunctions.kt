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
 * Identifiers for AppFunctions exposed by ChatApp.
 */
object AppFunctionsIds {
    const val SEND_ID = "com.example.chatapp.appfunctions.BaseChatAppFunctionService#send"
    const val SEARCH_CONTACTS_ID = "com.example.chatapp.appfunctions.BaseChatAppFunctionService#searchContacts"
    const val MAKE_CALL_ID = "com.example.chatapp.appfunctions.BaseChatAppFunctionService#makeCall"
}

/**
 * Represents a result from a contact or group search.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ContactSearchResult(
    /** The unique identifier for the contact or group. */
    val endpointValue: String,
    /** The type of the found entity, either "INDIVIDUAL" or "GROUP". */
    val endpointType: String,
    /** The human-readable display name of the contact or group. */
    val displayName: String,
)

/**
 * Result of a message sending operation.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class Result(
    /** The unique identifier for the successfully sent message. */
    val messageId: String,
    /** A human-readable status message confirming success. */
    val message: String,
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
