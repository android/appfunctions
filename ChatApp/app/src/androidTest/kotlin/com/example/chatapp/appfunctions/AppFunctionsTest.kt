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

import android.content.Context
import android.net.Uri
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chatapp.data.CallManager
import com.example.chatapp.data.MessageRepository
import com.example.chatapp.data.RecipientsRepository
import com.example.chatapp.uicomponents.DisplayMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFunctionsTest {
    private val testContext =
        object : AppFunctionContext {
            override val context: Context
                get() = ApplicationProvider.getApplicationContext()
        }

    private class MockMessageRepository : MessageRepository {
        var shouldFail = false
        private val messages = MutableStateFlow<Map<String, List<DisplayMessage>>>(emptyMap())

        override fun getMessages(recipientId: String): Flow<List<DisplayMessage>> {
            return messages.map { it[recipientId] ?: emptyList() }
        }

        override fun saveMessage(
            recipientId: String,
            message: DisplayMessage,
        ) {
            messages.value =
                messages.value.toMutableMap().apply {
                    val current = this[recipientId] ?: emptyList()
                    this[recipientId] = listOf(message) + current
                }
        }

        override suspend fun send(
            text: String,
            recipientIds: List<String>,
            imageUris: List<String>?,
        ): String {
            if (shouldFail) {
                throw RuntimeException("Failed to send")
            }
            return "Message ID"
        }

        override suspend fun sendToBot(text: String): String {
            return "Bot response"
        }
    }

    private val messageRepository = MockMessageRepository()

    private val recipientsRepository = RecipientsRepository()

    private val callManager = CallManager(recipientsRepository)

    private val appFunctions = AppFunctions(messageRepository, recipientsRepository, callManager)

    @Test(expected = AppFunctionInvalidArgumentException::class)
    fun searchContacts_returnsEmptyList() {
        runBlocking {
            appFunctions.searchContacts(testContext, "nonexistent", "INDIVIDUAL")
        }
    }

    @Test
    fun searchContacts_returnsMatches() {
        runBlocking {
            val contacts = appFunctions.searchContacts(testContext, "Alice", "INDIVIDUAL")
            Assert.assertEquals(1, contacts.size)
            Assert.assertEquals("Alice Smith", contacts[0].displayName)
        }
    }

    @Test
    fun searchContacts_groups_returnsMatches() {
        runBlocking {
            val contacts = appFunctions.searchContacts(testContext, "Work", "GROUP")
            Assert.assertEquals(1, contacts.size)
            Assert.assertEquals("Work Friends", contacts[0].displayName)
        }
    }

    @Test
    fun searchContacts_emptyQuery_returnsRecent() {
        runBlocking {
            val contacts = appFunctions.searchContacts(testContext, "", "INDIVIDUAL")
            Assert.assertEquals(3, contacts.size)
        }
    }

    @Test
    fun searchContacts_anyType_returnsMatches() {
        runBlocking {
            // "searchAny" branch (when filterType is not INDIVIDUAL or GROUP)
            val contacts = appFunctions.searchContacts(testContext, "Alice", "ANY")
            Assert.assertEquals(1, contacts.size)
            Assert.assertEquals("Alice Smith", contacts[0].displayName)
        }
    }

    @Test
    fun send_validMessage_returnsSuccess() {
        runTest {
            val result = appFunctions.send(testContext, "Alice Smith", "1", "Hello")
            Assert.assertEquals(
                AppFunctions.Result(
                    "Message ID",
                    "Message sent to: Alice Smith.",
                ),
                result,
            )
        }
    }

    @Test
    fun send_withImageUris_success() {
        runTest {
            val result =
                appFunctions.send(
                    testContext,
                    "Alice Smith",
                    "1",
                    "Hello",
                    listOf(Uri.parse("content://media/1")),
                )
            Assert.assertEquals(
                AppFunctions.Result(
                    "Message ID",
                    "Message sent to: Alice Smith.",
                ),
                result,
            )
        }
    }

    @Test
    fun send_toGroup_success() {
        runTest {
            val result = appFunctions.send(testContext, "Work Friends", "g1", "Hello")
            Assert.assertEquals(
                AppFunctions.Result(
                    "Message ID",
                    "Message sent to: Work Friends.",
                ),
                result,
            )
        }
    }

    @Test(expected = AppFunctionInvalidArgumentException::class)
    fun send_emptyContent_fails() {
        runTest {
            appFunctions.send(testContext, "Alice Smith", "1", "")
        }
    }

    @Test(expected = AppFunctionElementNotFoundException::class)
    fun send_invalidRecipient_fails() {
        runTest {
            appFunctions.send(testContext, "Unknown", "nonexistent_id", "Hello")
        }
    }

    @Test(expected = AppFunctionAppUnknownException::class)
    fun send_repositoryError_returnsError() {
        runTest {
            messageRepository.shouldFail = true
            appFunctions.send(testContext, "Alice Smith", "1", "Hello")
        }
    }

    @Test
    fun makeCall_returnsPendingIntent() {
        runBlocking {
            val pendingIntent = appFunctions.makeCall(testContext, endpointValue = "1")
            Assert.assertNotNull(pendingIntent)
        }
    }

    @Test
    fun makeCall_withContactName_success() {
        runBlocking {
            val pendingIntent = appFunctions.makeCall(testContext, contactName = "Alice Smith")
            Assert.assertNotNull(pendingIntent)
        }
    }

    @Test(expected = AppFunctionInvalidArgumentException::class)
    fun makeCall_missingParameters_fails() {
        runBlocking {
            appFunctions.makeCall(testContext, contactName = null, endpointValue = null)
        }
    }

    @Test(expected = AppFunctionElementNotFoundException::class)
    fun makeCall_invalidContactName_fails() {
        runBlocking {
            appFunctions.makeCall(testContext, contactName = "Unknown")
        }
    }

    @Test(expected = AppFunctionElementNotFoundException::class)
    fun makeCall_invalidEndpointValue_fails() {
        runBlocking {
            appFunctions.makeCall(testContext, endpointValue = "nonexistent_id")
        }
    }
}
