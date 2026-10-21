package com.example.data

import android.content.Context
import com.example.R

data class RecommendationExplanation(
    val text: String,
    val source: String = "local-metadata",
    val isFallback: Boolean = true
)

class RecommendationExplanationService(private val context: Context) {
    fun explainShelf(shelfType: String, tracks: List<CachedTrack>): RecommendationExplanation {
        if (tracks.isEmpty()) return RecommendationExplanation(context.getString(R.string.home_explanation_no_tracks))
        val artists = tracks.map { it.artist }.distinct().take(2).joinToString(" and ")
        val reason = when {
            shelfType == "forgotten" -> context.getString(R.string.home_explanation_forgotten)
            shelfType == "unplayed" -> context.getString(R.string.home_explanation_unplayed)
            shelfType == "deep-cuts" -> context.getString(R.string.home_explanation_deep_cuts)
            shelfType == "soundtrack" -> context.getString(R.string.home_explanation_soundtracks)
            shelfType == "random-album" -> context.getString(R.string.home_explanation_random_album)
            else -> context.getString(R.string.home_explanation_featuring, artists)
        }
        return RecommendationExplanation(reason)
    }
}
