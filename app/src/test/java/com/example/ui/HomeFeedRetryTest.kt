package com.example.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.data.BackendClientManager
import com.example.data.BackendHomeFeedResponse
import com.example.data.CompanionConnectionStatus
import com.example.data.PlexMedia
import com.example.data.PlexMetadata
import com.example.data.PlexPart
import com.example.data.PlexSettingsManager
import com.example.playback.PlaybackManager
import com.example.ui.theme.MyApplicationTheme
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeFeedRetryTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }
    private var viewModelStore: ViewModelStore? = null
    private var homeFeedServer: LocalHomeFeedServer? = null

    @Before
    fun setUp() {
        runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        clearSavedConnection()
    }

    @After
    fun tearDown() {
        viewModelStore?.clear()
        viewModelStore = null
        homeFeedServer?.close()
        homeFeedServer = null
        clearSavedConnection()
        PlaybackManager.getInstance(context).release()
        composeTestRule.waitForIdle()
    }

    @Test
    fun productionHomeScreenRetryRepeatsTheFailedRequestAndRestoresConnectedState() {
        val feedAttempts = AtomicInteger()
        val retryAllowed = AtomicBoolean(false)
        val viewModel = createViewModel { _, authorization ->
            assertEquals("Bearer qa-device-token", authorization)
            feedAttempts.incrementAndGet()
            if (!retryAllowed.get()) throw IOException("connection refused")
            BackendHomeFeedResponse()
        }
        assertTrue(
            viewModel.saveCompanionBackend(
                "http://127.0.0.1:32300",
                "qa-device-token",
                "qa-device"
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onStartPairing = {},
                    onNavigateToArtist = { _, _ -> },
                    onNavigateToAlbum = { _, _ -> },
                    onNavigateToCustomPlaylist = { _, _, _, _, _, _ -> }
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            feedAttempts.get() > 0 && viewModel.homeFeedFailure.value != null
        }
        assertTrue(feedAttempts.get() > 0)
        assertTrue("The failed Home request is retained separately from general action messages", viewModel.homeFeedFailure.value != null)
        assertTrue(
            viewModel.companionConnectionStatus.value in setOf(
                CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE,
                CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE
            )
        )
        val attemptsBeforeRetry = feedAttempts.get()
        val feedFailureBeforeActionError = requireNotNull(viewModel.homeFeedFailure.value)
        val connectionFailureBeforeActionError = viewModel.connectionErrorMessage.value
        viewModel.postErrorMessage("The selected radio found no matching tracks.")
        viewModel.clearActionErrorMessage()
        assertEquals(feedFailureBeforeActionError, viewModel.homeFeedFailure.value)
        assertEquals(connectionFailureBeforeActionError, viewModel.connectionErrorMessage.value)

        retryAllowed.set(true)
        composeTestRule.onNodeWithTag("home_retry_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            feedAttempts.get() > attemptsBeforeRetry &&
                viewModel.companionConnectionStatus.value == CompanionConnectionStatus.CONNECTED &&
                viewModel.connectionErrorMessage.value == null &&
                viewModel.homeFeedFailure.value == null
        }
        assertTrue("Retry must start another Home feed request", feedAttempts.get() > attemptsBeforeRetry)
        assertEquals(CompanionConnectionStatus.CONNECTED, viewModel.companionConnectionStatus.value)
        assertEquals(null, viewModel.connectionErrorMessage.value)
        assertEquals(null, viewModel.homeFeedFailure.value)
        composeTestRule.onNodeWithTag("home_retry_button").assertDoesNotExist()
    }

    @Test
    fun productionBackendClientSurfacesHttp503ThenParsesSuccessfulHomeRetry() {
        val server = LocalHomeFeedServer(failFirstHomeFeedRequest = true)
        homeFeedServer = server
        val backendUrl = "http://127.0.0.1:${server.port}"
        val service = BackendClientManager.getApiService(backendUrl)
        val firstFailure = runCatching {
            runBlocking(Dispatchers.IO) { service.getHomeFeed("Bearer qa-device-token") }
        }.exceptionOrNull()
        assertTrue("HTTP 503 must be surfaced as a Retrofit failure", firstFailure is HttpException)
        assertEquals(503, (firstFailure as HttpException).code())

        val recoveredFeed = runBlocking(Dispatchers.IO) {
            service.getHomeFeed("Bearer qa-device-token")
        }
        assertEquals(BackendHomeFeedResponse(), recoveredFeed)
        assertEquals("The retry must make a second real HTTP request", 2, server.homeFeedRequests.get())
        assertTrue("The production client must send the explicit bearer credential", server.homeFeedAuthorizationValid.get())
    }

    @Test
    fun cancelledSearchCannotClearLoadingStateForTheCurrentQuery() {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val firstRequestCleanupStarted = CompletableDeferred<Unit>()
        val releaseFirstRequestCleanup = CompletableDeferred<Unit>()
        val firstRequestCleanupFinished = CompletableDeferred<Unit>()
        val secondRequestStarted = CompletableDeferred<Unit>()
        val secondRequestResult = CompletableDeferred<List<PlexMetadata>>()
        val viewModel = createViewModel(
            searchDebounceMillis = 0,
            searchResultsOverride = { query ->
                when (query) {
                    "first" -> try {
                        firstRequestStarted.complete(Unit)
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            firstRequestCleanupStarted.complete(Unit)
                            releaseFirstRequestCleanup.await()
                            firstRequestCleanupFinished.complete(Unit)
                        }
                    }
                    "second" -> {
                        secondRequestStarted.complete(Unit)
                        secondRequestResult.await()
                    }
                    else -> error("Unexpected search query: $query")
                }
            }
        ) { _, _ -> BackendHomeFeedResponse() }

        composeTestRule.setContent {
            MyApplicationTheme { Box(Modifier.size(1.dp)) }
        }
        viewModel.search("first")
        composeTestRule.waitUntil(timeoutMillis = 10_000) { firstRequestStarted.isCompleted }
        assertTrue(viewModel.isSearchLoading.value)

        viewModel.search("second")
        composeTestRule.waitUntil(timeoutMillis = 10_000) { secondRequestStarted.isCompleted }
        composeTestRule.waitUntil(timeoutMillis = 10_000) { firstRequestCleanupStarted.isCompleted }
        assertTrue("The current query should still show loading before old-request cleanup", viewModel.isSearchLoading.value)

        releaseFirstRequestCleanup.complete(Unit)
        composeTestRule.waitUntil(timeoutMillis = 10_000) { firstRequestCleanupFinished.isCompleted }
        composeTestRule.waitForIdle()
        assertTrue("Finishing a canceled request must not hide the current query's progress", viewModel.isSearchLoading.value)
        assertNull("A canceled query must not replace the current query's error state", viewModel.searchError.value)
        assertTrue(viewModel.searchResults.value.isEmpty())

        val expectedTrack = PlexMetadata(ratingKey = "second-result", title = "Current result", type = "track")
        secondRequestResult.complete(listOf(expectedTrack))
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.searchResults.value.singleOrNull()?.ratingKey == expectedTrack.ratingKey &&
                !viewModel.isSearchLoading.value
        }
        assertEquals(listOf(expectedTrack), viewModel.searchResults.value)
    }

    @Test
    fun failedArtistLoadDoesNotMasqueradeAsEmptyAndCanRetryWhileAlbumsStillLoad() {
        val albumRequestStarted = CompletableDeferred<Unit>()
        val releaseAlbumRequest = CompletableDeferred<List<PlexMetadata>>()
        val artistAttempts = AtomicInteger()
        val viewModel = createViewModel(
            loadLibraryArtistsOverride = { section ->
                assertEquals("test-section", section)
                if (artistAttempts.incrementAndGet() == 1) {
                    throw IOException("private metadata detail")
                }
                listOf(PlexMetadata(ratingKey = "artist-1", title = "Recovered Artist", type = "artist"))
            },
            loadLibraryAlbumsOverride = { section ->
                assertEquals("test-section", section)
                albumRequestStarted.complete(Unit)
                releaseAlbumRequest.await()
            }
        ) { _, _ -> BackendHomeFeedResponse() }

        composeTestRule.setContent {
            MyApplicationTheme { Box(Modifier.size(1.dp)) }
        }
        viewModel.selectLibrary("test-section", "Test Library")

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            albumRequestStarted.isCompleted &&
                viewModel.artistLoadError.value != null &&
                !viewModel.isLoadingArtists.value &&
                viewModel.isLoadingAlbums.value
        }
        val failure = requireNotNull(viewModel.artistLoadError.value)
        assertTrue("Raw request detail must not reach the user-facing state", !failure.contains("private metadata detail"))
        assertTrue("A failed request must not be represented as a successful empty library", viewModel.artists.value.isEmpty())
        assertTrue("The album load must remain active after the artist request ends", viewModel.isLoadingAlbums.value)
        assertTrue("Library category loading must not change Search's independent loading state", !viewModel.isSearchLoading.value)

        viewModel.loadArtists()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            artistAttempts.get() == 2 &&
                viewModel.artists.value.singleOrNull()?.ratingKey == "artist-1" &&
                viewModel.artistLoadError.value == null &&
                !viewModel.isLoadingArtists.value
        }
        assertTrue("Retrying Artists must not cancel or hide the Albums load", viewModel.isLoadingAlbums.value)

        releaseAlbumRequest.complete(emptyList())
        composeTestRule.waitUntil(timeoutMillis = 10_000) { !viewModel.isLoadingAlbums.value }
    }

    @Test
    fun failedRecentlyAddedLoadKeepsHomeShelfAndRetryRecoversIt() {
        val attempts = AtomicInteger()
        val retryStarted = CompletableDeferred<Unit>()
        val releaseRetry = CompletableDeferred<List<PlexMetadata>>()
        val previousAlbum = PlexMetadata(ratingKey = "previous-album", title = "Previously loaded album", type = "album")
        val recoveredAlbum = PlexMetadata(ratingKey = "recovered-album", title = "Recovered album", type = "album")
        val viewModel = createViewModel(
            loadLibraryArtistsOverride = { emptyList() },
            loadLibraryAlbumsOverride = { emptyList() },
            loadRecentlyAddedOverride = { section ->
                assertEquals("test-section", section)
                when (attempts.incrementAndGet()) {
                    1 -> listOf(previousAlbum)
                    2 -> throw IOException("private server detail")
                    3 -> {
                        retryStarted.complete(Unit)
                        releaseRetry.await()
                    }
                    else -> error("Unexpected Recently Added request")
                }
            }
        ) { _, _ -> BackendHomeFeedResponse() }
        viewModel.selectLibrary("test-section", "Test Library")

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            attempts.get() == 1 && viewModel.recentlyAddedAlbums.value == listOf(previousAlbum)
        }
        viewModel.loadRecentlyAdded()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            attempts.get() == 2 && viewModel.recentlyAddedLoadError.value != null
        }
        assertEquals("A failed refresh must retain the previously loaded shelf", listOf(previousAlbum), viewModel.recentlyAddedAlbums.value)
        assertTrue(
            "Raw server detail must not reach the user-facing state",
            !requireNotNull(viewModel.recentlyAddedLoadError.value).contains("private server detail")
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onStartPairing = {},
                    onNavigateToArtist = { _, _ -> },
                    onNavigateToAlbum = { _, _ -> },
                    onNavigateToCustomPlaylist = { _, _, _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("home_retry_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_retry_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            retryStarted.isCompleted && viewModel.isLoadingRecentlyAdded.value
        }
        assertEquals("Retry progress must not discard the last good shelf", listOf(previousAlbum), viewModel.recentlyAddedAlbums.value)
        assertTrue("The banner should remain until the retry succeeds", viewModel.recentlyAddedLoadError.value != null)
        composeTestRule.onNodeWithTag("home_retry_button").assertIsNotEnabled()
        composeTestRule.onNodeWithText(context.getString(com.example.R.string.home_retrying)).assertExists()

        releaseRetry.complete(listOf(recoveredAlbum))
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.recentlyAddedAlbums.value == listOf(recoveredAlbum) &&
                viewModel.recentlyAddedLoadError.value == null &&
                !viewModel.isLoadingRecentlyAdded.value
        }
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(recoveredAlbum.title).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(recoveredAlbum.title).performScrollTo().assertExists()
        composeTestRule.onNodeWithTag("home_retry_button").assertDoesNotExist()
    }

    @Test
    fun changingLibrariesDoesNotShowThePreviousLibraryShelfAfterFailure() {
        val attempts = AtomicInteger()
        val previousLibraryAlbum = PlexMetadata(
            ratingKey = "previous-library-album",
            title = "Previous library album",
            type = "album"
        )
        val viewModel = createViewModel(
            loadLibraryArtistsOverride = { emptyList() },
            loadLibraryAlbumsOverride = { emptyList() },
            loadRecentlyAddedOverride = { section ->
                when (section) {
                    "first-library" -> listOf(previousLibraryAlbum)
                    "second-library" -> {
                        attempts.incrementAndGet()
                        throw IOException("private second-library detail")
                    }
                    else -> error("Unexpected library section: $section")
                }
            }
        ) { _, _ -> BackendHomeFeedResponse() }

        composeTestRule.setContent { MyApplicationTheme { Box(Modifier.size(1.dp)) } }
        viewModel.selectLibrary("first-library", "First Library")
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.recentlyAddedAlbums.value == listOf(previousLibraryAlbum) &&
                !viewModel.isLoadingRecentlyAdded.value
        }

        viewModel.selectLibrary("second-library", "Second Library")
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            attempts.get() == 1 &&
                viewModel.recentlyAddedLoadError.value != null &&
                !viewModel.isLoadingRecentlyAdded.value
        }
        assertTrue(
            "A failed load for another library must not expose the previous library's albums",
            viewModel.recentlyAddedAlbums.value.isEmpty()
        )
    }

    @Test
    fun metadataPlaybackUsesLocalizedUnknownLabelsAndAlbumArtistFallback() {
        val viewModel = createViewModel { _, _ -> BackendHomeFeedResponse() }
        val metadata = PlexMetadata(
            ratingKey = "track-with-partial-metadata",
            title = "Track",
            parentTitle = "Album title",
            albumArtist = "Album artist",
            media = listOf(PlexMedia(listOf(PlexPart("/track"))))
        )

        val albumArtistFallback = requireNotNull(viewModel.mapMetadataToTrackItem(metadata))
        assertEquals("Album artist", albumArtistFallback.artist)
        assertEquals("Album title", albumArtistFallback.album)

        val unknownFallback = requireNotNull(
            viewModel.mapMetadataToTrackItem(metadata.copy(parentTitle = " ", albumArtist = null))
        )
        assertEquals(context.getString(com.example.R.string.common_unknown_artist), unknownFallback.artist)
        assertEquals(context.getString(com.example.R.string.common_unknown_album), unknownFallback.album)
    }

    private fun createViewModel(
        searchResultsOverride: (suspend (String) -> List<PlexMetadata>)? = null,
        searchDebounceMillis: Long = 150,
        loadLibraryArtistsOverride: (suspend (String) -> List<PlexMetadata>)? = null,
        loadLibraryAlbumsOverride: (suspend (String) -> List<PlexMetadata>)? = null,
        loadRecentlyAddedOverride: (suspend (String) -> List<PlexMetadata>)? = null,
        fetchHomeFeed: suspend (String, String) -> BackendHomeFeedResponse
    ): MusicViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = ViewModelStore()
        viewModelStore = store
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(MusicViewModel::class.java))
                return MusicViewModel(
                    application,
                    fetchHomeFeed,
                    searchResultsOverride,
                    searchDebounceMillis,
                    loadLibraryArtistsOverride,
                    loadLibraryAlbumsOverride,
                    loadRecentlyAddedOverride
                ) as T
            }
        }
        return ViewModelProvider(owner, factory)[MusicViewModel::class.java]
    }

    private fun clearSavedConnection() {
        PlexSettingsManager(context).apply {
            clearCompanionCredential()
            baseUrl = ""
            token = ""
            sectionId = ""
        }
    }

    private class LocalHomeFeedServer(
        private val failFirstHomeFeedRequest: Boolean = false
    ) : Closeable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port: Int = server.address.port
        val homeFeedRequests = AtomicInteger()
        val homeFeedAuthorizationValid = AtomicBoolean(true)
        init {
            server.createContext("/") { exchange ->
                val isHomeFeedRequest = exchange.requestURI.path == "/api/v1/music/home"
                val isReachabilityProbe = exchange.requestHeaders.getFirst("X-SpotAmp-Reachability-Check") == "true"
                val response = if (isHomeFeedRequest && isReachabilityProbe) {
                    if (exchange.requestHeaders.getFirst("Authorization") != "Bearer qa-device-token") {
                        homeFeedAuthorizationValid.set(false)
                    }
                    200 to "{}"
                } else if (isHomeFeedRequest) {
                    val feedRequest = homeFeedRequests.incrementAndGet()
                    if (exchange.requestHeaders.getFirst("Authorization") != "Bearer qa-device-token") {
                        homeFeedAuthorizationValid.set(false)
                    }
                    if (failFirstHomeFeedRequest && feedRequest == 1) {
                        503 to "{\"error\":\"temporarily unavailable\"}"
                    } else {
                        200 to "{}"
                    }
                } else {
                    404 to "{\"error\":\"not found\"}"
                }
                val body = response.second.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
                exchange.sendResponseHeaders(response.first, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
        }

        override fun close() {
            server.stop(0)
        }
    }
}
