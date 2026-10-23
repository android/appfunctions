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

import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import com.example.appfunctions.agent.data.GeminiToolConverter
import com.example.appfunctions.agent.data.SettingsRepository
import com.example.appfunctions.agent.domain.chat.SendMessageUseCase
import io.ktor.client.HttpClient
import io.mockk.mockk
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiLiveSessionManagerTest {
    private lateinit var sessionManager: GeminiLiveSessionManager

    @Before
    fun setUp() {
        sessionManager =
            GeminiLiveSessionManager(
                httpClient = mockk<HttpClient>(relaxed = true),
                toolConverter = GeminiToolConverter(),
                settingsRepository = mockk<SettingsRepository>(relaxed = true),
                agentOrchestrator = mockk<AgentOrchestrator>(relaxed = true),
                sendMessageUseCase = mockk<SendMessageUseCase>(relaxed = true),
            )
    }

    @Test
    fun `buildSetupMessage configures gemini-3_8-live model, audio modality, transcriptions, and tools`() {
        val tool =
            AppFunctionMetadata(
                id = "com.example.notes.createNote",
                packageName = "com.example.notes",
                isEnabled = true,
                schema = null,
                parameters =
                    listOf(
                        AppFunctionParameterMetadata(
                            name = "title",
                            isRequired = true,
                            dataType = AppFunctionStringTypeMetadata(isNullable = false),
                            description = "Note title",
                        ),
                    ),
                response =
                    AppFunctionResponseMetadata(
                        valueType = AppFunctionStringTypeMetadata(isNullable = false),
                        description = "",
                    ),
                components = AppFunctionComponentsMetadata(emptyMap()),
                description = "Creates a note",
                deprecation = null,
            )

        val setupEnvelope = sessionManager.buildSetupMessage(listOf(tool))
        val setup = setupEnvelope["setup"]!!.jsonObject

        assertEquals("models/gemini-3.8-live", setup["model"]!!.jsonPrimitive.content)

        val modalities =
            setup["generationConfig"]!!
                .jsonObject["responseModalities"]!!
                .jsonArray
                .map { it.jsonPrimitive.content }
        assertEquals(listOf("AUDIO"), modalities)

        assertTrue(setup.containsKey("inputAudioTranscription"))
        assertTrue(setup.containsKey("outputAudioTranscription"))
        assertTrue(setup.containsKey("systemInstruction"))

        val declarations =
            setup["tools"]!!
                .jsonArray
                .first()
                .jsonObject["functionDeclarations"]!!
                .jsonArray
        assertEquals(1, declarations.size)
        assertEquals(
            "com_example_notes_createNote",
            declarations.first().jsonObject["name"]!!.jsonPrimitive.content,
        )
    }
}
