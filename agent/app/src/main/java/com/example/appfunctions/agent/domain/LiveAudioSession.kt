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

import android.Manifest
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.Flow

/**
 * Provider-supplied PCM audio sample rate configuration for a [LiveAudioSession].
 *
 * @property inputSampleRateHz Microphone recording sample rate in Hz.
 * @property outputSampleRateHz Default assistant playback sample rate in Hz.
 */
data class LiveAudioConfig(
    val inputSampleRateHz: Int,
    val outputSampleRateHz: Int,
) {
    init {
        require(inputSampleRateHz > 0) { "inputSampleRateHz must be positive: $inputSampleRateHz" }
        require(outputSampleRateHz > 0) { "outputSampleRateHz must be positive: $outputSampleRateHz" }
    }
}

/** Abstraction over full-duplex audio recording and playback for a live voice session. */
interface LiveAudioSession {
    /** PCM microphone capture sample rate in Hz emitted by [listenToRecording]. */
    val inputSampleRateHz: Int

    /** Acquires audio focus/routing and starts microphone capture and audio playback resources. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
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
     * @param sampleRateHz Playback sample rate in Hz.
     */
    fun queueAudio(
        pcmBytes: ByteArray,
        sampleRateHz: Int,
    )

    /** Immediately clears queued assistant audio and flushes active playback on interruption. */
    fun stopAudio()

    /** Releases all underlying audio recording, playback, and audio-focus resources. */
    fun release()
}

/** Factory for creating a new [LiveAudioSession] configured with [LiveAudioConfig] per voice session. */
fun interface LiveAudioSessionFactory {
    fun create(config: LiveAudioConfig): LiveAudioSession
}
