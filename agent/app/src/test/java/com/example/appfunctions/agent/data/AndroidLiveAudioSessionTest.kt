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

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import androidx.test.core.app.ApplicationProvider
import com.example.appfunctions.agent.domain.LiveAudioConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidLiveAudioSessionTest {
    @Test
    fun `start enters communication mode with voice communication focus and release restores state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = context.getSystemService(AudioManager::class.java)
        audioManager.mode = AudioManager.MODE_NORMAL
        val shadowAudioManager = Shadows.shadowOf(audioManager)

        val mockRecorder = createInitializedMockRecorder()
        val mockTrack = createInitializedMockTrack()
        var capturedRecordSampleRate = 0
        var capturedAttributes: AudioAttributes? = null
        var capturedTrackSampleRate = 0

        val session =
            AndroidLiveAudioSession(
                config = DEFAULT_CONFIG,
                audioManager = audioManager,
                audioRecordFactory = { sampleRateHz, _ ->
                    capturedRecordSampleRate = sampleRateHz
                    mockRecorder
                },
                audioTrackFactory = { attrs, sampleRateHz, _ ->
                    capturedAttributes = attrs
                    capturedTrackSampleRate = sampleRateHz
                    mockTrack
                },
            )

        session.start()

        val focusReq = shadowAudioManager.lastAudioFocusRequest?.audioFocusRequest
        assertNotNull(focusReq)
        assertEquals(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            focusReq?.focusGain,
        )
        assertEquals(AudioAttributes.USAGE_VOICE_COMMUNICATION, capturedAttributes?.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, capturedAttributes?.contentType)
        assertEquals(16_000, capturedRecordSampleRate)
        assertEquals(24_000, capturedTrackSampleRate)
        assertEquals(16_000, session.inputSampleRateHz)
        assertEquals(AudioManager.MODE_IN_COMMUNICATION, audioManager.mode)
        assertNull(shadowAudioManager.lastAbandonedAudioFocusRequest)

        session.release()

        assertEquals(AudioManager.MODE_NORMAL, audioManager.mode)
        assertSame(focusReq, shadowAudioManager.lastAbandonedAudioFocusRequest)
        verify(exactly = 1) { mockRecorder.stop() }
        verify(exactly = 1) { mockRecorder.release() }
        verify(exactly = 1) { mockTrack.release() }
    }

    @Test
    fun `start routes communication audio to speakerphone when no external headset is connected`() {
        val earpiece =
            mockk<AudioDeviceInfo> {
                every { type } returns AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
        val speaker =
            mockk<AudioDeviceInfo> {
                every { type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
        val telephony =
            mockk<AudioDeviceInfo> {
                every { type } returns AudioDeviceInfo.TYPE_TELEPHONY
            }
        val audioManager =
            mockk<AudioManager>(relaxed = true) {
                every { mode } returns AudioManager.MODE_NORMAL
                every { requestAudioFocus(any()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                every { communicationDevice } returns earpiece
                every { availableCommunicationDevices } returns listOf(earpiece, speaker, telephony)
                every { setCommunicationDevice(speaker) } returns true
            }

        val session =
            AndroidLiveAudioSession(
                config = DEFAULT_CONFIG,
                audioManager = audioManager,
                audioRecordFactory = { _, _ -> createInitializedMockRecorder() },
                audioTrackFactory = { _, _, _ -> createInitializedMockTrack() },
            )

        session.start()
        verify(exactly = 1) { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
        verify(exactly = 1) { audioManager.setCommunicationDevice(speaker) }

        session.release()
        verify(exactly = 1) { audioManager.clearCommunicationDevice() }
        verify(exactly = 1) { audioManager.mode = AudioManager.MODE_NORMAL }
    }

    @Test
    fun `start preserves external headset routing when BLE or wired headset is available`() {
        val speaker =
            mockk<AudioDeviceInfo> {
                every { type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
        val bleHeadset =
            mockk<AudioDeviceInfo> {
                every { type } returns AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        val audioManager =
            mockk<AudioManager>(relaxed = true) {
                every { mode } returns AudioManager.MODE_NORMAL
                every { requestAudioFocus(any()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                every { communicationDevice } returns null
                every { availableCommunicationDevices } returns listOf(speaker, bleHeadset)
            }

        val session =
            AndroidLiveAudioSession(
                config = DEFAULT_CONFIG,
                audioManager = audioManager,
                audioRecordFactory = { _, _ -> createInitializedMockRecorder() },
                audioTrackFactory = { _, _, _ -> createInitializedMockTrack() },
            )

        session.start()
        verify(exactly = 0) { audioManager.setCommunicationDevice(any()) }

        session.release()
        verify(exactly = 0) { audioManager.clearCommunicationDevice() }
    }

    @Test
    fun `start throws IllegalStateException when audio focus request is not granted`() {
        val audioManager =
            mockk<AudioManager>(relaxed = true) {
                every { requestAudioFocus(any()) } returns AudioManager.AUDIOFOCUS_REQUEST_FAILED
            }
        var recorderCreated = false

        val session =
            AndroidLiveAudioSession(
                config = DEFAULT_CONFIG,
                audioManager = audioManager,
                audioRecordFactory = { _, _ ->
                    recorderCreated = true
                    createInitializedMockRecorder()
                },
                audioTrackFactory = { _, _, _ -> createInitializedMockTrack() },
            )

        try {
            session.start()
            fail("Expected IllegalStateException when audio focus is denied")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Failed to acquire audio focus"))
        }

        assertEquals(false, recorderCreated)
        verify(exactly = 0) { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
    }

    @Test
    fun `start throws IllegalStateException when called while already started or after release`() {
        val session =
            AndroidLiveAudioSession(
                config = DEFAULT_CONFIG,
                audioManager = null,
                audioRecordFactory = { _, _ -> createInitializedMockRecorder() },
                audioTrackFactory = { _, _, _ -> createInitializedMockTrack() },
            )

        session.start()

        try {
            session.start()
            fail("Expected IllegalStateException when calling start() while already started")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("already been started"))
        }

        session.release()

        try {
            session.start()
            fail("Expected IllegalStateException when calling start() after release()")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("already been released"))
        }
    }

    @Test
    fun `stopAudio flushes active AudioTrack and discards queued chunks`() {
        val flushedLatch = CountDownLatch(1)
        val mockTrack =
            createInitializedMockTrack().apply {
                every { flush() } answers {
                    flushedLatch.countDown()
                }
            }
        val session =
            AndroidLiveAudioSession(
                config = DEFAULT_CONFIG,
                audioManager = null,
                audioRecordFactory = { _, _ -> createInitializedMockRecorder() },
                audioTrackFactory = { _, _, _ -> mockTrack },
            )

        session.start()
        session.stopAudio()

        assertTrue(flushedLatch.await(2, TimeUnit.SECONDS))
        verify(atLeast = 1) { mockTrack.pause() }
        verify(atLeast = 1) { mockTrack.flush() }
        verify(atLeast = 2) { mockTrack.play() }

        session.release()
    }

    @Test
    fun `queueAudio recreates AudioTrack when chunk sampleRateHz changes`() {
        val chunk1Written = CountDownLatch(1)
        val chunk2Written = CountDownLatch(1)
        val track24k =
            createInitializedMockTrack().apply {
                every { write(any<ByteArray>(), any<Int>(), any<Int>()) } answers {
                    chunk1Written.countDown()
                    thirdArg()
                }
            }
        val track16k =
            createInitializedMockTrack().apply {
                every { write(any<ByteArray>(), any<Int>(), any<Int>()) } answers {
                    chunk2Written.countDown()
                    thirdArg()
                }
            }
        val createdSampleRates = CopyOnWriteArrayList<Int>()
        val session =
            AndroidLiveAudioSession(
                config = DEFAULT_CONFIG,
                audioManager = null,
                audioRecordFactory = { _, _ -> createInitializedMockRecorder() },
                audioTrackFactory = { _, sampleRateHz, _ ->
                    createdSampleRates.add(sampleRateHz)
                    if (sampleRateHz == 24_000) track24k else track16k
                },
            )

        session.start()
        session.queueAudio(byteArrayOf(1, 2), sampleRateHz = 24_000)
        assertTrue(chunk1Written.await(2, TimeUnit.SECONDS))

        session.queueAudio(byteArrayOf(3, 4), sampleRateHz = 16_000)
        assertTrue(chunk2Written.await(2, TimeUnit.SECONDS))

        assertEquals(listOf(24_000, 16_000), createdSampleRates.toList())
        verify(exactly = 1) { track24k.release() }

        session.release()
        verify(exactly = 1) { track16k.release() }
    }

    @Test
    fun `queueAudio drops chunks when audio focus is lost and resumes playback when focus is regained`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = context.getSystemService(AudioManager::class.java)
        val shadowAudioManager = Shadows.shadowOf(audioManager)

        val writtenChunks = CopyOnWriteArrayList<ByteArray>()
        val secondAllowedChunkLatch = CountDownLatch(1)
        val mockTrack =
            createInitializedMockTrack().apply {
                every { write(any<ByteArray>(), any<Int>(), any<Int>()) } answers {
                    val bytes = firstArg<ByteArray>().copyOf(thirdArg())
                    writtenChunks.add(bytes)
                    if (bytes.contentEquals(byteArrayOf(5, 6))) {
                        secondAllowedChunkLatch.countDown()
                    }
                    thirdArg()
                }
            }

        val session =
            AndroidLiveAudioSession(
                config = DEFAULT_CONFIG,
                audioManager = audioManager,
                audioRecordFactory = { _, _ -> createInitializedMockRecorder() },
                audioTrackFactory = { _, _, _ -> mockTrack },
            )

        session.start()
        val focusListener =
            checkNotNull(shadowAudioManager.lastAudioFocusRequest?.listener)

        // Lose focus: chunks queued while focus is lost must be ignored
        focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        session.queueAudio(byteArrayOf(9, 9), sampleRateHz = 24_000)

        // Regain focus: subsequent chunks are played
        focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        session.queueAudio(byteArrayOf(5, 6), sampleRateHz = 24_000)

        assertTrue(secondAllowedChunkLatch.await(2, TimeUnit.SECONDS))
        assertEquals(1, writtenChunks.size)
        assertArrayEquals(byteArrayOf(5, 6), writtenChunks.single())

        session.release()
    }

    @Test
    fun `pauseRecording and resumeRecording drain AudioRecord in software without stopping hardware`() =
        runBlocking {
            val mockRecorder = createInitializedMockRecorder()
            val mockTrack = createInitializedMockTrack()
            val session =
                AndroidLiveAudioSession(
                    config = DEFAULT_CONFIG,
                    audioManager = null,
                    audioRecordFactory = { _, _ -> mockRecorder },
                    audioTrackFactory = { _, _, _ -> mockTrack },
                )

            val readStep = AtomicInteger(0)
            every { mockRecorder.read(any<ByteArray>(), any<Int>(), any<Int>()) } answers {
                val buf = firstArg<ByteArray>()
                when (readStep.incrementAndGet()) {
                    1 -> {
                        buf[0] = 10
                        buf[1] = 11
                        2
                    }
                    2 -> {
                        // Simulate pauseRecording() active during tool call: read() still runs to
                        // drain the HAL ring buffer, but chunk 2 must be discarded.
                        session.pauseRecording()
                        buf[0] = 20
                        buf[1] = 21
                        2
                    }
                    3 -> {
                        // Resume before delivering chunk 3
                        session.resumeRecording()
                        buf[0] = 30
                        buf[1] = 31
                        2
                    }
                    else -> {
                        session.release()
                        -1
                    }
                }
            }

            session.start()
            val emittedChunks = session.listenToRecording().toList()

            assertEquals(2, emittedChunks.size)
            assertArrayEquals(byteArrayOf(10, 11), emittedChunks[0])
            assertArrayEquals(byteArrayOf(30, 31), emittedChunks[1])

            // Hardware AudioRecord was started only once in start(), and stop() was only called once
            // during final release() — never during pauseRecording()/resumeRecording().
            verify(exactly = 1) { mockRecorder.startRecording() }
            verify(exactly = 1) { mockRecorder.stop() }
        }

    @Test
    fun `audio focus loss pauses emission independently of tool-call pause state`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val audioManager = context.getSystemService(AudioManager::class.java)
            val shadowAudioManager = Shadows.shadowOf(audioManager)
            val mockRecorder = createInitializedMockRecorder()
            val mockTrack = createInitializedMockTrack()

            val session =
                AndroidLiveAudioSession(
                    config = DEFAULT_CONFIG,
                    audioManager = audioManager,
                    audioRecordFactory = { _, _ -> mockRecorder },
                    audioTrackFactory = { _, _, _ -> mockTrack },
                )

            session.start()
            val focusListener =
                checkNotNull(shadowAudioManager.lastAudioFocusRequest?.listener)

            val readStep = AtomicInteger(0)
            every { mockRecorder.read(any<ByteArray>(), any<Int>(), any<Int>()) } answers {
                val buf = firstArg<ByteArray>()
                when (readStep.incrementAndGet()) {
                    1 -> {
                        // Unpaused & focus held -> emitted (chunk 1)
                        buf[0] = 1
                        1
                    }
                    2 -> {
                        // Tool-call pause + focus loss followed by focus gain must NOT clear tool-call pause
                        session.pauseRecording()
                        focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                        focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
                        buf[0] = 2
                        1
                    }
                    3 -> {
                        // Resume tool-call pause, then lose focus -> still suppressed
                        session.resumeRecording()
                        focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                        buf[0] = 3
                        1
                    }
                    4 -> {
                        // Regain focus while unpaused -> emitted (chunk 4)
                        focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
                        buf[0] = 4
                        1
                    }
                    else -> {
                        session.release()
                        -1
                    }
                }
            }

            val emittedChunks = session.listenToRecording().toList()
            assertEquals(2, emittedChunks.size)
            assertArrayEquals(byteArrayOf(1), emittedChunks[0])
            assertArrayEquals(byteArrayOf(4), emittedChunks[1])
        }

    @Test
    fun `concurrent release during start throws IllegalStateException and cleans up resources without leaking active AudioRecord`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = context.getSystemService(AudioManager::class.java)
        val shadowAudioManager = Shadows.shadowOf(audioManager)

        val mockRecorder = createInitializedMockRecorder()
        val mockTrack = createInitializedMockTrack()
        val insideStartLatch = CountDownLatch(1)
        val releaseIssuedLatch = CountDownLatch(1)

        val session =
            AndroidLiveAudioSession(
                config = DEFAULT_CONFIG,
                audioManager = audioManager,
                audioRecordFactory = { _, _ ->
                    insideStartLatch.countDown()
                    assertTrue(releaseIssuedLatch.await(2, TimeUnit.SECONDS))
                    mockRecorder
                },
                audioTrackFactory = { _, _, _ -> mockTrack },
            )

        val releaseThread =
            Thread {
                assertTrue(insideStartLatch.await(2, TimeUnit.SECONDS))
                // Trigger release() from a second thread while start() is mid-initialization.
                // release() flips isReleased=true immediately and then waits on stateLock.
                val releaseCaller =
                    Thread {
                        session.release()
                    }
                releaseCaller.start()
                // Spin briefly until releaseCaller flips isReleased (before blocking on stateLock)
                Thread.sleep(30)
                releaseIssuedLatch.countDown()
                releaseCaller.join(2000)
            }
        releaseThread.start()

        try {
            session.start()
            fail("Expected IllegalStateException when session is released during start()")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("released"))
        }
        releaseThread.join(2000)

        // Because release() was triggered mid-start(), AudioRecord must never be left recording.
        verify(exactly = 0) { mockRecorder.startRecording() }
        verify(exactly = 1) { mockRecorder.release() }
        verify(exactly = 1) { mockTrack.release() }
        val focusReq = shadowAudioManager.lastAudioFocusRequest?.audioFocusRequest
        assertNotNull(focusReq)
        assertSame(focusReq, shadowAudioManager.lastAbandonedAudioFocusRequest)
    }

    private fun createInitializedMockRecorder(): AudioRecord =
        mockk<AudioRecord>(relaxed = true).apply {
            every { state } returns AudioRecord.STATE_INITIALIZED
            every { recordingState } returns AudioRecord.RECORDSTATE_RECORDING
            every { audioSessionId } returns 42
        }

    private fun createInitializedMockTrack(): AudioTrack =
        mockk<AudioTrack>(relaxed = true).apply {
            every { state } returns AudioTrack.STATE_INITIALIZED
            every { playState } returns AudioTrack.PLAYSTATE_PLAYING
        }

    companion object {
        private val DEFAULT_CONFIG =
            LiveAudioConfig(
                inputSampleRateHz = 16_000,
                outputSampleRateHz = 24_000,
            )
    }
}
