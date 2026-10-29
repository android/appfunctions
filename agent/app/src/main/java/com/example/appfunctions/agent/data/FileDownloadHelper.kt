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

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Helper for downloading remote files into local cache storage. */
interface FileDownloadHelper {
    /**
     * Downloads a remote `http://` or `https://` file at [url] into the local cache directory and
     * returns its `content://` URI string.
     */
    suspend fun downloadToLocalUri(url: String): String
}

/** Default [FileDownloadHelper] implementation backed by [HttpURLConnection] and [FileProvider]. */
@Singleton
class FileDownloadHelperImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : FileDownloadHelper {
        override suspend fun downloadToLocalUri(url: String): String =
            withContext(Dispatchers.IO) {
                val parsedUrl = URL(url)
                val connection =
                    (parsedUrl.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000
                        readTimeout = 15000
                    }
                try {
                    connection.connect()
                    val contentType = connection.contentType ?: ""
                    val ext =
                        when {
                            contentType.contains("png", ignoreCase = true) -> "png"
                            contentType.contains("jpeg", ignoreCase = true) ||
                                contentType.contains("jpg", ignoreCase = true) -> "jpg"
                            contentType.contains("gif", ignoreCase = true) -> "gif"
                            contentType.contains("webp", ignoreCase = true) -> "webp"
                            url.substringAfterLast("/", "").contains(".") ->
                                url.substringAfterLast("/").substringAfterLast(".")
                            else -> "jpg"
                        }
                    val cacheDir = File(context.cacheDir, "file_references").apply { mkdirs() }
                    val file = File(cacheDir, "generated_${UUID.randomUUID()}.$ext")
                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val contentUri =
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                    contentUri.toString()
                } finally {
                    connection.disconnect()
                }
            }
    }
