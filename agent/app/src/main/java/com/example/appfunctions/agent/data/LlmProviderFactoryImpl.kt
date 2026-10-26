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

import com.example.appfunctions.agent.domain.LiveLlmProvider
import com.example.appfunctions.agent.domain.LlmProvider
import com.example.appfunctions.agent.domain.LlmProviderFactory
import javax.inject.Inject
import javax.inject.Singleton

/** Implementation of [LlmProviderFactory]. */
@Singleton
class LlmProviderFactoryImpl
    @Inject
    constructor(
        private val geminiProvider: GeminiProviderImpl,
        private val geminiLiveProvider: GeminiLiveProviderImpl,
    ) : LlmProviderFactory {
        override fun getProvider(name: LlmProviderName): LlmProvider {
            return when (name) {
                LlmProviderName.GEMINI -> geminiProvider
            }
        }

        override fun getLiveProvider(name: LlmProviderName): LiveLlmProvider {
            return when (name) {
                LlmProviderName.GEMINI -> geminiLiveProvider
            }
        }
    }
