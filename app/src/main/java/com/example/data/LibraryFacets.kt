package com.example.data

import java.util.Locale

/**
 * Metadata-backed facets used by the Library category browser.
 *
 * Plex exposes genres, while the local tagging contract treats those same
 * persisted Plex tags as style tags. Mood tags are intentionally not
 * inferred from a genre; they are only exposed when a metadata provider
 * supplies them.
 */
data class LibraryMetadataFacets(
    val artistGenres: List<String>,
    val albumGenres: List<String>,
    val styles: List<String>,
    val moods: List<String>
)

private val localMetadataTagService = LocalMetadataTagService()

fun buildLibraryMetadataFacets(
    artists: List<PlexMetadata>,
    albums: List<PlexMetadata>,
    cachedTracks: List<CachedTrack>
): LibraryMetadataFacets {
    val artistTags = artists.flatMap { it.genres.orEmpty().map(PlexTag::tag) }
    val albumTags = albums.flatMap { it.genres.orEmpty().map(PlexTag::tag) }
    val cachedStyleTags = cachedTracks.flatMap { localMetadataTagService.tag(it).styleTags }
    val cachedMoodTags = cachedTracks.flatMap { localMetadataTagService.tag(it).moods }

    return LibraryMetadataFacets(
        artistGenres = distinctLibraryTags(artistTags + cachedStyleTags),
        albumGenres = distinctLibraryTags(albumTags + cachedStyleTags),
        styles = distinctLibraryTags(albumTags + cachedStyleTags),
        moods = distinctLibraryTags(cachedMoodTags)
    )
}

fun PlexMetadata.hasGenreTag(value: String): Boolean =
    genres.orEmpty().any { it.tag.equals(value, ignoreCase = true) }

fun CachedTrack.hasStyleTag(value: String): Boolean =
    localMetadataTagService.tag(this).styleTags.any { it.equals(value, ignoreCase = true) }

fun CachedTrack.hasMoodTag(value: String): Boolean =
    localMetadataTagService.tag(this).moods.any { it.equals(value, ignoreCase = true) }

internal fun distinctLibraryTags(values: Iterable<String>): List<String> {
    val seen = LinkedHashMap<String, String>()
    values.forEach { rawValue ->
        val value = rawValue.trim()
        if (value.isNotEmpty()) {
            seen.putIfAbsent(value.lowercase(Locale.ROOT), value)
        }
    }
    return seen.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
}
