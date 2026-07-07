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
import com.example.chatapp.data.DisplayMessage
import com.example.chatapp.data.MessageRepository
import com.example.chatapp.data.RecipientsRepository
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
            Assert.assertEquals("Alice Smith", contacts[0].contactDisplayName)
            Assert.assertEquals("INDIVIDUAL", contacts[0].endpointType)
            Assert.assertEquals(1, contacts[0].endpoints.size)
            Assert.assertEquals("1", contacts[0].endpoints[0].endpointValue)
            Assert.assertEquals("alice@example.com", contacts[0].endpoints[0].endpointDisplayName)
        }
    }

    @Test
    fun searchContacts_groups_returnsMatches() {
        runBlocking {
            val contacts = appFunctions.searchContacts(testContext, "Work", "GROUP")
            Assert.assertEquals(1, contacts.size)
            Assert.assertEquals("Work Friends", contacts[0].contactDisplayName)
            Assert.assertEquals("GROUP", contacts[0].endpointType)
            Assert.assertEquals(1, contacts[0].endpoints.size)
            Assert.assertEquals("g1", contacts[0].endpoints[0].endpointValue)
            Assert.assertEquals("Work Friends", contacts[0].endpoints[0].endpointDisplayName)
        }
    }

    @Test(expected = AppFunctionInvalidArgumentException::class)
    fun searchContacts_emptyQuery_fails() {
        runBlocking {
            appFunctions.searchContacts(testContext, "", "INDIVIDUAL")
        }
    }

    @Test
    fun searchContacts_anyType_returnsMatches() {
        runBlocking {
            val contacts = appFunctions.searchContacts(testContext, "Alice", "ANY")
            Assert.assertEquals(1, contacts.size)
            Assert.assertEquals("Alice Smith", contacts[0].contactDisplayName)
            Assert.assertEquals("INDIVIDUAL", contacts[0].endpointType)
            Assert.assertEquals(1, contacts[0].endpoints.size)
            Assert.assertEquals("1", contacts[0].endpoints[0].endpointValue)
            Assert.assertEquals("alice@example.com", contacts[0].endpoints[0].endpointDisplayName)
        }
    }

    @Test
    fun searchContacts_duplicateNamesMerged() {
        runBlocking {
            val contacts = appFunctions.searchContacts(testContext, "Bob", "INDIVIDUAL")
            Assert.assertEquals(1, contacts.size)
            Assert.assertEquals("Bob Johnson", contacts[0].contactDisplayName)
            Assert.assertEquals("INDIVIDUAL", contacts[0].endpointType)
            Assert.assertEquals(2, contacts[0].endpoints.size)
            Assert.assertEquals("2", contacts[0].endpoints[0].endpointValue)
            Assert.assertEquals("bob@example.com", contacts[0].endpoints[0].endpointDisplayName)
            Assert.assertEquals("7", contacts[0].endpoints[1].endpointValue)
            Assert.assertEquals("bob2@example.com", contacts[0].endpoints[1].endpointDisplayName)
        }
    }

    @Test
    fun sendMessage_validMessage_returnsSuccess() {
        runTest {
            val result = appFunctions.sendMessage(testContext, "1", "Hello")
            Assert.assertEquals(
                "Message sent to: Alice Smith.",
                result,
            )
        }
    }

    @Test
    fun sendMessage_withImageUris_success() {
        runTest {
            val result =
                appFunctions.sendMessage(
                    testContext,
                    "1",
                    "Hello",
                    listOf(Uri.parse("content://media/1")),
                )
            Assert.assertEquals(
                "Message sent to: Alice Smith.",
                result,
            )
        }
    }

    @Test
    fun sendMessage_toGroup_success() {
        runTest {
            val result = appFunctions.sendMessage(testContext, "g1", "Hello")
            Assert.assertEquals(
                "Message sent to: Work Friends.",
                result,
            )
        }
    }

    @Test(expected = AppFunctionInvalidArgumentException::class)
    fun sendMessage_emptyContent_fails() {
        runTest {
            appFunctions.sendMessage(testContext, "1", "")
        }
    }

    @Test(expected = AppFunctionElementNotFoundException::class)
    fun sendMessage_invalidRecipient_fails() {
        runTest {
            appFunctions.sendMessage(testContext, "nonexistent_id", "Hello")
        }
    }

    @Test(expected = AppFunctionAppUnknownException::class)
    fun sendMessage_repositoryError_returnsError() {
        runTest {
            messageRepository.shouldFail = true
            appFunctions.sendMessage(testContext, "1", "Hello")
        }
    }

    @Test
    fun makeCall_returnsPendingIntent() {
        runBlocking {
            val pendingIntent = appFunctions.makeCall(testContext, endpointValue = "1")
            Assert.assertNotNull(pendingIntent)
        }
    }


    @Test(expected = AppFunctionElementNotFoundException::class)
    fun makeCall_invalidEndpointValue_fails() {
        runBlocking {
            appFunctions.makeCall(testContext, endpointValue = "nonexistent_id")
        }
    }

    @Test
    fun searchMessages_allChats_returnsMatches() {
        runTest {
            messageRepository.saveMessage("1", DisplayMessage("Hello Alice", 1000, isInbound = true))
            messageRepository.saveMessage("2", DisplayMessage("Hello Bob", 2000, isInbound = false))
            messageRepository.saveMessage("1", DisplayMessage("How are you?", 3000, isInbound = false))

            val results = appFunctions.searchMessages(testContext, "Hello")

            Assert.assertEquals(2, results.size)

            val aliceResult = results.first { it.endpointValue == "1" }
            Assert.assertEquals(1, aliceResult.messages.size)
            Assert.assertEquals("Hello Alice", aliceResult.messages[0].messageBody)
            Assert.assertEquals(1000L, aliceResult.messages[0].timestamp)
            Assert.assertEquals("Alice Smith", aliceResult.messages[0].senderDisplayName)

            val bobResult = results.first { it.endpointValue == "2" }
            Assert.assertEquals(1, bobResult.messages.size)
            Assert.assertEquals("Hello Bob", bobResult.messages[0].messageBody)
            Assert.assertEquals(2000L, bobResult.messages[0].timestamp)
            Assert.assertEquals("Me", bobResult.messages[0].senderDisplayName)
        }
    }

    @Test
    fun searchMessages_specificChat_returnsMatches() {
        runTest {
            messageRepository.saveMessage("1", DisplayMessage("Hello Alice", 1000, isInbound = true))
            messageRepository.saveMessage("2", DisplayMessage("Hello Bob", 2000, isInbound = false))

            val results = appFunctions.searchMessages(testContext, "Hello", endpointValue = "1")

            Assert.assertEquals(1, results.size)
            Assert.assertEquals("1", results[0].endpointValue)
            Assert.assertEquals("Hello Alice", results[0].messages[0].messageBody)
        }
    }

    @Test
    fun searchMessages_noMatches_returnsEmpty() {
        runTest {
            messageRepository.saveMessage("1", DisplayMessage("Hello Alice", 1000, isInbound = true))

            val results = appFunctions.searchMessages(testContext, "Goodbye")

            Assert.assertTrue(results.isEmpty())
        }
    }

}
