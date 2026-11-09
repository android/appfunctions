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

import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An app exposing AppFunctions, as presented to the model during progressive tool disclosure.
 *
 * @property packageName The package name of the app.
 * @property instructions The full agent-facing instructions of the app (`AppFunctionAppMetadata.instructions`).
 * @property functions The AppFunctions exposed by the app.
 */
data class AppToolGroup(
    val packageName: String,
    val instructions: String,
    val functions: List<AppFunctionMetadata>,
)

/**
 * All tools available to the agent for a single message, split into apps that are disclosed
 * progressively and tools that are always fully visible to the model.
 */
data class ToolCatalog(
    val apps: List<AppToolGroup>,
    val alwaysAvailableTools: List<AppFunctionMetadata>,
)

/** What a tool selection meta-tool call is doing, used for user-facing status updates. */
sealed interface MetaToolActivity {
    /** The model is reading the details of the app [packageName] (L1). */
    data class ExploreApp(val packageName: String) : MetaToolActivity

    /** The model is loading the full declarations of [functions] (L2). */
    data class LoadFunctions(val functions: List<AppFunctionMetadata>) : MetaToolActivity
}

/**
 * Implements progressive disclosure for tool selection.
 *
 * Instead of sending every function declaration to the model up front, the model initially only
 * sees the first line of each app's instructions (embedded in the [EXPAND_TOOL_DESCRIPTION] tool). It
 * then calls:
 * 1. [EXPAND_TOOL_DESCRIPTION] to read an app's full instructions and a one-line description of
 *    each of its functions (without schemas).
 * 2. [EXPAND_FUNCTION_DECLARATIONS] to load the full declarations of the functions it needs. The
 *    loaded functions are included as regular tools in subsequent requests of the same thread.
 */
@Singleton
class ToolSelectionManager
    @Inject
    constructor() {
        /** Expanded functions keyed by thread ID. Kept in memory for the process lifetime. */
        private val expandedFunctionsByThread = ConcurrentHashMap<String, Set<AppFunctionName>>()

        /** Returns the tools to send to the model for the next request in [threadId]. */
        fun getActiveTools(
            threadId: String,
            catalog: ToolCatalog,
        ): List<AppFunctionMetadata> {
            if (catalog.apps.isEmpty()) {
                return catalog.alwaysAvailableTools
            }
            val expanded = expandedFunctionsByThread[threadId].orEmpty()
            val expandedTools = catalog.apps.flatMap { it.functions }.filter { it.name in expanded }
            return buildMetaTools(catalog) + catalog.alwaysAvailableTools + expandedTools
        }

        /** Returns whether the given tool call targets one of the tool selection meta-tools. */
        fun isMetaTool(packageName: String): Boolean = packageName == META_TOOL_PACKAGE

        /**
         * Describes what a meta-tool call is doing, for user-facing status updates.
         *
         * @return The apps being explored or functions being loaded, or null if the call does not
         *   refer to anything in [catalog].
         */
        fun resolveMetaToolActivity(
            functionId: String,
            arguments: Map<String, Any?>,
            catalog: ToolCatalog,
        ): MetaToolActivity? =
            when (functionId) {
                EXPAND_TOOL_DESCRIPTION -> {
                    val packageName = arguments[PARAM_PACKAGE_NAME] as? String
                    catalog.apps
                        .find { it.packageName == packageName }
                        ?.let { MetaToolActivity.ExploreApp(it.packageName) }
                }
                EXPAND_FUNCTION_DECLARATIONS -> {
                    val ids = parseFunctionIds(arguments).toSet()
                    catalog.apps
                        .flatMap { it.functions }
                        .filter { it.id in ids }
                        .takeIf { it.isNotEmpty() }
                        ?.let { MetaToolActivity.LoadFunctions(it) }
                }
                else -> null
            }

        private fun parseFunctionIds(arguments: Map<String, Any?>): List<String> =
            when (val rawIds = arguments[PARAM_FUNCTION_IDS]) {
                is List<*> -> rawIds.filterIsInstance<String>()
                is String -> listOf(rawIds)
                else -> emptyList()
            }.distinct()

        /**
         * Handles a call to one of the tool selection meta-tools.
         *
         * @return The JSON result to send back to the model.
         */
        fun handleMetaToolCall(
            threadId: String,
            functionId: String,
            arguments: Map<String, Any?>,
            catalog: ToolCatalog,
        ): String =
            when (functionId) {
                EXPAND_TOOL_DESCRIPTION -> expandToolDescription(arguments, catalog)
                EXPAND_FUNCTION_DECLARATIONS ->
                    expandFunctionDeclarations(threadId, arguments, catalog)
                else -> errorJson("Unknown tool selection function: $functionId")
            }

        private fun expandToolDescription(
            arguments: Map<String, Any?>,
            catalog: ToolCatalog,
        ): String {
            val packageName = arguments[PARAM_PACKAGE_NAME] as? String
            val app =
                catalog.apps.find { it.packageName == packageName }
                    ?: return errorJson(
                        "Unknown $PARAM_PACKAGE_NAME '$packageName'. Valid values: " +
                            catalog.apps.joinToString { it.packageName },
                    )
            return buildJsonObject {
                put(PARAM_PACKAGE_NAME, app.packageName)
                put("instructions", app.instructions)
                put(
                    "functions",
                    buildJsonArray {
                        app.functions.forEach { function ->
                            add(
                                buildJsonObject {
                                    put("function_id", function.id)
                                    put("description", function.description.toOneLineDescription())
                                },
                            )
                        }
                    },
                )
                put(
                    "next_step",
                    "Call $EXPAND_FUNCTION_DECLARATIONS with the function_ids you need to load " +
                        "their full declarations before calling them.",
                )
            }.toString()
        }

        private fun expandFunctionDeclarations(
            threadId: String,
            arguments: Map<String, Any?>,
            catalog: ToolCatalog,
        ): String {
            val requestedIds = parseFunctionIds(arguments)
            val allFunctions = catalog.apps.flatMap { it.functions }
            val loaded = mutableListOf<String>()
            val notFound = mutableListOf<String>()
            val newlyExpanded = mutableSetOf<AppFunctionName>()
            for (id in requestedIds) {
                val matches = allFunctions.filter { it.id == id }
                if (matches.isEmpty()) {
                    notFound.add(id)
                } else {
                    loaded.add(id)
                    newlyExpanded.addAll(matches.map { it.name })
                }
            }
            if (newlyExpanded.isNotEmpty()) {
                expandedFunctionsByThread.merge(threadId, newlyExpanded) { old, new -> old + new }
            }
            return buildJsonObject {
                put("loaded", buildJsonArray { loaded.forEach { add(JsonPrimitive(it)) } })
                if (notFound.isNotEmpty()) {
                    put("not_found", buildJsonArray { notFound.forEach { add(JsonPrimitive(it)) } })
                }
                put(
                    "next_step",
                    if (loaded.isNotEmpty()) {
                        "The loaded functions are now available as tools. Call them directly."
                    } else {
                        "No functions were loaded. Use function_ids returned by " +
                            "$EXPAND_TOOL_DESCRIPTION."
                    },
                )
            }.toString()
        }

        private fun buildMetaTools(catalog: ToolCatalog): List<AppFunctionMetadata> {
            val appCatalog =
                catalog.apps.joinToString(separator = "\n") { app ->
                    "- ${app.packageName}: ${app.instructions.toOneLineDescription()}"
                }
            val expandToolDescription =
                AppFunctionMetadata(
                    name = AppFunctionName(META_TOOL_PACKAGE, EXPAND_TOOL_DESCRIPTION),
                    schema = null,
                    parameters =
                        listOf(
                            AppFunctionParameterMetadata(
                                name = PARAM_PACKAGE_NAME,
                                isRequired = true,
                                dataType =
                                    AppFunctionStringTypeMetadata(
                                        isNullable = false,
                                        enumValues = catalog.apps.map { it.packageName }.toSet(),
                                    ),
                                description = "The package name of the app to expand.",
                            ),
                        ),
                    response = stringResponse("The app instructions and its functions."),
                    description =
                        "Returns the full instructions of an app and a one-line description " +
                            "of each of its functions. Call this to discover what an app can " +
                            "do before using it. Available apps:\n$appCatalog",
                    deprecation = null,
                    packageMetadata = metaToolPackageMetadata,
                )
            val expandFunctionDeclarations =
                AppFunctionMetadata(
                    name = AppFunctionName(META_TOOL_PACKAGE, EXPAND_FUNCTION_DECLARATIONS),
                    schema = null,
                    parameters =
                        listOf(
                            AppFunctionParameterMetadata(
                                name = PARAM_FUNCTION_IDS,
                                isRequired = true,
                                dataType =
                                    AppFunctionArrayTypeMetadata(
                                        itemType = AppFunctionStringTypeMetadata(isNullable = false),
                                        isNullable = false,
                                    ),
                                description =
                                    "The function_id values returned by " +
                                        "$EXPAND_TOOL_DESCRIPTION to load.",
                            ),
                        ),
                    response = stringResponse("The functions that were loaded."),
                    description =
                        "Loads the full declarations of the given app functions so they can be " +
                            "called as tools. Call $EXPAND_TOOL_DESCRIPTION first to find the " +
                            "function_ids.",
                    deprecation = null,
                    packageMetadata = metaToolPackageMetadata,
                )
            return listOf(expandToolDescription, expandFunctionDeclarations)
        }

        private fun stringResponse(description: String) =
            AppFunctionResponseMetadata(
                valueType = AppFunctionStringTypeMetadata(isNullable = false),
                description = description,
            )

        private fun errorJson(message: String): String = buildJsonObject { put("error", message) }.toString()

        companion object {
            const val META_TOOL_PACKAGE = "com.example.appfunctions.agent.toolselection"
            const val EXPAND_TOOL_DESCRIPTION = "expand_tool_description"
            const val EXPAND_FUNCTION_DECLARATIONS = "expand_function_declarations"
            const val PARAM_PACKAGE_NAME = "package_name"
            const val PARAM_FUNCTION_IDS = "function_ids"

            private val metaToolPackageMetadata =
                AppFunctionPackageMetadata(
                    packageName = META_TOOL_PACKAGE,
                    components = AppFunctionComponentsMetadata(),
                )
        }
    }
