package com.example

import com.example.data.PlaybackStateEntity
import com.example.playback.LyricsRequestGate
import com.example.playback.PlaybackStateRestoration
import com.example.playback.TrackItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateRestorationRegressionTest {
    @Test
    fun restore_preservesNonZeroCurrentQueueOccurrenceAndPosition() {
        val queue = listOf(
            TrackItem("a", "A", "Artist", "Album", "/a", "", 1000),
            TrackItem("duplicate", "First", "Artist", "Album", "/first", "", 1000),
            TrackItem("duplicate", "Second", "Artist", "Album", "/second", "", 1000),
            TrackItem("d", "D", "Artist", "Album", "/d", "", 1000),
            TrackItem("e", "E", "Artist", "Album", "/e", "", 1000)
        )
        val state = PlaybackStateEntity(
            queueJson = Json.encodeToString(queue),
            currentIndex = 3,
            positionMs = 42_000,
            shuffleEnabled = true,
            repeatMode = 2,
            playWhenReady = true
        )

        val restored = PlaybackStateRestoration.restore(state) { true }

        assertEquals(listOf("a", "duplicate", "duplicate", "d", "e"), restored!!.queue.map { it.ratingKey })
        assertEquals("d", restored.queue[restored.currentIndex].ratingKey)
        assertEquals(3, restored.currentIndex)
        assertEquals(42_000, restored.positionMs)
        assertEquals(true, restored.shuffleEnabled)
        assertEquals(2, restored.repeatMode)
        assertEquals(true, restored.playWhenReady)
    }

    @Test
    fun restore_selectsNextAvailableOccurrenceWhenCurrentFileIsMissing() {
        val queue = listOf(
            TrackItem("a", "A", "Artist", "Album", "/a", "", 1000),
            TrackItem("b", "B", "Artist", "Album", "/b", "", 1000, "missing-b.mp3"),
            TrackItem("c", "C", "Artist", "Album", "/c", "", 1000)
        )
        val state = PlaybackStateEntity(queueJson = Json.encodeToString(queue), currentIndex = 1, positionMs = 10, shuffleEnabled = false, repeatMode = 0)

        val restored = PlaybackStateRestoration.restore(state) { false }

        assertEquals(listOf("a", "c"), restored!!.queue.map { it.ratingKey })
        assertEquals(1, restored.currentIndex)
        assertEquals("c", restored.queue[restored.currentIndex].ratingKey)
    }

    @Test
    fun lyricsRequestGateRejectsStaleResponses() {
        val gate = LyricsRequestGate()
        val first = gate.begin()
        val second = gate.begin()

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }
}
