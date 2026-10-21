package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.data.BackendHomeFeedResponse
import com.example.data.DownloadedTrackEntity
import com.example.data.MusicDatabase
import com.example.data.PlexMetadata
import com.example.data.PlexSettingsManager
import com.example.playback.PlaybackManager
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Warm Light captures for the actual Home, Library, and Downloads routes at compact sizes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class CoreRoutesAppearanceVisualTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private var viewModelStore: ViewModelStore? = null
    private var originalDownloads: List<com.example.data.DownloadedTrackEntity>? = null
    private var originalSettings: SavedSettings? = null

    private data class SavedSettings(
        val baseUrl: String,
        val token: String,
        val companionUrl: String,
        val companionToken: String,
        val companionDeviceId: String,
        val sectionId: String,
        val libraryName: String,
        val offlineOnly: Boolean,
        val activeTheme: String
    )

    @Before
    fun setUp() {
        runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        val settings = PlexSettingsManager(context)
        originalSettings = SavedSettings(
            baseUrl = settings.baseUrl,
            token = settings.token,
            companionUrl = settings.companionBackendUrl,
            companionToken = settings.companionBackendToken,
            companionDeviceId = settings.companionDeviceId,
            sectionId = settings.sectionId,
            libraryName = settings.libraryName,
            offlineOnly = settings.offlineOnly,
            activeTheme = settings.activeTheme
        )
        settings.apply {
            clearCompanionCredential()
            baseUrl = ""
            token = ""
            sectionId = ""
            libraryName = ""
            offlineOnly = false
            activeTheme = "Warm Light"
        }
        val dao = MusicDatabase.getDatabase(context).musicDao()
        originalDownloads = runBlocking(Dispatchers.IO) { dao.getDownloadedTracksList() }
        runBlocking(Dispatchers.IO) { dao.clearDownloadedTracks() }
    }

    @After
    fun tearDown() {
        viewModelStore?.clear()
        viewModelStore = null
        PlaybackManager.getInstance(context).release()
        val savedDownloads = originalDownloads.orEmpty()
        runBlocking(Dispatchers.IO) {
            val dao = MusicDatabase.getDatabase(context).musicDao()
            dao.clearDownloadedTracks()
            savedDownloads.forEach { dao.insertDownloadedTrack(it) }
        }
        originalDownloads = null
        originalSettings?.let { saved ->
            PlexSettingsManager(context).apply {
                baseUrl = saved.baseUrl
                token = saved.token
                if (saved.companionUrl.isNotBlank() && saved.companionToken.isNotBlank()) {
                    saveCompanionCredential(saved.companionUrl, saved.companionDeviceId, saved.companionToken)
                } else {
                    clearCompanionCredential()
                }
                sectionId = saved.sectionId
                libraryName = saved.libraryName
                offlineOnly = saved.offlineOnly
                activeTheme = saved.activeTheme
            }
        }
        originalSettings = null
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun homeWarmLightAtDefaultText_screenshot() = captureHome(
        fontScale = 1f,
        screenshot = "src/test/screenshots/home-warm-light-first-run-compact-100.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun homeWarmLightAtLargeText_screenshot() = captureHome(
        fontScale = 1.3f,
        screenshot = "src/test/screenshots/home-warm-light-first-run-compact-130.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun libraryWarmLightAtDefaultText_screenshot() = captureLibrary(
        fontScale = 1f,
        screenshot = "src/test/screenshots/library-warm-light-unpaired-compact-100.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun libraryWarmLightAtLargeText_screenshot() = captureLibrary(
        fontScale = 1.3f,
        screenshot = "src/test/screenshots/library-warm-light-unpaired-compact-130.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun populatedLibraryCategoriesAtDefaultText_screenshot() = captureLibraryCategories(
        fontScale = 1f,
        screenshot = "src/test/screenshots/library-warm-light-categories-compact-100.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun populatedLibraryCategoriesAtLargeText_screenshot() = captureLibraryCategories(
        fontScale = 1.3f,
        screenshot = "src/test/screenshots/library-warm-light-categories-compact-130.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun populatedArtistGridAtLargeText_screenshot() = captureArtistGrid(
        fontScale = 1.3f,
        screenshot = "src/test/screenshots/library-warm-light-artists-compact-130.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun downloadsWarmLightAtDefaultText_screenshot() = captureDownloads(
        fontScale = 1f,
        screenshot = "src/test/screenshots/downloads-warm-light-empty-compact-100.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun downloadsWarmLightAtLargeText_screenshot() = captureDownloads(
        fontScale = 1.3f,
        screenshot = "src/test/screenshots/downloads-warm-light-empty-compact-130.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun downloadedAlbumWarmLightAtLargeText_screenshot() = captureDownloadedAlbum(
        fontScale = 1.3f,
        screenshot = "src/test/screenshots/downloads-warm-light-album-compact-130.png"
    )

    private fun captureHome(fontScale: Float, screenshot: String) {
        val viewModel = createViewModel()
        showRoute(fontScale) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToSettings = {},
                onStartPairing = {},
                onNavigateToArtist = { _, _ -> },
                onNavigateToAlbum = { _, _ -> },
                onNavigateToCustomPlaylist = { _, _, _, _, _, _ -> }
            )
        }

        composeRule.waitUntil(10_000) {
            runCatching { composeRule.onNodeWithTag("home_empty_state").assertIsDisplayed() }
                .isSuccess
        }
        composeRule.onNodeWithTag("home_pairing_cta").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(com.example.R.string.home_section_stations))
            .assertDoesNotExist()
        composeRule.onRoot().captureRoboImage(filePath = screenshot)
    }

    private fun captureLibrary(fontScale: Float, screenshot: String) {
        val viewModel = createViewModel()
        showLibrary(viewModel, fontScale)

        composeRule.onNodeWithTag("library_title").assertIsDisplayed()
        composeRule.onNodeWithTag("library_pair_spotcore_action").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = screenshot)
    }

    private fun captureLibraryCategories(fontScale: Float, screenshot: String) {
        val viewModel = createViewModel(
            artists = listOf(PlexMetadata("m83", title = "M83", type = "artist")),
            albums = listOf(PlexMetadata("hurry-up", title = "Hurry Up, We're Dreaming", type = "album", parentTitle = "M83"))
        )
        viewModel.selectLibrary("warm-light-fixture", "QA Music Library")
        showLibrary(viewModel, fontScale)

        composeRule.onNodeWithTag("library_categories_list").assertIsDisplayed()
        composeRule.onNodeWithText("Artists").assertIsDisplayed()
        composeRule.onNodeWithText("Albums").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = screenshot)
    }

    private fun captureArtistGrid(fontScale: Float, screenshot: String) {
        val artist = PlexMetadata("m83", title = "M83", type = "artist")
        val viewModel = createViewModel(artists = listOf(artist))
        viewModel.selectLibrary("warm-light-fixture", "QA Music Library")
        showLibrary(viewModel, fontScale)
        composeRule.onNodeWithText("Artists").performClick()
        composeRule.waitUntil(10_000) {
            runCatching { composeRule.onNodeWithTag("artist_item_${artist.ratingKey}").assertIsDisplayed() }
                .isSuccess
        }
        composeRule.onNodeWithTag("library_category_sort").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = screenshot)
    }

    private fun showLibrary(viewModel: MusicViewModel, fontScale: Float) = showRoute(fontScale) {
        LibraryScreen(
            viewModel = viewModel,
            onNavigateToArtist = { _, _ -> },
            onNavigateToAlbum = { _, _ -> },
            onNavigateToPlaylist = { _, _ -> },
            onStartPairing = {},
            onNavigateToSettings = {}
        )
    }

    private fun captureDownloads(fontScale: Float, screenshot: String) {
        val viewModel = createViewModel()
        showRoute(fontScale) { DownloadsScreen(viewModel = viewModel, onStartPairing = {}) }

        composeRule.waitUntil(10_000) {
            runCatching { composeRule.onNodeWithTag("downloads_pair_spotcore_action").assertIsDisplayed() }
                .isSuccess
        }
        composeRule.onNodeWithTag("downloads_pair_spotcore_action").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = screenshot)
    }

    private fun captureDownloadedAlbum(fontScale: Float, screenshot: String) {
        val track = DownloadedTrackEntity(
            ratingKey = "warm-light-fixture-track",
            title = "Midnight City",
            artist = "M83",
            album = "Dreaming",
            key = "/library/parts/warm-light-fixture-track",
            thumb = "",
            duration = 241_000L,
            fileSize = 4_000_000L,
            localPath = context.cacheDir.resolve("missing-warm-light-fixture.mp3").absolutePath,
            status = "completed"
        )
        runBlocking(Dispatchers.IO) {
            MusicDatabase.getDatabase(context).musicDao().insertDownloadedTrack(track)
        }
        val viewModel = createViewModel()
        showRoute(fontScale) { DownloadsScreen(viewModel = viewModel, onStartPairing = {}) }

        composeRule.waitUntil(10_000) {
            runCatching { composeRule.onNodeWithTag("download_album_dreaming").assertIsDisplayed() }
                .isSuccess
        }
        composeRule.onNodeWithText("Dreaming").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = screenshot)
    }

    private fun showRoute(fontScale: Float, content: @Composable () -> Unit) {
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, fontScale)) {
                MyApplicationTheme(activeTheme = "Warm Light") {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        content()
                    }
                }
            }
        }
    }

    private fun createViewModel(
        artists: List<PlexMetadata> = emptyList(),
        albums: List<PlexMetadata> = emptyList()
    ): MusicViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = ViewModelStore().also { viewModelStore = it }
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(MusicViewModel::class.java))
                return MusicViewModel(
                    application = application,
                    fetchCompanionHomeFeed = { _, _ -> BackendHomeFeedResponse() },
                    searchResultsOverride = { emptyList() },
                    searchDebounceMillis = 0,
                    loadLibraryArtistsOverride = { artists },
                    loadLibraryAlbumsOverride = { albums },
                    loadRecentlyAddedOverride = { emptyList() }
                ) as T
            }
        }
        return ViewModelProvider(owner, factory)[MusicViewModel::class.java]
    }
}
