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
package com.example.appfunctions.agent.domain

/**
 * Represents the unified connection, listening, and tool-invocation status of a live voice session.
 */
sealed class LiveAgentStatus {
    object Idle : LiveAgentStatus()

    object Connecting : LiveAgentStatus()

    object Listening : LiveAgentStatus()

    data class InvokingTool(
        val functionId: String,
        val packageName: String,
    ) : LiveAgentStatus()

    data class Error(val message: String) : LiveAgentStatus()
}
