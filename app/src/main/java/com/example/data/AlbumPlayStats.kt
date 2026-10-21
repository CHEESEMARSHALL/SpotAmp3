package com.example.data

/**
 * Album-level statistics used by the detail header.
 *
 * The play count is derived from SpotAmp's app-local listening history. Plex
 * or companion view counts are intentionally not added here because they can
 * represent a different source of playback and would double-count local plays.
 * Album playtime is the duration credited to those plays: each track duration
 * multiplied by that track's local play count.
 */
data class AlbumPlayStats(
    val totalPlays: Int,
    val totalPlaytimeMs: Long
)

fun calculateAlbumPlayStats(
    tracks: List<PlexMetadata>,
    localPlayCounts: Map<String, Int> = emptyMap()
): AlbumPlayStats {
    var totalPlays = 0L
    var totalPlaytimeMs = 0L

    tracks.forEach { track ->
        val localPlayCount = localPlayCounts[track.ratingKey]?.coerceAtLeast(0)?.toLong() ?: 0L
        totalPlays = (totalPlays + localPlayCount)
            .coerceAtMost(Int.MAX_VALUE.toLong())

        val durationMs = track.duration?.coerceAtLeast(0L) ?: 0L
        if (localPlayCount > 0L && durationMs > 0L) {
            val trackPlaytimeMs = if (durationMs > Long.MAX_VALUE / localPlayCount) {
                Long.MAX_VALUE
            } else {
                durationMs * localPlayCount
            }
            totalPlaytimeMs = if (Long.MAX_VALUE - totalPlaytimeMs < trackPlaytimeMs) {
                Long.MAX_VALUE
            } else {
                totalPlaytimeMs + trackPlaytimeMs
            }
        }
    }

    return AlbumPlayStats(
        totalPlays = totalPlays.toInt(),
        totalPlaytimeMs = totalPlaytimeMs
    )
}
