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

import android.net.Uri
import androidx.appfunctions.AppFunctionResourceContainer
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionTextResource
import androidx.appfunctions.ExperimentalAppFunctionsApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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
 * Confirmation-card content for Gemini.
 */
@OptIn(ExperimentalAppFunctionsApi::class)
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ConfirmationPreview(
    /** Exactly one A2UI resource (mimeType "application/a2ui+json"). */
    override val resources: List<AppFunctionTextResource>,
) : AppFunctionResourceContainer

/**
 * All contacts and chat groups, as data and as a visual list for A2UI renderers.
 */
@OptIn(ExperimentalAppFunctionsApi::class)
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ContactList(
    /** Every contact and chat group. */
    val contacts: List<ContactSearchResult>,
    /** Exactly one A2UI resource (mimeType "application/a2ui+json") that lists the contacts using the A2UI Basic Catalog. */
    override val resources: List<AppFunctionTextResource>,
) : AppFunctionResourceContainer

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

// The A2UI payload of listContacts, built with components and functions of the A2UI Basic Catalog.

private const val A2UI_BASIC_CATALOG_ID = "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"

/**
 * Builds the A2UI messages for a list of contact cards, using the Basic Catalog: one Card per
 * contact (icon, name, and email or group members) with an "Open chat" button that opens the chat
 * in ChatApp.
 */
internal fun buildContactListA2ui(
    recipients: List<Recipient>,
    groups: List<ChatGroup>,
): String {
    val contacts =
        recipients.map { Triple(it.id, it.name, it.email) } +
            groups.map { group -> Triple(group.id, group.name, "Group: ${group.recipients.joinToString { it.name }}") }
    val components =
        listOf(
            column("root", listOf("title") + contacts.map { "contact_${it.first}" }),
            text("title", "Contacts", variant = "h4"),
        ) + contacts.flatMap { (id, name, subtitle) -> contactCard(id, name, subtitle) }
    // Surface IDs must be unique within a conversation, and this function can be called many times.
    return a2uiSurface("contacts_${UUID.randomUUID()}", components)
}

/** A Card with an icon, the contact's name and subtitle, and an "Open chat" button. */
private fun contactCard(
    id: String,
    name: String,
    subtitle: String,
): List<JSONObject> {
    val card = "contact_$id"
    return listOf(
        component(card, "Card").put("child", "${card}_row"),
        row("${card}_row", listOf("${card}_icon", "${card}_details", "${card}_open")).put("align", "center"),
        component("${card}_icon", "Icon").put("name", "accountCircle"),
        // weight lets the details take the free space, so the buttons line up at the end.
        column("${card}_details", listOf("${card}_name", "${card}_subtitle")).put("weight", 1),
        text("${card}_name", name, variant = "h5"),
        text("${card}_subtitle", subtitle, variant = "caption"),
        // openUrl runs on the device, in the renderer, so opening the chat doesn't start another
        // agent turn.
        button("${card}_open", child = "${card}_openLabel", action = openUrl(buildChatUri(id)))
            .put("accessibility", JSONObject().put("label", "Open chat with $name")),
        text("${card}_openLabel", "Open chat"),
    )
}

private fun buildChatUri(endpointValue: String): String =
    Uri.Builder()
        .scheme("app")
        .authority("com.example.chatapp")
        .appendPath("chat")
        .appendPath(endpointValue)
        .build()
        .toString()

// Builders for Basic Catalog components and functions.

/** The A2UI messages that create a Basic Catalog surface with [components]. */
private fun a2uiSurface(
    surfaceId: String,
    components: List<JSONObject>,
): String =
    JSONArray()
        .put(
            JSONObject()
                .put("version", "v0.9")
                .put("createSurface", JSONObject().put("surfaceId", surfaceId).put("catalogId", A2UI_BASIC_CATALOG_ID)),
        ).put(
            JSONObject()
                .put("version", "v0.9")
                .put("updateComponents", JSONObject().put("surfaceId", surfaceId).put("components", JSONArray(components))),
        ).toString()

private fun component(
    id: String,
    type: String,
): JSONObject = JSONObject().put("id", id).put("component", type)

private fun text(
    id: String,
    text: String,
    variant: String? = null,
): JSONObject = component(id, "Text").putOpt("variant", variant).put("text", text)

private fun row(
    id: String,
    children: List<String>,
): JSONObject = component(id, "Row").put("children", JSONArray(children))

private fun column(
    id: String,
    children: List<String>,
): JSONObject = component(id, "Column").put("children", JSONArray(children))

private fun button(
    id: String,
    child: String,
    action: JSONObject,
): JSONObject = component(id, "Button").put("child", child).put("action", action)

private fun openUrl(url: String): JSONObject =
    JSONObject().put("functionCall", JSONObject().put("call", "openUrl").put("args", JSONObject().put("url", url)))
