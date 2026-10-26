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
    /** Acquires audio focus/routing and starts microphone capture and audio playback resources. */
    fun start()

    /** Streams captured PCM microphone audio chunks while recording is active and unpaused. */
    fun listenToRecording(): Flow<ByteArray>

    /** Temporarily suppresses microphone emissions (e.g. while executing tools). */
    fun pauseRecording()

    /** Resumes microphone emissions after [pauseRecording]. */
    fun resumeRecording()

    /** Enqueues a PCM audio chunk from the assistant for speaker/headset playback. */
    fun queueAudio(pcmBytes: ByteArray)

    /** Immediately clears queued assistant audio and flushes active playback on interruption. */
    fun stopAudio()

    /** Releases all underlying audio recording, playback, and audio-focus resources. */
    fun release()
}

/** Factory for creating a new [LiveAudioSession] per voice session. */
fun interface LiveAudioSessionFactory {
    fun create(): LiveAudioSession
}
