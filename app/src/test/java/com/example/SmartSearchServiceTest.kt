package com.example

import com.example.data.CachedTrack
import com.example.data.SmartSearchService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSearchServiceTest {
    private val tracks = listOf(
        CachedTrack("1", "Battle Theme", "Game Composer", "Final Fantasy OST", "/one", "", 1000, 2006, genres = "soundtrack", collections = "Final Fantasy"),
        CachedTrack("2", "Quiet Song", "Other Artist", "Acoustic Album", "/two", "", 1000, 2019, genres = "acoustic")
    )

    @Test
    fun `search intent filters by artist album genre and decade`() {
        val service = SmartSearchService()
        val intent = service.parse("Final Fantasy soundtrack from the 2000s", tracks)
        val result = service.filter(intent, tracks)
        assertEquals(listOf("1"), result.map { it.ratingKey })
    }

    @Test
    fun `downloaded only excludes tracks not in download set`() {
        val service = SmartSearchService()
        val intent = service.parse("downloaded soundtrack", tracks)
        val result = service.filter(intent, tracks, setOf("1", "2"))
        assertEquals(listOf("1"), result.map { it.ratingKey })
    }

    @Test
    fun `decade is a hard filter rather than a ranking hint`() {
        val service = SmartSearchService()
        val result = service.filter(service.parse("from the 2000s", tracks), tracks)
        assertEquals(listOf("1"), result.map { it.ratingKey })
    }

    @Test
    fun `preference intents become hard catalog filters`() {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val candidates = listOf(
            tracks[0].copy(addedAt = nowSeconds, playCount = 0, genres = "instrumental ambient"),
            tracks[1].copy(addedAt = nowSeconds - 60L * 24L * 60L * 60L, playCount = 2)
        )
        val service = SmartSearchService()
        val result = service.filter(service.parse("recently added instrumental deep cuts", candidates), candidates)
        assertEquals(listOf("1"), result.map { it.ratingKey })
    }

    @Test
    fun `structured empty queries do not fall back to broad plex search`() {
        val service = SmartSearchService()
        assertTrue(service.isStructuredQuery("downloaded instrumental deep cuts"))
        assertTrue(service.isStructuredQuery("albums from the 2000s"))
    }
}
