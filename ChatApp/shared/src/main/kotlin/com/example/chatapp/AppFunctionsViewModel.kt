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
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.appfunctions.BaseChatAppFunctionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Information about an app function.
 *
 * @property id The unique identifier of the app function.
 * @property isEnabled Whether the app function is currently enabled.
 * @property description A human-readable description of the app function, if available.
 */
data class AppFunctionInfo(
    val id: String,
    val isEnabled: Boolean,
    val description: String?,
)

/**
 * UI state for the App Functions settings screen.
 *
 * @property functions The list of available app functions and their current status.
 */
data class AppFunctionsUiState(
    val functions: List<AppFunctionInfo> = emptyList(),
)

/**
 * ViewModel for the App Functions settings screen.
 *
 * This ViewModel interacts with the [AppFunctionManager] to list and manage the state
 * of app functions within the application.
 */
@HiltViewModel
class AppFunctionsViewModel
    @Inject
    constructor(
        application: Application,
    ) : AndroidViewModel(application) {
        private val appFunctionManager = AppFunctionManager.getInstance(application)

        val uiState: StateFlow<AppFunctionsUiState> =
            appFunctionManager
                ?.observeAppFunctions()
                ?.map { loadAppFunctions(application.packageName) }
                ?.onStart { emit(loadAppFunctions(application.packageName)) }
                ?.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = AppFunctionsUiState(),
                )
                ?: MutableStateFlow(AppFunctionsUiState())

        private suspend fun loadAppFunctions(packageName: String): AppFunctionsUiState {
            if (appFunctionManager == null) return AppFunctionsUiState()
            val functions =
                appFunctionManager.searchAppFunctions(
                    AppFunctionSearchSpec(packageNames = setOf(packageName)),
                )
            val statesByName =
                appFunctionManager
                    .getAppFunctionStates(functions.map { it.name })
                    .associateBy { it.functionName }

            val functionInfos =
                functions
                    .map {
                        AppFunctionInfo(
                            id = it.id,
                            isEnabled = statesByName[it.name]?.isEnabled ?: true,
                            description = it.description,
                        )
                    }.sortedBy { it.id }

            return AppFunctionsUiState(functions = functionInfos)
        }

        /**
         * Toggles the enabled state of an app function.
         *
         * @param id The ID of the function to toggle.
         * @param enabled The new enabled state.
         */
        fun toggleFunction(
            id: String,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                val newState =
                    if (enabled) {
                        AppFunctionManager.APP_FUNCTION_STATE_ENABLED
                    } else {
                        AppFunctionManager.APP_FUNCTION_STATE_DISABLED
                    }
                appFunctionManager?.setAppFunctionEnabled(id, newState)
            }
        }

        /**
         * Registers (enables) the searchContacts capability with the OS at runtime.
         */
        @RequiresApi(36)
        fun registerSearchContacts() {
            toggleFunction(BaseChatAppFunctionService.FUNCTION_ID_SEARCH_CONTACTS, enabled = true)
        }

        /**
         * Unregisters (disables) the searchContacts capability with the OS at runtime.
         */
        @RequiresApi(36)
        fun unregisterSearchContacts() {
            toggleFunction(BaseChatAppFunctionService.FUNCTION_ID_SEARCH_CONTACTS, enabled = false)
        }

        /**
         * Registers (enables) the fetchSendMessageDetails capability with the OS at runtime.
         */
        @RequiresApi(36)
        fun registerFetchSendMessageDetails() {
            toggleFunction(BaseChatAppFunctionService.FUNCTION_ID_FETCH_SEND_MESSAGE_DETAILS, enabled = true)
        }

        /**
         * Unregisters (disables) the fetchSendMessageDetails capability with the OS at runtime.
         */
        @RequiresApi(36)
        fun unregisterFetchSendMessageDetails() {
            toggleFunction(BaseChatAppFunctionService.FUNCTION_ID_FETCH_SEND_MESSAGE_DETAILS, enabled = false)
        }

        /**
         * Registers (enables) the sendMessage capability with the OS at runtime.
         */
        @RequiresApi(36)
        fun registerSendMessage() {
            toggleFunction(BaseChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE, enabled = true)
        }

        /**
         * Unregisters (disables) the sendMessage capability with the OS at runtime.
         */
        @RequiresApi(36)
        fun unregisterSendMessage() {
            toggleFunction(BaseChatAppFunctionService.FUNCTION_ID_SEND_MESSAGE, enabled = false)
        }

        /**
         * Registers (enables) the makeCall capability with the OS at runtime.
         */
        @RequiresApi(36)
        fun registerMakeCall() {
            toggleFunction(BaseChatAppFunctionService.FUNCTION_ID_MAKE_CALL, enabled = true)
        }

        /**
         * Unregisters (disables) the makeCall capability with the OS at runtime.
         */
        @RequiresApi(36)
        fun unregisterMakeCall() {
            toggleFunction(BaseChatAppFunctionService.FUNCTION_ID_MAKE_CALL, enabled = false)
        }

        /**
         * Registers (enables) the updateChatWallpaper capability with the OS at runtime.
         */
        @RequiresApi(36)
        fun registerUpdateChatWallpaper() {
            toggleFunction(BaseChatAppFunctionService.FUNCTION_ID_UPDATE_CHAT_WALLPAPER, enabled = true)
        }

        /**
         * Unregisters (disables) the updateChatWallpaper capability with the OS at runtime.
         */
        @RequiresApi(36)
        fun unregisterUpdateChatWallpaper() {
            toggleFunction(BaseChatAppFunctionService.FUNCTION_ID_UPDATE_CHAT_WALLPAPER, enabled = false)
        }

        /**
         * Registers (enables) the searchMessages capability with the OS at runtime.
         */
        @RequiresApi(36)
        fun registerSearchMessages() {
            toggleFunction(BaseChatAppFunctionService.FUNCTION_ID_SEARCH_MESSAGES, enabled = true)
        }

        /**
         * Unregisters (disables) the searchMessages capability with the OS at runtime.
         */
        @RequiresApi(36)
        fun unregisterSearchMessages() {
            toggleFunction(BaseChatAppFunctionService.FUNCTION_ID_SEARCH_MESSAGES, enabled = false)
        }

        /**
         * Handles user authentication state changes to dynamically register or unregister
         * app capabilities with the OS at runtime.
         */
        @RequiresApi(36)
        fun onUserAuthStateChanged(isSignedIn: Boolean) {
            if (isSignedIn) {
                registerSearchContacts()
                registerFetchSendMessageDetails()
                registerSendMessage()
                registerMakeCall()
                registerUpdateChatWallpaper()
                registerSearchMessages()
            } else {
                unregisterSearchContacts()
                unregisterFetchSendMessageDetails()
                unregisterSendMessage()
                unregisterMakeCall()
                unregisterUpdateChatWallpaper()
                unregisterSearchMessages()
            }
        }

        /**
         * Invokes the app-private fetchSendMessageDetails AppFunction internally from within ChatApp.
         *
         * @param endpointValue The recipient or group identifier.
         * @param messageBody The text message body.
         * @return The execution response from AppFunctionManager.
         */
        @RequiresApi(36)
        suspend fun invokeFetchSendMessageDetailsInternally(
            endpointValue: String,
            messageBody: String,
        ): ExecuteAppFunctionResponse? {
            if (appFunctionManager == null) return null
            val packageName = getApplication<Application>().packageName
            val searchSpec = AppFunctionSearchSpec(packageNames = setOf(packageName))
            val metadata =
                appFunctionManager.searchAppFunctions(searchSpec)
                    .singleOrNull { it.id == BaseChatAppFunctionService.FUNCTION_ID_FETCH_SEND_MESSAGE_DETAILS }
                    ?: return null

            val request =
                ExecuteAppFunctionRequest(
                    targetPackageName = packageName,
                    BaseChatAppFunctionService.FUNCTION_ID_FETCH_SEND_MESSAGE_DETAILS,
                    AppFunctionData.Builder(metadata.parameters, metadata.components)
                        .setString("endpointValue", endpointValue)
                        .setString("messageBody", messageBody)
                        .build(),
                )

            return appFunctionManager.executeAppFunction(request)
        }
    }
