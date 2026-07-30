package com.example

import com.example.data.CachedTrack
import com.example.data.LocalCollectionSuggestionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCollectionSuggestionServiceTest {
    @Test
    fun suggestsOnlyDeterministicMetadataGroups() {
        val tracks = (1..3).map { index ->
            CachedTrack("$index", "Track $index", "Artist", "Album", "", "", 1000, genres = "Ambient|Electronic")
        }

        val result = LocalCollectionSuggestionService().suggest(tracks)

        assertEquals(listOf("Ambient", "Electronic"), result.map { it.name })
        assertEquals(listOf("1", "2", "3"), result.first().ratingKeys)
        assertTrue(result.all { it.reason.contains("cached Plex tracks") })
    }
}
