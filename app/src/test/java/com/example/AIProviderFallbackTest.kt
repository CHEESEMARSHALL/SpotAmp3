package com.example

import com.example.data.CachedTrack
import com.example.data.LlamaCppAIProvider
import com.example.data.LocalAIProvider
import com.example.data.NoAIProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AIProviderFallbackTest {
    private val tracks = listOf(
        CachedTrack(
            ratingKey = "track-1",
            title = "Neon Skies",
            artist = "QA Artist",
            album = "Night Drive",
            key = "/track-1",
            thumb = "",
            duration = 180_000L,
            genres = "synthwave, electronic",
            playCount = 4
        ),
        CachedTrack(
            ratingKey = "track-2",
            title = "Quiet Roads",
            artist = "Other Artist",
            album = "Acoustic",
            key = "/track-2",
            thumb = "",
            duration = 200_000L,
            genres = "acoustic",
            playCount = 1
        )
    )

    @Test
    fun localProviderProducesDeterministicStructuredIntentFromCachedLibrary() = runBlocking {
        val first = LocalAIProvider().generatePlaylistIntent("energetic QA Artist electronic 1980s", tracks)
        val second = LocalAIProvider().generatePlaylistIntent("energetic QA Artist electronic 1980s", tracks)

        assertEquals(first, second)
        assertTrue(first.seedArtists.contains("QA Artist"))
        assertTrue(first.genres.contains("electronic"))
        assertEquals("high", first.energyLevel)
    }

    @Test
    fun noAiProviderReturnsSafeLocalSelectionWithoutNetworkDependency() = runBlocking {
        val intent = NoAIProvider().generatePlaylistIntent("anything", tracks)

        assertEquals("No AI (Pure Algorithms)", NoAIProvider().name)
        assertEquals(20, intent.targetTrackCount)
        assertTrue(intent.explanation!!.contains("local", ignoreCase = true))
    }

    @Test
    fun missingGgufModelFallsBackToLocalRulesWithExplicitExplanation() = runBlocking {
        val intent = LlamaCppAIProvider(modelPath = "", modelPresetSize = "2B")
            .generatePlaylistIntent("QA Artist", tracks)

        assertTrue(intent.explanation!!.contains("Fallback to local rules engine"))
        assertTrue(intent.seedArtists.contains("QA Artist"))
    }
}