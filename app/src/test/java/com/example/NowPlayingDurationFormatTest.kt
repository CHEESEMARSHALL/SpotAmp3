package com.example

import com.example.ui.formatDuration
import com.example.ui.formatRemainingDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingDurationFormatTest {
    @Test
    fun `duration formatting clamps negative progress`() {
        assertEquals("0:00", formatDuration(-1_000L))
    }

    @Test
    fun `duration formatting uses unbounded minutes`() {
        assertEquals("125:07", formatDuration(7_507_000L))
    }

    @Test
    fun `remaining duration is unknown when total duration is unavailable`() {
        assertEquals("--:--", formatRemainingDuration(0L, 0L))
        assertEquals("--:--", formatRemainingDuration(4_000L, -1L))
    }

    @Test
    fun `remaining duration never becomes negative zero or negative`() {
        assertEquals("0:00", formatRemainingDuration(99_500L, 100_000L))
        assertEquals("0:00", formatRemainingDuration(120_000L, 100_000L))
    }

    @Test
    fun `remaining duration clamps to the valid track range`() {
        assertEquals("-1:30", formatRemainingDuration(30_000L, 120_000L))
    }
}
