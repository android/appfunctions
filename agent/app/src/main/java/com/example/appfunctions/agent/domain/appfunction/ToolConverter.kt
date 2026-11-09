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
package com.example.appfunctions.agent.domain.appfunction

import androidx.appfunctions.metadata.AppFunctionMetadata

/** Interface for converting AppFunctionMetadata to a tool schema representation T. */
interface ToolConverter<T> {
    /** Converts the given tool metadata to the target representation. */
    fun convert(tool: AppFunctionMetadata): T

    /** Returns the name of the tool as it should be represented in the LLM schema. */
    fun getToolName(tool: AppFunctionMetadata): String
}

/**
 * Deduplicates [tools] by their converted tool name, prioritizing functions whose ID starts with
 * their package name.
 */
fun ToolConverter<*>.selectDistinctTools(tools: List<AppFunctionMetadata>): List<AppFunctionMetadata> =
    tools
        .sortedByDescending { it.id.startsWith(it.packageName) }
        .distinctBy { getToolName(it) }

/** Finds the [AppFunctionMetadata] in [tools] matching a converted tool [name]. */
fun ToolConverter<*>.findToolByName(
    name: String,
    tools: List<AppFunctionMetadata>,
): AppFunctionMetadata? = tools.find { getToolName(it) == name }

/**
 * Resolves the converted tool name for [functionId] from [tools], falling back to [functionId] if
 * no matching tool metadata is present.
 */
fun ToolConverter<*>.resolveToolName(
    functionId: String,
    tools: List<AppFunctionMetadata>,
): String {
    val matchingTool = tools.find { it.id == functionId } ?: return functionId
    return getToolName(matchingTool)
}
