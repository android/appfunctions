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

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResultImageReaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resultImageReader = ResultImageReaderImpl(context)
    private lateinit var imageProvider: ImageProvider

    @Before
    fun setUp() {
        imageProvider = Robolectric.setupContentProvider(ImageProvider::class.java, PROVIDER_AUTHORITY)
    }

    @Test
    fun `read returns the image with the type of its provider in lowercase`() =
        runTest {
            val uri = imageProvider.addFile("photo.webp", PHOTO_BYTES, type = "Image/WebP")

            val image = resultImageReader.read(uri, mimeType = null, maxBytes = MAX_BYTES)

            assertEquals("image/webp", image.mimeType)
            assertArrayEquals(PHOTO_BYTES, image.data)
        }

    @Test
    fun `read uses the type of the provider over the declared type`() =
        runTest {
            val photoUri = imageProvider.addFile("photo.png", PHOTO_BYTES, type = "image/png")
            val settingsUri = imageProvider.addFile("settings.preferences_pb", "api_key".toByteArray(), type = "application/octet-stream")

            assertEquals("image/png", resultImageReader.read(photoUri, mimeType = "image/jpeg", maxBytes = MAX_BYTES).mimeType)
            assertEquals("it is not an image (application/octet-stream)", readFailureReason(settingsUri, mimeType = "image/png"))
        }

    @Test
    fun `read uses the declared type in lowercase when the provider reports none`() =
        runTest {
            val uri = imageProvider.addFile("photo", PHOTO_BYTES, type = null)

            val image = resultImageReader.read(uri, mimeType = "IMAGE/JPEG", maxBytes = MAX_BYTES)

            assertEquals("image/jpeg", image.mimeType)
            assertArrayEquals(PHOTO_BYTES, image.data)
        }

    @Test
    fun `read returns an image of maxBytes`() =
        runTest {
            val bytes = ByteArray(MAX_BYTES) { it.toByte() }
            val uri = imageProvider.addFile("large.png", bytes, type = "image/png")

            val image = resultImageReader.read(uri, mimeType = null, maxBytes = MAX_BYTES)

            assertArrayEquals(bytes, image.data)
        }

    @Test
    fun `read rejects an image larger than maxBytes`() =
        runTest {
            val uri = imageProvider.addFile("huge.png", ByteArray(MAX_BYTES + 1), type = "image/png")

            assertEquals("it is larger than 1 MiB", readFailureReason(uri))
        }

    @Test
    fun `read rejects content that is not an image`() =
        runTest {
            val notesUri = imageProvider.addFile("notes.txt", "Buy milk".toByteArray(), type = "text/plain")
            val documentUri = imageProvider.addFile("document", "%PDF".toByteArray(), type = null)

            assertEquals("it is not an image (text/plain)", readFailureReason(notesUri))
            assertEquals("it is not an image (application/pdf)", readFailureReason(documentUri, mimeType = "application/pdf"))
        }

    @Test
    fun `read rejects content of unknown type`() =
        runTest {
            val uri = imageProvider.addFile("photo", PHOTO_BYTES, type = null)

            assertEquals("its content type is unknown", readFailureReason(uri))
        }

    @Test
    fun `read reports content the agent has no access to`() =
        runTest {
            val uri = imageProvider.addFailure("private.png", type = "image/png", SecurityException("Permission Denial"))

            assertEquals("the agent has no read access to it", readFailureReason(uri))
        }

    @Test
    fun `read reports content that doesn't exist`() =
        runTest {
            val uri = imageProvider.addFailure("deleted.png", type = "image/png", FileNotFoundException("deleted.png"))

            assertEquals("it was not found", readFailureReason(uri))
        }

    @Test
    fun `read reports content that its provider doesn't open`() =
        runTest {
            val uri = imageProvider.uriOf("unserved.png")

            assertEquals("it could not be opened", readFailureReason(uri, mimeType = "image/png"))
        }

    @Test
    fun `read reports content that its provider fails to open`() =
        runTest {
            val failures =
                listOf(
                    IllegalArgumentException("Failed to find configured root that contains /data/photo.png"),
                    UnsupportedOperationException("No files supported by provider"),
                    IllegalStateException("Provider is shutting down"),
                )

            for ((index, failure) in failures.withIndex()) {
                val uri = imageProvider.addFailure("broken$index.png", type = "image/png", failure)

                assertEquals("it could not be read (${failure.message})", readFailureReason(uri))
            }
        }

    @Test
    fun `read rethrows a cancellation instead of reporting it`() =
        runTest {
            val uri = imageProvider.addFailure("photo.png", type = "image/png", CancellationException("Cancelled"))

            try {
                resultImageReader.read(uri, mimeType = null, maxBytes = MAX_BYTES)
                fail("Expected the CancellationException to be rethrown")
            } catch (e: CancellationException) {
                assertEquals("Cancelled", e.message)
            }
        }

    /** Reads [uri] and returns why the reader rejected it, or null if it didn't. */
    private suspend fun readFailureReason(
        uri: Uri,
        mimeType: String? = null,
    ): String? =
        try {
            resultImageReader.read(uri, mimeType, MAX_BYTES)
            null
        } catch (e: ResultImageException) {
            e.reason
        }

    /**
     * The provider of another app's images. Serves the files added with [addFile] with their type,
     * and fails to open the ones added with [addFailure].
     */
    class ImageProvider : ContentProvider() {
        private val types = mutableMapOf<String, String>()
        private val failures = mutableMapOf<String, Exception>()

        /** Serves [bytes] with the content [type], or no type when null, and returns their URI. */
        fun addFile(
            name: String,
            bytes: ByteArray,
            type: String?,
        ): Uri {
            File(requireContext().filesDir, name).writeBytes(bytes)
            type?.let { types[name] = it }
            return uriOf(name)
        }

        /** Fails opening a file of the content [type] with [failure], and returns its URI. */
        fun addFailure(
            name: String,
            type: String,
            failure: Exception,
        ): Uri {
            types[name] = type
            failures[name] = failure
            return uriOf(name)
        }

        fun uriOf(name: String): Uri = "content://$PROVIDER_AUTHORITY/$name".toUri()

        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String? = types[uri.lastPathSegment]

        override fun openFile(
            uri: Uri,
            mode: String,
        ): ParcelFileDescriptor? {
            val name = uri.lastPathSegment ?: return null
            failures[name]?.let { throw it }
            val file = File(requireContext().filesDir, name)
            return if (file.exists()) ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) else null
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private companion object {
        const val PROVIDER_AUTHORITY = "com.example.photos.provider"
        const val MAX_BYTES = 1024 * 1024
        val PHOTO_BYTES = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    }
}
