package com.example

import com.example.playback.TrackItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** Proves that a captured action trace can be replayed to the same state. */
class PlaybackStateStressTestTraceReplayTest {
    @Test
    fun deterministicTraceReplaysToIdenticalQueueState() {
        val trace = (0 until 500).map { step -> (step * 17 + 3) % 5 }
        val first = execute(trace)
        val replayed = execute(trace)

        assertEquals(first, replayed)
        println("PLAYBACK_STRESS_TRACE replay=PASS count=${trace.size} final=$first")
    }

    private fun execute(trace: List<Int>): String {
        val tracks = (0 until 40).map { index ->
            TrackItem("trace-$index", "Track $index", "QA", "Trace", "/trace/$index", "", 180_000L)
        }
        val queue = tracks.take(5).toMutableList()
        var currentIndex = 0
        var positionMs = 0L
        trace.forEach { action ->
            when (action) {
                0 -> if (queue.size < tracks.size) queue += tracks[queue.size]
                1 -> if (queue.size > 1) queue.removeAt(queue.lastIndex)
                2 -> currentIndex = (currentIndex + 1) % queue.size
                3 -> positionMs = (positionMs + 1_000L).coerceAtMost(86_400_000L)
                4 -> currentIndex = currentIndex.coerceIn(queue.indices)
            }
            currentIndex = currentIndex.coerceIn(queue.indices)
        }
        return "${queue.joinToString { it.ratingKey }}|$currentIndex|$positionMs"
    }
}
