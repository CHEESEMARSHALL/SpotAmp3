package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.MusicRepository
import com.example.data.PlaylistSyncState
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/** Exercises the shared-playlist repository flow against a disposable loopback service. */
@RunWith(RobolectricTestRunner::class)
class SharedPlaylistRepositoryIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repository: MusicRepository
    private lateinit var server: HttpServer
    private lateinit var playlistName: String
    private var failRemoteDelete = false
    private var failRemoteRenameConflict = false
    private var failRemoteDeleteConflict = false

    @Before
    fun setUp() {
        repository = MusicRepository(context)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        playlistName = "QA shared ${server.address.port}"
        repository.settings.companionBackendUrl = "http://127.0.0.1:${server.address.port}"
        repository.settings.companionBackendToken = "QA_PLAYLIST_TOKEN_DO_NOT_USE"
        server.createContext("/api/v1/playlists") { exchange ->
            if (exchange.requestMethod == "GET") {
                val body = "{\"contractVersion\":\"qa-v1\",\"playlists\":[{\"id\":\"remote-import\",\"name\":\"Imported QA\",\"updatedAt\":7,\"tracks\":[]}]}".toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } else if (exchange.requestMethod == "POST") {
                val body = "{\"contractVersion\":\"qa-v1\",\"playlist\":{\"id\":\"remote-qa\",\"name\":\"$playlistName\",\"updatedAt\":42,\"tracks\":[]}}".toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } else {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
        }
        server.createContext("/api/v1/playlists/remote-import") { exchange: HttpExchange ->
            if (exchange.requestMethod == "GET") {
                val body = "{\"contractVersion\":\"qa-v1\",\"playlist\":{\"id\":\"remote-import\",\"name\":\"Imported QA\",\"updatedAt\":7,\"tracks\":[]}}".toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } else {
                exchange.sendResponseHeaders(if (exchange.requestMethod == "DELETE") 200 else 404, -1)
                exchange.close()
            }
        }
        server.createContext("/api/v1/playlists/remote-qa") { exchange: HttpExchange ->
            val status: Int
            val responseBody: String
            when (exchange.requestMethod) {
                "GET" -> {
                    val revision = if (failRemoteRenameConflict || failRemoteDeleteConflict) 43 else 42
                    val name = if (failRemoteRenameConflict || failRemoteDeleteConflict) "Remote version" else playlistName
                    status = 200
                    responseBody = "{\"contractVersion\":\"qa-v1\",\"playlist\":{\"id\":\"remote-qa\",\"name\":\"$name\",\"updatedAt\":$revision,\"tracks\":[]}}"
                }
                "PATCH" -> {
                    exchange.requestBody.use { it.readBytes() }
                    if (failRemoteRenameConflict) {
                        status = 409
                        responseBody = "{\"contractVersion\":\"qa-v1\",\"conflict\":true,\"currentRevision\":43}"
                    } else {
                        status = 200
                        responseBody = "{\"contractVersion\":\"qa-v1\",\"playlist\":{\"id\":\"remote-qa\",\"name\":\"Renamed QA\",\"updatedAt\":43,\"tracks\":[]}}"
                    }
                }
                "DELETE" -> {
                    status = when {
                        failRemoteDelete -> 503
                        failRemoteDeleteConflict -> 409
                        else -> 200
                    }
                    responseBody = when (status) {
                        200 -> "{\"contractVersion\":\"qa-v1\"}"
                        409 -> "{\"contractVersion\":\"qa-v1\",\"conflict\":true,\"currentRevision\":43}"
                        else -> ""
                    }
                }
                else -> {
                    status = 404
                    responseBody = ""
                }
            }
            if (responseBody.isEmpty()) {
                exchange.sendResponseHeaders(status, -1)
                exchange.close()
            } else {
                val body = responseBody.toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(status, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
    }

    @After
    fun tearDown() = runBlocking {
        repository.playlists.first().firstOrNull { it.name == playlistName || it.remoteId == "remote-qa" }?.let { repository.deletePlaylist(it.id) }
        repository.settings.companionBackendUrl = ""
        repository.settings.companionBackendToken = ""
        server.stop(0)
    }

    @Test
    fun syncSharedPlaylistsImportsRemotePlaylistIntoRoom() = runBlocking {
        repository.syncSharedPlaylists()
        val imported = repository.playlists.first().first { it.remoteId == "remote-import" }

        assertEquals("Imported QA", imported.name)
        assertEquals(PlaylistSyncState.SYNCED, imported.syncState)
        assertEquals(7L, imported.remoteUpdatedAt)
        assertTrue(repository.getPlaylistTracksList(imported.id).isEmpty())
    }

    @Test
    fun createPlaylistSharesAndPersistsRemoteIdentity() = runBlocking {
        val localId = repository.createPlaylist(playlistName)
        val persisted = repository.playlists.first().first { it.id == localId.toInt() }

        assertEquals("remote-qa", persisted.remoteId)
        assertEquals(PlaylistSyncState.SYNCED, persisted.syncState)
        assertEquals(42L, persisted.remoteUpdatedAt)
        assertTrue(repository.getPlaylistTracksList(localId.toInt()).isEmpty())
    }

    @Test
    fun staleRenamePreservesTheLocalEditAndStoresTheRemoteRevisionAsConflict() = runBlocking {
        val localId = repository.createPlaylist(playlistName).toInt()
        failRemoteRenameConflict = true

        repository.renamePlaylist(localId, "Local rename")

        val conflicted = repository.playlists.first().first { it.id == localId }
        assertEquals("Local rename", conflicted.name)
        assertEquals(43L, conflicted.remoteUpdatedAt)
        assertEquals(PlaylistSyncState.CONFLICT, conflicted.syncState)
    }

    @Test
    fun staleDeletePreservesLocalPlaylistAndMarksConflict() = runBlocking {
        val localId = repository.createPlaylist(playlistName).toInt()
        failRemoteDeleteConflict = true

        assertFalse(repository.deletePlaylist(localId))
        val conflicted = repository.playlists.first().first { it.id == localId }
        assertEquals(PlaylistSyncState.CONFLICT, conflicted.syncState)
        assertEquals(43L, conflicted.remoteUpdatedAt)

        failRemoteDeleteConflict = false
        assertTrue(repository.deletePlaylist(localId))
    }

    @Test
    fun failedRemoteDeletePreservesLocalPlaylistForRetry() = runBlocking {
        val localId = repository.createPlaylist(playlistName).toInt()
        failRemoteDelete = true

        assertFalse(repository.deletePlaylist(localId))
        assertTrue(repository.playlists.first().any { it.id == localId })

        failRemoteDelete = false
        repository.settings.companionBackendToken = ""
        assertFalse(repository.deletePlaylist(localId))
        assertTrue(repository.playlists.first().any { it.id == localId })

        repository.settings.companionBackendToken = "QA_PLAYLIST_TOKEN_DO_NOT_USE"
        assertTrue(repository.deletePlaylist(localId))
        assertFalse(repository.playlists.first().any { it.id == localId })
    }
}
