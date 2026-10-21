package com.example

import com.example.data.PlexMetadata
import com.example.data.rankArtistTracksByListens
import com.example.data.sortArtistAlbumsChronologically
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistDetailOrderingTest {
    @Test
    fun `top tracks follow local listens instead of metadata view count`() {
        val tracks = listOf(
            PlexMetadata(ratingKey = "track-a", title = "A", index = 1, viewCount = 99),
            PlexMetadata(ratingKey = "track-b", title = "B", index = 2, viewCount = 0),
            PlexMetadata(ratingKey = "track-c", title = "C", index = 3, viewCount = 50)
        )

        val ranked = rankArtistTracksByListens(
            tracks = tracks,
            localPlayCounts = mapOf("track-a" to 1, "track-b" to 4, "track-c" to 2),
            limit = 2
        )

        assertEquals(listOf("track-b", "track-c"), ranked.map { it.ratingKey })
    }

    @Test
    fun `artist albums sort newest first with stable tie breakers`() {
        val albums = listOf(
            PlexMetadata(ratingKey = "old", title = "Old", year = 2019),
            PlexMetadata(ratingKey = "new", title = "New", year = 2024),
            PlexMetadata(ratingKey = "unknown", title = "Unknown"),
            PlexMetadata(ratingKey = "same-old", title = "Same Old", year = 2024, addedAt = 10),
            PlexMetadata(ratingKey = "same-new", title = "Same New", year = 2024, addedAt = 20)
        )

        assertEquals(
            listOf("same-new", "same-old", "new", "old", "unknown"),
            sortArtistAlbumsChronologically(albums).map { it.ratingKey }
        )
    }
}
