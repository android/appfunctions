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

import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionBooleanTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionDoubleTypeMetadata
import androidx.appfunctions.metadata.AppFunctionFloatTypeMetadata
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionLongTypeMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import com.example.appfunctions.agent.ui.layout.AdaptiveInputContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.appfunctions.agent.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFunctionDataTypeInput(
    dataType: AppFunctionDataTypeMetadata,
    value: Any?,
    onValueChange: (Any) -> Unit,
    components: AppFunctionComponentsMetadata,
    label: String,
    modifier: Modifier = Modifier,
    isRequired: Boolean = false,
) {
    var showSheet by remember { mutableStateOf(false) }

    when (dataType) {
        is AppFunctionStringTypeMetadata -> {
            val enumValues = dataType.enumValues
            if (!enumValues.isNullOrEmpty()) {
                EnumDropdown(
                    value = value as? String ?: "",
                    options = enumValues.toList(),
                    onValueChange = onValueChange,
                    modifier = modifier,
                    label = label,
                    isRequired = isRequired,
                )
            } else {
                PrimitiveTextInput(
                    value = value as? String ?: "",
                    onValueChange = onValueChange,
                    label = label,
                    isRequired = isRequired,
                    modifier = modifier,
                )
            }
        }
        is AppFunctionIntTypeMetadata -> {
            val enumValues = dataType.enumValues
            if (!enumValues.isNullOrEmpty()) {
                EnumDropdown(
                    value = value as? String ?: "",
                    options = enumValues.map { it.toString() },
                    onValueChange = onValueChange,
                    modifier = modifier,
                    label = label,
                    isRequired = isRequired,
                )
            } else {
                PrimitiveTextInput(
                    value = value as? String ?: "",
                    onValueChange = onValueChange,
                    label = label,
                    isRequired = isRequired,
                    modifier = modifier,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
        is AppFunctionLongTypeMetadata -> {
            PrimitiveTextInput(
                value = value as? String ?: "",
                onValueChange = onValueChange,
                label = label,
                isRequired = isRequired,
                modifier = modifier,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        is AppFunctionBooleanTypeMetadata -> {
            BooleanTypeInput(
                value = value as? Boolean ?: false,
                onValueChange = onValueChange,
                label = label,
                isRequired = isRequired,
                modifier = modifier,
            )
        }
        is AppFunctionDoubleTypeMetadata,
        is AppFunctionFloatTypeMetadata,
        -> {
            PrimitiveTextInput(
                value = value as? String ?: "",
                onValueChange = onValueChange,
                label = label,
                isRequired = isRequired,
                modifier = modifier,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        is AppFunctionObjectTypeMetadata -> {
            val mapValue = value as? Map<String, Any> ?: emptyMap()
            val isModified = mapValue.isNotEmpty()
            val summary = if (isModified) createSummary(mapValue) else null

            ComplexTypeInput(
                label = label,
                summary = summary,
                selected = isModified,
                onClick = { showSheet = true },
                isRequired = isRequired,
                modifier = modifier,
            )

            if (showSheet) {
                AdaptiveInputContainer(
                    onDismissRequest = { showSheet = false },
                ) {
                    AppFunctionObjectTypeSheetContent(
                        dataType = dataType,
                        value = mapValue,
                        onValueChange = onValueChange,
                        components = components,
                        onReset = {
                            onValueChange(emptyMap<String, Any>())
                            showSheet = false
                        },
                        onConfirm = { showSheet = false },
                    )
                }
            }
        }
        is AppFunctionArrayTypeMetadata -> {
            ArrayTypeInput(
                dataType = dataType,
                value = value as? List<Any> ?: emptyList(),
                onValueChange = onValueChange,
                components = components,
                label = label,
                isRequired = isRequired,
                modifier = modifier,
            )
        }
        is AppFunctionReferenceTypeMetadata -> {
            val referenceKey = dataType.referenceDataType
            val objectType = components.dataTypes[referenceKey] as? AppFunctionObjectTypeMetadata
            if (objectType != null) {
                val mapValue = value as? Map<String, Any> ?: emptyMap()
                val isModified = mapValue.isNotEmpty()
                val summary = if (isModified) createSummary(mapValue) else null

                ComplexTypeInput(
                    label = label,
                    summary = summary,
                    selected = isModified,
                    onClick = { showSheet = true },
                    isRequired = isRequired,
                    modifier = modifier,
                )

                if (showSheet) {
                    AdaptiveInputContainer(
                        onDismissRequest = { showSheet = false },
                    ) {
                        AppFunctionObjectTypeSheetContent(
                            dataType = objectType,
                            value = mapValue,
                            onValueChange = onValueChange,
                            components = components,
                            onReset = {
                                onValueChange(emptyMap<String, Any>())
                                showSheet = false
                            },
                            onConfirm = { showSheet = false },
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.debugging_reference_not_found, referenceKey),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = modifier,
                )
            }
        }
        else -> {
            Text(
                text =
                    stringResource(
                        R.string.debugging_unsupported_type,
                        dataType::class.simpleName ?: "",
                    ),
                style = MaterialTheme.typography.bodySmall,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun PrimitiveTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isRequired: Boolean,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    com.example.appfunctions.agent.ui.components.TvSurfaceTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = label,
        label = if (isRequired) "$label *" else label,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = keyboardOptions,
        shape = MaterialTheme.shapes.medium,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnumDropdown(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    label: String,
    isRequired: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        ) {
            ListItem(
                overlineContent = {
                    Row {
                        Text(label)
                        if (isRequired) {
                            Text(
                                text = stringResource(R.string.debugging_required_indicator),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                headlineContent = {
                    Text(
                        text = value.ifEmpty { stringResource(R.string.debugging_set_object) },
                        style = MaterialTheme.typography.bodyLarge,
                        color =
                            if (value.isNotEmpty()) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                },
                trailingContent = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun BooleanTypeInput(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    label: String,
    isRequired: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(interactionSource, indication = null) { onValueChange(!value) }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (isRequired) {
                Text(
                    text = stringResource(R.string.debugging_required_indicator),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Checkbox(
            checked = value,
            interactionSource = interactionSource,
            onCheckedChange = onValueChange,
        )
    }
}

@Composable
fun ObjectTypeInput(
    dataType: AppFunctionObjectTypeMetadata,
    value: Map<String, Any>,
    onValueChange: (Map<String, Any>) -> Unit,
    components: AppFunctionComponentsMetadata,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
    ) {
        for ((propName, propType) in dataType.properties) {
            val isPropRequired = dataType.required.contains(propName)
            val propValue = value[propName] ?: createDefaultValue(propType)
            AppFunctionDataTypeInput(
                dataType = propType,
                value = propValue,
                onValueChange = { valValue -> onValueChange(value + (propName to valValue)) },
                components = components,
                label = propName,
                isRequired = isPropRequired,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
fun ArrayTypeInput(
    dataType: AppFunctionArrayTypeMetadata,
    value: List<Any>,
    onValueChange: (List<Any>) -> Unit,
    components: AppFunctionComponentsMetadata,
    label: String,
    isRequired: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                Text(
                    modifier = Modifier.padding(start = 4.dp),
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isRequired) {
                    Text(
                        text = stringResource(R.string.debugging_required_indicator),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            TextButton(
                onClick = { onValueChange(value + createDefaultValue(dataType.itemType)) },
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.debugging_add_item))
            }
        }

        value.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Column(modifier = Modifier.weight(1.0f)) {
                    AppFunctionDataTypeInput(
                        dataType = dataType.itemType,
                        value = item,
                        onValueChange = { valValue ->
                            val newItems = value.toMutableList().apply { set(index, valValue) }
                            onValueChange(newItems)
                        },
                        components = components,
                        label = stringResource(R.string.debugging_item, index + 1),
                        isRequired = false,
                    )
                }
                IconButton(
                    onClick = {
                        val newItems = value.toMutableList().apply { removeAt(index) }
                        onValueChange(newItems)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.RemoveCircle,
                        contentDescription = stringResource(R.string.debugging_remove),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

fun createDefaultValue(dataType: AppFunctionDataTypeMetadata): Any {
    return when (dataType) {
        is AppFunctionStringTypeMetadata -> ""
        is AppFunctionIntTypeMetadata -> ""
        is AppFunctionBooleanTypeMetadata -> false
        is AppFunctionObjectTypeMetadata -> emptyMap<String, Any>()
        is AppFunctionArrayTypeMetadata -> emptyList<Any>()
        is AppFunctionReferenceTypeMetadata -> emptyMap<String, Any>()
        else -> ""
    }
}

@Composable
fun ComplexTypeInput(
    label: String,
    summary: String?,
    selected: Boolean,
    onClick: () -> Unit,
    isRequired: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        ListItem(
            overlineContent = {
                Row {
                    Text(label)
                    if (isRequired) {
                        Text(
                            text = stringResource(R.string.debugging_required_indicator),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            headlineContent = {
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = summary ?: stringResource(R.string.debugging_set_object),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

fun createSummary(map: Map<String, Any>): String {
    val summary = map.entries.joinToString(", ") { "${it.key}=${it.value}" }
    return if (summary.length > 40) summary.take(37) + "..." else summary
}

@Composable
private fun AppFunctionObjectTypeSheetContent(
    dataType: AppFunctionObjectTypeMetadata,
    value: Map<String, Any>,
    onValueChange: (Map<String, Any>) -> Unit,
    components: AppFunctionComponentsMetadata,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ObjectTypeInput(
            dataType = dataType,
            value = value,
            onValueChange = onValueChange,
            components = components,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(
                onClick = onReset,
                modifier = Modifier.height(56.dp).weight(1f),
            ) {
                Text(text = stringResource(R.string.debugging_reset))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.height(56.dp).weight(1f),
            ) {
                Text(text = stringResource(R.string.debugging_confirm))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppFunctionObjectTypeSheetContentPreview() {
    val objectType =
        AppFunctionObjectTypeMetadata(
            properties =
                mapOf(
                    "prop1" to AppFunctionStringTypeMetadata(isNullable = false),
                    "prop2" to AppFunctionIntTypeMetadata(isNullable = false),
                ),
            required = listOf("prop1"),
            qualifiedName = null,
            isNullable = false,
        )
    AppFunctionObjectTypeSheetContent(
        dataType = objectType,
        value = mapOf("prop1" to "Value 1", "prop2" to "123"),
        onValueChange = {},
        components = AppFunctionComponentsMetadata(emptyMap()),
        onReset = {},
        onConfirm = {},
    )
}

@Preview(showBackground = true)
@Composable
fun AppFunctionObjectTypeSheetContentEmptyPreview() {
    val objectType =
        AppFunctionObjectTypeMetadata(
            properties =
                mapOf(
                    "prop1" to AppFunctionStringTypeMetadata(isNullable = false),
                    "prop2" to AppFunctionIntTypeMetadata(isNullable = false),
                ),
            required = listOf("prop1"),
            qualifiedName = null,
            isNullable = false,
        )
    AppFunctionObjectTypeSheetContent(
        dataType = objectType,
        value = emptyMap(),
        onValueChange = {},
        components = AppFunctionComponentsMetadata(emptyMap()),
        onReset = {},
        onConfirm = {},
    )
}

@Preview(showBackground = true)
@Composable
fun PrimitiveTextInputPreview() {
    Column(modifier = Modifier.padding(16.dp)) {
        PrimitiveTextInput(
            value = "",
            onValueChange = {},
            label = "Parameter Name",
            isRequired = true,
        )
        Spacer(Modifier.height(8.dp))
        PrimitiveTextInput(
            value = "Filled value",
            onValueChange = {},
            label = "Optional Parameter",
            isRequired = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EnumDropdownPreview() {
    Column(modifier = Modifier.padding(16.dp)) {
        EnumDropdown(
            value = "",
            options = listOf("Option 1", "Option 2", "Option 3"),
            onValueChange = {},
            label = "Enum Parameter",
            isRequired = true,
        )
        Spacer(Modifier.height(8.dp))
        EnumDropdown(
            value = "Option 1",
            options = listOf("Option 1", "Option 2", "Option 3"),
            onValueChange = {},
            label = "Enum Parameter",
            isRequired = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BooleanTypeInputPreview() {
    Column(modifier = Modifier.padding(16.dp)) {
        BooleanTypeInput(
            value = false,
            onValueChange = {},
            label = "Boolean Parameter",
            isRequired = true,
        )
        BooleanTypeInput(
            value = true,
            onValueChange = {},
            label = "Optional Boolean",
            isRequired = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ObjectTypeInputPreview() {
    val objectType =
        AppFunctionObjectTypeMetadata(
            properties =
                mapOf(
                    "prop1" to AppFunctionStringTypeMetadata(isNullable = false),
                    "prop2" to AppFunctionIntTypeMetadata(isNullable = false),
                ),
            required = listOf("prop1"),
            qualifiedName = null,
            isNullable = false,
        )
    AppFunctionDataTypeInput(
        dataType = objectType,
        value = emptyMap<String, Any>(),
        onValueChange = {},
        components = AppFunctionComponentsMetadata(emptyMap()),
        label = "Object Parameter",
    )
}

@Preview(showBackground = true)
@Composable
fun ArrayTypeInputPreview() {
    val arrayType =
        AppFunctionArrayTypeMetadata(
            itemType = AppFunctionStringTypeMetadata(isNullable = false),
            isNullable = false,
        )
    Column(modifier = Modifier.padding(16.dp)) {
        ArrayTypeInput(
            dataType = arrayType,
            value = emptyList<Any>(),
            onValueChange = {},
            components = AppFunctionComponentsMetadata(emptyMap()),
            label = "Empty Array",
            isRequired = true,
        )
        Spacer(Modifier.height(16.dp))
        ArrayTypeInput(
            dataType = arrayType,
            value = listOf("Item 1", "Item 2"),
            onValueChange = {},
            components = AppFunctionComponentsMetadata(emptyMap()),
            label = "Filled Array",
            isRequired = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ReferenceTypeInputPreview() {
    val objectType =
        AppFunctionObjectTypeMetadata(
            properties =
                mapOf(
                    "prop1" to AppFunctionStringTypeMetadata(isNullable = false),
                ),
            required = listOf("prop1"),
            qualifiedName = null,
            isNullable = false,
        )
    val components =
        AppFunctionComponentsMetadata(
            mapOf("FakeType" to objectType),
        )
    val referenceType =
        AppFunctionReferenceTypeMetadata(
            referenceDataType = "FakeType",
            isNullable = false,
        )
    AppFunctionDataTypeInput(
        dataType = referenceType,
        value = emptyMap<String, Any>(),
        onValueChange = {},
        components = components,
        label = "Reference Parameter",
    )
}

@Preview(showBackground = true)
@Composable
fun ComplexTypeInputPreview() {
    Column(modifier = Modifier.padding(16.dp)) {
        ComplexTypeInput(
            label = "Location",
            summary = null,
            selected = false,
            onClick = {},
            isRequired = true,
        )
        Spacer(Modifier.height(8.dp))
        ComplexTypeInput(
            label = "User",
            summary = "name=John, age=30",
            selected = true,
            onClick = {},
            isRequired = false,
        )
    }
}
