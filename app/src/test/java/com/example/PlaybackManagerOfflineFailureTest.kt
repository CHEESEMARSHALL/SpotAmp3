package com.example

import android.os.Looper
import com.example.data.PlexSettingsManager
import com.example.playback.PlaybackManager
import com.example.playback.MusicPlaybackService
import com.example.playback.TrackItem
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackManagerOfflineFailureTest {
    @Test
    fun downloadedFileIsSelectedByProductionUriResolverInOfflineMode() {
        val applicationContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        PlaybackManager.getInstance(applicationContext).release()
        shadowOf(Looper.getMainLooper()).idle()
        val settings = PlexSettingsManager(applicationContext)
        settings.offlineOnly = true
        val controller = Robolectric.buildService(MusicPlaybackService::class.java)
        val context = controller.create().get()
        val manager = PlaybackManager.getInstance(context)
        val musicDirectory = context.getExternalFilesDir("music") ?: File(context.filesDir, "music")
        val downloadedFile = File(musicDirectory, "downloaded-offline.mp3")
        musicDirectory.mkdirs()
        downloadedFile.writeBytes(byteArrayOf(1, 2, 3))

        try {
            val selectedUri = manager.mediaUri(
                track("downloaded-offline").copy(key = "https://example.invalid/remote-audio"),
                "https://example.invalid"
            )
            assertEquals(downloadedFile.toURI().toString(), selectedUri)
        } finally {
            downloadedFile.delete()
            settings.offlineOnly = false
            manager.release()
            shadowOf(Looper.getMainLooper()).idle()
            controller.destroy()
        }
    }

    @Test
    fun missingOfflineFilesShowRecoveryAndDoNotMutateQueues() {
        val applicationContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        PlaybackManager.getInstance(applicationContext).release()
        shadowOf(Looper.getMainLooper()).idle()
        val settings = PlexSettingsManager(applicationContext)
        settings.offlineOnly = true
        val controller = Robolectric.buildService(MusicPlaybackService::class.java)
        val context = controller.create().get()
        val manager = PlaybackManager.getInstance(context)

        try {
            manager.clearQueue()
            shadowOf(Looper.getMainLooper()).idle()
            manager.addToQueue(track("missing-for-queue"))
            assertTrue(manager.queue.value.isEmpty())
            assertEquals(context.getString(R.string.playback_offline_track_missing), manager.playbackError.value)
            assertFalse(manager.playbackErrorRetryable.value)
            assertTrue(manager.playbackErrorOfflineTrackMissing.value)

            manager.clearPlaybackError()
            assertFalse(manager.playbackErrorOfflineTrackMissing.value)
            manager.playQueue(listOf(track("missing-for-play")))
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(context.getString(R.string.playback_offline_track_missing), manager.playbackError.value)
            assertTrue(manager.playbackErrorRetryable.value)
            assertTrue(manager.playbackErrorOfflineTrackMissing.value)
            assertFalse(manager.isPlaying.value)
        } finally {
            settings.offlineOnly = false
            manager.release()
            shadowOf(Looper.getMainLooper()).idle()
            controller.destroy()
        }
    }

    private fun track(id: String) = TrackItem(
        ratingKey = id,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        key = "/library/parts/$id/file.mp3",
        thumb = "",
        duration = 180_000L
    )
}
