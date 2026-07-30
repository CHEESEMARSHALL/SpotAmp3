package com.example

import com.example.data.PlaybackStateEntity
import com.example.playback.PlaybackStateRestoration
import com.example.playback.TrackItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStateRestorationTest {
    @Test
    fun restoresQueueIndexPositionAndModesWhileDroppingMissingLocalFiles() {
        val queue = listOf(
            TrackItem("remote", "Remote", "Artist", "Album", "/stream", "", 1000),
            TrackItem("local", "Local", "Artist", "Album", "/stream", "", 1000, "missing.mp3")
        )
        val state = PlaybackStateEntity(queueJson = Json.encodeToString(queue), currentIndex = 99, positionMs = -20L, shuffleEnabled = true, repeatMode = 2)

        val restored = PlaybackStateRestoration.restore(state) { false }

        assertEquals(listOf("remote"), restored?.queue?.map { it.ratingKey })
        assertEquals(0, restored?.currentIndex)
        assertEquals(0L, restored?.positionMs)
        assertEquals(true, restored?.shuffleEnabled)
        assertEquals(2, restored?.repeatMode)
    }

    @Test
    fun returnsNullWhenPersistedQueueHasNoUsableTracks() {
        val queue = listOf(TrackItem("local", "Local", "Artist", "Album", "/stream", "", 1000, "missing.mp3"))
        val state = PlaybackStateEntity(queueJson = Json.encodeToString(queue), currentIndex = 0, positionMs = 0L, shuffleEnabled = false, repeatMode = 0)

        assertNull(PlaybackStateRestoration.restore(state) { false })
    }
}
