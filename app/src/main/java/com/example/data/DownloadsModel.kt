package com.example.data

import java.io.File
import java.util.Locale

/** Treat missing, non-file, and zero-byte paths as unavailable audio. */
fun isNonEmptyRegularFile(path: String): Boolean = runCatching {
    File(path).let { it.isFile && it.length() > 0L }
}.getOrDefault(false)

/**
 * The downloads surface is collection-first: individual completed tracks are
 * represented by their album, while playlist membership is rendered by the
 * playlist UI separately.
 */
data class DownloadedAlbumGroup(
    val album: String,
    val artist: String,
    val tracks: List<DownloadedTrackEntity>
)

data class DownloadedAlbumProgress(
    val fraction: Float,
    val activeTrackCount: Int,
    val totalTrackCount: Int,
    val isIndeterminate: Boolean
)

fun groupDownloadedAlbums(
    tracks: List<DownloadedTrackEntity>,
    cachedTracks: List<CachedTrack> = emptyList()
): List<DownloadedAlbumGroup> {
    val cachedByRatingKey = cachedTracks.associateBy { it.ratingKey }
    fun cachedTrack(track: DownloadedTrackEntity): CachedTrack? =
        cachedByRatingKey[track.ratingKey]
            ?: track.ratingKey.removePrefix("companion:").takeIf { it != track.ratingKey }
                ?.let(cachedByRatingKey::get)
    fun albumArtist(track: DownloadedTrackEntity): String = cachedTrack(track)?.effectiveAlbumArtist() ?: track.artist

    return tracks
        .groupBy { track ->
            val knownAlbumArtist = cachedTrack(track)?.albumArtist?.trim().orEmpty().lowercase(Locale.ROOT)
            knownAlbumArtist to track.album.trim().lowercase(Locale.ROOT)
        }
        .values
        .map { albumTracks ->
            val artists = albumTracks
                .map(::albumArtist)
                .map(String::trim)
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase(Locale.ROOT) }

            DownloadedAlbumGroup(
                album = albumTracks.first().album.ifBlank { "Unknown album" },
                artist = artists.singleOrNull() ?: "Various Artists",
                tracks = albumTracks.sortedWith(
                    compareBy<DownloadedTrackEntity> { track ->
                        cachedTrack(track)?.discNumber?.takeIf { it > 0 } ?: 1
                    }.thenBy { track ->
                        cachedTrack(track)?.index?.takeIf { it > 0 } ?: Int.MAX_VALUE
                    }
                )
            )
        }
}

fun downloadedAlbumProgress(
    albumTracks: List<DownloadedTrackEntity>,
    locallyAvailableRatingKeys: Set<String>,
    progressByRatingKey: Map<String, Float>
): DownloadedAlbumProgress? {
    val activeTrackCount = albumTracks.count { track ->
        track.status in setOf("queued", "downloading", "retrying") || track.ratingKey in progressByRatingKey
    }
    if (activeTrackCount == 0 || albumTracks.isEmpty()) return null

    val fraction = albumTracks.sumOf { track ->
        when {
            track.ratingKey in locallyAvailableRatingKeys -> 1.0
            else -> progressByRatingKey[track.ratingKey]
                ?.takeIf { it.isFinite() && it >= 0f }
                ?.coerceIn(0f, 1f)
                ?.toDouble()
                ?: 0.0
        }
    }.div(albumTracks.size).toFloat().coerceIn(0f, 1f)

    return DownloadedAlbumProgress(
        fraction = fraction,
        activeTrackCount = activeTrackCount,
        totalTrackCount = albumTracks.size,
        isIndeterminate = albumTracks.any { track ->
            track.status == "downloading" &&
                progressByRatingKey[track.ratingKey]?.let { it < 0f || !it.isFinite() } != false
        }
    )
}

fun filterDownloadedPlaylistTracks(
    playlistTracks: List<PlaylistTrackEntity>,
    downloadedTracks: List<DownloadedTrackEntity>,
    fileExists: (String) -> Boolean
): List<PlaylistTrackEntity> {
    val completedRatingKeys = locallyAvailableDownloadRatingKeys(downloadedTracks, fileExists)
    return playlistTracks.filter { it.ratingKey in completedRatingKeys }
}

fun completedDownloadRatingKeys(downloadedTracks: List<DownloadedTrackEntity>): Set<String> =
    downloadedTracks.asSequence()
        .filter { it.status == "completed" && !it.localPath.isNullOrBlank() }
        .map { it.ratingKey }
        .toSet()

/**
 * Stable album identities for offline Library filtering. Prefer cached album
 * metadata (which knows compilation album artists); old downloads fall back to
 * the track artist stored with the download record.
 */
fun completedDownloadAlbumIdentities(
    downloadedTracks: List<DownloadedTrackEntity>,
    cachedTracks: List<CachedTrack>,
    fileExists: (String) -> Boolean
): Set<Pair<String, String>> {
    val cachedByRatingKey = cachedTracks.associateBy { it.ratingKey }
    return downloadedTracks.asSequence()
        .filter { track -> isLocallyAvailableDownload(track, fileExists) }
        .map { download ->
            val cachedTrack = cachedByRatingKey[download.ratingKey]
            val artist = cachedTrack?.effectiveAlbumArtist() ?: download.artist
            artist.trim().lowercase(Locale.ROOT) to download.album.trim().lowercase(Locale.ROOT)
        }
        .toSet()
}

fun locallyAvailableDownloadRatingKeys(
    downloadedTracks: List<DownloadedTrackEntity>,
    fileExists: (String) -> Boolean
): Set<String> = downloadedTracks.asSequence()
    .filter { track -> isLocallyAvailableDownload(track, fileExists) }
    .map { it.ratingKey }
    .toSet()

fun locallyAvailableDownloadArtistNames(
    downloadedTracks: List<DownloadedTrackEntity>,
    fileExists: (String) -> Boolean
): Set<String> = downloadedTracks.asSequence()
    .filter { track -> isLocallyAvailableDownload(track, fileExists) }
    .map { it.artist.trim().lowercase(Locale.ROOT) }
    .filter(String::isNotEmpty)
    .toSet()

private fun isLocallyAvailableDownload(
    track: DownloadedTrackEntity,
    fileExists: (String) -> Boolean
): Boolean {
    val path = track.localPath?.takeIf(String::isNotBlank) ?: return false
    return DownloadIntegrityPolicy.isPlayable(track, fileExists(path))
}

fun formatDownloadSize(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L).toDouble()
    val (scaled, unit) = when {
        value >= 1_000_000_000 -> value / 1_000_000_000 to "GB"
        value >= 1_000_000 -> value / 1_000_000 to "MB"
        value >= 1_000 -> value / 1_000 to "KB"
        else -> value to "B"
    }
    val pattern = if (scaled >= 100 || scaled == scaled.toLong().toDouble()) "%.0f" else "%.1f"
    return "${String.format(Locale.US, pattern, scaled)} $unit"
}

fun formatDownloadDuration(durationMs: Long): String {
    val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L).toInt()
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60
    val dayUnit = if (days == 1) "day" else "days"

    return when {
        days > 0 && hours > 0 -> "$days $dayUnit, $hours hr"
        days > 0 -> "$days $dayUnit"
        hours > 0 && minutes > 0 -> "$hours hr, $minutes min"
        hours > 0 -> "$hours hr"
        else -> if (minutes == 1) "1 minute" else "$minutes minutes"
    }
}
