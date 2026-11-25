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

import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.ui.theme.GoogleSansCodeFontFamily

/**
 * A function of the selected app. Shows whether it is disabled or dynamic (activity-scoped) and
 * enables Run only when the function can run from this screen.
 */
@Composable
fun AppFunctionItem(
    function: AppFunctionMetadata,
    status: AppFunctionStatus,
    expanded: Boolean,
    inputValues: Map<String, Any>,
    onExpandedChange: (Boolean) -> Unit,
    onInputValuesChange: (Map<String, Any>) -> Unit,
    onInvoke: (Map<String, Any>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tonalElevation by animateDpAsState(targetValue = if (expanded) 0.dp else 2.dp)
    val shadowElevation by animateDpAsState(targetValue = if (expanded) 2.dp else 0.dp)
    val surfaceColor by
        animateColorAsState(
            targetValue =
                if (expanded) {
                    MaterialTheme.colorScheme.surfaceBright
                } else {
                    MaterialTheme.colorScheme.surface
                },
        )
    val contentAlpha = if (status.isEnabled) 1f else DISABLED_CONTENT_ALPHA

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        shape = MaterialTheme.shapes.large,
        color = surfaceColor,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
        ) {
            // Header
            val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)
            val interactionSource = remember { MutableInteractionSource() }

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) {
                            onExpandedChange(!expanded)
                        },
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val hashIndex = function.id.indexOf('#')
                    val (pkg, name) =
                        if (hashIndex != -1) {
                            function.id.substring(0, hashIndex) to
                                function.id.substring(hashIndex + 1)
                        } else {
                            function.packageName to function.id
                        }

                    Row(
                        modifier = Modifier.alpha(contentAlpha),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            modifier = Modifier.size(16.dp),
                            painter = painterResource(R.drawable.ic_rounded_function),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(text = "\u2022")

                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }

                    // On their own line, so that they don't shorten the function name.
                    if (!status.isEnabled || status.isDynamic) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!status.isEnabled) {
                                StatusChip(
                                    text = stringResource(R.string.debugging_disabled),
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (status.isDynamic) {
                                StatusChip(
                                    text = stringResource(R.string.debugging_dynamic),
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    AnimatedContent(
                        expanded,
                        modifier = Modifier.alpha(contentAlpha),
                        transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                    ) {
                            expanded ->
                        Text(
                            text = pkg,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = GoogleSansCodeFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) 3 else 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    }

                    val statusDescription = statusDescription(status)
                    if (statusDescription != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = statusDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier =
                        Modifier.padding(end = 2.dp).size(48.dp).clip(CircleShape).clickable(
                            interactionSource = interactionSource,
                            indication = ripple(),
                        ) {
                            onExpandedChange(!expanded)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription =
                            if (expanded) {
                                stringResource(R.string.debugging_collapse_function)
                            } else {
                                stringResource(R.string.debugging_expand_function)
                            },
                        modifier = Modifier.rotate(rotation),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    if (function.parameters.isNotEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )

                        // Parameters List
                        Column(
                            modifier = Modifier.alpha(contentAlpha),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (parameter in function.parameters) {
                                val currentValue =
                                    inputValues[parameter.name]
                                        ?: createDefaultValue(parameter.dataType)
                                ParameterInput(
                                    name = parameter.name,
                                    dataType = parameter.dataType,
                                    isRequired = parameter.isRequired,
                                    value = currentValue,
                                    onValueChange = { value ->
                                        onInputValuesChange(
                                            inputValues + (parameter.name to value),
                                        )
                                    },
                                    components = function.components,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!status.canRun) {
                        Text(
                            text =
                                stringResource(
                                    if (status.isDynamic) {
                                        R.string.debugging_run_unavailable_dynamic
                                    } else {
                                        R.string.debugging_run_unavailable_disabled
                                    },
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    Button(
                        onClick = { onInvoke(inputValues) },
                        modifier = Modifier.height(48.dp).fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(),
                        enabled = status.canRun,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text(
                            text = stringResource(R.string.debugging_invoke),
                        )
                    }
                }
            }
        }
    }
}

/** Explains a disabled or dynamic [status], or returns `null` for an enabled global function. */
@Composable
private fun statusDescription(status: AppFunctionStatus): String? {
    val registeredActivityCount = status.registeredActivityCount
    return when {
        registeredActivityCount != null && registeredActivityCount > 0 ->
            pluralStringResource(
                R.plurals.debugging_status_dynamic_registered,
                registeredActivityCount,
                registeredActivityCount,
            )
        registeredActivityCount != null ->
            stringResource(R.string.debugging_status_dynamic_not_registered)
        !status.isEnabled -> stringResource(R.string.debugging_status_disabled)
        else -> null
    }
}

@Composable
private fun StatusChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier =
                Modifier.padding(
                    horizontal = 4.dp,
                    vertical = 2.dp,
                ),
        )
    }
}

private const val DISABLED_CONTENT_ALPHA = 0.6f

@Composable
private fun ParameterInput(
    name: String,
    dataType: AppFunctionDataTypeMetadata,
    isRequired: Boolean,
    value: Any?,
    onValueChange: (Any) -> Unit,
    components: AppFunctionComponentsMetadata,
) {
    AppFunctionDataTypeInput(
        dataType = dataType,
        value = value,
        onValueChange = onValueChange,
        components = components,
        label = name,
        modifier = Modifier.padding(vertical = 4.dp),
        isRequired = isRequired,
    )
}

@Preview(showBackground = true)
@Composable
fun AppFunctionItemPreview() {
    val functionName = AppFunctionName("com.example.test", "testFunction")
    val stringType = AppFunctionStringTypeMetadata(isNullable = false)
    val response =
        AppFunctionResponseMetadata(
            valueType = stringType,
            description = "Returns a string",
        )
    val parameter =
        AppFunctionParameterMetadata(
            name = "param1",
            isRequired = true,
            dataType = stringType,
            description = "A test parameter",
        )

    val fakeMetadata =
        AppFunctionMetadata(
            name = functionName,
            schema = null,
            parameters = listOf(parameter, parameter, parameter),
            response = response,
            description = "Test function description",
            deprecation = null,
            packageMetadata =
                AppFunctionPackageMetadata(
                    packageName = "com.example.test",
                    components = AppFunctionComponentsMetadata(),
                ),
        )

    AppFunctionItem(
        function = fakeMetadata,
        status = AppFunctionStatus(isEnabled = false),
        expanded = true,
        inputValues = emptyMap(),
        onExpandedChange = {},
        onInputValuesChange = {},
        onInvoke = {},
    )
}

@Preview(showBackground = true)
@Composable
fun AppFunctionItem_NoParams_Preview() {
    val functionName = AppFunctionName("com.example.test", "testFunction")
    val stringType = AppFunctionStringTypeMetadata(isNullable = false)
    val response =
        AppFunctionResponseMetadata(
            valueType = stringType,
            description = "Returns a string",
        )

    val fakeMetadata =
        AppFunctionMetadata(
            name = functionName,
            schema = null,
            parameters = emptyList(),
            response = response,
            description = "Test function description",
            deprecation = null,
            packageMetadata =
                AppFunctionPackageMetadata(
                    packageName = "com.example.test",
                    components = AppFunctionComponentsMetadata(),
                ),
        )

    AppFunctionItem(
        function = fakeMetadata,
        status = AppFunctionStatus(isEnabled = true),
        expanded = true,
        inputValues = emptyMap(),
        onExpandedChange = {},
        onInputValuesChange = {},
        onInvoke = {},
    )
}

@Preview(showBackground = true)
@Composable
fun AppFunctionItem_DynamicRegistered_Preview() {
    AppFunctionItem(
        function = createDynamicPreviewFunction(),
        status = AppFunctionStatus(isEnabled = true, registeredActivityCount = 2),
        expanded = true,
        inputValues = emptyMap(),
        onExpandedChange = {},
        onInputValuesChange = {},
        onInvoke = {},
    )
}

@Preview(showBackground = true)
@Composable
fun AppFunctionItem_DynamicNotRegistered_Preview() {
    AppFunctionItem(
        function = createDynamicPreviewFunction(),
        status = AppFunctionStatus(isEnabled = false, registeredActivityCount = 0),
        expanded = false,
        inputValues = emptyMap(),
        onExpandedChange = {},
        onInputValuesChange = {},
        onInvoke = {},
    )
}

private fun createDynamicPreviewFunction(): AppFunctionMetadata =
    AppFunctionMetadata(
        name = AppFunctionName("com.example.test", "appendDraftMessage"),
        schema = null,
        parameters =
            listOf(
                AppFunctionParameterMetadata(
                    name = "messageText",
                    isRequired = true,
                    dataType = AppFunctionStringTypeMetadata(isNullable = false),
                    description = "The text to append",
                ),
            ),
        response =
            AppFunctionResponseMetadata(
                valueType = AppFunctionStringTypeMetadata(isNullable = false),
                description = "Returns a string",
            ),
        packageMetadata =
            AppFunctionPackageMetadata(
                packageName = "com.example.test",
                components = AppFunctionComponentsMetadata(),
            ),
        description = "Appends text to the chat input of the current thread",
        deprecation = null,
        scope = AppFunctionMetadata.SCOPE_ACTIVITY,
    )
