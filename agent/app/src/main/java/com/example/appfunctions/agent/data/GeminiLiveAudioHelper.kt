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
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.appfunctions.agent.domain.LiveAudioSession
import kotlinx.coroutines.CancellationException
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
 * Manages low-latency PCM audio recording (16 kHz mono 16-bit) and playback (24 kHz mono 16-bit by
 * default) for a Gemini Live bidirectional audio session.
 */
class GeminiLiveAudioHelper
    @VisibleForTesting
    internal constructor(
        private val audioManager: AudioManager?,
        private val audioRecordFactory: (minBufferSize: Int) -> AudioRecord,
        private val audioTrackFactory: (
            attributes: AudioAttributes,
            sampleRateHz: Int,
            minBufferSize: Int,
        ) -> AudioTrack,
    ) : LiveAudioSession {
        constructor(
            audioManager: AudioManager? = null,
        ) : this(
            audioManager = audioManager,
            audioRecordFactory = ::defaultAudioRecord,
            audioTrackFactory = ::defaultAudioTrack,
        )

        override val inputSampleRateHz: Int = INPUT_SAMPLE_RATE

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val stateLock = Any()
        private val isReleased = AtomicBoolean(false)
        private val isRecordingPaused = AtomicBoolean(false)
        private val isFocusLost = AtomicBoolean(false)
        private val flushRequested = AtomicBoolean(false)

        @Volatile private var audioRecord: AudioRecord? = null
        private var echoCanceler: AcousticEchoCanceler? = null
        private var noiseSuppressor: NoiseSuppressor? = null

        @Volatile private var audioTrack: AudioTrack? = null

        @Volatile private var currentTrackSampleRateHz: Int = OUTPUT_SAMPLE_RATE
        private var playbackAttributes: AudioAttributes? = null
        private var audioFocusRequest: AudioFocusRequest? = null
        private var previousAudioMode: Int? = null
        private var didSetCommunicationDevice: Boolean = false
        private var playbackJob: Job? = null

        private val audioQueue = ConcurrentLinkedQueue<QueuedAudioChunk>()

        private val focusChangeListener =
            AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    -> {
                        isFocusLost.set(true)
                        stopAudio()
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        isFocusLost.set(false)
                    }
                }
            }

        @SuppressLint("MissingPermission")
        override fun start() {
            if (isReleased.get()) return

            synchronized(stateLock) {
                if (isReleased.get() || audioRecord != null) return

                var createdFocusRequest: AudioFocusRequest? = null
                var savedPreviousMode: Int? = null
                var commDeviceSelected = false
                var createdRecorder: AudioRecord? = null
                var createdEchoCanceler: AcousticEchoCanceler? = null
                var createdNoiseSuppressor: NoiseSuppressor? = null
                var createdTrack: AudioTrack? = null

                try {
                    // Pair USAGE_VOICE_COMMUNICATION playback with VOICE_COMMUNICATION recording under
                    // MODE_IN_COMMUNICATION so the hardware VoIP DSP AcousticEchoCanceler receives the
                    // far-end speaker reference (VOIP_PLAYBACK <-> VOIP_RECORD) instead of feeding
                    // uncancelled media/assistant speaker audio back into the microphone.
                    val attributes =
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()

                    val manager = audioManager
                    if (manager != null) {
                        val focusReq =
                            AudioFocusRequest.Builder(
                                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                            )
                                .setAudioAttributes(attributes)
                                .setOnAudioFocusChangeListener(focusChangeListener)
                                .build()
                        val focusResult = manager.requestAudioFocus(focusReq)
                        createdFocusRequest = focusReq
                        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            Log.w(TAG, "Audio focus request not granted (result=$focusResult)")
                        }

                        try {
                            savedPreviousMode = manager.mode
                            manager.mode = AudioManager.MODE_IN_COMMUNICATION
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to enter MODE_IN_COMMUNICATION", e)
                        }
                        commDeviceSelected = selectSpeakerphoneIfNoExternalDevice(manager)
                    }

                    val minRecordBuf =
                        AudioRecord.getMinBufferSize(
                            INPUT_SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                        ).coerceAtLeast(DEFAULT_READ_BUFFER_SIZE)

                    val recorder = audioRecordFactory(minRecordBuf)
                    createdRecorder = recorder
                    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                        throw IllegalStateException("Failed to initialize AudioRecord")
                    }

                    if (AcousticEchoCanceler.isAvailable()) {
                        try {
                            createdEchoCanceler =
                                AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                                    enabled = true
                                }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to enable AcousticEchoCanceler", e)
                        }
                    }

                    if (NoiseSuppressor.isAvailable()) {
                        try {
                            createdNoiseSuppressor =
                                NoiseSuppressor.create(recorder.audioSessionId)?.apply {
                                    enabled = true
                                }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to enable NoiseSuppressor", e)
                        }
                    }

                    val track = createAndPlayTrack(attributes, OUTPUT_SAMPLE_RATE)
                    createdTrack = track

                    if (isReleased.get()) {
                        cleanupResources(
                            focusRequest = createdFocusRequest,
                            previousMode = savedPreviousMode,
                            clearCommDevice = commDeviceSelected,
                            echoCanceler = createdEchoCanceler,
                            noiseSuppressor = createdNoiseSuppressor,
                            recorder = createdRecorder,
                            track = createdTrack,
                        )
                        return
                    }

                    recorder.startRecording()

                    playbackAttributes = attributes
                    audioFocusRequest = createdFocusRequest
                    previousAudioMode = savedPreviousMode
                    didSetCommunicationDevice = commDeviceSelected
                    echoCanceler = createdEchoCanceler
                    noiseSuppressor = createdNoiseSuppressor
                    audioRecord = recorder
                    audioTrack = track
                    currentTrackSampleRateHz = OUTPUT_SAMPLE_RATE

                    playbackJob =
                        scope.launch {
                            while (isActive && !isReleased.get()) {
                                if (flushRequested.getAndSet(false)) {
                                    flushActiveTrack()
                                }
                                val chunk = audioQueue.poll()
                                if (chunk != null) {
                                    if (flushRequested.get()) {
                                        continue
                                    }
                                    val currentTrack = ensureTrackForSampleRate(chunk.sampleRateHz)
                                    if (currentTrack != null && !isReleased.get()) {
                                        try {
                                            currentTrack.write(
                                                chunk.pcmBytes,
                                                0,
                                                chunk.pcmBytes.size,
                                            )
                                        } catch (e: Exception) {
                                            Log.w(TAG, "AudioTrack.write failed", e)
                                        }
                                    }
                                } else {
                                    delay(PLAYBACK_POLL_DELAY_MS)
                                }
                            }
                        }
                } catch (t: Throwable) {
                    cleanupResources(
                        focusRequest = createdFocusRequest,
                        previousMode = savedPreviousMode,
                        clearCommDevice = commDeviceSelected,
                        echoCanceler = createdEchoCanceler,
                        noiseSuppressor = createdNoiseSuppressor,
                        recorder = createdRecorder,
                        track = createdTrack,
                    )
                    throw t
                }
            }
        }

        /**
         * Emits PCM 16-bit 16 kHz mono byte chunks recorded from the microphone.
         *
         * Keeps [AudioRecord] continuously recording across tool calls so `audioserver` does not
         * broadcast system-wide port updates on pause/resume, while draining the HAL ring buffer
         * and gating emission in software when paused or when audio focus is temporarily lost.
         */
        override fun listenToRecording(): Flow<ByteArray> =
            flow {
                val buffer = ByteArray(DEFAULT_READ_BUFFER_SIZE)
                while (currentCoroutineContext().isActive && !isReleased.get()) {
                    val recorder = audioRecord ?: break
                    val bytesRead =
                        try {
                            recorder.read(buffer, 0, buffer.size)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (!isReleased.get()) {
                                Log.w(TAG, "AudioRecord.read failed", e)
                            }
                            -1
                        }
                    if (bytesRead > 0) {
                        if (!isRecordingPaused.get() && !isFocusLost.get() && !isReleased.get()) {
                            emit(buffer.copyOf(bytesRead))
                        }
                    } else if (bytesRead < 0) {
                        if (!isReleased.get()) {
                            Log.w(TAG, "AudioRecord.read returned error code: $bytesRead")
                        }
                        break
                    } else {
                        delay(RECORD_IDLE_DELAY_MS)
                    }
                }
            }.flowOn(Dispatchers.IO)

        override fun queueAudio(
            pcmBytes: ByteArray,
            sampleRateHz: Int,
        ) {
            if (!isReleased.get()) {
                val effectiveRate = if (sampleRateHz > 0) sampleRateHz else OUTPUT_SAMPLE_RATE
                audioQueue.add(QueuedAudioChunk(pcmBytes = pcmBytes, sampleRateHz = effectiveRate))
            }
        }

        /** Clears any queued model audio and flushes the playback track when interrupted. */
        override fun stopAudio() {
            audioQueue.clear()
            flushRequested.set(true)
        }

        /**
         * Gates outgoing microphone emissions in software without stopping the underlying
         * [AudioRecord] hardware stream.
         */
        override fun pauseRecording() {
            isRecordingPaused.set(true)
        }

        /** Resumes outgoing microphone emissions after [pauseRecording]. */
        override fun resumeRecording() {
            if (!isReleased.get()) {
                isRecordingPaused.set(false)
            }
        }

        override fun release() {
            if (!isReleased.compareAndSet(false, true)) return
            audioQueue.clear()

            synchronized(stateLock) {
                playbackJob?.cancel()
                playbackJob = null
                scope.cancel()

                val focusReq = audioFocusRequest
                audioFocusRequest = null
                val prevMode = previousAudioMode
                previousAudioMode = null
                val clearComm = didSetCommunicationDevice
                didSetCommunicationDevice = false
                val aec = echoCanceler
                echoCanceler = null
                val ns = noiseSuppressor
                noiseSuppressor = null
                val recorder = audioRecord
                audioRecord = null
                val track = audioTrack
                audioTrack = null
                playbackAttributes = null

                cleanupResources(
                    focusRequest = focusReq,
                    previousMode = prevMode,
                    clearCommDevice = clearComm,
                    echoCanceler = aec,
                    noiseSuppressor = ns,
                    recorder = recorder,
                    track = track,
                )
            }
        }

        private fun createAndPlayTrack(
            attributes: AudioAttributes,
            sampleRateHz: Int,
        ): AudioTrack {
            val minTrackBuf =
                (
                    AudioTrack.getMinBufferSize(
                        sampleRateHz,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    ) * 2
                ).coerceAtLeast(DEFAULT_READ_BUFFER_SIZE)
            val track = audioTrackFactory(attributes, sampleRateHz, minTrackBuf)
            track.play()
            return track
        }

        private fun ensureTrackForSampleRate(sampleRateHz: Int): AudioTrack? {
            val existing = audioTrack
            if (existing != null && sampleRateHz == currentTrackSampleRateHz) {
                return existing
            }
            synchronized(stateLock) {
                if (isReleased.get()) return null
                val current = audioTrack
                if (current != null && sampleRateHz == currentTrackSampleRateHz) {
                    return current
                }
                val attributes = playbackAttributes ?: return current
                return try {
                    val newTrack = createAndPlayTrack(attributes, sampleRateHz)
                    releaseTrackQuietly(current)
                    audioTrack = newTrack
                    currentTrackSampleRateHz = sampleRateHz
                    newTrack
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to recreate AudioTrack for $sampleRateHz Hz", e)
                    current
                }
            }
        }

        private fun flushActiveTrack() {
            val currentTrack = audioTrack
            if (currentTrack != null && !isReleased.get()) {
                try {
                    if (currentTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        currentTrack.pause()
                        currentTrack.flush()
                        currentTrack.play()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to flush AudioTrack on interruption", e)
                }
            }
        }

        private fun selectSpeakerphoneIfNoExternalDevice(manager: AudioManager): Boolean {
            return try {
                val activeDevice = manager.communicationDevice
                if (activeDevice != null && activeDevice.type in EXTERNAL_COMMUNICATION_DEVICE_TYPES) {
                    return false
                }
                val availableDevices = manager.availableCommunicationDevices
                if (availableDevices.any { it.type in EXTERNAL_COMMUNICATION_DEVICE_TYPES }) {
                    return false
                }
                val speaker =
                    availableDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    } ?: return false
                manager.setCommunicationDevice(speaker)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to route communication audio to speakerphone", e)
                false
            }
        }

        private fun releaseTrackQuietly(track: AudioTrack?) {
            if (track == null) return
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED &&
                    track.playState == AudioTrack.PLAYSTATE_PLAYING
                ) {
                    track.pause()
                    track.flush()
                    track.stop()
                }
                track.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release AudioTrack", e)
            }
        }

        private fun cleanupResources(
            focusRequest: AudioFocusRequest?,
            previousMode: Int?,
            clearCommDevice: Boolean,
            echoCanceler: AcousticEchoCanceler?,
            noiseSuppressor: NoiseSuppressor?,
            recorder: AudioRecord?,
            track: AudioTrack?,
        ) {
            try {
                echoCanceler?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release AcousticEchoCanceler", e)
            }
            try {
                noiseSuppressor?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release NoiseSuppressor", e)
            }

            if (recorder != null) {
                try {
                    if (recorder.state == AudioRecord.STATE_INITIALIZED &&
                        recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
                    ) {
                        recorder.stop()
                    }
                    recorder.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to release AudioRecord", e)
                }
            }

            releaseTrackQuietly(track)

            if (clearCommDevice) {
                try {
                    audioManager?.clearCommunicationDevice()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear communication device", e)
                }
            }

            if (previousMode != null) {
                try {
                    audioManager?.mode = previousMode
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore audio mode", e)
                }
            }

            if (focusRequest != null) {
                try {
                    audioManager?.abandonAudioFocusRequest(focusRequest)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to abandon audio focus request", e)
                }
            }
        }

        private class QueuedAudioChunk(
            val pcmBytes: ByteArray,
            val sampleRateHz: Int,
        )

        companion object {
            private const val TAG = "GeminiLiveAudioHelper"
            private const val INPUT_SAMPLE_RATE = LiveAudioSession.DEFAULT_INPUT_SAMPLE_RATE_HZ
            private const val OUTPUT_SAMPLE_RATE = LiveAudioSession.DEFAULT_OUTPUT_SAMPLE_RATE_HZ
            private const val DEFAULT_READ_BUFFER_SIZE = 2048
            private const val PLAYBACK_POLL_DELAY_MS = 10L
            private const val RECORD_IDLE_DELAY_MS = 10L

            private val EXTERNAL_COMMUNICATION_DEVICE_TYPES =
                setOf(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER,
                    AudioDeviceInfo.TYPE_BLE_BROADCAST,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_ACCESSORY,
                    AudioDeviceInfo.TYPE_HEARING_AID,
                    AudioDeviceInfo.TYPE_HDMI,
                    AudioDeviceInfo.TYPE_AUX_LINE,
                    AudioDeviceInfo.TYPE_LINE_ANALOG,
                    AudioDeviceInfo.TYPE_LINE_DIGITAL,
                )

            @SuppressLint("MissingPermission")
            private fun defaultAudioRecord(minRecordBuf: Int): AudioRecord =
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    INPUT_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minRecordBuf,
                )

            private fun defaultAudioTrack(
                playbackAttributes: AudioAttributes,
                sampleRateHz: Int,
                minTrackBuf: Int,
            ): AudioTrack =
                AudioTrack.Builder()
                    .setAudioAttributes(playbackAttributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRateHz)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(minTrackBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
        }
    }
