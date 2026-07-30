package com.example.data

/** Local-only metadata tagging: preserves Plex tags without inventing moods. */
class LocalMetadataTagService {
    fun tag(track: CachedTrack): MoodTagResult {
        val tags = track.genres.split('|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return MoodTagResult(
            moods = emptyList(),
            energy = "unknown",
            styleTags = tags
        )
    }
}
