package com.example

import com.example.data.CachedTrack
import com.example.data.LibraryCleanupSuggestionService
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCleanupSuggestionServiceTest {
    @Test
    fun `suggestions are review-only and limited to real metadata issues`() {
        val tracks = listOf(
            CachedTrack("bad", "Song", "", "", "/bad", "", 0),
            CachedTrack("good", "Song", "Artist", "Album", "/good", "", 180_000)
        )

        val suggestions = LibraryCleanupSuggestionService().suggest(tracks)

        assertEquals(listOf("Missing artist", "Missing album", "Missing duration"), suggestions.map { it.issue })
        assertEquals(listOf("bad", "bad", "bad"), suggestions.map { it.ratingKey })
    }
}
