package com.example.ui

import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.playback.PlaybackVisualizerCapture
import com.example.playback.PlaybackVisualizerFrame
import com.example.playback.PlaybackVisualizerPlaybackGate
import com.example.playback.analyzePlaybackPcm
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the production waveform Canvas with frames derived from a local PCM test tone. */
@RunWith(AndroidJUnit4::class)
class PlaybackMotionDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test(timeout = 30_000L)
    fun waveformUpdatesFromLocalToneWhileSystemMotionScalesAreZero() {
        val contentResolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val motionScales = listOf(
            Settings.Global.WINDOW_ANIMATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.ANIMATOR_DURATION_SCALE
        )
        motionScales.forEach { scaleName ->
            assertTrue(
                "$scaleName must be zero for this reduced-motion check",
                Settings.Global.getFloat(contentResolver, scaleName, 1f) == 0f
            )
        }

        val firstFrame = toneFrame(phaseRadians = 0.0)
        val nextFrame = toneFrame(phaseRadians = PI / 2.0)
        val waveform = mutableStateOf(firstFrame.waveform)

        composeRule.setContent {
            MyApplicationTheme {
                Box(Modifier.size(320.dp)) {
                    PlaybackVisualSurface(
                        artworkUrl = null,
                        spectrum = firstFrame.spectrum,
                        waveform = waveform.value,
                        mode = PlaybackArtworkMode.WAVEFORM,
                        onModeChange = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val before = composeRule.onNodeWithTag(
            "player_visualizer_waveform",
            useUnmergedTree = true
        ).captureToImage().toPixelMap()

        composeRule.runOnUiThread {
            waveform.value = nextFrame.waveform
        }
        composeRule.waitForIdle()
        val after = composeRule.onNodeWithTag(
            "player_visualizer_waveform",
            useUnmergedTree = true
        ).captureToImage().toPixelMap()

        var changedPixelCount = 0
        for (y in 0 until minOf(before.height, after.height)) {
            for (x in 0 until minOf(before.width, after.width)) {
                if (before[x, y] != after[x, y]) changedPixelCount++
            }
        }

        Log.i(TAG, "LOCAL_TONE_WAVEFORM_CHANGED_PIXELS=$changedPixelCount")
        assertTrue(
            "The production waveform should redraw when its PCM frame changes with motion scales disabled",
            changedPixelCount > 100
        )
    }

    @OptIn(UnstableApi::class)
    @Test(timeout = 30_000L)
    fun visualizerFreezesOnPauseAndResumesWithLocalTone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "qa-visualizer-pause-resume.wav")
        writeToneWav(fixture, seconds = 12)

        val publishedFrames = Collections.synchronizedList(mutableListOf<PlaybackVisualizerFrame>())
        val capture = PlaybackVisualizerCapture(
            publishFrame = publishedFrames::add,
            minimumFrameIntervalMs = 40L
        )
        val gate = PlaybackVisualizerPlaybackGate(capture)
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(TeeAudioProcessor(capture)))
                .build()
        }
        val player = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .build()
        val isPlaying = AtomicBoolean(false)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.set(playing)
                gate.onPlaybackChanged(playing)
            }
        })

        fun frameSnapshot(): List<PlaybackVisualizerFrame> = synchronized(publishedFrames) {
            publishedFrames.toList()
        }

        fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return true
                Thread.sleep(50L)
            }
            return condition()
        }

        try {
            instrumentation.runOnMainSync {
                gate.setCaptureRequested(enabled = true, isPlaying = player.isPlaying)
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(fixture)))
                player.prepare()
                player.play()
            }
            assertTrue(
                "Media3 local tone should publish frames through the production capture",
                waitFor(10_000L) { isPlaying.get() && frameSnapshot().isNotEmpty() }
            )
            Log.i(TAG, "MEDIA3_LOCAL_TONE_PLAYING_FRAMES=${frameSnapshot().size}")
            Thread.sleep(1_000L)

            instrumentation.runOnMainSync { player.pause() }
            assertTrue("Media3 did not enter the paused state", waitFor(5_000L) { !isPlaying.get() })
            Thread.sleep(300L)
            val pausedFrames = frameSnapshot()
            val frozenFrame = pausedFrames.last()
            Thread.sleep(500L)
            val laterPausedFrames = frameSnapshot()
            assertEquals("Paused audio should freeze visualizer frame publication", pausedFrames.size, laterPausedFrames.size)
            assertSame("Pause should retain the last rendered frame", frozenFrame, laterPausedFrames.last())

            instrumentation.runOnMainSync { player.play() }
            assertTrue(
                "Resumed Media3 audio should publish a new visualizer frame",
                waitFor(5_000L) { isPlaying.get() && frameSnapshot().lastOrNull() !== frozenFrame }
            )
            Log.i(TAG, "MEDIA3_LOCAL_TONE_PAUSE_RESUME=PASS")
            Thread.sleep(1_000L)
        } finally {
            gate.setCaptureRequested(enabled = false, isPlaying = false)
            instrumentation.runOnMainSync {
                player.stop()
                player.release()
            }
            fixture.delete()
        }
    }

    private fun writeToneWav(file: File, seconds: Int) {
        val sampleRateHz = 8_000
        val sampleCount = sampleRateHz * seconds
        val pcm = ByteBuffer.allocate(sampleCount * Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleCount) { index ->
            val phase = 2.0 * PI * 440.0 * index / sampleRateHz
            pcm.putShort((sin(phase) * 8_000.0).toInt().toShort())
        }
        val pcmBytes = pcm.array()
        FileOutputStream(file).use { output ->
            fun writeIntLittleEndian(value: Int) {
                output.write(ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
            }
            fun writeShortLittleEndian(value: Short) {
                output.write(ByteBuffer.allocate(Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array())
            }
            output.write("RIFF".toByteArray())
            writeIntLittleEndian(36 + pcmBytes.size)
            output.write("WAVEfmt ".toByteArray())
            writeIntLittleEndian(16)
            writeShortLittleEndian(1)
            writeShortLittleEndian(1)
            writeIntLittleEndian(sampleRateHz)
            writeIntLittleEndian(sampleRateHz * Short.SIZE_BYTES)
            writeShortLittleEndian(Short.SIZE_BYTES.toShort())
            writeShortLittleEndian(16)
            output.write("data".toByteArray())
            writeIntLittleEndian(pcmBytes.size)
            output.write(pcmBytes)
        }
    }

    private fun toneFrame(phaseRadians: Double): PlaybackVisualizerFrame {
        val sampleRateHz = 8_000
        val sampleCount = 1_024
        val pcm = ByteBuffer.allocate(sampleCount * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until sampleCount) {
            val phase = 2.0 * PI * 440.0 * index / sampleRateHz + phaseRadians
            val sample = (sin(phase) * 8_000.0).toInt().toShort()
            pcm.putShort(sample)
        }
        pcm.flip()
        return analyzePlaybackPcm(pcm, sampleRateHz, 1, C.ENCODING_PCM_16BIT)
    }

    private companion object {
        const val TAG = "PlaybackMotionQa"
    }
}
