package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.DownloadIntegrityPolicy
import com.example.data.MusicRepository
import com.example.playback.TrackItem
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class DownloadRepositoryIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repository: MusicRepository
    private lateinit var server: HttpServer
    private lateinit var track: TrackItem

    @Before
    fun setUp() {
        repository = MusicRepository(context)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        repository.settings.baseUrl = "http://127.0.0.1:${server.address.port}"
        repository.settings.token = "QA_DOWNLOAD_TOKEN_DO_NOT_USE"
        track = TrackItem(
            ratingKey = "qa-download-${server.address.port}",
            title = "Generated fixture",
            artist = "QA",
            album = "Offline",
            key = "/download/fixture.wav",
            thumb = "",
            duration = 1_000L
        )
    }

    @After
    fun tearDown() = runBlocking {
        runCatching { repository.deleteDownloadedTrack(track.ratingKey) }
        repository.settings.baseUrl = ""
        repository.settings.token = ""
        server.stop(0)
    }

    @Test
    fun successfulDownloadAtomicallyIndexesPlayableFile() = runBlocking {
        val fixture = byteArrayOf(82, 73, 70, 70, 1, 2, 3, 4)
        server.createContext("/download/fixture.wav") { exchange: HttpExchange ->
            exchange.sendResponseHeaders(200, fixture.size.toLong())
            exchange.responseBody.use { it.write(fixture) }
        }

        val downloaded = repository.downloadTrack(track)
        val file = java.io.File(downloaded.localPath!!)
        val temporary = java.io.File(file.parentFile, ".${track.ratingKey}.wav.part")

        assertTrue(downloaded.status == "completed")
        assertTrue(file.isFile)
        assertArrayEquals(fixture, Files.readAllBytes(file.toPath()))
        assertTrue(DownloadIntegrityPolicy.isPlayable(downloaded, fileExists = true))
        assertFalse(temporary.exists())
    }

    @Test
    fun failedReplacementKeepsExistingValidFileAndRemovesPartial() = runBlocking {
        val existing = byteArrayOf(9, 8, 7, 6)
        val targetDir = context.getExternalFilesDir("music") ?: java.io.File(context.filesDir, "music")
        targetDir.mkdirs()
        val target = java.io.File(targetDir, "${track.ratingKey}.wav")
        target.writeBytes(existing)
        repository.addDownloadedTrack(track, existing.size.toLong(), target.absolutePath)

        server.createContext("/download/fixture.wav") { exchange: HttpExchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }

        var failed = false
        try {
            repository.downloadTrack(track)
        } catch (_: Exception) {
            failed = true
        }

        val persisted = repository.downloadedTracks.first().first { it.ratingKey == track.ratingKey }
        val temporary = java.io.File(targetDir, ".${track.ratingKey}.wav.part")
        assertTrue(failed)
        assertArrayEquals(existing, target.readBytes())
        assertTrue(DownloadIntegrityPolicy.isPlayable(persisted, fileExists = true))
        assertFalse(temporary.exists())
    }
    @Test
    fun emptyDownloadLeavesNoTargetPartialOrIndex() = runBlocking {
        server.createContext("/download/fixture.wav") { exchange: HttpExchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.close()
        }

        var failed = false
        try {
            repository.downloadTrack(track)
        } catch (_: Exception) {
            failed = true
        }

        val targetDir = context.getExternalFilesDir("music") ?: java.io.File(context.filesDir, "music")
        val target = java.io.File(targetDir, "${track.ratingKey}.wav")
        val temporary = java.io.File(targetDir, ".${track.ratingKey}.wav.part")
        assertTrue(failed)
        assertFalse(target.exists())
        assertFalse(temporary.exists())
        assertTrue(repository.downloadedTracks.first().none { it.ratingKey == track.ratingKey })
    }

    @Test
    fun downloadFailureDetailsAreRedactedBeforeTheyArePersisted() = runBlocking {
        repository.queueDownload(track)
        repository.markDownloadStatus(
            track.ratingKey,
            "failed",
            "Request https://music.example/file?X-Plex-Token=download-secret failed; " +
                "Authorization: Bearer bearer-secret"
        )

        val savedFailure = repository.downloadedTracks.first()
            .single { it.ratingKey == track.ratingKey }
            .errorMessage.orEmpty()
        assertTrue(savedFailure.contains("X-Plex-Token=<redacted>"))
        assertTrue(savedFailure.contains("Authorization: Bearer <redacted>"))
        assertFalse(savedFailure.contains("download-secret"))
        assertFalse(savedFailure.contains("bearer-secret"))
    }

    @Test
    fun deletingDownloadRemovesFileAndIndex() = runBlocking {
        val bytes = byteArrayOf(1, 3, 3, 7)
        val targetDir = context.getExternalFilesDir("music") ?: java.io.File(context.filesDir, "music")
        targetDir.mkdirs()
        val target = java.io.File(targetDir, "${track.ratingKey}.wav")
        target.writeBytes(bytes)
        repository.addDownloadedTrack(track, bytes.size.toLong(), target.absolutePath)

        assertTrue(repository.downloadedTracks.first().any { it.ratingKey == track.ratingKey })
        repository.deleteDownloadedTrack(track.ratingKey)

        assertFalse(target.exists())
        assertTrue(repository.downloadedTracks.first().none { it.ratingKey == track.ratingKey })
    }
}
