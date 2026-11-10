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
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chatapp.data.MessageRepository
import com.example.chatapp.data.RecipientsRepository
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.test.assertIs

/**
 * Instrumented test for AppFunctions executing via AppFunctionManager. Stubs are left for the user
 * to fill out.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppFunctionInstrumentationTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var messageRepository: MessageRepository

    @Inject lateinit var recipientsRepository: RecipientsRepository

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appFunctionManager: AppFunctionManager =
        checkNotNull(AppFunctionManager.getInstance(context))

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testSend_success() =
        runBlocking<Unit> {
            val sendMessageFunctionMetadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE }
            val testRecipient = recipientsRepository.getAllRecipients().first()
            val request =
                ExecuteAppFunctionRequest(
                    targetPackageName = context.packageName,
                    ChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE,
                    AppFunctionData.Builder(
                        sendMessageFunctionMetadata.parameters,
                        sendMessageFunctionMetadata.components,
                    )
                        .setString("endpointValue", testRecipient.id)
                        .setString("messageBody", "Hello!")
                        .build(),
                )

            val response = appFunctionManager.executeAppFunction(request)
            val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
            assertThat(
                successResponse.returnValue
                    .getString(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE),
            )
                .isEqualTo("Message sent to: Alice Smith.")
            // Verify that the message was actually saved in the repository
            val messages = messageRepository.getMessages(testRecipient.id).first()
            assertThat(messages).isNotEmpty()
            assertThat(messages.first().content).isEqualTo("Hello!")
        }

    @Test
    fun testSend_emptyContent_fails() =
        runBlocking<Unit> {
            val sendMessageFunctionMetadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE }
            val testRecipient = recipientsRepository.getAllRecipients().first()
            val request =
                ExecuteAppFunctionRequest(
                    targetPackageName = context.packageName,
                    ChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE,
                    AppFunctionData.Builder(
                        sendMessageFunctionMetadata.parameters,
                        sendMessageFunctionMetadata.components,
                    )
                        .setString("endpointValue", testRecipient.id)
                        .setString("messageBody", "")
                        .build(),
                )

            val response = appFunctionManager.executeAppFunction(request)
            val errorResponse = assertIs<ExecuteAppFunctionResponse.Error>(response)
            val error = assertIs<AppFunctionInvalidArgumentException>(errorResponse.error)
            assertThat(error).hasMessageThat().contains("Message body cannot be empty")
        }

    @Test
    fun testSearchContacts_success() =
        runBlocking<Unit> {
            val getRecipientsFunctionMetadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_SEARCH_CONTACTS }

            val request =
                ExecuteAppFunctionRequest(
                    targetPackageName = context.packageName,
                    ChatAppFunctionService.FUNCTION_ID_SEARCH_CONTACTS,
                    AppFunctionData.Builder(
                        getRecipientsFunctionMetadata.parameters,
                        getRecipientsFunctionMetadata.components,
                    )
                        .setString("query", "Alice")
                        .setString("contactType", "INDIVIDUAL")
                        .build(),
                )

            val response = appFunctionManager.executeAppFunction(request)
            val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
            assertThat(
                successResponse.returnValue
                    .getAppFunctionDataList(
                        ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE,
                    )
                    ?.map { it.deserialize(ContactSearchResult::class.java) },
            )
                .containsExactly(
                    ContactSearchResult(
                        contactDisplayName = "Alice Smith",
                        contactType = "INDIVIDUAL",
                        endpointValue = "1",
                        endpointDisplayName = "alice@example.com",
                    ),
                )
        }

    @Test
    fun testListContacts_returnsAllContactsAndGroups() =
        runBlocking<Unit> {
            val contacts =
                executeListContacts()
                    .getAppFunctionDataList("contacts")
                    ?.map { it.deserialize(ContactSearchResult::class.java) }
                    .orEmpty()

            assertThat(contacts.map { it.endpointValue })
                .containsExactlyElementsIn(
                    recipientsRepository.getAllRecipients().map { it.id } +
                        recipientsRepository.getAllGroups().map { it.id },
                ).inOrder()
            assertThat(contacts)
                .containsAtLeast(
                    ContactSearchResult(
                        contactDisplayName = "Alice Smith",
                        contactType = "INDIVIDUAL",
                        endpointValue = "1",
                        endpointDisplayName = "alice@example.com",
                    ),
                    ContactSearchResult(
                        contactDisplayName = "Work Friends",
                        contactType = "GROUP",
                        endpointValue = "g1",
                        endpointDisplayName = "Work Friends",
                    ),
                )
        }

    @Test
    fun testListContacts_returnsOneBasicCatalogCardPerContact() =
        runBlocking<Unit> {
            val messages = executeListContacts().a2uiMessages()

            val createSurface = messages.getJSONObject(0).getJSONObject("createSurface")
            assertThat(createSurface.getString("catalogId"))
                .isEqualTo("https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json")
            val updateComponents = messages.getJSONObject(1).getJSONObject("updateComponents")
            assertThat(updateComponents.getString("surfaceId")).isEqualTo(createSurface.getString("surfaceId"))
            val components = componentsById(updateComponents)
            assertThat(components.getValue("root").getJSONArray("children").toStringList())
                .containsExactly(
                    "title",
                    "contact_1",
                    "contact_2",
                    "contact_3",
                    "contact_4",
                    "contact_5",
                    "contact_6",
                    "contact_7",
                    "contact_g1",
                    "contact_g2",
                ).inOrder()
            assertThat(components.getValue("contact_1").getString("component")).isEqualTo("Card")
            assertThat(components.getValue("contact_1_name").getString("text")).isEqualTo("Alice Smith")
            assertThat(components.getValue("contact_1_subtitle").getString("text")).isEqualTo("alice@example.com")
            assertThat(components.getValue("contact_g1_subtitle").getString("text"))
                .isEqualTo("Group: Alice Smith, Bob Johnson, Charlie Brown")
        }

    @Test
    fun testListContacts_openChatButtonsOpenTheChatWithThatContact() =
        runBlocking<Unit> {
            val components =
                componentsById(executeListContacts().a2uiMessages().getJSONObject(1).getJSONObject("updateComponents"))

            for (id in listOf("1", "7", "g1")) {
                assertThat(components.getValue("contact_${id}_openLabel").getString("text")).isEqualTo("Open chat")
                val action = components.getValue("contact_${id}_open").getJSONObject("action").getJSONObject("functionCall")
                assertThat(action.getString("call")).isEqualTo("openUrl")
                assertThat(action.getJSONObject("args").getString("url")).isEqualTo("app://com.example.chatapp/chat/$id")
            }
        }

    @Test
    fun testListContacts_usesNewSurfaceIdForEachCall() =
        runBlocking<Unit> {
            fun JSONArray.surfaceId() = getJSONObject(0).getJSONObject("createSurface").getString("surfaceId")

            assertThat(executeListContacts().a2uiMessages().surfaceId())
                .isNotEqualTo(executeListContacts().a2uiMessages().surfaceId())
        }

    /** Executes listContacts and returns its return value. */
    private suspend fun executeListContacts(): AppFunctionData {
        val listContactsFunctionMetadata =
            appFunctionManager
                .searchAppFunctions(
                    AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                )
                .single { it.id == ChatAppFunctionService.FUNCTION_ID_LIST_CONTACTS }
        val request =
            ExecuteAppFunctionRequest(
                targetPackageName = context.packageName,
                ChatAppFunctionService.FUNCTION_ID_LIST_CONTACTS,
                AppFunctionData.Builder(
                    listContactsFunctionMetadata.parameters,
                    listContactsFunctionMetadata.components,
                ).build(),
            )

        val response = appFunctionManager.executeAppFunction(request)
        val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
        return checkNotNull(
            successResponse.returnValue.getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE),
        )
    }

    /** Returns the A2UI messages of the only A2UI resource in this result. */
    private fun AppFunctionData.a2uiMessages(): JSONArray {
        val resource = getAppFunctionDataList("resources")?.single()
        assertThat(resource?.getString("mimeType")).isEqualTo("application/a2ui+json")
        return JSONArray(checkNotNull(resource?.getString("content")))
    }

    private fun componentsById(updateComponents: JSONObject): Map<String, JSONObject> {
        val components = updateComponents.getJSONArray("components")
        return (0 until components.length()).map { components.getJSONObject(it) }.associateBy { it.getString("id") }
    }

    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
}
