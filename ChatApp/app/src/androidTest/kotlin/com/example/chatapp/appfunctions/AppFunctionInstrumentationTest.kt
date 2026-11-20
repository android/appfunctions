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
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
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
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
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
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_SEARCH_CONTACTS,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
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
    fun testSearchContacts_disabledByDefault() =
        runBlocking<Unit> {
            val metadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_SEARCH_CONTACTS }
            val states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            val searchContactsState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(searchContactsState?.isEnabled ?: false).isFalse()
        }

    @Test
    fun testSearchContacts_registrationLifecycle() =
        runBlocking<Unit> {
            val metadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_SEARCH_CONTACTS }

            // 1. Enable / Register capability
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_SEARCH_CONTACTS,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            var states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            var searchContactsState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(searchContactsState?.isEnabled).isTrue()

            // 2. Disable / Unregister capability
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_SEARCH_CONTACTS,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            searchContactsState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(searchContactsState?.isEnabled).isFalse()
        }

    @Test
    fun testFetchSendMessageDetails_disabledByDefault() =
        runBlocking<Unit> {
            val metadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_FETCH_SEND_MESSAGE_DETAILS }
            val states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            val functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled ?: false).isFalse()
        }

    @Test
    fun testFetchSendMessageDetails_registrationLifecycle() =
        runBlocking<Unit> {
            val metadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_FETCH_SEND_MESSAGE_DETAILS }

            // 1. Enable / Register capability
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_FETCH_SEND_MESSAGE_DETAILS,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            var states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            var functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled).isTrue()

            // 2. Disable / Unregister capability
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_FETCH_SEND_MESSAGE_DETAILS,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled).isFalse()
        }

    @Test
    fun testSendMessage_disabledByDefault() =
        runBlocking<Unit> {
            val metadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE }
            val states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            val functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled ?: false).isFalse()
        }

    @Test
    fun testSendMessage_registrationLifecycle() =
        runBlocking<Unit> {
            val metadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE }

            // 1. Enable / Register capability
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            var states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            var functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled).isTrue()

            // 2. Disable / Unregister capability
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled).isFalse()
        }

    @Test
    fun testMakeCall_disabledByDefault() =
        runBlocking<Unit> {
            val metadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_MAKE_CALL }
            val states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            val functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled ?: false).isFalse()
        }

    @Test
    fun testMakeCall_registrationLifecycle() =
        runBlocking<Unit> {
            val metadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_MAKE_CALL }

            // 1. Enable / Register capability
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_MAKE_CALL,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            var states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            var functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled).isTrue()

            // 2. Disable / Unregister capability
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_MAKE_CALL,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled).isFalse()
        }

    @Test
    fun testUpdateChatWallpaper_disabledByDefault() =
        runBlocking<Unit> {
            val metadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_UPDATE_CHAT_WALLPAPER }
            val states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            val functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled ?: false).isFalse()
        }

    @Test
    fun testUpdateChatWallpaper_registrationLifecycle() =
        runBlocking<Unit> {
            val metadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_UPDATE_CHAT_WALLPAPER }

            // 1. Enable / Register capability
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_UPDATE_CHAT_WALLPAPER,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            var states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            var functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled).isTrue()

            // 2. Disable / Unregister capability
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_UPDATE_CHAT_WALLPAPER,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled).isFalse()
        }

    @Test
    fun testSearchMessages_disabledByDefault() =
        runBlocking<Unit> {
            val metadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_SEARCH_MESSAGES }
            val states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            val functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled ?: false).isFalse()
        }

    @Test
    fun testSearchMessages_registrationLifecycle() =
        runBlocking<Unit> {
            val metadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_SEARCH_MESSAGES }

            // 1. Enable / Register capability
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_SEARCH_MESSAGES,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            var states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            var functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled).isTrue()

            // 2. Disable / Unregister capability
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_SEARCH_MESSAGES,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
            states = appFunctionManager.getAppFunctionStates(listOf(metadata.name))
            functionState = states.singleOrNull { it.functionName == metadata.name }
            assertThat(functionState?.isEnabled).isFalse()
        }

    @Test
    fun testFetchSendMessageDetails_selfInvocation() =
        runBlocking<Unit> {
            appFunctionManager.setAppFunctionEnabled(
                ChatAppFunctionService.FUNCTION_ID_FETCH_SEND_MESSAGE_DETAILS,
                AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
            )
            val metadata =
                appFunctionManager
                    .searchAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .single { it.id == ChatAppFunctionService.FUNCTION_ID_FETCH_SEND_MESSAGE_DETAILS }

            val testRecipient = recipientsRepository.getAllRecipients().first()
            val request =
                ExecuteAppFunctionRequest(
                    targetPackageName = context.packageName,
                    ChatAppFunctionService.FUNCTION_ID_FETCH_SEND_MESSAGE_DETAILS,
                    AppFunctionData.Builder(
                        metadata.parameters,
                        metadata.components,
                    )
                        .setString("endpointValue", testRecipient.id)
                        .setString("messageBody", "Test Preview")
                        .build(),
                )

            val response = appFunctionManager.executeAppFunction(request)
            val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
            assertThat(successResponse).isNotNull()
        }

}
