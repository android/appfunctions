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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.chatapp.ui.theme.ChatAppTheme
import com.example.chatapp.uicomponents.AppFunctionsScreen
import com.example.chatapp.uicomponents.CallScreen
import com.example.chatapp.uicomponents.ChatScreen
import com.example.chatapp.uicomponents.RecipientsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatAppTheme {
                val navController = rememberNavController()
                
                LaunchedEffect(intent) {
                    val navRoute = intent?.getStringExtra("nav_route")
                    if (navRoute != null) {
                        navController.navigate(navRoute) {
                            launchSingleTop = true
                        }
                    }
                }

                NavHost(navController = navController, startDestination = "recipients") {
                    composable(
                        route = "recipients",
                        deepLinks = listOf(navDeepLink { uriPattern = "app://com.example.chatapp/recipients" })
                    ) {
                        RecipientsScreen(
                            onRecipientClick = { recipientId ->
                                navController.navigate("chat/$recipientId")
                            },
                            onSettingsClick = {
                                navController.navigate("functions")
                            }
                        )
                    }
                    composable(
                        route = "functions",
                        deepLinks = listOf(navDeepLink { uriPattern = "app://com.example.chatapp/functions" })
                    ) {
                        AppFunctionsScreen(
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "chat/{recipientId}",
                        arguments = listOf(navArgument("recipientId") { type = NavType.StringType }),
                        deepLinks = listOf(navDeepLink { uriPattern = "app://com.example.chatapp/chat/{recipientId}" })
                    ) { backStackEntry ->
                        val recipientId = backStackEntry.arguments?.getString("recipientId") ?: "bot"
                        ChatScreen(
                            onCallClick = { navController.navigate("call/$recipientId") },
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "call/{recipientId}",
                        arguments = listOf(navArgument("recipientId") { type = NavType.StringType }),
                        deepLinks = listOf(navDeepLink { uriPattern = "app://com.example.chatapp/call/{recipientId}" })
                    ) {
                        CallScreen(
                            onEndCall = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    ChatAppTheme {
        RecipientsScreen(onRecipientClick = {})
    }
}
