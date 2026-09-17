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

import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ContactPickerA2uiBuilderTest {
    @Test
    fun createFallbackText_formatsCleanTextList() {
        val contacts =
            listOf(
                ContactSearchResult(
                    contactDisplayName = "Bob Smith",
                    contactType = "INDIVIDUAL",
                    endpointValue = "bob_smith_1",
                    endpointDisplayName = "bob.smith@example.com",
                ),
                ContactSearchResult(
                    contactDisplayName = "Bob Jones",
                    contactType = "INDIVIDUAL",
                    endpointValue = "bob_jones_2",
                    endpointDisplayName = "bob.jones@example.com",
                ),
            )

        val fallback = ContactPickerA2uiBuilder.createFallbackText("Bob", contacts)

        assertThat(fallback).contains("Multiple contacts found for 'Bob':")
        assertThat(fallback).contains("1. Bob Smith (bob.smith@example.com)")
        assertThat(fallback).contains("2. Bob Jones (bob.jones@example.com)")
        assertThat(fallback).contains("Please select or specify which contact you would like to reach.")
    }

    @Test
    fun createContactPickerA2ui_createsValidA2uiEnvelope() {
        val contacts =
            listOf(
                ContactSearchResult(
                    contactDisplayName = "Bob Smith",
                    contactType = "INDIVIDUAL",
                    endpointValue = "bob_smith_1",
                    endpointDisplayName = "bob.smith@example.com",
                ),
                ContactSearchResult(
                    contactDisplayName = "Bob Jones",
                    contactType = "INDIVIDUAL",
                    endpointValue = "bob_jones_2",
                    endpointDisplayName = "bob.jones@example.com",
                ),
            )

        val a2uiJson = ContactPickerA2uiBuilder.createContactPickerA2ui("Bob", contacts, "test_surface")

        val payloadArray = JSONArray(a2uiJson)
        assertThat(payloadArray.length()).isEqualTo(2)

        // 1. Check createSurface message
        val createSurfaceMsg = payloadArray.getJSONObject(0)
        assertThat(createSurfaceMsg.getString("version")).isEqualTo("v0.9")
        val createSurface = createSurfaceMsg.getJSONObject("createSurface")
        assertThat(createSurface.getString("surfaceId")).isEqualTo("test_surface")

        // 2. Check updateComponents message
        val updateComponentsMsg = payloadArray.getJSONObject(1)
        assertThat(updateComponentsMsg.getString("version")).isEqualTo("v0.9")
        val updateComponents = updateComponentsMsg.getJSONObject("updateComponents")
        assertThat(updateComponents.getString("surfaceId")).isEqualTo("test_surface")

        val components = updateComponents.getJSONArray("components")
        val componentMap = mutableMapOf<String, JSONObject>()
        for (i in 0 until components.length()) {
            val comp = components.getJSONObject(i)
            componentMap[comp.getString("id")] = comp
        }

        // Root exists and points to main_column
        assertThat(componentMap).containsKey("root")
        assertThat(componentMap["root"]?.getString("component")).isEqualTo("Card")

        // Buttons exist with select_contact action
        assertThat(componentMap).containsKey("select_btn_0")
        val btnAction =
            componentMap["select_btn_0"]
                ?.getJSONObject("properties")
                ?.getJSONObject("action")
        assertThat(btnAction?.getString("name")).isEqualTo("select_contact")
        val params = btnAction?.getJSONObject("parameters")
        assertThat(params?.getString("endpointValue")).isEqualTo("bob_smith_1")
        assertThat(params?.getString("contactDisplayName")).isEqualTo("Bob Smith")

        // Contact rows also have select_contact action
        assertThat(componentMap).containsKey("contact_row_0")
        val rowAction =
            componentMap["contact_row_0"]
                ?.getJSONObject("properties")
                ?.getJSONObject("action")
        assertThat(rowAction?.getString("name")).isEqualTo("select_contact")
    }
}
