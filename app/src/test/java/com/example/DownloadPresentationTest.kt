package com.example

import com.example.data.DownloadPresentationState
import com.example.data.DownloadedTrackEntity
import com.example.data.downloadPresentationState
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPresentationTest {
    @Test
    fun missingAudioRowMeansLibraryMetadataIsAvailableButAudioIsNotDownloaded() {
        assertEquals(
            DownloadPresentationState.AVAILABLE,
            downloadPresentationState(track = null, fileExists = { false })
        )
    }

    @Test
    fun queuedAndActiveRowsUseTheSharedProgressStates() {
        assertEquals(
            DownloadPresentationState.QUEUED,
            downloadPresentationState(download(status = "queued"), fileExists = { false })
        )
        assertEquals(
            DownloadPresentationState.DOWNLOADING,
            downloadPresentationState(download(status = "downloading"), fileExists = { false })
        )
        assertEquals(
            DownloadPresentationState.DOWNLOADING,
            downloadPresentationState(download(status = "retrying"), fileExists = { false })
        )
    }

    @Test
    fun onlyAnIntactCompletedFileIsReportedAsDownloaded() {
        val completed = download(status = "completed", path = "/audio/track.mp3", size = 12)
        assertEquals(
            DownloadPresentationState.DOWNLOADED,
            downloadPresentationState(completed, fileExists = { it == "/audio/track.mp3" })
        )
        assertEquals(
            DownloadPresentationState.FAILED,
            downloadPresentationState(completed, fileExists = { false })
        )
        assertEquals(
            DownloadPresentationState.FAILED,
            downloadPresentationState(completed.copy(fileSize = 0), fileExists = { true })
        )
    }

    @Test
    fun failedRowsRemainRetryable() {
        assertEquals(
            DownloadPresentationState.FAILED,
            downloadPresentationState(download(status = "failed"), fileExists = { false })
        )
    }

    private fun download(status: String, path: String? = null, size: Long = 0) =
        DownloadedTrackEntity(
            ratingKey = "track-1",
            title = "Track",
            artist = "Artist",
            album = "Album",
            key = "/media/track-1",
            thumb = "",
            duration = 60_000,
            fileSize = size,
            localPath = path,
            status = status
        )
}
