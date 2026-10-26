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
import com.example.appfunctions.agent.domain.LiveAudioSession
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
class GeminiLiveAudioHelper(
    private val audioManager: AudioManager? = null,
) : LiveAudioSession {
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
    private var audioFocusRequest: AudioFocusRequest? = null
    private var previousAudioMode: Int? = null
    private var didSetCommunicationDevice: Boolean = false
    private var playbackJob: Job? = null

    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()

    @SuppressLint("MissingPermission")
    internal var audioRecordFactory: (minBufferSize: Int) -> AudioRecord = { minRecordBuf ->
        AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minRecordBuf,
        )
    }

    internal var audioTrackFactory:
        (attributes: AudioAttributes, minBufferSize: Int) -> AudioTrack =
        { playbackAttributes, minTrackBuf ->
            AudioTrack.Builder()
                .setAudioAttributes(playbackAttributes)
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
        }

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
                val playbackAttributes =
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
                            .setAudioAttributes(playbackAttributes)
                            .setOnAudioFocusChangeListener(focusChangeListener)
                            .build()
                    val focusResult = manager.requestAudioFocus(focusReq)
                    createdFocusRequest = focusReq
                    if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        Log.w(TAG, "Audio focus request not granted (result=$focusResult)")
                    }

                    runCatching {
                        savedPreviousMode = manager.mode
                        manager.mode = AudioManager.MODE_IN_COMMUNICATION
                    }.onFailure { e ->
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
                    runCatching {
                        createdEchoCanceler =
                            AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                                enabled = true
                            }
                    }.onFailure { e ->
                        Log.w(TAG, "Failed to enable AcousticEchoCanceler", e)
                    }
                }

                if (NoiseSuppressor.isAvailable()) {
                    runCatching {
                        createdNoiseSuppressor =
                            NoiseSuppressor.create(recorder.audioSessionId)?.apply {
                                enabled = true
                            }
                    }.onFailure { e ->
                        Log.w(TAG, "Failed to enable NoiseSuppressor", e)
                    }
                }

                val minTrackBuf =
                    (
                        AudioTrack.getMinBufferSize(
                            OUTPUT_SAMPLE_RATE,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                        ) * 2
                    ).coerceAtLeast(DEFAULT_READ_BUFFER_SIZE)

                val track = audioTrackFactory(playbackAttributes, minTrackBuf)
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

                track.play()
                recorder.startRecording()

                audioFocusRequest = createdFocusRequest
                previousAudioMode = savedPreviousMode
                didSetCommunicationDevice = commDeviceSelected
                echoCanceler = createdEchoCanceler
                noiseSuppressor = createdNoiseSuppressor
                audioRecord = recorder
                audioTrack = track

                playbackJob =
                    scope.launch {
                        while (isActive && !isReleased.get()) {
                            if (flushRequested.getAndSet(false)) {
                                val currentTrack = audioTrack
                                if (currentTrack != null && !isReleased.get()) {
                                    runCatching {
                                        if (currentTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                            currentTrack.pause()
                                            currentTrack.flush()
                                            currentTrack.play()
                                        }
                                    }
                                }
                            }
                            val chunk = audioQueue.poll()
                            if (chunk != null) {
                                if (flushRequested.get()) {
                                    continue
                                }
                                val currentTrack = audioTrack
                                if (currentTrack != null && !isReleased.get()) {
                                    runCatching {
                                        currentTrack.write(chunk, 0, chunk.size)
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
     * broadcast system-wide port updates on pause/resume, while draining the HAL ring buffer and
     * gating emission in software when paused or when audio focus is temporarily lost.
     */
    override fun listenToRecording(): Flow<ByteArray> =
        flow {
            val buffer = ByteArray(DEFAULT_READ_BUFFER_SIZE)
            while (currentCoroutineContext().isActive && !isReleased.get()) {
                val recorder = audioRecord ?: break
                val bytesRead =
                    runCatching {
                        recorder.read(buffer, 0, buffer.size)
                    }.getOrElse { e ->
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

    override fun queueAudio(pcmBytes: ByteArray) {
        if (!isReleased.get()) {
            audioQueue.add(pcmBytes)
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

    internal fun isPausedForTesting(): Boolean = isRecordingPaused.get() || isFocusLost.get()

    internal fun notifyAudioFocusChangeForTesting(focusChange: Int) {
        focusChangeListener.onAudioFocusChange(focusChange)
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

    private fun selectSpeakerphoneIfNoExternalDevice(manager: AudioManager): Boolean =
        runCatching {
            val activeDevice = manager.communicationDevice
            if (activeDevice != null && activeDevice.type in EXTERNAL_COMMUNICATION_DEVICE_TYPES) {
                return@runCatching false
            }
            val availableDevices = manager.availableCommunicationDevices
            if (availableDevices.any { it.type in EXTERNAL_COMMUNICATION_DEVICE_TYPES }) {
                return@runCatching false
            }
            val speaker =
                availableDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } ?: return@runCatching false
            manager.setCommunicationDevice(speaker)
        }.getOrElse { e ->
            Log.w(TAG, "Failed to route communication audio to speakerphone", e)
            false
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
        runCatching { echoCanceler?.release() }
        runCatching { noiseSuppressor?.release() }

        runCatching {
            recorder?.let { rec ->
                if (rec.state == AudioRecord.STATE_INITIALIZED &&
                    rec.recordingState == AudioRecord.RECORDSTATE_RECORDING
                ) {
                    rec.stop()
                }
                rec.release()
            }
        }

        runCatching {
            track?.let { trk ->
                if (trk.state == AudioTrack.STATE_INITIALIZED &&
                    trk.playState == AudioTrack.PLAYSTATE_PLAYING
                ) {
                    trk.pause()
                    trk.flush()
                    trk.stop()
                }
                trk.release()
            }
        }

        runCatching {
            if (clearCommDevice) {
                audioManager?.clearCommunicationDevice()
            }
        }

        runCatching {
            if (previousMode != null) {
                audioManager?.mode = previousMode
            }
        }

        runCatching {
            if (focusRequest != null) {
                audioManager?.abandonAudioFocusRequest(focusRequest)
            }
        }
    }

    companion object {
        private const val TAG = "GeminiLiveAudioHelper"
        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
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
    }
}
