package com.example.data

/**
 * Finds review-only collection ideas from metadata already cached from Plex.
 * It never creates or edits a Plex collection.
 */
class LocalCollectionSuggestionService {
    fun suggest(tracks: List<CachedTrack>, maxSuggestions: Int = 8): List<CollectionSuggestion> {
        val groups = linkedMapOf<String, MutableList<CachedTrack>>()
        tracks.sortedBy { it.ratingKey }.forEach { track ->
            val labels = (track.collections.split('|') + track.genres.split('|'))
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
            labels.forEach { groups.getOrPut(it) { mutableListOf() }.add(track) }
        }
        return groups.entries
            .filter { it.value.size >= 3 }
            .sortedWith(compareByDescending<Map.Entry<String, MutableList<CachedTrack>>> { it.value.size }.thenBy { it.key })
            .take(maxSuggestions.coerceAtLeast(0))
            .map { (name, matching) ->
                CollectionSuggestion(
                    name = name,
                    ratingKeys = matching.map { it.ratingKey },
                    reason = "${matching.size} cached Plex tracks share this metadata tag."
                )
            }
    }
}
