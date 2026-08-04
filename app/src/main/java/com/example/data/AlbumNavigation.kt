package com.example.data

private const val ALBUM_NAVIGATION_PREFIX = "spotamp-album:"
private const val ALBUM_NAVIGATION_SEPARATOR = '\u0000'

/**
 * Stable local album identity used by cached and companion-backed Home items.
 * Plex rating keys remain valid as-is; this key is only used when the album is
 * represented by the local cache rather than a server metadata endpoint.
 */
fun albumNavigationKey(artist: String, album: String): String =
    "$ALBUM_NAVIGATION_PREFIX$artist$ALBUM_NAVIGATION_SEPARATOR$album"

/** Supports the current local key plus the older companion/cache key format. */
fun parseAlbumNavigationKey(value: String): Pair<String, String>? {
    val encoded = when {
        value.startsWith(ALBUM_NAVIGATION_PREFIX) -> value.removePrefix(ALBUM_NAVIGATION_PREFIX)
        value.startsWith("spotcore-album:") -> value.removePrefix("spotcore-album:")
        else -> return null
    }
    val separator = encoded.indexOf(ALBUM_NAVIGATION_SEPARATOR)
    if (separator <= 0 || separator == encoded.lastIndex) return null
    return encoded.substring(0, separator) to encoded.substring(separator + 1)
}
