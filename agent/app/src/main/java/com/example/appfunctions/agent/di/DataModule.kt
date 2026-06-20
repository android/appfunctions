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
package com.example.appfunctions.agent.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.appfunctions.agent.data.ChatRepository
import com.example.appfunctions.agent.data.DataStoreSettingsRepository
import com.example.appfunctions.agent.data.InMemoryPendingIntentRepository
import com.example.appfunctions.agent.data.PendingIntentRepository
import com.example.appfunctions.agent.data.RoomChatRepository
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.data.db.AppDatabase
import com.example.appfunctions.agent.data.db.dao.ChatDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: RoomChatRepository): ChatRepository

    @Binds
    @Singleton
    abstract fun bindPendingIntentRepository(impl: InMemoryPendingIntentRepository): PendingIntentRepository

    @Binds
    @Singleton
    abstract fun bindLlmProviderFactory(
        impl: com.example.appfunctions.agent.data.LlmProviderFactoryImpl,
    ): com.example.appfunctions.agent.domain.LlmProviderFactory

    companion object {
        @Provides
        @Singleton
        fun provideDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> {
            return context.settingsDataStore
        }

        @Provides
        @Singleton
        fun provideAppDatabase(
            @ApplicationContext context: Context,
        ): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "app_database",
            )
                .fallbackToDestructiveMigration()
                .build()
        }

        @Provides
        @Singleton
        fun provideChatDao(db: AppDatabase): ChatDao {
            return db.chatDao()
        }

        @Provides
        @Singleton
        fun provideHttpClient(): HttpClient {
            return HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            prettyPrint = true
                            isLenient = true
                        },
                    )
                }
                install(HttpTimeout) { socketTimeoutMillis = 30000 }
            }
        }
    }
}
