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
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.appfunctions.agent.domain.ToolOutputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Reads images that AppFunctions return as content URIs and keeps copies the chat can show later. */
interface ResultImageStore {
    /**
     * Reads the image at [uri].
     *
     * @param uri The `content://` URI to read.
     * @param mimeType The MIME type the result declared for [uri], or null. Used only when the
     *   provider of [uri] reports no type, because a result can declare any type for any URI.
     * @param maxBytes The size limit of the image, in bytes.
     * @return The image.
     * @throws ResultImageException If [uri] can't be read, isn't an image, or is larger than
     *   [maxBytes].
     */
    suspend fun read(
        uri: Uri,
        mimeType: String?,
        maxBytes: Int,
    ): ToolOutputImage

    /**
     * Copies [image] into the agent's cache.
     *
     * @return The `content://` URI of the copy, served by the agent's FileProvider.
     * @throws ResultImageException If the copy can't be written.
     */
    suspend fun saveSnapshot(image: ToolOutputImage): Uri

    /** Whether [uri] is served by the agent's own FileProvider. */
    fun isAgentUri(uri: Uri): Boolean
}

/**
 * Thrown when an image returned by an AppFunction can't be used.
 *
 * @property reason Why, phrased to complete "The image was not attached because …".
 */
class ResultImageException(
    val reason: String,
    cause: Throwable? = null,
) : Exception(reason, cause)

/** Default [ResultImageStore] backed by the [ContentResolver] and the agent's [FileProvider]. */
@Singleton
class ResultImageStoreImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ResultImageStore {
        private val authority = "${context.packageName}.fileprovider"

        override suspend fun read(
            uri: Uri,
            mimeType: String?,
            maxBytes: Int,
        ): ToolOutputImage =
            withContext(Dispatchers.IO) {
                try {
                    val type =
                        (context.contentResolver.getType(uri) ?: mimeType)?.lowercase()
                            ?: throw ResultImageException("its content type is unknown")
                    if (!type.startsWith(IMAGE_MIME_TYPE_PREFIX)) {
                        throw ResultImageException("it is not an image ($type)")
                    }
                    val bytes =
                        context.contentResolver.openInputStream(uri)?.use { it.readNBytes(maxBytes + 1) }
                            ?: throw ResultImageException("it could not be opened")
                    if (bytes.size > maxBytes) {
                        throw ResultImageException("it is larger than ${maxBytes / BYTES_PER_MIB} MiB")
                    }
                    ToolOutputImage(mimeType = type, data = bytes)
                } catch (e: SecurityException) {
                    throw ResultImageException("the agent has no read access to it", e)
                } catch (e: FileNotFoundException) {
                    throw ResultImageException("it was not found", e)
                } catch (e: IOException) {
                    throw ResultImageException("it could not be read (${e.message})", e)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RuntimeException) {
                    // ContentResolver rethrows what the provider's openFile() throws, e.g. an
                    // IllegalArgumentException for a URI outside the roots of a FileProvider.
                    throw ResultImageException("it could not be read (${e.message})", e)
                }
            }

        override suspend fun saveSnapshot(image: ToolOutputImage): Uri =
            withContext(Dispatchers.IO) {
                try {
                    val directory = File(context.cacheDir, SNAPSHOT_DIRECTORY).apply { mkdirs() }
                    val extension =
                        MimeTypeMap.getSingleton().getExtensionFromMimeType(image.mimeType)
                            ?: DEFAULT_EXTENSION
                    val file = File(directory, "${UUID.randomUUID()}.$extension")
                    file.writeBytes(image.data)
                    FileProvider.getUriForFile(context, authority, file)
                } catch (e: IOException) {
                    throw ResultImageException("it could not be copied (${e.message})", e)
                }
            }

        override fun isAgentUri(uri: Uri): Boolean = uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == authority

        private companion object {
            const val IMAGE_MIME_TYPE_PREFIX = "image/"
            const val SNAPSHOT_DIRECTORY = "result_images"
            const val DEFAULT_EXTENSION = "img"
            const val BYTES_PER_MIB = 1024 * 1024
        }
    }
