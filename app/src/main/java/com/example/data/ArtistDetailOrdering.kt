package com.example.data

/**
 * Ranks an artist's tracks by the listens recorded by SpotAmp.
 *
 * A track's Plex/companion metadata can contain a server-side view count, but
 * that value is not guaranteed to be present on artist child responses. The
 * local listening history is the consistent source used by the artist page.
 */
fun rankArtistTracksByListens(
    tracks: List<PlexMetadata>,
    localPlayCounts: Map<String, Int>,
    limit: Int = 5
): List<PlexMetadata> {
    if (limit <= 0) return emptyList()

    return tracks
        .sortedWith(
            compareByDescending<PlexMetadata> {
                localPlayCounts[it.ratingKey]?.coerceAtLeast(0) ?: 0
            }
                .thenBy { it.index ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
                .thenBy { it.ratingKey }
        )
        .take(limit)
}

/**
 * Presents an artist's releases newest-first. Added time breaks ties where
 * release years match; fully tied and unknown dates keep their source order.
 * Unknown release years are kept at the end of the list.
 */
fun sortArtistAlbumsChronologically(albums: List<PlexMetadata>): List<PlexMetadata> {
    return albums.sortedWith(
        compareByDescending<PlexMetadata> { it.year ?: Int.MIN_VALUE }
            .thenByDescending { it.addedAt ?: Long.MIN_VALUE }
    )
}
