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
package com.example.chatapp

import android.app.Application
import androidx.appfunctions.service.AppFunctionConfiguration
import com.example.chatapp.appfunctions.AppFunctions
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

abstract class BaseChatApplication : Application(), AppFunctionConfiguration.Provider {
    override val appFunctionConfiguration: AppFunctionConfiguration
        get() {
            val entryPoint = EntryPointAccessors.fromApplication(this, AppFunctionsEntryPoint::class.java)
            val taskFunctions = entryPoint.getAppFunctions()

            return AppFunctionConfiguration.Builder()
                .addEnclosingClassFactory(AppFunctions::class.java) { taskFunctions }
                .build()
        }
}

@HiltAndroidApp class ChatApplication : BaseChatApplication()

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppFunctionsEntryPoint {
    fun getAppFunctions(): AppFunctions
}
