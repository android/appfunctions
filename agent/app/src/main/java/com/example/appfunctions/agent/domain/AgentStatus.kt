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

/** Represents the status of the agent. */
sealed class AgentStatus {
    object Idle : AgentStatus()

    object Thinking : AgentStatus()

    /** Progressive tool disclosure L0: the model is reviewing the one-line app catalog. */
    object CheckingCapabilities : AgentStatus()

    /** Progressive tool disclosure L1: the model is reading the details of [packageNames]. */
    data class ExploringApps(val packageNames: List<String>) : AgentStatus()

    /**
     * Progressive tool disclosure L2: the model loaded [functionNames] from [packageNames] and is
     * planning how to call them.
     */
    data class PlanningWithFunctions(
        val packageNames: List<String>,
        val functionNames: List<String>,
    ) : AgentStatus()

    data class InvokingTool(val functionId: String, val packageName: String) : AgentStatus()
}
