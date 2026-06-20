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
package com.example.appfunctions.agent.ui.screens.debugging

import android.app.PendingIntent
import android.widget.Toast
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionResult
import com.example.appfunctions.agent.ui.theme.AppFunctionsAgentTheme
import com.example.appfunctions.agent.ui.theme.GoogleSansCodeFontFamily
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun FunctionsFoundContent(
    state: SearchAppResultState.FunctionsFoundState,
    onFunctionExpandedChange: (String, Boolean) -> Unit,
    onFunctionInputsChange: (String, Map<String, Any>) -> Unit,
    onInvoke: (AppFunctionMetadata) -> Unit,
    onClearResult: () -> Unit,
    onLaunchPendingIntent: (PendingIntent) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier.padding(horizontal = 2.dp)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 10.dp, top = 2.dp, end = 10.dp, bottom = 80.dp),
    ) {
        items(
            items = state.functions,
            key = { function -> function.id },
        ) { function ->
            val expanded = state.expandedFunctions.contains(function.id)
            val inputValues = state.functionInputs[function.id] ?: emptyMap()

            AppFunctionItem(
                function = function,
                expanded = expanded,
                inputValues = inputValues,
                onExpandedChange = { isExpanded ->
                    onFunctionExpandedChange(function.id, isExpanded)
                },
                onInputValuesChange = { newValues ->
                    onFunctionInputsChange(function.id, newValues)
                },
                onInvoke = { _ -> onInvoke(function) },
            )
        }
    }

    if (state.executionResult != null) {
        AlertDialog(
            onDismissRequest = onClearResult,
            title = {
                val executedFunc = state.executedFunction
                val executedInputs = state.executedInputs
                val paramColor = MaterialTheme.colorScheme.onSurfaceVariant
                val paramSize = MaterialTheme.typography.bodyMedium.fontSize
                Column {
                    val headerAnnotatedString =
                        remember(executedFunc, executedInputs, paramColor, paramSize) {
                            if (executedFunc != null) {
                                val hashIndex = executedFunc.id.indexOf('#')
                                val functionName =
                                    if (hashIndex != -1) {
                                        executedFunc.id.substring(hashIndex + 1)
                                    } else {
                                        executedFunc.id
                                    }
                                val paramsString =
                                    executedInputs.entries.joinToString(", ") {
                                        "${it.key}=${it.value}"
                                    }

                                androidx.compose.ui.text.buildAnnotatedString {
                                    append(functionName)
                                    append("(\n")
                                    if (paramsString.isNotEmpty()) {
                                        withStyle(
                                            style =
                                                androidx.compose.ui.text.SpanStyle(
                                                    color = paramColor,
                                                    fontSize = paramSize,
                                                    fontWeight = FontWeight.Normal,
                                                ),
                                        ) {
                                            append("  ")
                                            append(paramsString)
                                        }
                                        append("\n")
                                    }
                                    append(")")
                                }
                            } else {
                                androidx.compose.ui.text.AnnotatedString("")
                            }
                        }
                    Text(
                        text =
                            if (headerAnnotatedString.isNotEmpty()) {
                                headerAnnotatedString
                            } else {
                                val fallbackText =
                                    when (state.executionResult) {
                                        is ExecuteAppFunctionResult.Error ->
                                            stringResource(R.string.debugging_error)
                                        is ExecuteAppFunctionResult.PendingIntentAction ->
                                            stringResource(R.string.debugging_action_required)
                                        else ->
                                            stringResource(R.string.debugging_execution_result)
                                    }
                                androidx.compose.ui.text.AnnotatedString(fallbackText)
                            },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                ) {
                    when (val result = state.executionResult) {
                        is ExecuteAppFunctionResult.Error -> {
                            SelectionContainer {
                                Text(
                                    text = result.exception.message ?: "Unknown error",
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = GoogleSansCodeFontFamily,
                                        ),
                                )
                            }
                        }
                        is ExecuteAppFunctionResult.Data -> {
                            ExecutionResultDataView(
                                formattedJson = result.formattedJson,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is ExecuteAppFunctionResult.PendingIntentAction -> {
                            Text(
                                text = stringResource(R.string.debugging_pending_intent_disclaimer),
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = GoogleSansCodeFontFamily,
                                    ),
                            )
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                val result = state.executionResult
                if (result is ExecuteAppFunctionResult.PendingIntentAction) {
                    Button(onClick = { onLaunchPendingIntent(result.pendingIntent) }) {
                        Text(text = stringResource(R.string.debugging_open))
                    }
                } else {
                    Button(onClick = onClearResult) {
                        Text(text = stringResource(R.string.debugging_confirm))
                    }
                }
            },
            dismissButton = {
                if (state.executionResult is ExecuteAppFunctionResult.PendingIntentAction) {
                    TextButton(onClick = onClearResult) {
                        Text(text = stringResource(R.string.debugging_dismiss))
                    }
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FunctionsFoundContentPreview() {
    val dummyState =
        SearchAppResultState.FunctionsFoundState(
            functions = emptyList(),
            expandedFunctions = emptySet(),
            functionInputs = emptyMap(),
            executionResult = null,
        )
    AppFunctionsAgentTheme {
        FunctionsFoundContent(
            state = dummyState,
            onFunctionExpandedChange = { _, _ -> },
            onFunctionInputsChange = { _, _ -> },
            onInvoke = { _ -> },
            onClearResult = {},
            onLaunchPendingIntent = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExecutionResultDataView(
    formattedJson: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val flatItems = remember(formattedJson) { parseJsonToDisplayItems(formattedJson) }

    Column(modifier = modifier) {
        if (flatItems.isEmpty()) {
            SelectionContainer {
                Text(
                    text = formattedJson,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = GoogleSansCodeFontFamily,
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                )
            }
        } else {
            flatItems.forEach { displayItem ->
                when (displayItem) {
                    is ExecutionDisplayItem.SimpleProperty -> {
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayItem.key,
                                        style =
                                            MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = GoogleSansCodeFontFamily,
                                            ),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    SelectionContainer {
                                        Text(
                                            text = displayItem.value,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                    tooltip = {
                                        PlainTooltip {
                                            Text(stringResource(R.string.debugging_copy_value))
                                        }
                                    },
                                    state = rememberTooltipState(),
                                ) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(displayItem.value))
                                            Toast.makeText(
                                                context,
                                                context.getString(
                                                    R.string.debugging_copied_value,
                                                    displayItem.key,
                                                ),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = stringResource(R.string.debugging_copy_value),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is ExecutionDisplayItem.Group -> {
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border =
                                BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                ),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = displayItem.header,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                                displayItem.properties.forEach { prop ->
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = prop.key,
                                                style =
                                                    MaterialTheme.typography.bodyMedium.copy(
                                                        fontFamily = GoogleSansCodeFontFamily,
                                                    ),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            SelectionContainer {
                                                Text(
                                                    text = prop.value,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TooltipBox(
                                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                            tooltip = {
                                                PlainTooltip {
                                                    Text(stringResource(R.string.debugging_copy_value))
                                                }
                                            },
                                            state = rememberTooltipState(),
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(prop.value))
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(
                                                            R.string.debugging_copied_value,
                                                            prop.key,
                                                        ),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = stringResource(R.string.debugging_copy_value),
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed class ExecutionDisplayItem {
    data class SimpleProperty(val key: String, val value: String) : ExecutionDisplayItem()

    data class Group(val header: String, val properties: List<SimpleProperty>) : ExecutionDisplayItem()
}

private fun parseJsonToDisplayItems(jsonStr: String): List<ExecutionDisplayItem> {
    val items = mutableListOf<ExecutionDisplayItem>()
    try {
        val trimmed = jsonStr.trim()
        if (trimmed.startsWith("[")) {
            val jsonArray = JSONArray(trimmed)
            parseJsonArray(jsonArray, "Result", items)
        } else if (trimmed.startsWith("{")) {
            val jsonObject = JSONObject(trimmed)
            parseJsonObject(jsonObject, "", items)
        }
    } catch (e: Exception) {
        // Fallback
    }
    return items
}

private fun parseJsonObject(
    jsonObject: JSONObject,
    prefix: String,
    list: MutableList<ExecutionDisplayItem>,
) {
    val keys = jsonObject.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = jsonObject.get(key)
        val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
        when (value) {
            is JSONObject -> {
                parseJsonObject(value, fullKey, list)
            }
            is JSONArray -> {
                parseJsonArray(value, fullKey, list)
            }
            else -> {
                list.add(ExecutionDisplayItem.SimpleProperty(fullKey, value.toString()))
            }
        }
    }
}

private fun parseJsonArray(
    jsonArray: JSONArray,
    arrayKey: String,
    list: MutableList<ExecutionDisplayItem>,
) {
    for (i in 0 until jsonArray.length()) {
        val value = jsonArray.get(i)
        val groupHeader = "$arrayKey #${i + 1}"
        when (value) {
            is JSONObject -> {
                val groupProps = mutableListOf<ExecutionDisplayItem>()
                parseJsonObject(value, "", groupProps)
                val simpleProps = mutableListOf<ExecutionDisplayItem.SimpleProperty>()
                groupProps.forEach { item ->
                    when (item) {
                        is ExecutionDisplayItem.SimpleProperty -> simpleProps.add(item)
                        is ExecutionDisplayItem.Group -> simpleProps.addAll(item.properties)
                    }
                }
                list.add(ExecutionDisplayItem.Group(groupHeader, simpleProps))
            }
            is JSONArray -> {
                val groupProps = mutableListOf<ExecutionDisplayItem>()
                parseJsonArray(value, "", groupProps)
                val simpleProps = mutableListOf<ExecutionDisplayItem.SimpleProperty>()
                groupProps.forEach { item ->
                    when (item) {
                        is ExecutionDisplayItem.SimpleProperty -> simpleProps.add(item)
                        is ExecutionDisplayItem.Group -> simpleProps.addAll(item.properties)
                    }
                }
                list.add(ExecutionDisplayItem.Group(groupHeader, simpleProps))
            }
            else -> {
                list.add(
                    ExecutionDisplayItem.Group(
                        groupHeader,
                        listOf(ExecutionDisplayItem.SimpleProperty("value", value.toString())),
                    ),
                )
            }
        }
    }
}
