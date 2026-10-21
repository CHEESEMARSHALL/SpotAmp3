package com.example

import com.example.data.BackendClientManager
import com.example.data.BackendHomeMapper
import com.example.data.CompanionPairingClaimRequest
import com.example.data.BackendAddPlaylistTrackRequest
import com.example.data.BackendCreatePlaylistRequest
import com.example.data.BackendPlaylistRevisionRequest
import com.example.data.BackendReplacePlaylistRequest
import com.example.data.resolvedTrackNumber
import com.example.data.BackendRenamePlaylistRequest
import com.example.data.BackendReorderPlaylistRequest
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/** Exercises the Retrofit client against an in-process HTTP server only. */
class BackendApiLocalContractTest {
    @Test
    fun libraryRequestUsesExpectedPathQueryAndAuth() = runBlocking {
        var requestPath = ""
        var authorization = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/library/tracks") { exchange: HttpExchange ->
            requestPath = exchange.requestURI.toString()
            authorization = exchange.requestHeaders.getFirst("Authorization").orEmpty()
            val body = """
                {"contractVersion":"qa-v1","tracks":[{"id":"track-1","title":"Fixture","artist":"QA","album":"Local","albumArtist":"Label","genre":"Electronic","year":2024,"trackNumber":7,"streamUrl":"http://127.0.0.1/stream","coverUrl":"/api/v1/media/art/track-1","duration":123000,"favorite":true,"rating":5,"playCount":2,"lastPlayedAt":12345}]}
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val service = BackendClientManager.getApiService("http://127.0.0.1:${server.address.port}")
            val response = service.getLibraryTracks(limit = 2, offset = 4, userAuthToken = "qa-token")

            assertEquals("qa-v1", response.contractVersion)
            val track = response.tracks?.single() ?: error("Expected one track")
            assertEquals("track-1", track.id)
            assertEquals(123000.0, track.duration, 0.0)
            assertEquals("Label", track.albumArtist)
            assertEquals("Electronic", track.genre)
            assertEquals(2024, track.year)
            assertEquals(7, track.resolvedTrackNumber())
            assertTrue(track.favorite == true)
            assertEquals(5, track.rating)
            assertEquals(2, track.playCount)
            assertEquals(12345L, track.lastPlayedAt)
            assertEquals("/api/v1/library/tracks?limit=2&offset=4", requestPath)
            assertEquals("qa-token", authorization)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun homeFeedAcceptsFractionalMillisecondTrackDurations() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/music/home") { exchange: HttpExchange ->
            val tracks = (0..3).joinToString(",") { index ->
                val duration = if (index == 3) "220029.38775510204" else "123000"
                """{"id":"track-$index","title":"Fixture $index","artist":"QA","album":"Local","streamUrl":"/stream/$index","duration":$duration}"""
            }
            val body = """
                {"dailyMixes":[{"id":"mix-1","title":"Daily Mix","reason":"QA fixture","colors":[],"tracks":[$tracks]},{"id":"mix-2","title":"No colors","reason":"QA fixture","tracks":[]}]}
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val service = BackendClientManager.getApiService("http://127.0.0.1:${server.address.port}")
            val mixes = service.getHomeFeed().dailyMixes ?: error("Expected daily mixes")
            val mix = mixes.first()

            assertEquals(4, mix.tracks.size)
            assertEquals(220029.38775510204, mix.tracks[3].duration, 0.00001)
            assertEquals(220029L, BackendHomeMapper.track(mix.tracks[3]).duration)
            assertTrue(mixes[1].colors.isEmpty())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun libraryMutationPathsSafelyEncodeUnicodeAndReservedTrackIds() = runBlocking {
        val requests = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/library/tracks/") { exchange: HttpExchange ->
            requests += "${exchange.requestMethod} ${exchange.requestURI.rawPath} ${exchange.requestHeaders.getFirst("Authorization")}"
            val body = "{\"contractVersion\":\"qa-v1\"}".toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val service = BackendClientManager.getApiService("http://127.0.0.1:${server.address.port}")
            val trackId = "track/élan ?#mix"
            service.setLibraryTrackFavorite(
                trackId,
                com.example.data.BackendFavoriteRequest(true),
                userAuthToken = "qa-token"
            )
            service.setLibraryTrackRating(
                trackId,
                com.example.data.BackendRatingRequest(4),
                userAuthToken = "qa-token"
            )

            val encodedId = "track%2F%C3%A9lan%20%3F%23mix"
            assertEquals(
                listOf(
                    "POST /api/v1/library/tracks/$encodedId/favorite qa-token",
                    "POST /api/v1/library/tracks/$encodedId/rating qa-token"
                ),
                requests
            )
        } finally {
            server.stop(0)
        }
    }
    @Test
    fun lyricsReloadReturnsUpdatedTextAndRevision() = runBlocking {
        var lyricsText = "[00:01.00]Original lyric"
        var lyricsRevision = "revision-one"
        var requestCount = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/lyrics/track-1") { exchange: HttpExchange ->
            requestCount += 1
            val body = """{"text":"$lyricsText","synced":true,"revision":"$lyricsRevision"}"""
                .toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val service = BackendClientManager.getApiService("http://127.0.0.1:${server.address.port}")
            val first = service.getLyrics("track-1", userAuthToken = "qa-token")
            lyricsText = "[00:03.75]Updated while paired"
            lyricsRevision = "revision-two"
            val refreshed = service.getLyrics("track-1", userAuthToken = "qa-token")

            assertEquals("[00:01.00]Original lyric", first.text)
            assertEquals("revision-one", first.revision)
            assertEquals("[00:03.75]Updated while paired", refreshed.text)
            assertEquals("revision-two", refreshed.revision)
            assertEquals(2, requestCount)
        } finally {
            server.stop(0)
        }
    }
    @Test
    fun musicIntelligenceContractMapsVersionedFeaturesAndLabels() = runBlocking {
        var requestPath = ""
        var authorization = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/music/intelligence") { exchange: HttpExchange ->
            requestPath = exchange.requestURI.toString()
            authorization = exchange.requestHeaders.getFirst("Authorization").orEmpty()
            val body = """
                {"contractVersion":"1.4","tracks":[{"trackId":"track-1","components":[{"component":"clap","state":"Complete","analysisVersion":"clap-v2","updatedAt":1700000000000}],"features":{"analysisVersion":"essentia-v3","bpm":128.5,"energy":0.76,"danceability":0.81,"musicalKey":"C minor","analyzedAt":1700000000000,"extractorId":"essentia","extractorVersion":"3.0"},"labels":[{"type":"mood","value":"dreamy","score":0.93,"source":"clap-zero-shot","modelId":"clap-music","modelVersion":"2.0","taxonomyVersion":"7","reviewStatus":"reviewed","manuallyLocked":true,"updatedAt":1700000000001}]}]}
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val service = BackendClientManager.getApiService("http://127.0.0.1:${server.address.port}")
            val page = service.getMusicIntelligence(limit = 2, offset = 3, userAuthToken = "qa-token")
            val track = page.tracks.single()

            assertEquals("1.4", page.contractVersion)
            assertEquals("track-1", track.trackId)
            assertEquals("clap-v2", track.components.single().analysisVersion)
            assertEquals(128.5, track.features?.bpm ?: 0.0, 0.001)
            assertEquals("C minor", track.features?.musicalKey)
            assertEquals("dreamy", track.labels.single().value)
            assertTrue(track.labels.single().manuallyLocked)
            val roundTrip = com.example.data.BackendIntelligenceJson.decode(
                com.example.data.BackendIntelligenceJson.encode(track)
            )
            assertEquals(track, roundTrip)
            assertEquals("/api/v1/music/intelligence?limit=2&offset=3", requestPath)
            assertEquals("qa-token", authorization)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun playlistAndPlaybackEventContractsUseExpectedRoutesAndAuth() = runBlocking {
        val requests = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/playlists") { exchange: HttpExchange ->
            requests += "${exchange.requestMethod} ${exchange.requestURI} ${exchange.requestHeaders.getFirst("Authorization")}"
            val body = "{\"contractVersion\":\"qa-v1\",\"playlists\":[]}".toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/api/v1/events/playback") { exchange: HttpExchange ->
            val requestBody = exchange.requestBody.bufferedReader().use { it.readText() }
            requests += "${exchange.requestMethod} ${exchange.requestURI} ${exchange.requestHeaders.getFirst("Authorization")} $requestBody"
            val body = "{\"contractVersion\":\"qa-v1\"}".toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val service = BackendClientManager.getApiService("http://127.0.0.1:${server.address.port}")
            val playlists = service.getPlaylists(userAuthToken = "qa-token")
            val event = service.recordPlaybackEvent(
                com.example.data.BackendPlaybackEventRequest(
                    trackId = "track-1",
                    playedAt = 123L,
                    eventId = "event-1",
                    listenedMs = 4567L
                ),
                userAuthToken = "qa-token"
            )

            assertEquals("qa-v1", playlists.contractVersion)
            assertEquals("qa-v1", event.contractVersion)
            assertTrue(requests.any { it.startsWith("GET /api/v1/playlists") && it.contains("qa-token") })
            assertTrue(requests.any { it.startsWith("POST /api/v1/events/playback") && it.contains("qa-token") && it.contains("event-1") })
            assertTrue(requests.any { it.contains("\"listenedMs\":4567") })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun pairingAndLyricsContractsKeepAuthenticationBoundaries() = runBlocking {
        var pairingAuthorization: String? = "sentinel"
        var revokeAuthorization = ""
        var lyricsAuthorization = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/pairing/claim") { exchange: HttpExchange ->
            pairingAuthorization = exchange.requestHeaders.getFirst("Authorization")
            val body = """
                {"contractVersion":"qa-v1","device":{"id":"device-1","name":"QA Device"},"token":"QA_DEVICE_TOKEN_DO_NOT_USE"}
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/api/v1/pairing/revoke") { exchange: HttpExchange ->
            revokeAuthorization = exchange.requestHeaders.getFirst("Authorization").orEmpty()
            val body = """{"contractVersion":"qa-v1","revoked":true}""".toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/api/v1/lyrics/track-1") { exchange: HttpExchange ->
            lyricsAuthorization = exchange.requestHeaders.getFirst("Authorization").orEmpty()
            val body = """{"text":"[00:01.00] Fixture lyric","synced":true,"revision":"fixture-revision"}""".toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val service = BackendClientManager.getApiService("http://127.0.0.1:${server.address.port}")
            val pairing = service.claimPairing(
                CompanionPairingClaimRequest("pairing-1", "QA_PAIRING_SECRET_DO_NOT_USE", "QA Device")
            )
            val revoked = service.revokePairing(userAuthToken = "Bearer qa-user-token")
            val lyrics = service.getLyrics("track-1", userAuthToken = "qa-user-token")

            assertEquals("qa-v1", pairing.contractVersion)
            assertEquals("device-1", pairing.device.id)
            assertTrue(revoked.revoked)
            assertEquals("qa-v1", revoked.contractVersion)
            assertEquals("Bearer qa-user-token", revokeAuthorization)
            assertEquals("QA_DEVICE_TOKEN_DO_NOT_USE", pairing.deviceToken)
            assertEquals(null, pairingAuthorization)
            assertEquals("[00:01.00] Fixture lyric", lyrics.text)
            assertTrue(lyrics.synced)
            assertEquals("fixture-revision", lyrics.revision)
            assertEquals("qa-user-token", lyricsAuthorization)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun playlistMutationContractsUseExpectedMethodsPathsBodiesAndAuth() = runBlocking {
        val requests = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fun register(path: String) {
            server.createContext(path) { exchange: HttpExchange ->
                val body = exchange.requestBody.bufferedReader().use { it.readText() }
                requests += "${exchange.requestMethod} ${exchange.requestURI} ${exchange.requestHeaders.getFirst("Authorization")} $body"
                val response = "{\"contractVersion\":\"qa-v1\"}".toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
        }
        register("/api/v1/playlists")
        register("/api/v1/playlists/remote-1")
        register("/api/v1/playlists/remote-1/reorder")
        register("/api/v1/playlists/remote-1/tracks")
        server.start()

        try {
            val service = BackendClientManager.getApiService("http://127.0.0.1:${server.address.port}")
            service.createPlaylist(BackendCreatePlaylistRequest("Mixtape", "spotamp-playlist:10:1700000000000"), userAuthToken = "qa-token")
            service.renamePlaylist("remote-1", BackendRenamePlaylistRequest("Renamed", 11L), userAuthToken = "qa-token")
            service.replacePlaylist(
                "remote-1",
                BackendReplacePlaylistRequest(name = "Renamed", trackIds = listOf("track-1"), baseUpdatedAt = 12L),
                userAuthToken = "qa-token"
            )
            service.reorderPlaylist("remote-1", BackendReorderPlaylistRequest(2, 0, 13L), userAuthToken = "qa-token")
            service.addPlaylistTrack("remote-1", BackendAddPlaylistTrackRequest("track-1", 14L), userAuthToken = "qa-token")
            service.removePlaylistTrack("remote-1", "track-1", BackendPlaylistRevisionRequest(15L), userAuthToken = "qa-token")
            service.deletePlaylist("remote-1", BackendPlaylistRevisionRequest(16L), userAuthToken = "qa-token")

            assertTrue(requests.any { it.startsWith("POST /api/v1/playlists ") && it.contains("qa-token") && it.contains("Mixtape") && it.contains("clientMutationId") && it.contains("spotamp-playlist:10:1700000000000") })
            assertTrue(requests.any { it.startsWith("PATCH /api/v1/playlists/remote-1 ") && it.contains("qa-token") && it.contains("Renamed") })
            assertTrue(requests.any { it.startsWith("POST /api/v1/playlists/remote-1/reorder ") && it.contains("qa-token") && it.contains("fromIndex") && it.contains("toIndex") && it.contains("baseUpdatedAt") })
            assertTrue(requests.any { it.startsWith("PUT /api/v1/playlists/remote-1 ") && it.contains("baseUpdatedAt") && it.contains("12") })
            assertTrue(requests.any { it.startsWith("POST /api/v1/playlists/remote-1/tracks ") && it.contains("baseUpdatedAt") && it.contains("14") })
            assertTrue(requests.any { it.startsWith("DELETE /api/v1/playlists/remote-1/tracks/track-1 ") && it.contains("baseUpdatedAt") && it.contains("15") })
            assertTrue(requests.any { it.startsWith("DELETE /api/v1/playlists/remote-1 ") && it.contains("baseUpdatedAt") && it.contains("16") })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun feedDetailContractsSendCompanionAuthentication() = runBlocking {
        val requests = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fun write(exchange: HttpExchange, body: String) {
            requests += "${exchange.requestMethod} ${exchange.requestURI} ${exchange.requestHeaders.getFirst("Authorization")}"
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/api/v1/music/mixes/mix-1") { exchange ->
            write(exchange, "{\"id\":\"mix-1\",\"title\":\"Mix\",\"reason\":\"QA\",\"colors\":[],\"tracks\":[]}")
        }
        server.createContext("/api/v1/music/stations/station-1/queue") { exchange ->
            write(exchange, "[]")
        }
        server.createContext("/api/v1/music/on-this-day") { exchange ->
            write(exchange, "{\"albumTitle\":\"\",\"artist\":\"\",\"year\":2026,\"timeAgo\":\"\",\"matchReason\":\"QA\",\"tracks\":[]}")
        }
        server.start()

        try {
            val service = BackendClientManager.getApiService("http://127.0.0.1:${server.address.port}")
            service.getDailyMixDetail("mix-1", userAuthToken = "qa-token")
            service.getStationQueue("station-1", userAuthToken = "qa-token")
            service.getOnThisDayContent(userAuthToken = "qa-token")

            assertTrue(requests.any { it.startsWith("GET /api/v1/music/mixes/mix-1") && it.contains("qa-token") })
            assertTrue(requests.any { it.startsWith("GET /api/v1/music/stations/station-1/queue") && it.contains("qa-token") })
            assertTrue(requests.any { it.startsWith("GET /api/v1/music/on-this-day") && it.contains("qa-token") })
        } finally {
            server.stop(0)
        }
    }
}
