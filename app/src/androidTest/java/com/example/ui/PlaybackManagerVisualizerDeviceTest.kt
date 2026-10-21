package com.example.ui

import android.util.Log
import android.os.SystemClock
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.R
import com.example.data.MusicDatabase
import com.example.data.PlaybackStateEntity
import com.example.playback.TrackItem
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Connects the real PlaybackManager audio path to the production Now Playing waveform. */
@RunWith(AndroidJUnit4::class)
class PlaybackManagerVisualizerDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 60_000L)
    fun playbackManagerFeedsTheNowPlayingWaveformAndPauseFreezesIt() {
        val context = composeRule.activity
        val playback = ViewModelProvider(context)[MusicViewModel::class.java].playbackManager
        val pretestTrack = playback.currentTrack.value
        val originalQueue = playback.queue.value.toList()
        val originalIndex = playback.currentIndex.value
        val originalProgress = playback.progress.value
        val originalShuffle = playback.shuffleModeEnabled.value
        val originalRepeat = playback.repeatMode.value
        Log.i(
            TAG,
            "QA_PLAYBACK_PRETEST queueCount=${playback.queue.value.size} hasCurrentTrack=${pretestTrack != null} " +
                "isPlaying=${playback.isPlaying.value} localFileAvailable=${pretestTrack?.let { playback.getLocalAudioFile(it) != null }}"
        )
        assumeTrue(
            "Do not interrupt active QA playback",
            !playback.isPlaying.value
        )

        val fixture = File(context.cacheDir, "qa-playback-manager-visualizer.wav")
        fixture.delete()
        writeToneWav(fixture, seconds = 12)
        val dao = MusicDatabase.getDatabase(context).musicDao()
        runBlocking {
            dao.deleteRecentTrackByKey(TEST_TRACK_RATING_KEY)
            dao.deleteListeningHistoryByKey(TEST_TRACK_RATING_KEY)
        }
        val track = TrackItem(
            ratingKey = TEST_TRACK_RATING_KEY,
            title = "Wave 5 visualizer UI fixture",
            artist = "SpotAmp QA",
            album = "Generated test audio",
            key = "/qa/playback-manager-visualizer",
            thumb = "",
            duration = 12_000L,
            localPath = fixture.absolutePath
        )

        try {
            composeRule.runOnUiThread { playback.playTrack(track, listOf(track)) }
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                playback.currentTrack.value?.ratingKey == track.ratingKey && playback.isPlaying.value
            }
            composeRule.onNodeWithTag("mini_player").assertIsDisplayed().performClick()
            composeRule.onNodeWithTag("now_playing_scroll_container").assertIsDisplayed()

            // Wait for each production mode transition before activating the control again.
            val modeButton = composeRule.onNodeWithTag("player_visual_mode_button")
            modeButton.performClick()
            waitForVisualMode(modeButton, R.string.player_visual_mode_spectrum)
            modeButton.performClick()
            waitForVisualMode(modeButton, R.string.player_visual_mode_waveform)
            composeRule.onNodeWithTag("player_visualizer_waveform", useUnmergedTree = true).assertIsDisplayed()

            composeRule.waitUntil(timeoutMillis = 10_000L) {
                playback.isPlaying.value && playback.visualizerFrame.value.waveform.any { sample ->
                    sample > 0.05f || sample < -0.05f
                }
            }
            val firstPlayingFrame = playback.visualizerFrame.value
            val waveformNode = composeRule.onNodeWithTag("player_visualizer_waveform", useUnmergedTree = true)
            val firstPlayingImage = waveformNode
                .captureToImage().toPixelMap()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                playback.visualizerFrame.value !== firstPlayingFrame
            }
            val nextPlayingImage = waveformNode
                .captureToImage().toPixelMap()
            val playingPixelChanges = changedPixelCount(firstPlayingImage, nextPlayingImage)
            assertTrue("Live PCM frames should redraw the production waveform", playingPixelChanges > 100)

            val progressAtPause = playback.progress.value
            composeRule.onNodeWithTag("play_pause_fab").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) { !playback.isPlaying.value }
            Thread.sleep(500L)
            val pausedFrame = playback.visualizerFrame.value
            val pausedImage = waveformNode
                .captureToImage().toPixelMap()
            Thread.sleep(500L)
            assertSame(
                "The production PlaybackManager should retain its last frame while paused",
                pausedFrame,
                playback.visualizerFrame.value
            )
            val stillPausedImage = waveformNode
                .captureToImage().toPixelMap()
            assertEquals("A paused Now Playing waveform should remain visually still", 0, changedPixelCount(pausedImage, stillPausedImage))

            composeRule.onNodeWithTag("play_pause_fab").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                playback.isPlaying.value && playback.visualizerFrame.value !== pausedFrame
            }
            val firstResumedFrame = playback.visualizerFrame.value
            var resumedPixelChanges = 0
            val redrawDeadline = SystemClock.elapsedRealtime() + 5_000L
            while (resumedPixelChanges <= 100 && SystemClock.elapsedRealtime() < redrawDeadline) {
                val resumedImage = waveformNode.captureToImage().toPixelMap()
                resumedPixelChanges = changedPixelCount(pausedImage, resumedImage)
                if (resumedPixelChanges <= 100) Thread.sleep(100L)
            }
            val resumedFrame = playback.visualizerFrame.value
            val waveformDelta = maxWaveformDelta(pausedFrame.waveform, resumedFrame.waveform)
            Log.i(
                TAG,
                "PLAYBACK_MANAGER_WAVEFORM_RESUMED pixelChanges=$resumedPixelChanges " +
                    "frameDelta=$waveformDelta progressAtPauseMs=$progressAtPause " +
                    "progressAfterResumeMs=${playback.progress.value} " +
                    "firstFrameChanged=${firstResumedFrame !== pausedFrame}"
            )
            assertTrue("Resuming through the Now Playing control should redraw the waveform", resumedPixelChanges > 100)
        } finally {
            composeRule.runOnUiThread { playback.pause() }
            composeRule.waitUntil(timeoutMillis = 5_000L) { !playback.isPlaying.value }
            try {
                if (composeRule.onAllNodesWithTag("collapse_button").fetchSemanticsNodes().isNotEmpty()) {
                    composeRule.onNodeWithTag("collapse_button").performClick()
                }
            } finally {
                try {
                    composeRule.runOnUiThread {
                        playback.replaceQueueForQa(originalQueue, originalIndex)
                        playback.seekTo(originalProgress)
                    }
                    composeRule.waitUntil(timeoutMillis = 5_000L) {
                        playback.queue.value == originalQueue &&
                            playback.currentIndex.value == originalIndex &&
                            playback.progress.value == originalProgress
                    }
                    Log.i(
                        TAG,
                        "QA_PLAYBACK_RESTORED queueCount=${playback.queue.value.size} " +
                            "currentIndex=${playback.currentIndex.value} progressMs=${playback.progress.value}"
                    )
                    runBlocking {
                        dao.savePlaybackState(
                            PlaybackStateEntity(
                                queueJson = Json.encodeToString(originalQueue),
                                currentIndex = originalIndex,
                                positionMs = originalProgress,
                                shuffleEnabled = originalShuffle,
                                repeatMode = originalRepeat
                            )
                        )
                    }
                } finally {
                    runBlocking {
                        dao.deleteRecentTrackByKey(TEST_TRACK_RATING_KEY)
                        dao.deleteListeningHistoryByKey(TEST_TRACK_RATING_KEY)
                    }
                    fixture.delete()
                }
            }
        }
    }

    private fun changedPixelCount(before: PixelMap, after: PixelMap): Int {
        var changed = 0
        for (y in 0 until minOf(before.height, after.height)) {
            for (x in 0 until minOf(before.width, after.width)) {
                if (before[x, y] != after[x, y]) changed++
            }
        }
        return changed
    }

    private fun maxWaveformDelta(before: FloatArray, after: FloatArray): Float =
        before.indices.maxOfOrNull { index -> kotlin.math.abs(before[index] - after[index]) } ?: 0f

    private fun waitForVisualMode(
        modeButton: androidx.compose.ui.test.SemanticsNodeInteraction,
        modeLabelResource: Int
    ) {
        val expectedDescription = composeRule.activity.getString(
            R.string.player_change_visual_mode_accessibility,
            composeRule.activity.getString(modeLabelResource)
        )
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            modeButton.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
                .contains(expectedDescription)
        }
    }

    private fun writeToneWav(file: File, seconds: Int) {
        val sampleRateHz = 8_000
        val sampleCount = sampleRateHz * seconds
        val pcm = ByteBuffer.allocate(sampleCount * Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleCount) { index ->
            val secondsFromStart = index.toDouble() / sampleRateHz
            val phase = 2.0 * PI * (220.0 * secondsFromStart + 40.0 * secondsFromStart * secondsFromStart)
            val envelope = 0.5 + 0.5 * sin(2.0 * PI * 1.3 * secondsFromStart)
            pcm.putShort((sin(phase) * envelope * 8_000.0).toInt().toShort())
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

    private companion object {
        const val TAG = "PlaybackManagerMotionQa"
        const val TEST_TRACK_RATING_KEY = "qa-playback-manager-visualizer"
    }
}
