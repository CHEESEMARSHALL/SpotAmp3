package com.example.data

/** Uses explicit library metadata and SpotCore analysis without inventing labels. */
class LocalMetadataTagService {
    fun tag(track: CachedTrack): MoodTagResult {
        val intelligence = track.musicIntelligenceJson.takeIf(String::isNotBlank)
            ?.let { runCatching { BackendIntelligenceJson.decode(it) }.getOrNull() }
        val metadataGenres = track.genres.split('|')
            .map(String::trim)
            .filter(String::isNotEmpty)
        val labels = intelligence?.currentLabels().orEmpty()
        val moods = labels
            .filter { it.type.equals("mood", ignoreCase = true) }
            .map { it.value.trim() }
            .filter(String::isNotEmpty)
            .distinct()
        val analysisStyles = labels
            .filter { it.type.equals("style", ignoreCase = true) || it.type.equals("genre", ignoreCase = true) }
            .map { it.value.trim() }
            .filter(String::isNotEmpty)
        return MoodTagResult(
            moods = moods,
            energy = energyLevel(intelligence?.currentFeatures()?.energy),
            styleTags = (metadataGenres + analysisStyles).distinct()
        )
    }

    private fun energyLevel(energy: Double?): String = when {
        energy == null -> "unknown"
        energy <= 0.33 -> "low"
        energy >= 0.67 -> "high"
        else -> "medium"
    }
}