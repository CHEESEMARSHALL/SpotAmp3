package com.example

import com.example.data.CachedTrack
import com.example.data.RadioRequest
import com.example.data.RadioService
import com.example.data.RadioType
import com.example.data.RecentTrack
import com.example.data.deterministicDailyIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioServiceTest {
    @Test
    fun `artist radio only returns seeded artist tracks`() {
        val tracks = listOf(
            CachedTrack("1", "A", "Artist One", "Album", "/1", "", 1),
            CachedTrack("2", "B", "Artist Two", "Album", "/2", "", 1)
        )
        val result = RadioService().generate(RadioRequest(RadioType.ARTIST_RADIO, seedArtist = "Artist One"), tracks)
        assertEquals(listOf("1"), result.map { it.ratingKey })
    }

    @Test
    fun `style radio only returns tracks with matching Plex genre metadata`() {
        val tracks = listOf(
            CachedTrack("rock", "Rock", "Artist One", "Album", "/rock", "", 1, genres = "Rock"),
            CachedTrack("jazz", "Jazz", "Artist Two", "Album", "/jazz", "", 1, genres = "Jazz"),
            CachedTrack("album-only", "Album", "Artist Three", "Rock", "/album", "", 1)
        )

        val result = RadioService().generate(
            RadioRequest(RadioType.STYLE_RADIO, genre = "rock", trackCount = 10, recentCooldownDays = 0),
            tracks
        )

        assertEquals(listOf("rock"), result.map { it.ratingKey })
    }

    @Test
    fun `collection radio requires an exact Plex collection token`() {
        val tracks = listOf(
            CachedTrack("one", "One", "Artist One", "Album", "/one", "", 1, collections = "Road Trip|Favorites"),
            CachedTrack("two", "Two", "Artist Two", "Album", "/two", "", 1, collections = "Road Trip Essentials"),
            CachedTrack("three", "Three", "Artist Three", "Album", "/three", "", 1, collections = "Other")
        )

        val result = RadioService().generate(
            RadioRequest(RadioType.COLLECTION_RADIO, collection = "Road Trip", trackCount = 10, recentCooldownDays = 0),
            tracks
        )

        assertEquals(listOf("one"), result.map { it.ratingKey })
    }

    @Test
    fun `deep cuts rank least played cached tracks first`() {
        val tracks = listOf(
            CachedTrack("played", "Played", "Artist One", "Album", "/played", "", 1, playCount = 20),
            CachedTrack("rare", "Rare", "Artist Two", "Album", "/rare", "", 1, playCount = 0),
            CachedTrack("middle", "Middle", "Artist Three", "Album", "/middle", "", 1, playCount = 5)
        )

        val result = RadioService().generate(
            RadioRequest(RadioType.DEEP_CUTS, trackCount = 10, recentCooldownDays = 0),
            tracks
        )

        assertEquals(listOf("rare", "middle", "played"), result.map { it.ratingKey })
    }

    @Test
    fun `recently added radio ranks by plex added time`() {
        val tracks = listOf(
            CachedTrack("old", "Old", "Artist", "Album", "/old", "", 1, addedAt = 100L),
            CachedTrack("new", "New", "Artist", "Album", "/new", "", 1, addedAt = 200L)
        )
        val result = RadioService().generate(
            RadioRequest(RadioType.RECENTLY_ADDED_RADIO, trackCount = 2, recentCooldownDays = 0),
            tracks
        )
        assertEquals(listOf("new", "old"), result.map { it.ratingKey })
    }

    @Test
    fun `constrained radio never invents tracks and caps artists`() {
        val tracks = listOf(
            CachedTrack("1", "A", "Artist One", "Album", "/1", "", 1, playCount = 3),
            CachedTrack("2", "B", "Artist One", "Album", "/2", "", 1, playCount = 2),
            CachedTrack("3", "C", "Artist Two", "Album", "/3", "", 1, playCount = 1)
        )
        val service = RadioService()

        assertEquals(emptyList<String>(), service.generate(
            RadioRequest(RadioType.ARTIST_RADIO, seedArtist = "Missing Artist"), tracks
        ).map { it.ratingKey })
        assertEquals(listOf("1", "3"), service.generate(
            RadioRequest(RadioType.LIBRARY_RADIO, trackCount = 10, maxTracksPerArtist = 1, recentCooldownDays = 0), tracks
        ).map { it.ratingKey })
    }

    @Test
    fun `daily album index is stable within a day and rotates predictably`() {
        val day = 202L * 86_400_000L
        assertEquals(2, deterministicDailyIndex(5, day + 1_000L))
        assertEquals(2, deterministicDailyIndex(5, day + 86_399_000L))
        assertEquals(3, deterministicDailyIndex(5, day + 86_400_000L))
        assertEquals(-1, deterministicDailyIndex(0, day))
    }

    @Test
    fun `on this day only returns tracks added on today's month and day`() {
        val now = java.util.Calendar.getInstance()
        val todaySeconds = now.timeInMillis / 1000L
        val sameDay = CachedTrack("today", "Today", "Artist", "Album", "/today", "", 1, addedAt = todaySeconds)
        val otherDay = CachedTrack("other", "Other", "Artist", "Album", "/other", "", 1, addedAt = todaySeconds - 86_400L)

        val result = RadioService().generate(
            RadioRequest(RadioType.ON_THIS_DAY, trackCount = 10, recentCooldownDays = 0),
            listOf(sameDay, otherDay)
        )

        assertEquals(listOf("today"), result.map { it.ratingKey })
    }

    @Test
    fun `recent cooldown uses history timestamps instead of row count`() {
        val oldTimestamp = System.currentTimeMillis() - 30L * 86_400_000L
        val tracks = listOf(
            CachedTrack("old", "Old", "Artist", "Album", "/old", "", 1),
            CachedTrack("new", "New", "Artist Two", "Album", "/new", "", 1)
        )
        val history = listOf(
            RecentTrack(ratingKey = "old", title = "Old", artist = "Artist", album = "Album", key = "/old", thumb = "", timestamp = oldTimestamp)
        )

        val result = RadioService().generate(
            RadioRequest(RadioType.LIBRARY_RADIO, trackCount = 10, recentCooldownDays = 7, maxTracksPerArtist = 10),
            tracks,
            history
        )

        assertEquals(listOf("old", "new"), result.map { it.ratingKey })
    }

    @Test
    fun `decade radio fallback when start is null`() {
        val tracks = listOf(
            CachedTrack("old", "Old", "Artist", "Album", "/old", "", 1, year = 1975),
            CachedTrack("mid", "Mid", "Artist Two", "Album", "/mid", "", 1, year = 1982),
            CachedTrack("new", "New", "Artist Three", "Album", "/new", "", 1, year = 2021)
        )
        // Earliest year is 1975, so default decade should be 1970 (1970..1979)
        val result = RadioService().generate(
            RadioRequest(RadioType.DECADE_RADIO, decadeStart = null, trackCount = 10, recentCooldownDays = 0),
            tracks
        )
        assertEquals(listOf("old"), result.map { it.ratingKey })
    }

    @Test
    fun `time travel radio sorts chronologically ascending`() {
        val tracks = listOf(
            CachedTrack("new", "New", "Artist", "Album", "/new", "", 1, year = 2021),
            CachedTrack("old", "Old", "Artist Two", "Album", "/old", "", 1, year = 1975),
            CachedTrack("mid", "Mid", "Artist Three", "Album", "/mid", "", 1, year = 1999)
        )
        val result = RadioService().generate(
            RadioRequest(RadioType.TIME_TRAVEL, decadeStart = null, trackCount = 10, recentCooldownDays = 0),
            tracks
        )
        assertEquals(listOf("old", "mid", "new"), result.map { it.ratingKey })
    }

    @Test
    fun `time travel radio filters by start decade`() {
        val tracks = listOf(
            CachedTrack("old", "Old", "Artist", "Album", "/old", "", 1, year = 1975),
            CachedTrack("mid", "Mid", "Artist Two", "Album", "/mid", "", 1, year = 1992),
            CachedTrack("new", "New", "Artist Three", "Album", "/new", "", 1, year = 2021)
        )
        val result = RadioService().generate(
            RadioRequest(RadioType.TIME_TRAVEL, decadeStart = 1990, trackCount = 10, recentCooldownDays = 0),
            tracks
        )
        assertEquals(listOf("mid", "new"), result.map { it.ratingKey })
    }
}
