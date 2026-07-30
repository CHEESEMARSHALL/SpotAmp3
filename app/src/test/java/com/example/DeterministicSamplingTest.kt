package com.example

import com.example.data.deterministicTrackSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicSamplingTest {
    @Test
    fun `sampling is stable and spans the full candidate list`() {
        val input = (1..1000).toList()
        val first = deterministicTrackSample(input, 10)
        val second = deterministicTrackSample(input, 10)

        assertEquals(first, second)
        assertEquals(10, first.size)
        assertEquals(1, first.first())
        assertEquals(1000, first.last())
        assertTrue(first.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `sampling handles empty and oversized limits`() {
        assertEquals(emptyList<Int>(), deterministicTrackSample(emptyList<Int>(), 10))
        assertEquals(listOf(1, 2, 3), deterministicTrackSample(listOf(1, 2, 3), 10))
        assertEquals(listOf(1), deterministicTrackSample(listOf(1, 2, 3), 1))
    }
}
