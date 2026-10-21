package com.example

import com.example.data.BackendClientManager
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.HttpException
import java.net.InetSocketAddress

/** Protects the client boundary from credential forwarding across redirects. */
class BackendApiRedirectSafetyTest {
    @Test
    fun libraryRedirectIsNotFollowed() = runBlocking {
        var redirectedRequestObserved = false
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/library/tracks") { exchange ->
            exchange.responseHeaders.add("Location", "/capture")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/capture") { exchange ->
            redirectedRequestObserved = true
            exchange.sendResponseHeaders(200, 0)
            exchange.close()
        }
        server.start()

        try {
            val service = BackendClientManager.getApiService("http://127.0.0.1:${server.address.port}")
            try {
                service.getLibraryTracks(userAuthToken = "qa-token")
            } catch (error: HttpException) {
                assertEquals(302, error.code())
            }
            assertFalse(redirectedRequestObserved)
        } finally {
            server.stop(0)
        }
    }
}
