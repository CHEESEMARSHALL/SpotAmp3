package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MusicRepositoryLibraryFailureTest {
    @Test
    fun plexArtistAlbumAndRecentlyAddedFailuresAreNotConvertedIntoSuccessfulEmptyLists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            val body = "{\"error\":\"temporarily unavailable\"}".toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(503, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        val settings = PlexSettingsManager(context)
        settings.apply {
            clearCompanionCredential()
            baseUrl = "http://127.0.0.1:${server.address.port}"
            token = "qa-library-token"
        }
        try {
            val repository = MusicRepository(context)
            val failures = runBlocking {
                listOf(
                    runCatching { repository.getArtists("music") }.exceptionOrNull(),
                    runCatching { repository.getAlbums("music") }.exceptionOrNull(),
                    runCatching { repository.getRecentlyAddedAlbums("music") }.exceptionOrNull(),
                    runCatching { repository.getArtistAlbumsForDetail("artist-1") }.exceptionOrNull(),
                    runCatching { repository.getAlbumTracksForDetail("album-1") }.exceptionOrNull()
                )
            }

            assertEquals(5, requests.get())
            assertTrue("Artist request failure must remain observable", failures[0] is HttpException)
            assertTrue("Album request failure must remain observable", failures[1] is HttpException)
            assertTrue("Recently Added request failure must remain observable", failures[2] is HttpException)
            assertTrue("Artist detail failure must remain observable", failures[3] is HttpException)
            assertTrue("Album detail failure must remain observable", failures[4] is HttpException)
        } finally {
            settings.baseUrl = ""
            settings.token = ""
            server.stop(0)
        }
    }
}
