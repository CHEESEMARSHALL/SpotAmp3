package com.example.data

data class RecommendationExplanation(
    val text: String,
    val source: String = "local-metadata",
    val isFallback: Boolean = true
)

class RecommendationExplanationService {
    fun explainShelf(title: String, tracks: List<CachedTrack>): RecommendationExplanation {
        if (tracks.isEmpty()) return RecommendationExplanation("No matching library tracks yet.")
        val artists = tracks.map { it.artist }.distinct().take(2).joinToString(" and ")
        val reason = when {
            title.contains("Forgotten", true) -> "You played these before but have not returned to them recently."
            title.contains("Barely", true) -> "Recently added to your library and not played yet."
            title.contains("Deep Cuts", true) -> "Less-played tracks from artists you already replay."
            title.contains("Soundtrack", true) -> "From your soundtrack and game-music metadata."
            title.contains("Random Album", true) -> "A full album selected from your indexed library."
            else -> "Featuring $artists from your indexed Plex library."
        }
        return RecommendationExplanation(reason)
    }
}
