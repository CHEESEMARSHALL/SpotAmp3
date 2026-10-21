package com.example

import com.example.data.CachedTrack
import com.example.data.BackendAnalysisComponentDto
import com.example.data.BackendIntelligenceJson
import com.example.data.BackendIntelligenceLabelDto
import com.example.data.BackendTrackIntelligenceDto
import com.example.data.SmartSearchService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSearchServiceTest {
    private fun clapTrack(state: String): CachedTrack = CachedTrack(
        ratingKey = "qa-track-a",
        title = "QA Track A",
        artist = "QA Artist",
        album = "QA Album",
        key = "/qa-track-a",
        thumb = "",
        duration = 30_000,
        musicIntelligenceJson = BackendIntelligenceJson.encode(
            BackendTrackIntelligenceDto(
                trackId = "qa-track-a",
                components = listOf(
                    BackendAnalysisComponentDto("essentia", "Complete", "essentia-v1"),
                    BackendAnalysisComponentDto("clap", state, "clap-v1")
                ),
                labels = listOf(
                    BackendIntelligenceLabelDto(type = "mood", value = "warm", source = "clap-zero-shot", manuallyLocked = true),
                    BackendIntelligenceLabelDto(type = "genre", value = "synth-pop", source = "clap-zero-shot")
                )
            )
        )
    )

    @Test
    fun freeTextSearchFindsCurrentSyncedMoodAndStyleTagsOnly() {
        val complete = clapTrack("Complete")
        val partial = clapTrack("Running")
        val service = SmartSearchService()

        assertEquals(listOf("qa-track-a"), service.filter(service.parse("synth-pop", listOf(complete)), listOf(complete)).map { it.ratingKey })
        assertEquals(listOf("qa-track-a"), service.filter(service.parse("warm", listOf(complete)), listOf(complete)).map { it.ratingKey })
        assertTrue(service.filter(service.parse("synth-pop", listOf(partial)), listOf(partial)).isEmpty())
        assertEquals(listOf("qa-track-a"), service.filter(service.parse("warm", listOf(partial)), listOf(partial)).map { it.ratingKey })
    }

    @Test
    fun staleAnalysisLabelsRemainLocalQueriesWhenNoCurrentTrackMatches() {
        val partial = clapTrack("Running")
        val service = SmartSearchService()

        assertTrue(service.filter(service.parse("synth-pop", listOf(partial)), listOf(partial)).isEmpty())
        assertTrue(service.isKnownAnalysisLabelQuery("synth-pop", listOf(partial)))
        assertTrue(service.isKnownAnalysisLabelQuery("find synth-pop", listOf(partial)))
        assertTrue(!service.isKnownAnalysisLabelQuery("unrelated search", listOf(partial)))
    }

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
