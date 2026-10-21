package com.example.ui

import com.example.data.CachedTrack
import com.example.data.BackendAnalysisComponentDto
import com.example.data.BackendIntelligenceJson
import com.example.data.BackendIntelligenceLabelDto
import com.example.data.BackendTrackIntelligenceDto
import com.example.data.DiscoveryLevel
import com.example.data.RadioType
import com.example.data.RecentTrack
import com.example.data.RadioService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StationLibraryPreviewTest {
    private fun analyzedTrack(clapState: String): CachedTrack = CachedTrack(
        ratingKey = "analyzed",
        title = "Analyzed",
        artist = "Artist",
        album = "Album",
        key = "/analyzed",
        thumb = "",
        duration = 1000,
        genres = "Electronic",
        musicIntelligenceJson = BackendIntelligenceJson.encode(
            BackendTrackIntelligenceDto(
                trackId = "analyzed",
                components = listOf(BackendAnalysisComponentDto("clap", clapState, "clap-v1")),
                labels = listOf(
                    BackendIntelligenceLabelDto(type = "mood", value = "warm", source = "clap-zero-shot", manuallyLocked = true),
                    BackendIntelligenceLabelDto(type = "genre", value = "synth-pop", source = "clap-zero-shot")
                )
            )
        )
    )

    @Test
    fun moodAndStyleChoicesUseOnlyCurrentSyncedTags() {
        val complete = analyzedTrack("Complete")
        val running = analyzedTrack("Running")

        assertEquals(listOf("warm"), stationRadioFacetChoices(RadioType.MOOD_RADIO, listOf(complete)))
        assertEquals(listOf("Electronic", "synth-pop"), stationRadioFacetChoices(RadioType.STYLE_RADIO, listOf(complete)))
        assertEquals(listOf("warm"), stationRadioFacetChoices(RadioType.MOOD_RADIO, listOf(running)))
        assertEquals(listOf("Electronic"), stationRadioFacetChoices(RadioType.STYLE_RADIO, listOf(running)))
        assertEquals(emptyList<String>(), stationRadioFacetChoices(RadioType.MOOD_RADIO, emptyList()))
    }

    @Test
    fun stationRadioRequestsKeepGenreMoodAndStyleFacetsDistinct() {
        val mood = stationRadioRequest(
            type = RadioType.MOOD_RADIO, seedArtist = null, selectedFacet = "warm",
            collection = null, decadeStart = null, trackCount = 20,
            discoveryLevel = DiscoveryLevel.BALANCED, recentCooldownDays = 7
        )
        val style = stationRadioRequest(
            type = RadioType.STYLE_RADIO, seedArtist = null, selectedFacet = "synth-pop",
            collection = null, decadeStart = null, trackCount = 20,
            discoveryLevel = DiscoveryLevel.BALANCED, recentCooldownDays = 7
        )

        assertEquals("warm", mood.mood)
        assertEquals(null, mood.genre)
        assertEquals("synth-pop", style.style)
        assertEquals(null, style.genre)
    }

    @Test
    fun previewUsesTheSameCooldownAndDiscoverySettingsAsPlayback() {
        val request = stationRadioRequest(
            type = RadioType.LIBRARY_RADIO,
            seedArtist = null,
            selectedFacet = null,
            collection = null,
            decadeStart = null,
            trackCount = 20,
            discoveryLevel = DiscoveryLevel.BALANCED,
            recentCooldownDays = 7
        )
        val tracks = listOf("one", "two", "three").mapIndexed { index, id ->
            CachedTrack(
                ratingKey = id,
                title = "Track $id",
                artist = "Artist $index",
                album = "Album $index",
                key = "/track/$id",
                thumb = "",
                duration = 180_000L
            )
        }
        val recent = RecentTrack(
            ratingKey = "two",
            title = "Track two",
            artist = "Artist 1",
            album = "Album 1",
            key = "/track/two",
            thumb = "",
            timestamp = System.currentTimeMillis()
        )

        val result = RadioService().generate(request, tracks, listOf(recent))

        assertEquals(7, request.recentCooldownDays)
        assertEquals(2, result.size)
        assertFalse(result.any { it.ratingKey == "two" })
    }

    @Test
    fun discoveryChoicesUseListenerFriendlyLabels() {
        assertEquals("Familiar", stationDiscoveryLabel(DiscoveryLevel.FAMILIAR))
        assertEquals("Balanced", stationDiscoveryLabel(DiscoveryLevel.BALANCED))
        assertEquals("Deep cuts", stationDiscoveryLabel(DiscoveryLevel.DEEP_CUTS))
    }
}
