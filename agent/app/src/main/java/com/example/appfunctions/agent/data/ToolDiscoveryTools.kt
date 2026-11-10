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
package com.example.appfunctions.agent.data

import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import com.example.appfunctions.agent.data.AgentInternalTools.Companion.EXPAND_FUNCTION_DECLARATIONS
import com.example.appfunctions.agent.data.AgentInternalTools.Companion.EXPAND_TOOL_DESCRIPTION
import com.example.appfunctions.agent.data.AgentInternalTools.Companion.INTERNAL_TOOL_PACKAGE
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionResult
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsParams
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import com.example.appfunctions.agent.domain.appfunction.ResolveAppInfoUseCase
import com.example.appfunctions.agent.domain.appfunction.toSummary
import dagger.Lazy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Internal agent tools that implement progressive disclosure for tool selection.
 *
 * Instead of declaring every app function up front, the model sees one line per app (embedded in
 * [EXPAND_TOOL_DESCRIPTION]) and then:
 * 1. calls [EXPAND_TOOL_DESCRIPTION] to read an app's full instructions and a one-line description
 *    of each of its functions (without schemas);
 * 2. calls [EXPAND_FUNCTION_DECLARATIONS] to load the declarations it needs. The result is an
 *    [ExecuteAppFunctionResult.ToolsDiscovered] whose tools the caller makes available for the rest
 *    of the current agent turn.
 *
 * This class is stateless: apps are re-queried on every call and nothing is kept across turns.
 */
@Singleton
class ToolDiscoveryTools
    @Inject
    constructor(
        private val resolveAppInfoUseCase: ResolveAppInfoUseCase,
        // Lazy breaks the dependency cycle GetAppFunctionsUseCase -> AgentInternalTools -> this.
        private val getAppFunctionsUseCase: Lazy<GetAppFunctionsUseCase>,
    ) {
        /**
         * Builds the tool discovery tools for [apps]. Returns no tools when there are no apps.
         */
        suspend fun buildTools(apps: Map<AppFunctionPackageMetadata, List<AppFunctionMetadata>>): List<AppFunctionMetadata> {
            val catalog = resolveCatalog(apps)
            if (catalog.isEmpty()) {
                return emptyList()
            }
            val appLines =
                catalog.joinToString(separator = "\n") { app ->
                    "- ${app.label} (${app.packageName}): ${app.summary()}"
                }
            return listOf(
                AppFunctionMetadata(
                    name = AppFunctionName(INTERNAL_TOOL_PACKAGE, EXPAND_TOOL_DESCRIPTION),
                    schema = null,
                    parameters = listOf(packageNameParameter()),
                    response = stringResponse("The app's instructions and its functions."),
                    description =
                        "Returns an app's full instructions and a one-line description of each of " +
                            "its functions, without parameter schemas. App functions are " +
                            "discovered progressively: call this for each app you need, then " +
                            "call $EXPAND_FUNCTION_DECLARATIONS to load the functions you will " +
                            "use. Loaded functions become callable tools. Available apps:\n" +
                            appLines,
                    deprecation = null,
                    packageMetadata = INTERNAL_PACKAGE_METADATA,
                ),
                AppFunctionMetadata(
                    name = AppFunctionName(INTERNAL_TOOL_PACKAGE, EXPAND_FUNCTION_DECLARATIONS),
                    schema = null,
                    parameters =
                        listOf(
                            packageNameParameter(),
                            AppFunctionParameterMetadata(
                                name = PARAM_FUNCTION_IDS,
                                isRequired = true,
                                dataType =
                                    AppFunctionArrayTypeMetadata(
                                        itemType = AppFunctionStringTypeMetadata(isNullable = false),
                                        isNullable = false,
                                    ),
                                description =
                                    "The function_id values returned by $EXPAND_TOOL_DESCRIPTION " +
                                        "for this app.",
                            ),
                        ),
                    response = stringResponse("The functions that were loaded."),
                    description =
                        "Loads the full declarations of functions from one app so they can be " +
                            "called as tools. Call $EXPAND_TOOL_DESCRIPTION first to get the " +
                            "function_ids. To use functions from several apps, call this once " +
                            "per app.",
                    deprecation = null,
                    packageMetadata = INTERNAL_PACKAGE_METADATA,
                ),
            )
        }

        /** Executes one of the tool discovery tools. */
        suspend fun execute(
            function: AppFunctionMetadata,
            parameters: AppFunctionData,
        ): ExecuteAppFunctionResult {
            val packageName = parameters.getString(PARAM_PACKAGE_NAME)
            val apps = getAvailableApps()
            val entry =
                apps.entries.find { it.key.packageName == packageName }
                    ?: return errorResult(
                        "Unknown $PARAM_PACKAGE_NAME '$packageName'. Available apps: " +
                            apps.keys.joinToString { it.packageName },
                    )
            return when (function.id) {
                EXPAND_TOOL_DESCRIPTION -> expandToolDescription(entry.key, entry.value)
                EXPAND_FUNCTION_DECLARATIONS ->
                    expandFunctionDeclarations(
                        packageName = entry.key.packageName,
                        functions = entry.value,
                        requestedIds = parameters.getStringList(PARAM_FUNCTION_IDS).orEmpty(),
                    )
                else -> throw IllegalArgumentException("Unknown tool discovery tool: ${function.id}")
            }
        }

        private suspend fun expandToolDescription(
            packageMetadata: AppFunctionPackageMetadata,
            functions: List<AppFunctionMetadata>,
        ): ExecuteAppFunctionResult {
            val info = resolveAppInfoUseCase(packageMetadata)
            val json =
                buildJsonObject {
                    put(PARAM_PACKAGE_NAME, packageMetadata.packageName)
                    put("app_name", info.label)
                    put("instructions", info.instructions ?: NO_INSTRUCTIONS)
                    put(
                        "functions",
                        buildJsonArray {
                            functions.forEach { function ->
                                add(
                                    buildJsonObject {
                                        put("function_id", function.id)
                                        put("description", function.description.toSummary())
                                    },
                                )
                            }
                        },
                    )
                    put(
                        "next_step",
                        "Call $EXPAND_FUNCTION_DECLARATIONS with this $PARAM_PACKAGE_NAME and the " +
                            "function_ids you need before calling them.",
                    )
                }
            return ExecuteAppFunctionResult.Data(AppFunctionData.EMPTY, json.toString())
        }

        private fun expandFunctionDeclarations(
            packageName: String,
            functions: List<AppFunctionMetadata>,
            requestedIds: List<String>,
        ): ExecuteAppFunctionResult {
            val requested = requestedIds.distinct()
            val loaded =
                requested.mapNotNull { id ->
                    functions.find { it.name == AppFunctionName(packageName, id) }
                }
            val notFound = requested - loaded.map { it.id }.toSet()
            val json =
                buildJsonObject {
                    put(PARAM_PACKAGE_NAME, packageName)
                    put("loaded", JsonArray(loaded.map { JsonPrimitive(it.id) }))
                    if (notFound.isNotEmpty()) {
                        put("not_found", JsonArray(notFound.map { JsonPrimitive(it) }))
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
                }
            return ExecuteAppFunctionResult.ToolsDiscovered(json.toString(), loaded)
        }

        private suspend fun getAvailableApps(): Map<AppFunctionPackageMetadata, List<AppFunctionMetadata>> =
            getAppFunctionsUseCase
                .get()
                .invoke(
                    GetAppFunctionsParams(
                        isEnabled = true,
                        isConnected = true,
                        includeInternalTools = false,
                    ),
                ).first()

        private suspend fun resolveCatalog(apps: Map<AppFunctionPackageMetadata, List<AppFunctionMetadata>>): List<CatalogEntry> =
            coroutineScope {
                apps
                    .filterKeys { it.packageName != INTERNAL_TOOL_PACKAGE }
                    .filterValues { it.isNotEmpty() }
                    .map { (packageMetadata, functions) ->
                        async {
                            val info = resolveAppInfoUseCase(packageMetadata)
                            CatalogEntry(
                                packageName = packageMetadata.packageName,
                                label = info.label,
                                instructions = info.instructions,
                                functions = functions,
                            )
                        }
                    }.awaitAll()
            }

        private fun packageNameParameter() =
            AppFunctionParameterMetadata(
                name = PARAM_PACKAGE_NAME,
                isRequired = true,
                dataType = AppFunctionStringTypeMetadata(isNullable = false),
                description = "The package name of the app, from the list of available apps.",
            )

        private fun stringResponse(description: String) =
            AppFunctionResponseMetadata(
                valueType = AppFunctionStringTypeMetadata(isNullable = false),
                description = description,
            )

        private fun errorResult(message: String): ExecuteAppFunctionResult =
            ExecuteAppFunctionResult.Data(
                AppFunctionData.EMPTY,
                buildJsonObject { put("error", message) }.toString(),
            )

        private data class CatalogEntry(
            val packageName: String,
            val label: String,
            val instructions: String?,
            val functions: List<AppFunctionMetadata>,
        ) {
            /** One-line summary; falls back to function names when the app has no instructions. */
            fun summary(): String =
                instructions?.toSummary()
                    ?: (
                        "Functions: " +
                            functions.joinToString { it.id.substringAfterLast('#').substringAfterLast('.') }
                    ).toSummary()
        }

        companion object {
            const val PARAM_PACKAGE_NAME = "package_name"
            const val PARAM_FUNCTION_IDS = "function_ids"
            const val NO_INSTRUCTIONS = "No instructions provided."

            private val INTERNAL_PACKAGE_METADATA =
                AppFunctionPackageMetadata(
                    packageName = INTERNAL_TOOL_PACKAGE,
                    components = AppFunctionComponentsMetadata(),
                )
        }
    }
