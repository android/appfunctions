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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.appfunctions.agent.domain.LiveLlmProvider
import io.ktor.client.HttpClient
import org.junit.runner.RunWith

/** Integration tests for [GeminiLiveProviderImpl]. */
@RunWith(AndroidJUnit4::class)
class GeminiLiveProviderImplIntegrationTest : LiveLlmProviderIntegrationTestBase() {
    override val apiKeyArgumentKey: String = "gemini_api_key"
    override val inputSampleRateHz: Int = GeminiLiveProviderImpl.DEFAULT_INPUT_SAMPLE_RATE_HZ
    override val voiceName: String = GeminiLiveVoice.DEFAULT.apiName

    override fun createProvider(httpClient: HttpClient): LiveLlmProvider {
        return GeminiLiveProviderImpl(
            httpClient = httpClient,
            toolConverter = GeminiLiveToolConverter(GeminiToolConverter()),
        )
    }
}
