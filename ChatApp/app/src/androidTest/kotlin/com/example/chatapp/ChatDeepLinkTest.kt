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

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chatapp.appfunctions.Recipient
import com.example.chatapp.data.RecipientsRepository
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Tests that chat deep links, such as the `editMessageUri` returned by `fetchSendMessageDetails`,
 * open the chat screen with the optional `draft` query parameter pre-filled in the message input.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ChatDeepLinkTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject lateinit var recipientsRepository: RecipientsRepository

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var recipient: Recipient

    @Before
    fun setUp() {
        hiltRule.inject()
        recipient = recipientsRepository.getAllRecipients().first()
    }

    @Test
    fun chatLink_resolvesToMainActivity() {
        val intent =
            Intent(Intent.ACTION_VIEW, chatLink(recipient.id, draft = "Hi"))
                .setPackage(context.packageName)

        val activity = intent.resolveActivity(context.packageManager)

        assertThat(activity?.className).isEqualTo(MainActivity::class.java.name)
    }

    @Test
    fun chatLinkWithDraft_prefillsMessageInput() {
        launch(chatLink(recipient.id, draft = "Running 10 minutes late")).use {
            composeRule.onNodeWithText(recipient.name).assertIsDisplayed()
            assertThat(messageInputText()).isEqualTo("Running 10 minutes late")
        }
    }

    @Test
    fun chatLinkWithoutDraft_leavesMessageInputEmpty() {
        launch(chatLink(recipient.id, draft = null)).use {
            composeRule.onNodeWithText(recipient.name).assertIsDisplayed()
            assertThat(messageInputText()).isEmpty()
        }
    }

    @Test
    fun chatLinkWithDraft_keepsSpecialCharacters() {
        // Characters that must be percent-encoded in a URI, non-ASCII text and a line break.
        val draft = "Hello 👋\nA&B=C? 100% + \"quotes\" #1"

        launch(chatLink(recipient.id, draft)).use {
            assertThat(messageInputText()).isEqualTo(draft)
        }
    }

    /** Builds the link the same way as `buildEditMessageUri` in `BaseChatAppFunctionService`. */
    private fun chatLink(
        recipientId: String,
        draft: String?,
    ): Uri =
        Uri.Builder()
            .scheme("app")
            .authority("com.example.chatapp")
            .appendPath("chat")
            .appendPath(recipientId)
            .apply { if (draft != null) appendQueryParameter("draft", draft) }
            .build()

    private fun launch(link: Uri): ActivityScenario<MainActivity> =
        ActivityScenario.launch(Intent(Intent.ACTION_VIEW, link, context, MainActivity::class.java))

    private fun messageInputText(): String =
        composeRule
            .onNode(hasSetTextAction())
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text
}
