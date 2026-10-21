package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.LastFmScrobbler
import com.example.data.PlexSettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer

/** Exercises Last.fm request semantics against a disposable loopback fake only. */
@RunWith(RobolectricTestRunner::class)
class LastFmIsolationContractTest {
    @Test
    fun tokenRequestUsesFakeEndpointAndSignedForm() = runBlocking {
        var requestBody = ""
        val server = fakeServer(200, "{\"token\":\"qa-token\"}") { requestBody = it }
        val settings = testSettings()
        LastFmScrobbler.endpointOverrideForTest = "http://127.0.0.1:${server.address.port}/2.0/"

        try {
            val result = LastFmScrobbler.requestToken(settings)

            assertTrue(result.isSuccess)
            assertEquals("qa-token", result.getOrThrow())
            val form = requestBody.split('&').associate {
                val parts = it.split('=', limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
            }
            assertEquals("auth.getToken", form["method"])
            assertEquals("qa-api-key", form["api_key"])
            assertEquals("json", form["format"])
            assertTrue(form["api_sig"].orEmpty().matches(Regex("[0-9a-f]{32}")))
        } finally {
            LastFmScrobbler.endpointOverrideForTest = null
            server.stop(0)
        }
    }

    @Test
    fun fakeHttpFailureBecomesFailureResultWithoutCloudCall() = runBlocking {
        val server = fakeServer(503, "{\"error\":\"qa unavailable\"}") {}
        val settings = testSettings()
        LastFmScrobbler.endpointOverrideForTest = "http://127.0.0.1:${server.address.port}/2.0/"

        try {
            val result = LastFmScrobbler.requestToken(settings)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("503"))
        } finally {
            LastFmScrobbler.endpointOverrideForTest = null
            server.stop(0)
        }
    }

    private fun testSettings(): PlexSettingsManager {
        val settings = PlexSettingsManager(ApplicationProvider.getApplicationContext())
        settings.lastFmApiKey = "qa-api-key"
        settings.lastFmApiSecret = "qa-api-secret"
        settings.lastFmPendingToken = "pending-token"
        return settings
    }

    private fun fakeServer(status: Int, body: String, onBody: (String) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/2.0/") { exchange: HttpExchange ->
            val requestBody = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            onBody(requestBody)
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server
    }
}
