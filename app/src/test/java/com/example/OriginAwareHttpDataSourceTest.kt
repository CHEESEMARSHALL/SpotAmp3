package com.example

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import com.example.playback.OriginAwareHttpDataSourceFactory
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/** Verifies the real Media3 HTTP boundary for companion-only music streams. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OriginAwareHttpDataSourceTest {
    @Test
    fun companionStreamReceivesOnlyTheCompanionBearerToken() {
        val payload = byteArrayOf(0, 1, 2, 3, 4, 5)
        var authorization: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/stream") { exchange: HttpExchange ->
            authorization = exchange.requestHeaders.getFirst("Authorization")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()

        val baseUrl = "http://127.0.0.1:${server.address.port}"
        val dataSource = OriginAwareHttpDataSourceFactory(
            plexBaseUrl = { "https://plex.example.test" },
            plexToken = { "plex-secret" },
            companionBaseUrl = { baseUrl },
            companionToken = { "companion-secret" }
        ).createDataSource()

        try {
            val length = dataSource.open(DataSpec(Uri.parse("$baseUrl/stream")))
            val received = ByteArray(payload.size)
            var offset = 0
            while (offset < received.size) {
                val count = dataSource.read(received, offset, received.size - offset)
                if (count == -1) break
                offset += count
            }
            assertEquals(payload.size.toLong(), length)
            assertArrayEquals(payload, received)
            assertEquals("Bearer companion-secret", authorization)
        } finally {
            dataSource.close()
            server.stop(0)
        }
    }

    @Test
    fun crossOriginRedirectDoesNotReceiveCompanionCredentials() {
        var initialAuthorization: String? = null
        var redirectedAuthorization: String? = "not-observed"
        val destination = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        destination.createContext("/stream") { exchange: HttpExchange ->
            redirectedAuthorization = exchange.requestHeaders.getFirst("Authorization")
            val payload = byteArrayOf(9, 8, 7)
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        destination.start()

        val source = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        source.createContext("/stream") { exchange: HttpExchange ->
            initialAuthorization = exchange.requestHeaders.getFirst("Authorization")
            exchange.responseHeaders.add(
                "Location",
                "http://127.0.0.1:${destination.address.port}/stream"
            )
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        source.start()

        val sourceBaseUrl = "http://127.0.0.1:${source.address.port}"
        val dataSource = OriginAwareHttpDataSourceFactory(
            plexBaseUrl = { "https://plex.example.test" },
            plexToken = { "plex-secret" },
            companionBaseUrl = { sourceBaseUrl },
            companionToken = { "companion-secret" }
        ).createDataSource()

        try {
            dataSource.open(DataSpec(Uri.parse("$sourceBaseUrl/stream")))
            val buffer = ByteArray(3)
            assertEquals(3, dataSource.read(buffer, 0, buffer.size))
            assertEquals("Bearer companion-secret", initialAuthorization)
            assertNull(redirectedAuthorization)
        } finally {
            dataSource.close()
            source.stop(0)
            destination.stop(0)
        }
    }
}