package com.example

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.example.data.BackendHomeFeedResponse
import com.example.data.PlexMetadata
import com.example.data.PlexSettingsManager
import com.example.ui.MainAppScreen
import com.example.ui.MusicViewModel
import com.example.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/** Ensures Library category and scroll state survive opening artist or album details. */
@RunWith(AndroidJUnit4::class)
class LibraryNavigationStateDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }
    private val artistFixtures = (0 until 48).map { index ->
        PlexMetadata(
            ratingKey = "qa-backstack-artist-$index",
            title = "QA Backstack Artist ${index.toString().padStart(2, '0')}",
            type = "artist"
        )
    }
    private val albumFixtures = (0 until 48).map { index ->
        PlexMetadata(
            ratingKey = "qa-backstack-album-$index",
            title = "QA Backstack Album ${index.toString().padStart(2, '0')}",
            type = "album",
            parentTitle = "QA Backstack Artist"
        )
    }

    private lateinit var settings: PlexSettingsManager
    private lateinit var detailPlexServer: LoopbackPlexServer
    private lateinit var viewModel: MusicViewModel
    private var viewModelStore: ViewModelStore? = null
    private var originalBaseUrl: String? = null
    private var originalToken: String? = null
    private var originalSectionId: String? = null
    private var originalLibraryName: String? = null
    private var originalOfflineOnly: Boolean? = null

    @Before
    fun setUp() {
        runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
        }

        settings = PlexSettingsManager(context)
        assertTrue("The physical QA profile must remain unpaired during the Plex fixture audit", !settings.isCompanionConfigured)
        originalBaseUrl = settings.baseUrl
        originalToken = settings.token
        originalSectionId = settings.sectionId
        originalLibraryName = settings.libraryName
        originalOfflineOnly = settings.offlineOnly
        detailPlexServer = LoopbackPlexServer()
        settings.baseUrl = detailPlexServer.baseUrl
        settings.token = "qa-route-audit-token"
        settings.offlineOnly = false

        viewModel = createViewModel()
        viewModel.selectLibrary("qa-backstack-section", "QA Backstack Library")

        composeRule.setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }

    @After
    fun tearDown() {
        viewModelStore?.clear()
        viewModelStore = null
        if (::detailPlexServer.isInitialized) detailPlexServer.close()
        originalBaseUrl?.let { settings.baseUrl = it }
        originalToken?.let { settings.token = it }
        originalSectionId?.let { settings.sectionId = it }
        originalLibraryName?.let { settings.libraryName = it }
        originalOfflineOnly?.let { settings.offlineOnly = it }
        originalSectionId = null
        originalLibraryName = null
        originalOfflineOnly = null
        originalBaseUrl = null
        originalToken = null
        composeRule.waitForIdle()
    }

    @Test
    fun artistGridPositionReturnsAfterOpeningArtistDetail() {
        val artist = artistFixtures[35]
        val detailAlbumTitle = "QA Detail Album 2026"
        val detailTrackTitle = "QA Detail Track 01"
        openLibraryCategory(context.getString(R.string.library_category_artists))
        composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.artists.value.size == artistFixtures.size }

        composeRule.onNodeWithTag("library_artists_grid")
            .performScrollToNode(hasText(artist.title))
        capturePhysicalScreenshot("library-artists-scrolled")
        composeRule.onNodeWithTag("artist_item_${artist.ratingKey}")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            viewModel.currentArtistMetadata.value?.ratingKey == artist.ratingKey &&
                viewModel.currentArtistAlbums.value.isNotEmpty() &&
                viewModel.currentArtistTracks.value.isNotEmpty() &&
                !viewModel.isLoading.value
        }
        assertEquals(artist.title, viewModel.currentArtistMetadata.value?.title)
        assertEquals(detailAlbumTitle, viewModel.currentArtistAlbums.value.single().title)
        assertEquals(detailTrackTitle, viewModel.currentArtistTracks.value.first().title)
        assertTrue("Plex detail requests must use the temporary QA token", detailPlexServer.plexTokenValid.get())
        composeRule.onNodeWithTag("artist_detail_content")
            .performScrollToNode(hasText(detailAlbumTitle))
        composeRule.onNodeWithText(detailAlbumTitle).assertIsDisplayed()
        composeRule.waitForIdle()
        capturePhysicalScreenshot("library-artist-detail")
        composeRule.onNodeWithTag("artist_detail_content")
            .performScrollToNode(hasContentDescription(context.getString(R.string.details_back_accessibility)))
        capturePhysicalScreenshot("library-artist-detail-top")
        composeRule.onNode(hasContentDescription(context.getString(R.string.details_back_accessibility)))
            .performClick()

        composeRule.onNodeWithTag("artist_item_${artist.ratingKey}").assertIsDisplayed()
        capturePhysicalScreenshot("library-artists-return-position")
    }

    @Test
    fun albumListPositionReturnsAfterOpeningAlbumDetail() {
        val album = albumFixtures[35]
        val detailTrackTitle = "QA Detail Track 01"
        openLibraryCategory(context.getString(R.string.library_category_albums))
        composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.albums.value.size == albumFixtures.size }

        composeRule.onNodeWithTag("library_view_mode_toggle").performClick()
        composeRule.onNodeWithTag("library_albums_list")
            .performScrollToNode(hasText(album.title))
        capturePhysicalScreenshot("library-albums-scrolled")
        composeRule.onNodeWithText(album.title).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(album.title).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            viewModel.currentAlbumTracks.value.isNotEmpty() && !viewModel.isLoading.value
        }
        assertEquals(detailTrackTitle, viewModel.currentAlbumTracks.value.first().title)
        assertTrue("Plex detail requests must use the temporary QA token", detailPlexServer.plexTokenValid.get())
        composeRule.onNodeWithTag("album_detail_content")
            .performScrollToNode(hasText(detailTrackTitle))
        composeRule.onNodeWithText(detailTrackTitle).assertIsDisplayed()
        composeRule.waitForIdle()
        capturePhysicalScreenshot("library-album-detail")
        composeRule.onNodeWithTag("album_detail_content")
            .performScrollToNode(hasContentDescription(context.getString(R.string.details_back_accessibility)))

        composeRule.onNode(hasContentDescription(context.getString(R.string.details_back_accessibility)))
            .performClick()

        composeRule.onNodeWithText(album.title).assertIsDisplayed()
        capturePhysicalScreenshot("library-albums-return-position")
    }

    @Test
    fun libraryMenuPositionReturnsAfterOpeningCategory() {
        val albumsTitle = context.getString(R.string.library_category_albums)
        composeRule.onNode(navigationTab("Library")).performClick()
        val categoryList = composeRule.onNodeWithTag("library_categories_list")
        val albumsRow = composeRule.onNodeWithText(albumsTitle)
        val originalTop = albumsRow.fetchSemanticsNode().boundsInRoot.top

        categoryList.performTouchInput {
            swipeUp(startY = height * 0.72f, endY = height * 0.62f, durationMillis = 250)
        }
        composeRule.waitForIdle()
        val scrolledTop = composeRule.onNodeWithText(albumsTitle).fetchSemanticsNode().boundsInRoot.top
        assertTrue("The Library category list should scroll before opening Albums", scrolledTop < originalTop - 16f)
        capturePhysicalScreenshot("library-categories-scrolled")
        composeRule.onNodeWithText(albumsTitle).assertIsDisplayed().performClick()

        composeRule.onNode(hasContentDescription(context.getString(R.string.library_back_accessibility)))
            .performClick()

        val returnedTop = composeRule.onNodeWithText(albumsTitle).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.top
        assertEquals("The Library menu should return to its prior scroll offset", scrolledTop, returnedTop, 1f)
        capturePhysicalScreenshot("library-categories-return-position")
    }

    private fun openLibraryCategory(title: String) {
        composeRule.onNode(navigationTab("Library")).performClick()
        composeRule.onNodeWithTag("library_categories_list").performScrollToNode(hasText(title))
        composeRule.onNodeWithText(title).performClick()
    }

    private fun capturePhysicalScreenshot(name: String) {
        composeRule.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("S24 screenshot capture returned no image for $name")
        val directory = File(context.cacheDir, "wave5-physical-audit")
        check(directory.isDirectory || directory.mkdirs()) { "Unable to create ${directory.absolutePath}" }
        val output = File(directory, "$name.png")
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to encode screenshot ${output.name}"
            }
        }
        bitmap.recycle()
        Log.i("Wave5PhysicalAudit", "CAPTURE ${output.absolutePath} (${output.length()} bytes)")
    }

    private fun navigationTab(label: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            (hasText(label) or hasContentDescription(label))

    private fun createViewModel(): MusicViewModel {
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
                    fetchCompanionHomeFeed = { _, _ -> BackendHomeFeedResponse() },
                    loadLibraryArtistsOverride = { artistFixtures },
                    loadLibraryAlbumsOverride = { albumFixtures },
                    loadRecentlyAddedOverride = { emptyList() }
                ) as T
            }
        }
        return ViewModelProvider(owner, factory)[MusicViewModel::class.java]
    }

    private inner class LoopbackPlexServer : Closeable {
        private val serverSocket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val running = AtomicBoolean(true)
        val baseUrl = "http://127.0.0.1:${serverSocket.localPort}"
        val plexTokenValid = AtomicBoolean(true)
        private val acceptThread = Thread({
            while (running.get()) {
                val client = try {
                    serverSocket.accept()
                } catch (_: Exception) {
                    continue
                }
                runCatching { client.use(::respond) }
            }
        }, "spotamp-library-detail-device-server").apply {
            isDaemon = true
            start()
        }

        private fun respond(client: Socket) {
            client.soTimeout = 5_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine().orEmpty()
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(":")
                if (separator > 0) {
                    headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
                }
            }
            if (headers["x-plex-token"] != "qa-route-audit-token") plexTokenValid.set(false)

            val path = requestLine.split(" ").getOrNull(1)?.substringBefore("?").orEmpty()
            val body = when {
                path == "/library/metadata/${artistFixtures[35].ratingKey}" ->
                    container(metadata(artistFixtures[35].ratingKey, artistFixtures[35].title, "artist"))
                path == "/library/metadata/${artistFixtures[35].ratingKey}/children" ->
                    container(metadata("qa-detail-album-for-artist-35", "QA Detail Album 2026", "album", artistFixtures[35].title, 2026))
                path == "/library/metadata/${albumFixtures[35].ratingKey}/children" ->
                    trackContainer(albumFixtures[35].ratingKey, albumFixtures[35].title, albumFixtures[35].parentTitle.orEmpty())
                path == "/library/metadata/qa-detail-album-for-artist-35/children" ->
                    trackContainer("qa-detail-album-for-artist-35", "QA Detail Album 2026", artistFixtures[35].title)
                path == "/library/metadata/${albumFixtures[35].ratingKey}" ->
                    container(metadata(albumFixtures[35].ratingKey, albumFixtures[35].title, "album", albumFixtures[35].parentTitle.orEmpty(), 2024))
                else -> container("")
            }
            val payload = body.toByteArray(StandardCharsets.UTF_8)
            val output = BufferedOutputStream(client.getOutputStream())
            output.write("HTTP/1.1 200 OK\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Type: application/json; charset=utf-8\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Length: ${payload.size}\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write(payload)
            output.flush()
        }

        private fun container(metadata: String): String =
            """{"MediaContainer":{"Metadata":[${metadata}]}}"""

        private fun trackContainer(albumId: String, albumTitle: String, artistName: String): String {
            val tracks = (1..2).joinToString(",") { index ->
                """{"ratingKey":"qa-detail-track-$index","title":"QA Detail Track ${index.toString().padStart(2, '0')}","type":"track","index":$index,"duration":183000,"parentTitle":"$albumTitle","grandparentTitle":"$artistName","parentRatingKey":"$albumId","grandparentRatingKey":"${artistFixtures[35].ratingKey}"}"""
            }
            return """{"MediaContainer":{"Metadata":[${tracks}]}}"""
        }

        private fun metadata(
            ratingKey: String,
            title: String,
            type: String,
            parentTitle: String? = null,
            year: Int? = null
        ): String = buildString {
            append("{\"ratingKey\":\"$ratingKey\",\"key\":\"/library/metadata/$ratingKey\",\"title\":\"$title\",\"type\":\"$type\"")
            parentTitle?.let { append(",\"parentTitle\":\"$it\"") }
            year?.let { append(",\"year\":$it") }
            append("}")
        }

        override fun close() {
            running.set(false)
            runCatching { serverSocket.close() }
            runCatching { acceptThread.join(1_000) }
        }
    }
}
