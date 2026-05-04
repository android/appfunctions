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

    @Inject lateinit var appFunctions: AppFunctions

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
                    .observeAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .first()
                    .flatMap { it.appFunctions }
                    .single { it.id == AppFunctionsIds.SEND_ID }
            val testRecipient = recipientsRepository.getAllRecipients().first()
            val request =
                ExecuteAppFunctionRequest(
                    targetPackageName = context.packageName,
                    AppFunctionsIds.SEND_ID,
                    AppFunctionData.Builder(
                        sendMessageFunctionMetadata.parameters,
                        sendMessageFunctionMetadata.components,
                    )
                        .setString("name", testRecipient.name)
                        .setString("endpointValue", testRecipient.id)
                        .setString("messageBody", "Hello!")
                        .build(),
                )

            val response = appFunctionManager.executeAppFunction(request)
            val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
            assertThat(
                successResponse.returnValue
                    .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)
                    ?.getString("message"),
            )
                .isEqualTo("Message sent to recipient: Alice Smith.")
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
                    .observeAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .first()
                    .flatMap { it.appFunctions }
                    .single { it.id == AppFunctionsIds.SEND_ID }
            val testRecipient = recipientsRepository.getAllRecipients().first()
            val request =
                ExecuteAppFunctionRequest(
                    targetPackageName = context.packageName,
                    AppFunctionsIds.SEND_ID,
                    AppFunctionData.Builder(
                        sendMessageFunctionMetadata.parameters,
                        sendMessageFunctionMetadata.components,
                    )
                        .setString("name", testRecipient.name)
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
                    .observeAppFunctions(
                        AppFunctionSearchSpec(packageNames = setOf(context.packageName)),
                    )
                    .first()
                    .flatMap { it.appFunctions }
                    .single { it.id == AppFunctionsIds.SEARCH_CONTACTS_ID }

            val request =
                ExecuteAppFunctionRequest(
                    targetPackageName = context.packageName,
                    AppFunctionsIds.SEARCH_CONTACTS_ID,
                    AppFunctionData.Builder(
                        getRecipientsFunctionMetadata.parameters,
                        getRecipientsFunctionMetadata.components,
                    )
                        .setString("query", "Alice")
                        .setString("filterType", "INDIVIDUAL")
                        .build(),
                )

            val response = appFunctionManager.executeAppFunction(request)
            val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
            assertThat(
                successResponse.returnValue
                    .getAppFunctionDataList(
                        ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE,
                    )
                    ?.map { it.deserialize(AppFunctions.ContactSearchResult::class.java) },
            )
                .containsExactly(
                    AppFunctions.ContactSearchResult(endpointValue = "1", endpointType = "INDIVIDUAL", displayName = "Alice Smith"),
                )
        }
}
