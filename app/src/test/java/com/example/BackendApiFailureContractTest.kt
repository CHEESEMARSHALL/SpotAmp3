package com.example

import com.example.data.BackendClientManager
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import java.net.InetSocketAddress

/** Ensures controlled backend failures remain visible to callers. */
class BackendApiFailureContractTest {
    @Test
    fun serverErrorDoesNotBecomeSuccessfulEmptyLibrary() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/library/tracks") { exchange ->
            val body = "{\"error\":\"controlled-qa-outage\"}".toByteArray()
            exchange.sendResponseHeaders(503, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val service = BackendClientManager.getApiService("http://127.0.0.1:${server.address.port}")
            try {
                service.getLibraryTracks()
                fail("A 503 response must not be converted into a successful library result")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.stop(0)
        }
    }
}
