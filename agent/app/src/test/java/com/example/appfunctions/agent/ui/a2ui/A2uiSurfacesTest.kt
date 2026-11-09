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
package com.example.appfunctions.agent.ui.a2ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class A2uiSurfacesTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val composeRule = createComposeRule()

    // Unit tests don't use the merged manifest, so register the activity that hosts the content.
    @get:Rule
    val rules: RuleChain =
        RuleChain
            .outerRule(
                object : ExternalResource() {
                    override fun before() {
                        shadowOf(application.packageManager)
                            .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
                    }
                },
            ).around(composeRule)

    private fun render(payload: String) {
        composeRule.setContent { A2uiSurfaces(payload = payload, modifier = Modifier) }
    }

    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun rendersOneCardPerContact() {
        render(CONTACT_LIST)

        waitForText("Alice Smith")
        composeRule.onNodeWithText("Contacts").assertIsDisplayed()
        composeRule.onNodeWithText("alice@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Bob Johnson").assertIsDisplayed()
        composeRule.onAllNodesWithText("Open chat").assertCountEquals(2)
    }

    @Test
    fun openChatButton_opensTheChatWithThatContact() {
        render(CONTACT_LIST)
        waitForText("Bob Johnson")

        composeRule.onNodeWithContentDescription("Open chat with Bob Johnson").performClick()

        var intent: Intent? = null
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            intent = shadowOf(application).nextStartedActivity
            intent != null
        }
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("app://com.example.chatapp/chat/2", intent?.dataString)
    }

    @Test
    fun verticalList_insideLazyColumn_isShown() {
        // The chat shows A2UI surfaces inside a LazyColumn.
        composeRule.setContent { LazyColumn { item { A2uiSurfaces(payload = VERTICAL_LIST, modifier = Modifier) } } }

        waitForText("Item A")
        composeRule.onNodeWithText("Item B").assertIsDisplayed()
    }

    @Test
    fun unsupportedCatalog_rendersNothingAndLogsTheError() {
        render(CONTACT_LIST.replace(BASIC_CATALOG, "https://example.com/unknown_catalog.json"))

        composeRule.waitUntil(TIMEOUT_MILLIS) { loggedErrors().any { "not found" in it } }
        assertTrue(composeRule.onAllNodesWithText("Alice Smith").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun malformedPayload_logsTheErrorWithoutCrashing() {
        render("{not json")

        composeRule.waitUntil(TIMEOUT_MILLIS) { loggedErrors().any { "VALIDATION_FAILED" in it } }
    }

    private fun loggedErrors() = ShadowLog.getLogsForTag("A2uiSurfaces").map { it.msg }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val BASIC_CATALOG = "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"

        /** Two contacts, laid out like ChatApp's listContacts payload. */
        val CONTACT_LIST =
            """
            [
              {"version": "v0.9", "createSurface": {"surfaceId": "contacts", "catalogId": "$BASIC_CATALOG"}},
              {"version": "v0.9", "updateComponents": {"surfaceId": "contacts", "components": [
                {"id": "root", "component": "Column", "children": ["title", "contact_1", "contact_2"]},
                {"id": "title", "component": "Text", "variant": "h4", "text": "Contacts"},
                ${contactCard("1", "Alice Smith", "alice@example.com")},
                ${contactCard("2", "Bob Johnson", "bob@example.com")}
              ]}}
            ]
            """.trimIndent()

        fun contactCard(
            id: String,
            name: String,
            email: String,
        ) = """
            {"id": "contact_$id", "component": "Card", "child": "contact_${id}_row"},
            {"id": "contact_${id}_row", "component": "Row", "align": "center",
              "children": ["contact_${id}_icon", "contact_${id}_details", "contact_${id}_open"]},
            {"id": "contact_${id}_icon", "component": "Icon", "name": "accountCircle"},
            {"id": "contact_${id}_details", "component": "Column", "weight": 1,
              "children": ["contact_${id}_name", "contact_${id}_email"]},
            {"id": "contact_${id}_name", "component": "Text", "variant": "h5", "text": "$name"},
            {"id": "contact_${id}_email", "component": "Text", "variant": "caption", "text": "$email"},
            {"id": "contact_${id}_open", "component": "Button", "child": "contact_${id}_openLabel",
              "accessibility": {"label": "Open chat with $name"},
              "action": {"functionCall": {"call": "openUrl", "args": {"url": "app://com.example.chatapp/chat/$id"}}}},
            {"id": "contact_${id}_openLabel", "component": "Text", "text": "Open chat"}
            """

        val VERTICAL_LIST =
            """
            [
              {"version": "v0.9", "createSurface": {"surfaceId": "s", "catalogId": "$BASIC_CATALOG"}},
              {"version": "v0.9", "updateComponents": {"surfaceId": "s", "components": [
                {"id": "root", "component": "List", "direction": "vertical", "children": ["a", "b"]},
                {"id": "a", "component": "Text", "text": "Item A"},
                {"id": "b", "component": "Text", "text": "Item B"}
              ]}}
            ]
            """.trimIndent()
    }
}
