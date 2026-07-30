package com.example.data

/**
 * Produces review-only cleanup suggestions from cached Plex metadata.
 * It never edits Plex or the local cache.
 */
class LibraryCleanupSuggestionService {
    fun suggest(tracks: List<CachedTrack>, maxSuggestions: Int = 50): List<LibraryCleanupSuggestion> {
        return tracks.asSequence()
            .flatMap { track ->
                buildList {
                    if (track.artist.isBlank()) {
                        add(LibraryCleanupSuggestion(track.ratingKey, "Missing artist", 1f, "Review the artist metadata in Plex."))
                    }
                    if (track.album.isBlank()) {
                        add(LibraryCleanupSuggestion(track.ratingKey, "Missing album", 1f, "Review the album metadata in Plex."))
                    }
                    if (track.duration <= 0L) {
                        add(LibraryCleanupSuggestion(track.ratingKey, "Missing duration", 0.95f, "Review the track duration in Plex."))
                    }
                }
            }
            .distinctBy { it.ratingKey to it.issue }
            .take(maxSuggestions.coerceAtLeast(0))
            .toList()
    }
}
