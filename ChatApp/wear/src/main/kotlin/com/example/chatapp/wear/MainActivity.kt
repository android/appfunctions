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
package com.example.chatapp.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import com.example.chatapp.wear.uicomponents.WearAppFunctionsScreen
import com.example.chatapp.wear.uicomponents.WearCallScreen
import com.example.chatapp.wear.uicomponents.WearChatScreen
import com.example.chatapp.wear.uicomponents.WearRecipientsScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen : NavKey {
    @Serializable
    data object Recipients : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data class Chat(val recipientId: String) : Screen

    @Serializable
    data class Call(val recipientId: String) : Screen
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    val backStack = rememberNavBackStack(Screen.Recipients)

    MaterialTheme {
        AppScaffold {
            val swipeDismissableSceneStrategy = rememberSwipeDismissableSceneStrategy<NavKey>()

            NavDisplay(
                backStack = backStack,
                onBack = {
                    if (backStack.size > 1) {
                        backStack.removeAt(backStack.size - 1)
                    }
                },
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider =
                    entryProvider<NavKey> {
                        entry<Screen.Recipients> {
                            WearRecipientsScreen(
                                onRecipientClick = { id -> backStack.add(Screen.Chat(id)) },
                                onSettingsClick = { backStack.add(Screen.Settings) },
                            )
                        }
                        entry<Screen.Settings> {
                            WearAppFunctionsScreen()
                        }
                        entry<Screen.Chat> { key ->
                            WearChatScreen(
                                recipientId = key.recipientId,
                                onCallClick = { backStack.add(Screen.Call(key.recipientId)) },
                            )
                        }
                        entry<Screen.Call> { key ->
                            WearCallScreen(
                                recipientId = key.recipientId,
                                onEndCall = { backStack.removeAt(backStack.size - 1) },
                            )
                        }
                    },
                sceneStrategies = listOf(swipeDismissableSceneStrategy),
            )
        }
    }
}
