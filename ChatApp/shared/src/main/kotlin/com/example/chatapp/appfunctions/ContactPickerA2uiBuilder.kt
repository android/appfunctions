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

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds A2UI declarative JSON and textual fallbacks for contact disambiguation.
 */
object ContactPickerA2uiBuilder {
    const val A2UI_MIME_TYPE = "application/a2ui+json"
    private const val BASIC_CATALOG_ID = "https://a2ui.org/specification/v0_9/basic_catalog.json"

    /**
     * Formats a clean textual fallback for LLMs and headless/non-A2UI clients.
     */
    fun createFallbackText(
        query: String,
        contacts: List<ContactSearchResult>,
    ): String {
        val contactList =
            contacts.mapIndexed { index, contact ->
                val details =
                    if (contact.endpointDisplayName.isNotBlank() && contact.endpointDisplayName != contact.contactDisplayName) {
                        " (${contact.endpointDisplayName})"
                    } else {
                        ""
                    }
                val typeBadge = if (contact.contactType.equals("GROUP", ignoreCase = true)) " [Group]" else ""
                "${index + 1}. ${contact.contactDisplayName}$details$typeBadge"
            }.joinToString("\n")

        return "Multiple contacts found for '$query':\n$contactList\n\nPlease select or specify which contact you would like to reach."
    }

    /**
     * Builds an A2UI v0.9 compliant JSON envelope containing `createSurface` and `updateComponents`.
     */
    fun createContactPickerA2ui(
        query: String,
        contacts: List<ContactSearchResult>,
        surfaceId: String = "contact_picker_${System.currentTimeMillis()}",
    ): String =
        a2uiSurface(surfaceId) {
            text(
                id = "header_title",
                text = "Multiple Contacts Found",
                variant = "titleMedium",
                bold = true,
            )
            text(
                id = "header_subtitle",
                text = "Found ${contacts.size} results for \"$query\". Tap to choose:",
                variant = "bodySmall",
                color = "secondary",
            )

            val contactRowIds =
                contacts.mapIndexed { index, contact ->
                    val details =
                        if (contact.contactType.equals("GROUP", ignoreCase = true)) {
                            val isDistinct =
                                contact.endpointDisplayName.isNotBlank() &&
                                    contact.endpointDisplayName != contact.contactDisplayName
                            if (isDistinct) "Group • ${contact.endpointDisplayName}" else "Group"
                        } else {
                            contact.endpointDisplayName.ifBlank { contact.endpointValue }
                        }

                    val selectParams =
                        mapOf(
                            "endpointValue" to contact.endpointValue,
                            "contactDisplayName" to contact.contactDisplayName,
                            "prompt" to "Send message to ${contact.contactDisplayName} (${contact.endpointValue})",
                        )

                    text("name_$index", contact.contactDisplayName, variant = "bodyMedium", bold = true)
                    text("endpoint_$index", details, variant = "bodySmall", color = "secondary")
                    column("contact_info_$index", listOf("name_$index", "endpoint_$index"), spacing = 2)
                    button("select_btn_$index", "Select", actionName = "select_contact", params = selectParams)
                    row(
                        id = "contact_row_$index",
                        children = listOf("contact_info_$index", "select_btn_$index"),
                        alignment = "center",
                        spaceBetween = true,
                        actionName = "select_contact",
                        params = selectParams,
                    )
                    "contact_row_$index"
                }

            column(
                id = "main_column",
                children = listOf("header_title", "header_subtitle") + contactRowIds,
                spacing = 6,
                fillMaxWidth = true,
            )
            card("root", child = "main_column")
        }

    private fun a2uiSurface(
        surfaceId: String,
        catalogId: String = BASIC_CATALOG_ID,
        builder: A2uiBuilder.() -> Unit,
    ): String {
        val dsl = A2uiBuilder(surfaceId, catalogId).apply(builder)
        return dsl.buildJson()
    }

    /**
     * Lightweight, type-safe builder for A2UI Basic Catalog declarative UI components.
     */
    private class A2uiBuilder(
        private val surfaceId: String,
        private val catalogId: String,
    ) {
        private val components = JSONArray()

        fun text(
            id: String,
            text: String,
            variant: String = "bodyMedium",
            bold: Boolean = false,
            color: String? = null,
        ) {
            components.put(
                JSONObject().apply {
                    put("id", id)
                    put("component", "Text")
                    put(
                        "properties",
                        JSONObject().apply {
                            put("text", text)
                            put("variant", variant)
                            if (bold) put("bold", true)
                            if (color != null) put("color", color)
                        },
                    )
                },
            )
        }

        fun button(
            id: String,
            text: String,
            actionName: String,
            params: Map<String, Any> = emptyMap(),
        ) {
            components.put(
                JSONObject().apply {
                    put("id", id)
                    put("component", "Button")
                    put(
                        "properties",
                        JSONObject().apply {
                            put("text", text)
                            put(
                                "action",
                                JSONObject().apply {
                                    put("name", actionName)
                                    put("parameters", JSONObject(params))
                                },
                            )
                        },
                    )
                },
            )
        }

        fun row(
            id: String,
            children: List<String>,
            alignment: String = "center",
            spaceBetween: Boolean = false,
            actionName: String? = null,
            params: Map<String, Any> = emptyMap(),
        ) {
            components.put(
                JSONObject().apply {
                    put("id", id)
                    put("component", "Row")
                    put(
                        "properties",
                        JSONObject().apply {
                            put("children", JSONArray(children))
                            put("alignment", alignment)
                            if (spaceBetween) put("spaceBetween", true)
                            if (actionName != null) {
                                put(
                                    "action",
                                    JSONObject().apply {
                                        put("name", actionName)
                                        put("parameters", JSONObject(params))
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }

        fun column(
            id: String,
            children: List<String>,
            spacing: Int = 0,
            fillMaxWidth: Boolean = false,
        ) {
            components.put(
                JSONObject().apply {
                    put("id", id)
                    put("component", "Column")
                    put(
                        "properties",
                        JSONObject().apply {
                            put("children", JSONArray(children))
                            if (spacing != 0) put("spacing", spacing)
                            put("fillMaxWidth", fillMaxWidth)
                        },
                    )
                },
            )
        }

        fun card(
            id: String,
            child: String,
        ) {
            components.put(
                JSONObject().apply {
                    put("id", id)
                    put("component", "Card")
                    put(
                        "properties",
                        JSONObject().apply {
                            put("child", child)
                        },
                    )
                },
            )
        }

        fun buildJson(): String {
            val createSurfaceMsg =
                JSONObject().apply {
                    put("version", "v0.9")
                    put(
                        "createSurface",
                        JSONObject().apply {
                            put("surfaceId", surfaceId)
                            put("catalogId", catalogId)
                        },
                    )
                }

            val updateComponentsMsg =
                JSONObject().apply {
                    put("version", "v0.9")
                    put(
                        "updateComponents",
                        JSONObject().apply {
                            put("surfaceId", surfaceId)
                            put("components", components)
                        },
                    )
                }

            return JSONArray().apply {
                put(createSurfaceMsg)
                put(updateComponentsMsg)
            }.toString()
        }
    }
}
