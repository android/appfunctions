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

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages low-latency PCM audio recording (16 kHz mono 16-bit) and playback (24 kHz mono 16-bit)
 * for a Gemini Live bidirectional audio session.
 */
class GeminiLiveAudioHelper {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isReleased = AtomicBoolean(false)
    private val isRecordingPaused = AtomicBoolean(false)

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()

    @SuppressLint("MissingPermission")
    fun start() {
        val minRecordBuf =
            AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(DEFAULT_READ_BUFFER_SIZE)

        val recorder =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minRecordBuf,
            )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Failed to initialize AudioRecord")
        }

        if (AcousticEchoCanceler.isAvailable()) {
            runCatching {
                echoCanceler =
                    AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                        enabled = true
                    }
            }.onFailure { e ->
                Log.w(TAG, "Failed to enable AcousticEchoCanceler", e)
            }
        }

        val minTrackBuf =
            AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(DEFAULT_READ_BUFFER_SIZE)

        val track =
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minTrackBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

        track.play()
        recorder.startRecording()

        audioRecord = recorder
        audioTrack = track

        playbackJob =
            scope.launch {
                while (isActive && !isReleased.get()) {
                    val chunk = audioQueue.poll()
                    if (chunk != null) {
                        val currentTrack = audioTrack
                        if (currentTrack != null && !isReleased.get()) {
                            currentTrack.write(chunk, 0, chunk.size)
                        }
                    } else {
                        delay(PLAYBACK_POLL_DELAY_MS)
                    }
                }
            }
    }

    /** Emits PCM 16-bit 16 kHz mono byte chunks recorded from the microphone. */
    fun listenToRecording(): Flow<ByteArray> =
        flow {
            val buffer = ByteArray(DEFAULT_READ_BUFFER_SIZE)
            while (currentCoroutineContext().isActive && !isReleased.get()) {
                if (isRecordingPaused.get()) {
                    delay(RECORD_PAUSE_DELAY_MS)
                    continue
                }
                val recorder = audioRecord ?: break
                val bytesRead = recorder.read(buffer, 0, buffer.size)
                if (bytesRead > 0 && !isRecordingPaused.get()) {
                    emit(buffer.copyOf(bytesRead))
                } else if (bytesRead < 0) {
                    Log.w(TAG, "AudioRecord.read returned error code: $bytesRead")
                    break
                }
            }
        }.flowOn(Dispatchers.IO)

    fun queueAudio(data: ByteArray) {
        if (!isReleased.get()) {
            audioQueue.add(data)
        }
    }

    /** Clears any queued model audio when the user interrupts the model. */
    fun stopAudio() {
        audioQueue.clear()
    }

    fun pauseRecording() {
        isRecordingPaused.set(true)
        runCatching {
            val recorder = audioRecord
            if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        }
    }

    fun resumeRecording() {
        if (!isReleased.get()) {
            runCatching {
                val recorder = audioRecord
                if (recorder?.state == AudioRecord.STATE_INITIALIZED &&
                    recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING
                ) {
                    recorder.startRecording()
                }
            }
            isRecordingPaused.set(false)
        }
    }

    fun release() {
        if (!isReleased.compareAndSet(false, true)) return
        audioQueue.clear()
        playbackJob?.cancel()
        scope.cancel()

        runCatching { echoCanceler?.release() }
        echoCanceler = null

        runCatching {
            audioRecord?.let { recorder ->
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
                recorder.release()
            }
        }
        audioRecord = null

        runCatching {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                    track.stop()
                }
                track.release()
            }
        }
        audioTrack = null
    }

    companion object {
        private const val TAG = "GeminiLiveAudioHelper"
        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
        private const val DEFAULT_READ_BUFFER_SIZE = 2048
        private const val PLAYBACK_POLL_DELAY_MS = 10L
        private const val RECORD_PAUSE_DELAY_MS = 25L
    }
}
