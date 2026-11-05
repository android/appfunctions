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

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import androidx.test.platform.app.InstrumentationRegistry
import com.example.appfunctions.agent.domain.LiveLlmEvent
import com.example.appfunctions.agent.domain.LiveLlmProvider
import com.example.appfunctions.agent.domain.LiveLlmSession
import com.example.appfunctions.agent.domain.ToolOutput
import com.example.appfunctions.agent.domain.withSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Base class for integration tests for [LiveLlmProvider] implementations.
 *
 * Subclasses must provide the specific live provider instance, instrumentation API key argument
 * name, and input PCM sample rate.
 */
abstract class LiveLlmProviderIntegrationTestBase {
    protected lateinit var provider: LiveLlmProvider
    protected lateinit var apiKey: String
    private var httpClient: HttpClient? = null

    /** Argument key used to retrieve the API key from instrumentation arguments. */
    protected abstract val apiKeyArgumentKey: String

    /** Input PCM sample rate in Hz expected by the live provider. */
    protected abstract val inputSampleRateHz: Int

    /** Optional voice identifier to request during live session setup. */
    protected open val voiceName: String? = null

    /** Creates the specific [LiveLlmProvider] instance under test. */
    protected abstract fun createProvider(httpClient: HttpClient): LiveLlmProvider

    @Before
    fun setUp() {
        val arguments = InstrumentationRegistry.getArguments()
        apiKey = arguments.getString(apiKeyArgumentKey) ?: ""
        assumeTrue(
            "API key is required. Please provide it via instrumentation arguments.",
            apiKey.isNotEmpty() && apiKey != "PLACEHOLDER",
        )

        val client =
            HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            prettyPrint = true
                            isLenient = true
                        },
                    )
                }
                install(HttpTimeout) { socketTimeoutMillis = SOCKET_TIMEOUT_MS }
                install(WebSockets) { pingIntervalMillis = WEBSOCKET_PING_INTERVAL_MS }
            }
        httpClient = client
        provider = createProvider(client)
    }

    @After
    fun tearDown() {
        httpClient?.close()
    }

    @Test
    fun connect_withToolsAndVoice_completesHandshakeAndClosesCleanly() =
        runBlocking {
            val weatherTool = createWeatherTool()
            var sessionEstablished = false

            provider.withSession(
                apiKey = apiKey,
                tools = listOf(weatherTool),
                voiceName = voiceName,
            ) {
                sessionEstablished = true
            }

            assertTrue(sessionEstablished)
        }

    @Test
    fun connect_invalidApiKey_throwsException() =
        runBlocking {
            try {
                val session = provider.connect(apiKey = "INVALID_KEY", tools = emptyList())
                session.close()
                fail("Expected exception when connecting with an invalid API key")
            } catch (_: IllegalStateException) {
                // Expected when server rejects handshake or closes before setupComplete
            }
        }

    @Test
    fun session_audioPrompt_receivesAudioResponseAndTurnComplete() =
        runBlocking {
            val speechPcm = synthesizeSpeechPcm("Hello! Please say hi back in one short sentence.")

            provider.withSession(
                apiKey = apiKey,
                tools = emptyList(),
                voiceName = voiceName,
            ) { session ->
                withTimeout(LIVE_TURN_TIMEOUT_MS) {
                    coroutineScope {
                        val eventsChannel = Channel<LiveLlmEvent>(Channel.UNLIMITED)
                        val collectorJob =
                            launch {
                                session.events.collect { eventsChannel.send(it) }
                            }
                        val senderJob =
                            launch {
                                streamPcmWithTrailingSilence(session, speechPcm)
                            }

                        val audioChunks = mutableListOf<LiveLlmEvent.AudioChunk>()
                        try {
                            while (true) {
                                when (val event = eventsChannel.receive()) {
                                    is LiveLlmEvent.AudioChunk -> audioChunks.add(event)
                                    is LiveLlmEvent.Error ->
                                        fail("Unexpected LiveLlmEvent.Error: ${event.message}")
                                    is LiveLlmEvent.TurnComplete -> break
                                    else -> {}
                                }
                            }
                        } finally {
                            senderJob.cancel()
                            collectorJob.cancel()
                        }

                        assertTrue("Expected non-empty audio chunks from model", audioChunks.isNotEmpty())
                        assertTrue(
                            audioChunks.all { it.pcmBytes.isNotEmpty() && it.sampleRateHz > 0 },
                        )
                    }
                }
            }
        }

    @Test
    fun session_toolCallAndResponse_completesTurn() =
        runBlocking {
            val weatherTool = createWeatherTool()
            val speechPcm =
                synthesizeSpeechPcm("Use the weather tool to check the weather in Mountain View.")

            provider.withSession(
                apiKey = apiKey,
                tools = listOf(weatherTool),
                voiceName = voiceName,
            ) { session ->
                withTimeout(LIVE_TURN_TIMEOUT_MS) {
                    coroutineScope {
                        val eventsChannel = Channel<LiveLlmEvent>(Channel.UNLIMITED)
                        val collectorJob =
                            launch {
                                session.events.collect { eventsChannel.send(it) }
                            }
                        val senderJob =
                            launch {
                                streamPcmWithTrailingSilence(session, speechPcm)
                            }

                        try {
                            var toolCallRequest: LiveLlmEvent.ToolCallRequest? = null
                            while (toolCallRequest == null) {
                                when (val event = eventsChannel.receive()) {
                                    is LiveLlmEvent.ToolCallRequest -> toolCallRequest = event
                                    is LiveLlmEvent.Error ->
                                        fail("Unexpected LiveLlmEvent.Error: ${event.message}")
                                    is LiveLlmEvent.TurnComplete ->
                                        fail("Turn completed before expected tool call was requested")
                                    else -> {}
                                }
                            }
                            senderJob.cancelAndJoin()

                            assertTrue(toolCallRequest.calls.isNotEmpty())
                            val toolCall = toolCallRequest.calls.first()
                            assertEquals("com.example.appfunctions.agent", toolCall.packageName)
                            assertEquals(weatherTool.id, toolCall.functionId)

                            session.sendToolResponses(
                                listOf(
                                    ToolOutput(
                                        functionId = toolCall.functionId,
                                        callId = toolCall.callId,
                                        result = """{"weather":"Sunny and 72 degrees in Mountain View"}""",
                                    ),
                                ),
                            )

                            val postToolAudioChunks = mutableListOf<LiveLlmEvent.AudioChunk>()
                            while (true) {
                                when (val event = eventsChannel.receive()) {
                                    is LiveLlmEvent.AudioChunk -> postToolAudioChunks.add(event)
                                    is LiveLlmEvent.Error ->
                                        fail("Unexpected LiveLlmEvent.Error: ${event.message}")
                                    is LiveLlmEvent.TurnComplete -> {
                                        if (postToolAudioChunks.isNotEmpty()) break
                                    }
                                    else -> {}
                                }
                            }
                            assertTrue(
                                "Expected model audio response after tool output",
                                postToolAudioChunks.isNotEmpty(),
                            )
                        } finally {
                            senderJob.cancel()
                            collectorJob.cancel()
                        }
                    }
                }
            }
        }

    private suspend fun streamPcmWithTrailingSilence(
        session: LiveLlmSession,
        speechPcm: ByteArray,
    ) {
        val bytesPerChunk = (inputSampleRateHz * BYTES_PER_SAMPLE * CHUNK_DURATION_MS) / 1000
        var offset = 0
        while (offset < speechPcm.size) {
            val end = (offset + bytesPerChunk).coerceAtMost(speechPcm.size)
            session.sendAudio(speechPcm.copyOfRange(offset, end), inputSampleRateHz)
            offset = end
            delay(STREAM_PACE_MS)
        }

        val silenceChunk = ByteArray(bytesPerChunk)
        repeat(TRAILING_SILENCE_CHUNKS) {
            session.sendAudio(silenceChunk, inputSampleRateHz)
            delay(STREAM_PACE_MS)
        }
    }

    private suspend fun synthesizeSpeechPcm(text: String): ByteArray {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wavFile = File.createTempFile("live_llm_tts_", ".wav", context.cacheDir)
        var tts: TextToSpeech? = null
        try {
            val initResult = CompletableDeferred<Int>()
            tts =
                withContext(Dispatchers.Main) {
                    TextToSpeech(context) { status -> initResult.complete(status) }
                }
            val status = withTimeout(TTS_TIMEOUT_MS) { initResult.await() }
            assumeTrue("Android TextToSpeech initialization failed", status == TextToSpeech.SUCCESS)

            val langSupport = tts.setLanguage(Locale.US)
            assumeTrue(
                "US English TextToSpeech voice is unavailable on this device",
                langSupport >= TextToSpeech.LANG_AVAILABLE,
            )

            val synthesisDone = CompletableDeferred<Boolean>()
            val utteranceId = "live_test_utterance"
            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}

                    override fun onDone(id: String?) {
                        synthesisDone.complete(true)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        synthesisDone.complete(false)
                    }

                    override fun onError(
                        id: String?,
                        errorCode: Int,
                    ) {
                        synthesisDone.complete(false)
                    }
                },
            )

            val enqueueStatus = tts.synthesizeToFile(text, Bundle.EMPTY, wavFile, utteranceId)
            assumeTrue(
                "TextToSpeech.synthesizeToFile failed to enqueue",
                enqueueStatus == TextToSpeech.SUCCESS,
            )
            val completed = withTimeout(TTS_TIMEOUT_MS) { synthesisDone.await() }
            assumeTrue("TextToSpeech synthesis failed to complete", completed)

            return decodeWavAndResampleTo16BitMonoPcm(wavFile.readBytes(), inputSampleRateHz)
        } finally {
            tts?.shutdown()
            wavFile.delete()
        }
    }

    private fun decodeWavAndResampleTo16BitMonoPcm(
        wavBytes: ByteArray,
        targetSampleRateHz: Int,
    ): ByteArray {
        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = WAV_HEADER_PREAMBLE_BYTES
        var channels = 1
        var sourceSampleRateHz = targetSampleRateHz
        var pcmDataOffset = -1
        var pcmDataSize = 0

        while (offset + WAV_CHUNK_HEADER_BYTES <= wavBytes.size) {
            val chunkId = String(wavBytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = buffer.getInt(offset + 4)
            val payloadStart = offset + WAV_CHUNK_HEADER_BYTES
            if (chunkId == "fmt " && chunkSize >= 16) {
                channels = buffer.getShort(payloadStart + 2).toInt().coerceAtLeast(1)
                sourceSampleRateHz = buffer.getInt(payloadStart + 4)
            } else if (chunkId == "data") {
                pcmDataOffset = payloadStart
                pcmDataSize = chunkSize.coerceAtMost(wavBytes.size - payloadStart)
                break
            }
            offset = payloadStart + chunkSize + (chunkSize and 1)
        }
        check(pcmDataOffset >= 0 && pcmDataSize > 0) { "WAV file missing PCM data chunk" }

        val frameCount = pcmDataSize / (BYTES_PER_SAMPLE * channels)
        val monoSamples = ShortArray(frameCount)
        for (frame in 0 until frameCount) {
            var sum = 0
            for (ch in 0 until channels) {
                val byteIdx = pcmDataOffset + (frame * channels + ch) * BYTES_PER_SAMPLE
                sum += buffer.getShort(byteIdx).toInt()
            }
            monoSamples[frame] = (sum / channels).toShort()
        }

        if (sourceSampleRateHz == targetSampleRateHz) {
            val out = ByteBuffer.allocate(monoSamples.size * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in monoSamples) {
                out.putShort(sample)
            }
            return out.array()
        }

        val outFrameCount =
            ((monoSamples.size.toLong() * targetSampleRateHz) / sourceSampleRateHz).toInt()
        val resampled =
            ByteBuffer.allocate(outFrameCount * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until outFrameCount) {
            val srcPos = i.toDouble() * sourceSampleRateHz / targetSampleRateHz
            val idx = srcPos.toInt().coerceIn(0, monoSamples.lastIndex)
            val nextIdx = (idx + 1).coerceAtMost(monoSamples.lastIndex)
            val frac = srcPos - idx
            val interpolated =
                (monoSamples[idx] * (1.0 - frac) + monoSamples[nextIdx] * frac).toInt().toShort()
            resampled.putShort(interpolated)
        }
        return resampled.array()
    }

    private fun createWeatherTool(): AppFunctionMetadata {
        val stringType = AppFunctionStringTypeMetadata(isNullable = false)
        return AppFunctionMetadata(
            id = "com.example.appfunctions.agent.get_weather",
            packageName = "com.example.appfunctions.agent",
            isEnabled = true,
            schema = null,
            parameters =
                listOf(
                    AppFunctionParameterMetadata(
                        name = "location",
                        isRequired = true,
                        dataType = stringType,
                        description = "The city and state or location to get weather for",
                    ),
                ),
            response =
                AppFunctionResponseMetadata(
                    valueType = stringType,
                    description = "Returns the current weather description",
                ),
            components = AppFunctionComponentsMetadata(emptyMap()),
            description = "Gets the current weather for a given location.",
            deprecation = null,
        )
    }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 30_000L
        private const val WEBSOCKET_PING_INTERVAL_MS = 20_000L
        private const val TTS_TIMEOUT_MS = 15_000L
        private const val LIVE_TURN_TIMEOUT_MS = 45_000L
        private const val BYTES_PER_SAMPLE = 2
        private const val CHUNK_DURATION_MS = 100
        private const val STREAM_PACE_MS = 40L
        private const val TRAILING_SILENCE_CHUNKS = 30
        private const val WAV_HEADER_PREAMBLE_BYTES = 12
        private const val WAV_CHUNK_HEADER_BYTES = 8
    }
}
