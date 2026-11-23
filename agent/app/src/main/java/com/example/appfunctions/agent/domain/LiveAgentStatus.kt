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
 * Represents the per-session connection, listening, tool-invocation, and terminal status emitted by
 * [LiveAgentOrchestrator.runSession].
 *
 * Hosts should treat flow completion (after [Ended] or [Error]) or collector cancellation as idle.
 */
sealed class LiveAgentStatus {
    object Connecting : LiveAgentStatus()

    object Listening : LiveAgentStatus()

    data class InvokingTool(
        val functionId: String,
        val packageName: String,
    ) : LiveAgentStatus()

    object Ended : LiveAgentStatus()

    data class Error(val message: String) : LiveAgentStatus()
}

/**
 * Snapshot of the currently active [LiveAgentOrchestrator] voice session.
 *
 * @property threadId Identifier of the chat thread bound to the active live session.
 * @property status Latest [LiveAgentStatus] emitted for the active session.
 */
data class ActiveLiveSession(
    val threadId: String,
    val status: LiveAgentStatus,
)
