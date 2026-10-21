package com.example

import com.example.data.PlaybackStateEntity
import com.example.playback.PlaybackStateRestoration
import com.example.playback.TrackItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStateRestorationBenchmarkTest {
    @Test
    fun restore_throughputBaseline() {
        val queue = (0 until 100).map { index ->
            TrackItem("track-$index", "Track $index", "Artist", "Album", "/track/$index", "", 180_000)
        }
        val state = PlaybackStateEntity(
            queueJson = Json.encodeToString(queue),
            currentIndex = 50,
            positionMs = 12_000,
            shuffleEnabled = false,
            repeatMode = 0
        )
        val iterations = 1_000
        val startNanos = System.nanoTime()
        repeat(iterations) {
            assertEquals(50, PlaybackStateRestoration.restore(state)!!.currentIndex)
        }
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0
        println("PLAYBACK_RESTORE_BENCHMARK iterations=$iterations queueSize=${queue.size} elapsedMs=$elapsedMs")
    }
}
