package com.example

import com.example.data.PlexMetadata
import com.example.data.calculateAlbumPlayStats
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumPlayStatsTest {
    @Test
    fun `album play count and playtime use local track history`() {
        val tracks = listOf(
            PlexMetadata(
                ratingKey = "track-1",
                title = "One",
                duration = 180_000L,
                viewCount = 2
            ),
            PlexMetadata(
                ratingKey = "track-2",
                title = "Two",
                duration = 210_000L,
                viewCount = 1
            )
        )

        val stats = calculateAlbumPlayStats(
            tracks = tracks,
            localPlayCounts = mapOf("track-1" to 3, "track-2" to 2)
        )

        assertEquals(5, stats.totalPlays)
        assertEquals(960_000L, stats.totalPlaytimeMs)
    }

    @Test
    fun `unplayed album reports zero playtime`() {
        val stats = calculateAlbumPlayStats(
            tracks = listOf(
                PlexMetadata(ratingKey = "track-1", title = "One", duration = 245_000L),
                PlexMetadata(ratingKey = "track-2", title = "Two", duration = 125_000L)
            )
        )

        assertEquals(0, stats.totalPlays)
        assertEquals(0L, stats.totalPlaytimeMs)
    }
}
