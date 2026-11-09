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
package com.example.appfunctions.agent.ui.a2ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.model.processor.processInput
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.a2ui.A2uiSurface
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.material3.a2ui.catalog.materialA2uiBasicCatalogV1
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.appfunctions.agent.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

/**
 * Renders the A2UI surfaces described by [payload] (a JSON array of A2UI messages, or a single
 * message) returned by an AppFunction, using the Material 3 implementation of the Basic Catalog.
 *
 * Local actions such as `openUrl` are handled by the catalog. Buttons with an `event` action
 * aren't handled yet: tapping one shows a toast.
 */
@Composable
fun A2uiSurfaces(
    payload: String,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val processor = remember(payload) { A2uiMessageProcessor(listOf(basicCatalog(context))) }
    LaunchedEffect(processor) {
        launch(Dispatchers.Default) { processor.collectMessages() }
        launch(start = CoroutineStart.UNDISPATCHED) {
            processor.outboundEvents.collect { message ->
                when (message) {
                    // Events aren't handled yet: show a toast.
                    is A2uiClientEventMessage -> {
                        Log.d(TAG, "Ignored A2UI event '${message.type}' from ${message.componentId}")
                        Toast.makeText(
                            context,
                            context.getString(R.string.agent_demo_a2ui_event_not_supported, message.type),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    is A2uiClientErrorMessage -> Log.w(TAG, "A2UI error: $message")
                }
            }
        }
        val parser = A2uiMessageParser()
        val messages =
            runCatching { Json.parseToJsonElement(payload).jsonArray.map { it.toString() } }
                .getOrDefault(listOf(payload))
        messages.forEach { processor.processInput(parser, it) }
    }
    val surfaces by processor.activeSurfaces.collectAsStateWithLifecycle()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        surfaces.forEach { surface ->
            key(surface.id) {
                // The chat is a LazyColumn, and a List in a surface can't be measured with unbounded height.
                A2uiSurface(surfaceModel = surface, modifier = Modifier.heightIn(max = MAX_SURFACE_HEIGHT))
            }
        }
    }
}

/** The Material 3 Basic Catalog, loading images with Coil. */
private fun basicCatalog(context: Context) =
    materialA2uiBasicCatalogV1(
        image =
            MaterialA2uiBasicCatalogV1Defaults.image { url, description, scale, modifier, onError ->
                AsyncImage(
                    model = url,
                    contentDescription = description,
                    modifier = modifier,
                    contentScale = scale,
                    onError = { onError(it.result.throwable) },
                )
            },
        // Video and audio playback isn't supported.
        video = MaterialA2uiBasicCatalogV1Defaults.video { _, _, _ -> },
        audioPlayer = MaterialA2uiBasicCatalogV1Defaults.audioPlayer { _, _, _, _ -> },
        functions = A2uiBasicCatalogV1.Functions(context),
    )

/** A large value to cap the height of lazy lists in a surface, without cutting off other content. */
private val MAX_SURFACE_HEIGHT = 4_000.dp
private const val TAG = "A2uiSurfaces"
