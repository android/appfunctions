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
package com.example.appfunctions.agent.ui.layout

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun AdaptiveMainNavigation(
    navController: NavHostController,
    items: List<String>,
    icons: List<ImageVector>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val isTv = isTvFormFactor()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    if (isTv) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .consumeWindowInsets(WindowInsets(0, 0, 0, 0)),
        ) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Spacer(modifier = Modifier.weight(1f))
                items.forEachIndexed { index, screen ->
                    NavigationRailItem(
                        icon = { Icon(icons[index], contentDescription = labels[index]) },
                        label = { Text(labels[index]) },
                        selected =
                            currentDestination?.hierarchy?.any {
                                it.route?.startsWith(screen) == true
                            } == true,
                        onClick = {
                            navController.navigate(screen) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            content(
                Modifier
                    .weight(1f)
                    .fillMaxSize(),
            )
        }
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    items.forEachIndexed { index, screen ->
                        NavigationBarItem(
                            icon = { Icon(icons[index], contentDescription = labels[index]) },
                            label = { Text(labels[index]) },
                            selected =
                                currentDestination?.hierarchy?.any {
                                    it.route?.startsWith(screen) == true
                                } == true,
                            onClick = {
                                navController.navigate(screen) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            },
        ) { innerPadding ->
            content(
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        }
    }
}
