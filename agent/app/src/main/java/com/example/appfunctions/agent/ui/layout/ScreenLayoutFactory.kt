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

import com.example.appfunctions.agent.ui.contracts.AgentDemoScreenLayout
import com.example.appfunctions.agent.ui.contracts.ConnectedAppsScreenLayout
import com.example.appfunctions.agent.ui.contracts.DebuggingScreenLayout
import com.example.appfunctions.agent.ui.contracts.SettingsScreenLayout
import com.example.appfunctions.agent.ui.mobile.agentdemo.MobileAgentDemoLayout
import com.example.appfunctions.agent.ui.mobile.agentdemo.MobileConnectedAppsLayout
import com.example.appfunctions.agent.ui.mobile.agentdemo.MobileSettingsLayout
import com.example.appfunctions.agent.ui.mobile.debugging.MobileDebuggingLayout

/**
 * Centralized layout factory that resolves screen layout contracts for specific form factors.
 * This pattern decouples screen routers from explicit form factor resolution conditionals,
 * allowing new device form factors (e.g., Wear OS, Android XR, Auto) to be added cleanly.
 */
fun FormFactor.resolveAgentDemoLayout(): AgentDemoScreenLayout = MobileAgentDemoLayout

fun FormFactor.resolveConnectedAppsLayout(): ConnectedAppsScreenLayout = MobileConnectedAppsLayout

fun FormFactor.resolveSettingsLayout(): SettingsScreenLayout = MobileSettingsLayout

fun FormFactor.resolveDebuggingLayout(): DebuggingScreenLayout = MobileDebuggingLayout
