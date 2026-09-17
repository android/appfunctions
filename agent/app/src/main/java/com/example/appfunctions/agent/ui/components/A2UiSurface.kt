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
package com.example.appfunctions.agent.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** Parsed representation of an individual A2UI component instance. */
data class A2uiComponentData(
    val id: String,
    val component: String,
    val properties: JSONObject,
)

/** Action triggered by an interactive A2UI component (e.g. Button, Contact Row). */
data class A2uiAction(
    val name: String,
    val parameters: Map<String, Any?>,
    val prompt: String? = null,
)

/**
 * Declarative Jetpack Compose renderer for A2UI payloads.
 *
 * Parses A2UI `createSurface` and `updateComponents` envelopes and recursively
 * instantiates Material 3 composables matching the component hierarchy.
 */
@Composable
fun A2UiSurface(
    payloadJson: String,
    modifier: Modifier = Modifier,
    fallbackText: String? = null,
    onAction: (A2uiAction) -> Unit = {},
) {
    val model = remember(payloadJson) { parseA2uiPayload(payloadJson) }

    if (model == null || model.components.isEmpty()) {
        if (!fallbackText.isNullOrBlank()) {
            Text(
                text = fallbackText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = modifier.padding(8.dp),
            )
        }
        return
    }

    DisableSelection {
        Box(modifier = modifier) {
            val rootId = model.rootId ?: "root"
            RenderA2uiComponent(
                componentId = rootId,
                components = model.components,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun RenderA2uiComponent(
    componentId: String,
    components: Map<String, A2uiComponentData>,
    onAction: (A2uiAction) -> Unit,
) {
    val component = components[componentId] ?: return
    val props = component.properties

    when (component.component) {
        "Card" -> {
            val actionObj = props.optJSONObject("action")
            val cardModifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            val cardContent: @Composable () -> Unit = {
                val childId = props.optString("child")
                if (childId.isNotEmpty()) {
                    RenderA2uiComponent(childId, components, onAction)
                }
                val children = props.optJSONArray("children")
                if (children != null) {
                    for (i in 0 until children.length()) {
                        RenderA2uiComponent(children.getString(i), components, onAction)
                    }
                }
            }

            if (actionObj != null) {
                Card(
                    onClick = {
                        val actionName = actionObj.optString("name").ifEmpty { "click" }
                        val paramObj = actionObj.optJSONObject("parameters")
                        val params = mutableMapOf<String, Any?>()
                        if (paramObj != null) {
                            val keys = paramObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                params[key] = paramObj.opt(key)
                            }
                        }
                        val prompt = params["prompt"] as? String
                        onAction(A2uiAction(name = actionName, parameters = params, prompt = prompt))
                    },
                    modifier = cardModifier,
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceBright,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Box(modifier = Modifier.padding(14.dp)) { cardContent() }
                }
            } else {
                Card(
                    modifier = cardModifier,
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceBright,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Box(modifier = Modifier.padding(14.dp)) { cardContent() }
                }
            }
        }

        "Column" -> {
            val spacing = props.optInt("spacing", 0).dp
            val paddingVal = props.optInt("padding", 0).dp
            val fillMaxWidth = props.optBoolean("fillMaxWidth", true)
            val actionObj = props.optJSONObject("action")
            val colModifier =
                Modifier.then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                    .then(if (paddingVal > 0.dp) Modifier.padding(paddingVal) else Modifier)
                    .then(
                        if (actionObj != null) {
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val actionName = actionObj.optString("name").ifEmpty { "click" }
                                    val paramObj = actionObj.optJSONObject("parameters")
                                    val params = mutableMapOf<String, Any?>()
                                    if (paramObj != null) {
                                        val keys = paramObj.keys()
                                        while (keys.hasNext()) {
                                            val key = keys.next()
                                            params[key] = paramObj.opt(key)
                                        }
                                    }
                                    val prompt = params["prompt"] as? String
                                    onAction(
                                        A2uiAction(
                                            name = actionName,
                                            parameters = params,
                                            prompt = prompt,
                                        ),
                                    )
                                }
                                .padding(8.dp)
                        } else {
                            Modifier
                        },
                    )
            Column(
                modifier = colModifier,
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                val children = props.optJSONArray("children")
                if (children != null) {
                    for (i in 0 until children.length()) {
                        RenderA2uiComponent(children.getString(i), components, onAction)
                    }
                }
            }
        }

        "Row" -> {
            val spaceBetween = props.optBoolean("spaceBetween", false)
            val spacing = props.optInt("spacing", 8).dp
            val actionObj = props.optJSONObject("action")
            val rowModifier =
                Modifier.fillMaxWidth()
                    .then(
                        if (actionObj != null) {
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val actionName = actionObj.optString("name").ifEmpty { "click" }
                                    val paramObj = actionObj.optJSONObject("parameters")
                                    val params = mutableMapOf<String, Any?>()
                                    if (paramObj != null) {
                                        val keys = paramObj.keys()
                                        while (keys.hasNext()) {
                                            val key = keys.next()
                                            params[key] = paramObj.opt(key)
                                        }
                                    }
                                    val prompt = params["prompt"] as? String
                                    onAction(
                                        A2uiAction(
                                            name = actionName,
                                            parameters = params,
                                            prompt = prompt,
                                        ),
                                    )
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        } else {
                            Modifier.padding(vertical = 2.dp)
                        },
                    )
            Row(
                modifier = rowModifier,
                horizontalArrangement =
                    if (spaceBetween) Arrangement.SpaceBetween else Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val children = props.optJSONArray("children")
                if (children != null) {
                    for (i in 0 until children.length()) {
                        RenderA2uiComponent(children.getString(i), components, onAction)
                    }
                }
            }
        }

        "Text" -> {
            val text = props.optString("text", "")
            val variant = props.optString("variant", "bodyMedium")
            val bold = props.optBoolean("bold", false)
            val colorName = props.optString("color", "")

            val style =
                when (variant) {
                    "titleLarge" -> MaterialTheme.typography.titleLarge
                    "titleMedium" -> MaterialTheme.typography.titleMedium
                    "titleSmall" -> MaterialTheme.typography.titleSmall
                    "bodyLarge" -> MaterialTheme.typography.bodyLarge
                    "bodySmall" -> MaterialTheme.typography.bodySmall
                    else -> MaterialTheme.typography.bodyMedium
                }.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

            val color =
                when (colorName) {
                    "primary" -> MaterialTheme.colorScheme.primary
                    "secondary" -> MaterialTheme.colorScheme.onSurfaceVariant
                    "error" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }

            Text(text = text, style = style, color = color)
        }

        "Button" -> {
            val btnText = props.optString("text", "Select")
            val actionObj = props.optJSONObject("action")
            FilledTonalButton(
                onClick = {
                    val actionName = actionObj?.optString("name")?.ifEmpty { "click" } ?: "click"
                    val paramObj = actionObj?.optJSONObject("parameters")
                    val params = mutableMapOf<String, Any?>()
                    if (paramObj != null) {
                        val keys = paramObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            params[key] = paramObj.opt(key)
                        }
                    }
                    val prompt = params["prompt"] as? String
                    onAction(A2uiAction(name = actionName, parameters = params, prompt = prompt))
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(text = btnText, style = MaterialTheme.typography.labelMedium)
            }
        }

        "Divider", "HorizontalDivider" -> {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

private data class ParsedA2uiModel(
    val surfaceId: String,
    val rootId: String?,
    val components: Map<String, A2uiComponentData>,
)

private fun parseA2uiPayload(jsonString: String): ParsedA2uiModel? {
    return runCatching {
        val trimmed = jsonString.trim()
        val jsonArray =
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                when {
                    obj.has("messages") -> obj.getJSONArray("messages")
                    obj.has("updateComponents") || obj.has("createSurface") ->
                        JSONArray().apply { put(obj) }
                    else -> return null
                }
            } else {
                return null
            }

        var surfaceId = "default_surface"
        val componentMap = mutableMapOf<String, A2uiComponentData>()

        for (i in 0 until jsonArray.length()) {
            val msg = jsonArray.optJSONObject(i) ?: continue
            if (msg.has("createSurface")) {
                val createSurface = msg.getJSONObject("createSurface")
                surfaceId = createSurface.optString("surfaceId", surfaceId)
            }
            if (msg.has("updateComponents")) {
                val updateComponents = msg.getJSONObject("updateComponents")
                surfaceId = updateComponents.optString("surfaceId", surfaceId)
                val compsArray = updateComponents.optJSONArray("components") ?: continue
                for (j in 0 until compsArray.length()) {
                    val comp = compsArray.optJSONObject(j) ?: continue
                    val id = comp.optString("id")
                    val type = comp.optString("component").ifEmpty { comp.optString("type") }
                    val properties = comp.optJSONObject("properties") ?: JSONObject()
                    if (id.isNotEmpty() && type.isNotEmpty()) {
                        componentMap[id] =
                            A2uiComponentData(
                                id = id,
                                component = type,
                                properties = properties,
                            )
                    }
                }
            }
        }

        val rootId =
            if (componentMap.containsKey("root")) {
                "root"
            } else {
                componentMap.keys.firstOrNull()
            }

        ParsedA2uiModel(surfaceId = surfaceId, rootId = rootId, components = componentMap)
    }.getOrNull()
}
