package com.example

import com.example.data.HomeFeedSource
import com.example.data.RecentTrack
import com.example.data.RecommendedStation
import com.example.data.RadioType
import com.example.data.fallbackHomeFeedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedFallbackTest {
    @Test
    fun `local shelf failure keeps cached-library stations and recent history`() {
        val stations = listOf(
            RecommendedStation(
                id = "saved",
                title = "Saved music",
                subtitle = "From your library",
                type = RadioType.LIBRARY_RADIO,
                gradientColors = listOf(0L, 1L)
            )
        )
        val history = (1..12).map { index ->
            RecentTrack(
                ratingKey = "track-$index",
                title = "Track $index",
                artist = "Artist",
                album = "Album",
                key = "",
                thumb = "",
                timestamp = index.toLong()
            )
        }

        val fallback = fallbackHomeFeedState(
            recentlyAdded = emptyList(),
            history = history,
            stations = stations
        )

        assertEquals(stations, fallback.stations)
        assertEquals(10, fallback.history.size)
        assertTrue(fallback.recentPlays.isEmpty())
        assertEquals(HomeFeedSource.PLEX, fallback.source)
    }
}
