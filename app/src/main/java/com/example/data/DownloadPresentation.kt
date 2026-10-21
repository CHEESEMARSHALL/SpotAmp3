package com.example.data

/** Phone-local audio availability for a track whose metadata is in the library. */
enum class DownloadPresentationState {
    AVAILABLE,
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

/**
 * Maps persisted download rows to the listener-facing state. A completed row
 * only counts as downloaded when its file passes the same integrity checks as
 * offline playback; cached library metadata alone always maps to AVAILABLE.
 */
fun downloadPresentationState(
    track: DownloadedTrackEntity?,
    fileExists: (String) -> Boolean
): DownloadPresentationState {
    if (track == null) return DownloadPresentationState.AVAILABLE
    return when (track.status.lowercase()) {
        "queued" -> DownloadPresentationState.QUEUED
        "downloading", "retrying" -> DownloadPresentationState.DOWNLOADING
        "completed" -> {
            val path = track.localPath?.takeIf(String::isNotBlank)
            if (path != null && DownloadIntegrityPolicy.isPlayable(track, fileExists(path))) {
                DownloadPresentationState.DOWNLOADED
            } else {
                DownloadPresentationState.FAILED
            }
        }
        "failed" -> DownloadPresentationState.FAILED
        else -> DownloadPresentationState.AVAILABLE
    }
}
