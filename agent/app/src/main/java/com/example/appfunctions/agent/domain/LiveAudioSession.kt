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

import kotlinx.coroutines.flow.Flow

/** Abstraction over full-duplex audio recording and playback for a live voice session. */
interface LiveAudioSession {
    /** PCM microphone capture sample rate in Hz emitted by [listenToRecording]. */
    val inputSampleRateHz: Int

    /** Acquires audio focus/routing and starts microphone capture and audio playback resources. */
    fun start()

    /** Streams captured PCM microphone audio chunks while recording is active and unpaused. */
    fun listenToRecording(): Flow<ByteArray>

    /** Temporarily suppresses microphone emissions (e.g. while executing tools). */
    fun pauseRecording()

    /** Resumes microphone emissions after [pauseRecording]. */
    fun resumeRecording()

    /**
     * Enqueues a PCM audio chunk from the assistant for speaker/headset playback.
     *
     * @param pcmBytes Raw 16-bit little-endian mono PCM audio bytes.
     * @param sampleRateHz Playback sample rate in Hz (defaults to [DEFAULT_OUTPUT_SAMPLE_RATE_HZ]).
     */
    fun queueAudio(
        pcmBytes: ByteArray,
        sampleRateHz: Int = DEFAULT_OUTPUT_SAMPLE_RATE_HZ,
    )

    /** Immediately clears queued assistant audio and flushes active playback on interruption. */
    fun stopAudio()

    /** Releases all underlying audio recording, playback, and audio-focus resources. */
    fun release()

    companion object {
        const val DEFAULT_INPUT_SAMPLE_RATE_HZ = 16_000
        const val DEFAULT_OUTPUT_SAMPLE_RATE_HZ = 24_000
    }
}

/** Factory for creating a new [LiveAudioSession] per voice session. */
fun interface LiveAudioSessionFactory {
    fun create(): LiveAudioSession
}
