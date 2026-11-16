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

import android.content.ContentResolver
import android.net.Uri
import androidx.core.net.toUri
import com.example.appfunctions.agent.domain.ToolOutputImage

/**
 * In-memory [ResultImageStore]. Serves the images added with [addImage], rejects the URIs added
 * with [addFailure], and treats the URIs of [AGENT_AUTHORITY] as the agent's own.
 */
class FakeResultImageStore : ResultImageStore {
    private val images = mutableMapOf<String, ToolOutputImage>()
    private val failures = mutableMapOf<String, String>()

    /** Each URI passed to [read], with the MIME type declared for it, in order. */
    val reads = mutableListOf<Pair<String, String?>>()

    /** The images copied with [saveSnapshot], in order. */
    val snapshots = mutableListOf<ToolOutputImage>()

    /** Makes [read] return [image] for [uri]. */
    fun addImage(
        uri: String,
        image: ToolOutputImage,
    ) {
        images[uri] = image
    }

    /** Makes [read] fail for [uri] with [reason]. */
    fun addFailure(
        uri: String,
        reason: String,
    ) {
        failures[uri] = reason
    }

    override suspend fun read(
        uri: Uri,
        mimeType: String?,
        maxBytes: Int,
    ): ToolOutputImage {
        val key = uri.toString()
        reads += key to mimeType
        failures[key]?.let { throw ResultImageException(it) }
        val image = images[key] ?: throw ResultImageException("it was not found")
        if (image.data.size > maxBytes) {
            throw ResultImageException("it is larger than $maxBytes bytes")
        }
        return image
    }

    override suspend fun saveSnapshot(image: ToolOutputImage): Uri {
        snapshots += image
        return snapshotUri(snapshots.size).toUri()
    }

    override fun isAgentUri(uri: Uri): Boolean = uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == AGENT_AUTHORITY

    companion object {
        /** The authority of the agent's FileProvider. */
        const val AGENT_AUTHORITY = "com.example.appfunctions.agent.fileprovider"

        /** The URI that [saveSnapshot] returns for the [number]th copy, counting from 1. */
        fun snapshotUri(number: Int): String = "content://$AGENT_AUTHORITY/cache/result_images/snapshot$number"
    }
}
