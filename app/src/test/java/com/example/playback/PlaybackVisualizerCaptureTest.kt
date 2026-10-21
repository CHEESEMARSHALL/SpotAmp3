package com.example.playback

import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackVisualizerCaptureTest {
    @Test
    fun pcmToneProducesVisibleWaveformAndFrequencyEnergy() {
        val sampleRate = 48_000
        val frequency = 1_875.0
        val frameCount = 2_048
        val pcm = ByteBuffer.allocateDirect(frameCount * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        repeat(frameCount) { index ->
            val sample = (sin(2.0 * PI * frequency * index / sampleRate) * Short.MAX_VALUE * 0.8)
                .toInt()
                .toShort()
            pcm.putShort(sample)
        }
        pcm.flip()

        val frame = analyzePlaybackPcm(
            buffer = pcm,
            sampleRateHz = sampleRate,
            channelCount = 1,
            encoding = C.ENCODING_PCM_16BIT
        )

        assertEquals(PLAYBACK_VISUALIZER_WAVEFORM_SAMPLES, frame.waveform.size)
        assertEquals(PLAYBACK_VISUALIZER_SPECTRUM_BANDS, frame.spectrum.size)
        assertTrue(frame.waveform.maxOf { kotlin.math.abs(it) } > 0.7f)
        assertTrue(frame.spectrum.maxOrNull()!! > 0.5f)
    }

    @Test
    fun silenceAndUnsupportedFormatsReturnAQuietFrame() {
        val silence = ByteBuffer.allocateDirect(1_024 * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        val frame = analyzePlaybackPcm(
            buffer = silence,
            sampleRateHz = 48_000,
            channelCount = 1,
            encoding = C.ENCODING_PCM_16BIT
        )
        assertTrue(frame.waveform.all { it == 0f })
        assertTrue(frame.spectrum.all { it == 0f })

        val unsupported = analyzePlaybackPcm(
            buffer = silence,
            sampleRateHz = 48_000,
            channelCount = 1,
            encoding = C.ENCODING_INVALID
        )
        assertTrue(unsupported.waveform.all { it == 0f })
        assertTrue(unsupported.spectrum.all { it == 0f })
    }

    @Test
    fun pausingCaptureFreezesTheLastFrameAndResumeAcceptsNewFrames() {
        val publishedFrames = mutableListOf<PlaybackVisualizerFrame>()
        val capture = PlaybackVisualizerCapture(
            publishFrame = publishedFrames::add,
            minimumFrameIntervalMs = 0L,
            elapsedRealtimeMs = { 0L }
        )
        capture.flush(sampleRateHz = 8_000, channelCount = 1, encoding = C.ENCODING_PCM_16BIT)

        capture.setEnabled(true)
        capture.handleBuffer(toneBuffer(phaseRadians = 0.0))
        assertEquals(1, publishedFrames.size)
        val lastPlayingFrame = publishedFrames.last()

        capture.setEnabled(false, clearFrameWhenDisabled = false)
        capture.handleBuffer(toneBuffer(phaseRadians = PI / 2.0))
        assertEquals("A paused capture must not publish another frame", 1, publishedFrames.size)
        assertSame("Pause should retain the last rendered frame", lastPlayingFrame, publishedFrames.last())

        capture.setEnabled(true)
        capture.handleBuffer(toneBuffer(phaseRadians = PI / 2.0))
        assertEquals("Playback should resume visualizer updates", 2, publishedFrames.size)
        assertTrue(publishedFrames.last().waveform.indices.any { index ->
            publishedFrames.last().waveform[index] != lastPlayingFrame.waveform[index]
        })
    }

    @Test
    fun playbackGateFollowsRequestedAndActualPlaybackState() {
        val publishedFrames = mutableListOf<PlaybackVisualizerFrame>()
        val capture = PlaybackVisualizerCapture(
            publishFrame = publishedFrames::add,
            minimumFrameIntervalMs = 0L,
            elapsedRealtimeMs = { 0L }
        )
        val gate = PlaybackVisualizerPlaybackGate(capture)
        capture.flush(sampleRateHz = 8_000, channelCount = 1, encoding = C.ENCODING_PCM_16BIT)

        gate.setCaptureRequested(enabled = true, isPlaying = false)
        capture.handleBuffer(toneBuffer(phaseRadians = 0.0))
        assertTrue("A requested visualizer should stay idle before playback", publishedFrames.isEmpty())

        gate.onPlaybackChanged(isPlaying = true)
        capture.handleBuffer(toneBuffer(phaseRadians = 0.0))
        assertEquals("Actual playback should enable capture", 1, publishedFrames.size)
        val playingFrame = publishedFrames.last()

        gate.onPlaybackChanged(isPlaying = false)
        capture.handleBuffer(toneBuffer(phaseRadians = PI / 2.0))
        assertSame("Pause should freeze the current frame", playingFrame, publishedFrames.last())
        assertEquals("Pause should suppress new frames", 1, publishedFrames.size)

        gate.onPlaybackChanged(isPlaying = true)
        capture.handleBuffer(toneBuffer(phaseRadians = PI / 2.0))
        assertEquals("Resume should re-enable capture", 2, publishedFrames.size)

        gate.setCaptureRequested(enabled = false, isPlaying = true)
        assertTrue("Disabling the visualizer should clear its frame", publishedFrames.last().waveform.all { it == 0f })
    }

    @Test
    fun stoppingCaptureClearsThePausedFrame() {
        val publishedFrames = mutableListOf<PlaybackVisualizerFrame>()
        val capture = PlaybackVisualizerCapture(
            publishFrame = publishedFrames::add,
            minimumFrameIntervalMs = 0L,
            elapsedRealtimeMs = { 0L }
        )
        capture.flush(sampleRateHz = 8_000, channelCount = 1, encoding = C.ENCODING_PCM_16BIT)

        capture.setEnabled(true)
        capture.handleBuffer(toneBuffer(phaseRadians = 0.0))
        capture.setEnabled(false, clearFrameWhenDisabled = false)
        capture.setEnabled(false, clearFrameWhenDisabled = true)

        val stoppedFrame = publishedFrames.last()
        assertTrue(stoppedFrame.waveform.all { it == 0f })
        assertTrue(stoppedFrame.spectrum.all { it == 0f })
    }

    private fun toneBuffer(phaseRadians: Double): ByteBuffer {
        val sampleRate = 8_000
        val frameCount = 1_024
        val pcm = ByteBuffer.allocateDirect(frameCount * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        repeat(frameCount) { index ->
            val sample = (sin(2.0 * PI * 440.0 * index / sampleRate + phaseRadians) * 8_000)
                .toInt()
                .toShort()
            pcm.putShort(sample)
        }
        return pcm.apply { flip() }
    }
}
