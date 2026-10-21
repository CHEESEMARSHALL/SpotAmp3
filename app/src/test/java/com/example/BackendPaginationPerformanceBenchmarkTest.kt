package com.example

import com.example.data.BackendClientManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlin.system.measureNanoTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendPaginationPerformanceBenchmarkTest {
    @Test
    fun controlledLoopbackPaginationParsesTwentyRealBackendPages() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requestCount = 0
        server.createContext("/api/v1/library/tracks") { exchange: HttpExchange ->
            requestCount++
            val query = exchange.requestURI.query.orEmpty()
            val offset = query.substringAfter("offset=", "0").substringBefore("&").toInt()
            val body = buildPage(offset).toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val service = BackendClientManager.getApiService("http://127.0.0.1:" + server.address.port)
            val pageCount = 20
            val pageSize = 100
            var received = 0
            val elapsedNanos = measureNanoTime {
                repeat(pageCount) { page ->
                    val response = service.getLibraryTracks(
                        limit = pageSize,
                        offset = page * pageSize,
                        userAuthToken = "Bearer benchmark-local"
                    )
                    assertEquals(pageSize, response.tracks.orEmpty().size)
                    received += response.tracks.orEmpty().size
                }
            }

            assertEquals(pageCount, requestCount)
            assertEquals(pageCount * pageSize, received)
            println(
                "BACKEND_PAGINATION_BENCHMARK pages=" + pageCount +
                    " pageSize=" + pageSize +
                    " tracks=" + received +
                    " elapsedMs=" + (elapsedNanos / 1_000_000.0)
            )
        } finally {
            server.stop(0)
        }
    }

    private fun buildPage(offset: Int): String = buildString {
        append("{\"contractVersion\":\"qa-v1\",\"tracks\":[")
        repeat(100) { index ->
            if (index > 0) append(',')
            val id = offset + index
            append("{\"id\":\"benchmark-track-")
            append(id)
            append("\",\"title\":\"Track ")
            append(id)
            append("\",\"artist\":\"Benchmark Artist\",\"album\":\"Benchmark Album\",\"streamUrl\":\"http://127.0.0.1/track/")
            append(id)
            append("\",\"duration\":180}")
        }
        append("]}")
    }
}