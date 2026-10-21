package com.example.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackListenDurationAccumulatorTest {
    @Test
    fun countsOnlyActivePlaybackAndExcludesPausedIntervals() {
        val accumulator = PlaybackListenDurationAccumulator()

        assertEquals(0L, accumulator.sample(1_000L, isPlaying = true))
        assertEquals(250L, accumulator.sample(1_250L, isPlaying = true))
        assertEquals(250L, accumulator.sample(1_500L, isPlaying = false))
        assertEquals(250L, accumulator.sample(8_000L, isPlaying = true))
        assertEquals(500L, accumulator.sample(8_250L, isPlaying = true))
    }

    @Test
    fun resetSampleExcludesTimeAcrossSeekOrPlaybackDiscontinuity() {
        val accumulator = PlaybackListenDurationAccumulator()

        accumulator.sample(4_000L, isPlaying = true)
        accumulator.sample(4_250L, isPlaying = true)
        accumulator.resetSample()
        assertEquals(250L, accumulator.sample(9_000L, isPlaying = true))
        assertEquals(500L, accumulator.sample(9_250L, isPlaying = true))
    }
}
