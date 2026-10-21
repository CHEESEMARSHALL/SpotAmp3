package com.example

import com.example.data.BackendAnalysisComponentDto
import com.example.data.BackendIntelligenceJson
import com.example.data.BackendIntelligenceLabelDto
import com.example.data.BackendTrackFeaturesDto
import com.example.data.BackendTrackIntelligenceDto
import com.example.data.CachedTrack
import com.example.data.LocalMetadataTagService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicIntelligenceLibraryTagsTest {
    @Test
    fun `complete SpotCore labels and features feed library facets without inference`() {
        val intelligence = BackendTrackIntelligenceDto(
            trackId = "track-1",
            components = listOf(
                BackendAnalysisComponentDto("essentia", "Complete", "essentia-v1"),
                BackendAnalysisComponentDto("clap", "Complete", "clap-v1")
            ),
            features = BackendTrackFeaturesDto(analysisVersion = "essentia-v1", energy = 0.76),
            labels = listOf(
                BackendIntelligenceLabelDto(type = "mood", value = "dreamy", source = "clap-zero-shot"),
                BackendIntelligenceLabelDto(type = "style", value = "ambient", source = "clap-zero-shot"),
                BackendIntelligenceLabelDto(type = "instrument", value = "synth", source = "clap-zero-shot")
            )
        )
        val tags = LocalMetadataTagService().tag(track(intelligence))

        assertEquals(listOf("dreamy"), tags.moods)
        assertEquals("high", tags.energy)
        assertTrue(tags.styleTags.contains("Electronic"))
        assertTrue(tags.styleTags.contains("ambient"))
        assertFalse(tags.styleTags.contains("synth"))
    }

    @Test
    fun `failed or stale analysis is not exposed as current facets`() {
        val intelligence = BackendTrackIntelligenceDto(
            trackId = "track-2",
            components = listOf(
                BackendAnalysisComponentDto("essentia", "Failed", "essentia-v1"),
                BackendAnalysisComponentDto("clap", "Stale", "clap-v1")
            ),
            features = BackendTrackFeaturesDto(analysisVersion = "essentia-v1", energy = 0.82),
            labels = listOf(
                BackendIntelligenceLabelDto(type = "mood", value = "old mood", source = "clap-zero-shot"),
                BackendIntelligenceLabelDto(type = "style", value = "approved", source = "clap-zero-shot", manuallyLocked = true),
                BackendIntelligenceLabelDto(type = "style", value = "rejected", source = "clap-zero-shot", reviewStatus = "rejected")
            )
        )
        val tags = LocalMetadataTagService().tag(track(intelligence))

        assertTrue(tags.moods.isEmpty())
        assertEquals("unknown", tags.energy)
        assertTrue(tags.styleTags.contains("approved"))
        assertFalse(tags.styleTags.contains("rejected"))
    }

    @Test
    fun `feature version mismatch is treated as stale even when component reports complete`() {
        val intelligence = BackendTrackIntelligenceDto(
            trackId = "track-3",
            components = listOf(BackendAnalysisComponentDto("essentia", "Complete", "essentia-v2")),
            features = BackendTrackFeaturesDto(analysisVersion = "essentia-v1", energy = 0.9)
        )

        assertEquals("unknown", LocalMetadataTagService().tag(track(intelligence)).energy)
    }

    @Test
    fun `missing analysis stays unknown and does not invent a mood`() {
        val tags = LocalMetadataTagService().tag(track())

        assertTrue(tags.moods.isEmpty())
        assertEquals("unknown", tags.energy)
    }

    private fun track(intelligence: BackendTrackIntelligenceDto? = null) = CachedTrack(
        ratingKey = intelligence?.trackId ?: "track-1",
        title = "Fixture",
        artist = "Artist",
        album = "Album",
        key = "stream",
        thumb = "",
        duration = 120_000,
        genres = "Electronic",
        musicIntelligenceJson = intelligence?.let(BackendIntelligenceJson::encode) ?: "{}"
    )
}