package com.example

import com.example.data.BackendClientManager
import com.example.data.BackendFavoriteRequest
import com.example.data.BackendRatingRequest
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class CompanionTrackStateApiContractTest {
    @Test
    fun favoriteAndRatingWritesUseQtRoutesPayloadsAndAuthorization() = runBlocking {
        val requests = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/library/tracks/track-42/") { exchange: HttpExchange ->
            val payload = exchange.requestBody.bufferedReader().use { it.readText() }
            requests += "${exchange.requestMethod} ${exchange.requestURI.rawPath} " +
                "${exchange.requestHeaders.getFirst("Authorization")} $payload"
            val body = "{\"contractVersion\":\"1.4\",\"trackId\":\"track-42\"}"
                .toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val service = BackendClientManager.getApiService("http://127.0.0.1:${server.address.port}")
            service.setLibraryTrackFavorite("track-42", BackendFavoriteRequest(true), "Bearer qa-token")
            service.setLibraryTrackRating("track-42", BackendRatingRequest(4), "Bearer qa-token")

            assertEquals(
                listOf(
                    "POST /api/v1/library/tracks/track-42/favorite Bearer qa-token {\"favorite\":true}",
                    "POST /api/v1/library/tracks/track-42/rating Bearer qa-token {\"rating\":4}"
                ),
                requests
            )
        } finally {
            server.stop(0)
        }
    }
}
