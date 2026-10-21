package com.example.data

object DownloadIntegrityPolicy {
    fun isPlayable(track: DownloadedTrackEntity, fileExists: Boolean): Boolean =
        track.status == "completed" && !track.localPath.isNullOrBlank() && fileExists && track.fileSize > 0L
}
