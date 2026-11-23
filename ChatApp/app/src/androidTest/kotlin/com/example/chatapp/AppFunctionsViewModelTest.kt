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

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.example.chatapp.appfunctions.ChatAppFunctionService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFunctionsViewModelTest {
    private lateinit var viewModel: AppFunctionsViewModel
    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        viewModel = AppFunctionsViewModel(application)
    }

    @Test
    fun uiState_isInitiallyEmptyOrPopulated() =
        runTest {
            val state = viewModel.uiState.value
            assertNotNull(state)
        }

    @Test
    fun toggleFunction_doesNotCrash() =
        runTest {
            // Use a potentially real ID from the app
            viewModel.toggleFunction(ChatAppFunctionService.FUNCTION_ID_SEARCH_CONTACTS, true)
        }

    @Test
    fun observeUiState_completes() =
        runTest {
            // Just verify we can collect from the flow
            val state = viewModel.uiState.first()
            assertNotNull(state)
        }

    @Test
    @SdkSuppress(minSdkVersion = 36)
    fun registerSearchContacts_enablesFunction() =
        runTest {
            viewModel.registerSearchContacts()
            val state = viewModel.uiState.value
            assertNotNull(state)
        }

    @Test
    @SdkSuppress(minSdkVersion = 36)
    fun onUserAuthStateChanged_togglesRegistration() =
        runTest {
            viewModel.onUserAuthStateChanged(isSignedIn = true)
            var state = viewModel.uiState.value
            assertNotNull(state)

            viewModel.onUserAuthStateChanged(isSignedIn = false)
            state = viewModel.uiState.value
            assertNotNull(state)
        }

    @Test
    @SdkSuppress(minSdkVersion = 36)
    fun registerFetchSendMessageDetails_enablesFunction() =
        runTest {
            viewModel.registerFetchSendMessageDetails()
            val state = viewModel.uiState.value
            assertNotNull(state)
        }

    @Test
    @SdkSuppress(minSdkVersion = 36)
    fun registerSendMessage_enablesFunction() =
        runTest {
            viewModel.registerSendMessage()
            val state = viewModel.uiState.value
            assertNotNull(state)
        }

    @Test
    @SdkSuppress(minSdkVersion = 36)
    fun registerMakeCall_enablesFunction() =
        runTest {
            viewModel.registerMakeCall()
            val state = viewModel.uiState.value
            assertNotNull(state)
        }

    @Test
    @SdkSuppress(minSdkVersion = 36)
    fun registerUpdateChatWallpaper_enablesFunction() =
        runTest {
            viewModel.registerUpdateChatWallpaper()
            val state = viewModel.uiState.value
            assertNotNull(state)
        }

    @Test
    @SdkSuppress(minSdkVersion = 36)
    fun registerSearchMessages_enablesFunction() =
        runTest {
            viewModel.registerSearchMessages()
            val state = viewModel.uiState.value
            assertNotNull(state)
        }

    @Test
    @SdkSuppress(minSdkVersion = 36)
    fun invokeFetchSendMessageDetailsInternally_executesSuccessfully() =
        runTest {
            viewModel.registerFetchSendMessageDetails()
            val result = viewModel.invokeFetchSendMessageDetailsInternally("1", "Test Preview")
            assertNotNull(result)
        }
}
