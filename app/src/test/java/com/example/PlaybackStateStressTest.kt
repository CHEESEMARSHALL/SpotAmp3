package com.example

import com.example.data.PlaybackStateEntity
import com.example.playback.PlaybackStateRestoration
import com.example.playback.TrackItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaybackStateStressTest {
    @Test
    fun deterministicPlaybackStateMachine_preservesInvariants() {
        val seed = (System.getProperty("spotamp.stress.seed") ?: System.getenv("SPOTAMP_STRESS_SEED"))?.toIntOrNull() ?: 847229173
        val actions = (System.getProperty("spotamp.stress.actions") ?: System.getenv("SPOTAMP_STRESS_ACTIONS"))?.toIntOrNull()?.coerceIn(1, 10_000) ?: 500
        val random = Random(seed)
        val tracks = (0 until 40).map { index ->
            TrackItem("track-$index", "Track $index", "Artist", "Album", "/track/$index", "", 180_000)
        }
        var queue = tracks.take(5)
        var currentIndex = 0
        var positionMs = 0L

        repeat(actions) {
            when (random.nextInt(5)) {
                0 -> queue = (queue + tracks.random(random)).distinctBy { it.ratingKey }.take(40)
                1 -> if (queue.size > 1) queue = queue.toMutableList().also { it.removeAt(random.nextInt(it.size)) }
                2 -> if (queue.isNotEmpty()) currentIndex = random.nextInt(queue.size)
                3 -> positionMs = (positionMs + random.nextLong(0, 60_000)).coerceAtMost(86_400_000L)
                4 -> if (queue.isNotEmpty()) {
                    val state = PlaybackStateEntity(queueJson = Json.encodeToString(queue), currentIndex = currentIndex, positionMs = positionMs, shuffleEnabled = false, repeatMode = 0)
                    val restored = PlaybackStateRestoration.restore(state) { random.nextBoolean() }
                    if (restored != null) {
                        assertTrue(restored.queue.isNotEmpty())
                        assertTrue(restored.currentIndex in restored.queue.indices)
                        assertTrue(restored.positionMs >= 0L)
                    }
                }
            }
            if (queue.isNotEmpty()) currentIndex = currentIndex.coerceIn(queue.indices)
            assertTrue(currentIndex >= 0)
            assertTrue(positionMs >= 0L)
        }
        println("PLAYBACK_STRESS seed=$seed actions=$actions finalQueueSize=${queue.size}")
    }
}
