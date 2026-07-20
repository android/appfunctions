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
package com.example.chatapp.data

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing custom chat background wallpapers.
 */
interface WallpaperRepository {
    /**
     * Retrieves the wallpaper file path for a specific chat.
     *
     * @param chatId The ID of the chat.
     * @return A [Flow] emitting the wallpaper file path, or `null` if no custom wallpaper is set.
     */
    fun getWallpaper(chatId: String): Flow<String?>

    /**
     * Saves a wallpaper image for a specific chat.
     *
     * @param chatId The ID of the chat.
     * @param inputStream The input stream of the wallpaper image.
     * @return `true` if the wallpaper was saved successfully, `false` otherwise.
     */
    suspend fun setWallpaper(
        chatId: String,
        inputStream: InputStream,
    ): Boolean
}

@Singleton
class WallpaperRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : WallpaperRepository {
        private val wallpapers = MutableStateFlow<Map<String, String>>(emptyMap())

        override fun getWallpaper(chatId: String): Flow<String?> {
            return wallpapers.map { map ->
                map[chatId] ?: run {
                    val dir = File(context.filesDir, "wallpapers")
                    dir.listFiles { f -> f.name.startsWith("wallpaper_${chatId}_") }
                        ?.maxByOrNull { it.lastModified() }
                        ?.absolutePath
                }
            }.flowOn(Dispatchers.IO)
        }

        override suspend fun setWallpaper(
            chatId: String,
            inputStream: InputStream,
        ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                    dir.listFiles { f -> f.name.startsWith("wallpaper_${chatId}_") }?.forEach { it.delete() }
                    val file = File(dir, "wallpaper_${chatId}_${System.currentTimeMillis()}.jpg")
                    file.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    wallpapers.update { current -> current + (chatId to file.absolutePath) }
                    true
                } catch (e: Exception) {
                    false
                }
            }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class WallpaperModule {
    @Binds
    abstract fun bindWallpaperRepository(impl: WallpaperRepositoryImpl): WallpaperRepository
}
