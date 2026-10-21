package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.CachedTrack
import com.example.data.RecommendationExplanationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecommendationExplanationServiceTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `explanation is local and grounded in shelf type`() {
        val track = CachedTrack("1", "Track", "Artist", "Album", "/track", "", 1, playCount = 3)
        val explanation = RecommendationExplanationService(context).explainShelf("forgotten", listOf(track))
        assertTrue(explanation.isFallback)
        assertEquals(context.getString(R.string.home_explanation_forgotten), explanation.text)
    }

    @Test
    fun `general shelf explanation uses listener language instead of provider names`() {
        val track = CachedTrack("1", "Track", "Artist", "Album", "/track", "", 1)

        val explanation = RecommendationExplanationService(context).explainShelf("general", listOf(track))

        assertEquals(context.getString(R.string.home_explanation_featuring, "Artist"), explanation.text)
        assertFalse(explanation.text.contains("Plex", ignoreCase = true))
        assertFalse(explanation.text.contains("indexed", ignoreCase = true))
    }

    @Test
    fun soundtrackReasonAvoidsInternalMetadataLanguage() {
        val track = CachedTrack("1", "Track", "Artist", "Album", "/track", "", 1)

        val explanation = RecommendationExplanationService(context).explainShelf("soundtrack", listOf(track))

        assertEquals(context.getString(R.string.home_explanation_soundtracks), explanation.text)
        assertFalse(explanation.text.contains("metadata", ignoreCase = true))
    }
}
