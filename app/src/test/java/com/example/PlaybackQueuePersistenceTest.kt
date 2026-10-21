package com.example

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.MusicDatabase
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackQueuePersistenceTest {
    @Test
    fun queueEditsPersistAndRestoreThePausedActiveSession() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var manager = PlaybackManager.getInstance(context)
        try {
            manager.setCredentials("http://127.0.0.1:32400", "qa-token")
            manager.setMusicDao(database.musicDao())
            manager.replaceQueueForQa(listOf(track("one"), track("two")))
            awaitState(database, expectedQueueKeys = listOf("one", "two"))

            manager.playNext(track("next"))
            manager.addToQueue(track("last"))
            manager.moveQueueItem(from = 3, to = 2)
            val expectedQueue = listOf("one", "next", "last", "two")
            assertEquals(expectedQueue, manager.queue.value.map { it.ratingKey })
            val saved = awaitState(database, expectedQueueKeys = expectedQueue)
            assertEquals(expectedQueue, Json.decodeFromString<List<TrackItem>>(saved.queueJson).map { it.ratingKey })
            assertEquals("one", Json.decodeFromString<List<TrackItem>>(saved.queueJson)[saved.currentIndex].ratingKey)
            assertFalse(saved.playWhenReady)

            manager.release()
            shadowOf(Looper.getMainLooper()).idle()
            manager = PlaybackManager.getInstance(context)
            manager.setCredentials("http://127.0.0.1:32400", "qa-token")
            manager.setMusicDao(database.musicDao())
            withTimeout(5_000) {
                while (manager.queue.value.map { it.ratingKey } != expectedQueue) delay(10)
            }
            assertEquals(expectedQueue, manager.queue.value.map { it.ratingKey })
            assertEquals("one", manager.currentTrack.value?.ratingKey)
            assertFalse(manager.isPlaying.value)
        } finally {
            manager.release()
            shadowOf(Looper.getMainLooper()).idle()
            database.close()
        }
    }

    private suspend fun awaitState(database: MusicDatabase, expectedQueueKeys: List<String>) = withTimeout(5_000) {
        while (true) {
            val state = database.musicDao().getPlaybackState()
            if (state != null && Json.decodeFromString<List<TrackItem>>(state.queueJson).map { it.ratingKey } == expectedQueueKeys) {
                return@withTimeout state
            }
            delay(10)
        }
        error("unreachable")
    }

    private fun track(id: String) = TrackItem(id, "Track $id", "Artist", "Album", "/$id", "", 1_000)
}
