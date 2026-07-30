package com.example

import com.example.data.PlexPagination
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.runBlocking

class PlexPaginationTest {
    @Test
    fun collectsFullPagesAndStopsAtTheFirstShortPage() = runBlocking {
        val calls = mutableListOf<Pair<Int, Int>>()
        val result = PlexPagination.collect(pageSize = 2) { offset, limit ->
            calls += offset to limit
            when (offset) {
                0 -> listOf("a", "b")
                2 -> listOf("c")
                else -> error("unexpected page")
            }
        }

        assertEquals(listOf("a", "b", "c"), result)
        assertEquals(listOf(0 to 2, 2 to 2), calls)
    }

    @Test
    fun rejectsInvalidPageSize() = runBlocking {
        try {
            PlexPagination.collect(pageSize = 0) { _, _ -> emptyList<String>() }
            throw AssertionError("Expected invalid page size to fail")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
