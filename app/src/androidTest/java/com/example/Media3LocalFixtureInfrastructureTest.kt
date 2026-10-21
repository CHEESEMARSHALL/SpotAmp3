package com.example

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises Media3 local-file infrastructure with a disposable tone; not product playback confidence. */
@RunWith(AndroidJUnit4::class)
class Media3LocalFixtureInfrastructureTest {
    @Test
    fun unsupportedLocalAudioShowsNonRetryableRecoveryOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "qa-unsupported-audio.bin")
        fixture.writeText("This is not a supported audio file.")
        val track = TrackItem(
            ratingKey = "qa-unsupported-audio",
            title = "QA unsupported audio",
            artist = "QA",
            album = "Generated fixtures",
            key = "/qa/unsupported-audio.bin",
            thumb = "",
            duration = 1_000L,
            localPath = fixture.absolutePath
        )
        val manager = PlaybackManager.getInstance(context)

        try {
            instrumentation.runOnMainSync {
                manager.clearPlaybackError()
                manager.playTrack(track, listOf(track))
            }

            val deadline = System.currentTimeMillis() + 10_000L
            while (System.currentTimeMillis() < deadline && manager.playbackError.value == null) {
                Thread.sleep(100L)
            }

            assertEquals(context.getString(R.string.playback_audio_unplayable), manager.playbackError.value)
            assertFalse(manager.playbackErrorRetryable.value)
            assertFalse(manager.playbackErrorOfflineTrackMissing.value)
        } finally {
            instrumentation.runOnMainSync { manager.release() }
            fixture.delete()
        }
    }

    @Test
    fun localFixtureReachesMedia3PlayingState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "qa-generated-tone.wav")
        writeWav(fixture)

        val track = TrackItem(
            ratingKey = "qa-generated-tone",
            title = "QA generated tone",
            artist = "QA",
            album = "Generated fixtures",
            key = "/qa/generated-tone.wav",
            thumb = "",
            duration = 1_000L,
            localPath = fixture.absolutePath
        )
        val manager = PlaybackManager.getInstance(context)
        instrumentation.runOnMainSync { manager.playTrack(track, listOf(track)) }

        val deadline = System.currentTimeMillis() + 10_000L
        var observed = false
        while (System.currentTimeMillis() < deadline) {
            if (manager.duration.value > 0L && manager.isPlaying.value) {
                observed = true
                break
            }
            Thread.sleep(100L)
        }

        instrumentation.runOnMainSync {
            manager.pause()
            manager.release()
        }
        fixture.delete()
        assertTrue("Generated WAV never reached the playing state", observed)
    }

    private fun writeWav(file: File) {
        val sampleRate = 8_000
        val seconds = 1
        val samples = sampleRate * seconds
        val pcm = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val sample = (kotlin.math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 8_000).toInt().toShort()
            pcm[i * 2] = sample.toByte()
            pcm[i * 2 + 1] = (sample.toInt() shr 8).toByte()
        }
        FileOutputStream(file).use { output ->
            fun intLE(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
            fun shortLE(value: Short) = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
            output.write("RIFF".toByteArray())
            output.write(intLE(36 + pcm.size))
            output.write("WAVEfmt ".toByteArray())
            output.write(intLE(16))
            output.write(shortLE(1))
            output.write(shortLE(1))
            output.write(intLE(sampleRate))
            output.write(intLE(sampleRate * 2))
            output.write(shortLE(2))
            output.write(shortLE(16))
            output.write("data".toByteArray())
            output.write(intLE(pcm.size))
            output.write(pcm)
        }
    }
    @Test
    fun realMedia3ControlsPreserveQueueAndPlaybackState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val firstFixture = File(context.cacheDir, "qa-controls-first.wav")
        val secondFixture = File(context.cacheDir, "qa-controls-second.wav")
        writeWav(firstFixture)
        writeWav(secondFixture)
        val first = TrackItem("qa-controls-first", "QA controls first", "QA", "Generated fixtures", "/qa/first.wav", "", 1_000L, firstFixture.absolutePath)
        val second = TrackItem("qa-controls-second", "QA controls second", "QA", "Generated fixtures", "/qa/second.wav", "", 1_000L, secondFixture.absolutePath)
        val manager = PlaybackManager.getInstance(context)

        fun waitFor(condition: () -> Boolean): Boolean {
            val deadline = System.currentTimeMillis() + 10_000L
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return true
                Thread.sleep(100L)
            }
            return false
        }

        try {
            instrumentation.runOnMainSync { manager.playTrack(first, listOf(first, second)) }
            assertTrue("First fixture never reached the playing state", waitFor { manager.isPlaying.value })

            manager.pause()
            assertTrue("Pause command did not stop playback", waitFor { !manager.isPlaying.value })
            manager.seekTo(250L)
            assertTrue("Seek command did not update progress", waitFor { manager.progress.value >= 200L })
            manager.play()
            assertTrue("Resume command did not restart playback", waitFor { manager.isPlaying.value })

            manager.next()
            assertTrue("Next command did not advance to the second track", waitFor { manager.currentTrack.value?.ratingKey == second.ratingKey })
            manager.prev()
            assertTrue("Previous command did not return to the first track", waitFor { manager.currentTrack.value?.ratingKey == first.ratingKey })

            manager.toggleShuffle()
            assertTrue(manager.shuffleModeEnabled.value)
            manager.toggleShuffle()
            assertFalse(manager.shuffleModeEnabled.value)
            manager.toggleRepeat()
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_ALL, manager.repeatMode.value)
            manager.toggleRepeat()
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_ONE, manager.repeatMode.value)
            manager.toggleRepeat()
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, manager.repeatMode.value)
        } finally {
            instrumentation.runOnMainSync {
                manager.pause()
                manager.release()
            }
            firstFixture.delete()
            secondFixture.delete()
        }
    }
    @Test
    fun deletingDownloadedFileWhilePlayingRemovesFileAndIndex() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "qa-delete-while-playing.wav")
        writeWav(fixture)
        val repository = com.example.data.MusicRepository(context)
        val track = TrackItem(
            ratingKey = "qa-delete-while-playing",
            title = "QA delete while playing",
            artist = "QA",
            album = "Generated fixtures",
            key = "/qa/delete-while-playing.wav",
            thumb = "",
            duration = 1_000L,
            localPath = fixture.absolutePath
        )
        val manager = PlaybackManager.getInstance(context)

        try {
            kotlinx.coroutines.runBlocking {
                repository.addDownloadedTrack(track, fixture.length(), fixture.absolutePath)
            }
            instrumentation.runOnMainSync { manager.playTrack(track, listOf(track)) }

            val deadline = System.currentTimeMillis() + 10_000L
            var observed = false
            while (System.currentTimeMillis() < deadline) {
                if (manager.duration.value > 0L && manager.isPlaying.value) {
                    observed = true
                    break
                }
                Thread.sleep(100L)
            }
            assertTrue("Downloaded fixture never reached the playing state", observed)

            kotlinx.coroutines.runBlocking {
                repository.deleteDownloadedTrack(track.ratingKey)
            }

            assertTrue("The downloaded file was not deleted", !fixture.exists())
            kotlinx.coroutines.runBlocking {
                assertTrue(repository.downloadedTracks.first().none { it.ratingKey == track.ratingKey })
            }
        } finally {
            instrumentation.runOnMainSync {
                manager.pause()
                manager.release()
            }
            kotlinx.coroutines.runBlocking {
                repository.deleteDownloadedTrack(track.ratingKey)
            }
            fixture.delete()
        }
    }
}
