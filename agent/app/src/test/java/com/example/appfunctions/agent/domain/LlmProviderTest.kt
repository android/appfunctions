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
package com.example.appfunctions.agent.domain

import com.example.appfunctions.agent.data.LlmModel
import com.example.appfunctions.agent.data.LlmProviderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmProviderTest {
    private val proModel = LlmModel(LlmProviderName.GEMINI, "gemini-pro-latest")
    private val flashModel = LlmModel(LlmProviderName.GEMINI, "gemini-flash-latest")

    @Test
    fun resolveDefaultModel_defaultModelListed_returnsDefaultModel() {
        val provider = FakeLlmProvider().apply { defaultModelName = "gemini-flash-latest" }

        assertEquals(flashModel, provider.resolveDefaultModel(listOf(proModel, flashModel)))
    }

    @Test
    fun resolveDefaultModel_defaultModelNotListed_returnsFirstModel() {
        val provider = FakeLlmProvider().apply { defaultModelName = "gemini-flash-latest" }

        assertEquals(proModel, provider.resolveDefaultModel(listOf(proModel)))
    }

    @Test
    fun resolveDefaultModel_noDefaultModelName_returnsFirstModel() {
        val provider = FakeLlmProvider()

        assertEquals(proModel, provider.resolveDefaultModel(listOf(proModel, flashModel)))
    }

    @Test
    fun resolveDefaultModel_noModels_returnsNull() {
        val provider = FakeLlmProvider().apply { defaultModelName = "gemini-flash-latest" }

        assertNull(provider.resolveDefaultModel(emptyList()))
    }
}
