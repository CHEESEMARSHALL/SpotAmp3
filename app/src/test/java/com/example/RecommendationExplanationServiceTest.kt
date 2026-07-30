package com.example

import com.example.data.CachedTrack
import com.example.data.RecommendationExplanationService
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationExplanationServiceTest {
    @Test
    fun `explanation is local and grounded in shelf type`() {
        val track = CachedTrack("1", "Track", "Artist", "Album", "/track", "", 1, playCount = 3)
        val explanation = RecommendationExplanationService().explainShelf("Forgotten Favorites", listOf(track))
        assertTrue(explanation.isFallback)
        assertTrue(explanation.text.contains("not returned"))
    }
}
