package com.example

import com.example.data.DownloadIntegrityPolicy
import com.example.data.DownloadedTrackEntity
import com.example.data.isNonEmptyRegularFile
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadIntegrityPolicyTest {
    private val completed = DownloadedTrackEntity(
        ratingKey = "track-1", title = "Song", artist = "Artist", album = "Album",
        key = "/track-1", thumb = "", duration = 180_000L, fileSize = 10L,
        localPath = "/qa/track-1.mp3", status = "completed"
    )

    @Test
    fun completedExistingNonEmptyFileIsPlayable() {
        assertTrue(DownloadIntegrityPolicy.isPlayable(completed, fileExists = true))
    }

    @Test
    fun missingOrEmptyFileIsNotPlayable() {
        assertFalse(DownloadIntegrityPolicy.isPlayable(completed, fileExists = false))
        assertFalse(DownloadIntegrityPolicy.isPlayable(completed.copy(fileSize = 0L), fileExists = true))
    }

    @Test
    fun incompleteDownloadIsNeverPlayable() {
        assertFalse(DownloadIntegrityPolicy.isPlayable(completed.copy(status = "downloading"), fileExists = true))
        assertFalse(DownloadIntegrityPolicy.isPlayable(completed.copy(status = "failed"), fileExists = true))
    }

    @Test
    fun localAudioMustBeARegularNonEmptyFile() {
        val path = Files.createTempFile("spotamp-audio-check", ".mp3")
        try {
            assertFalse(isNonEmptyRegularFile(path.toString()))
            Files.write(path, byteArrayOf(1))
            assertTrue(isNonEmptyRegularFile(path.toString()))
            assertFalse(isNonEmptyRegularFile(path.parent.toString()))
            assertFalse(isNonEmptyRegularFile(path.resolveSibling("missing-audio.mp3").toString()))
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
