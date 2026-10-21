package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.R
import com.example.data.BackendHomeFeedResponse
import com.example.data.PlexMetadata
import com.example.data.PlexSettingsManager
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

/** Synthetic host screenshots for the production Search route and its offline-state component. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class SearchStateScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private var viewModelStore: ViewModelStore? = null

    @Before
    fun setUp() {
        runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        clearConnectionSettings()
    }

    @After
    fun tearDown() {
        viewModelStore?.clear()
        viewModelStore = null
        clearConnectionSettings()
        composeRule.waitForIdle()
    }

    @Test
    fun firstRunSearchScreen_screenshot() {
        val viewModel = createViewModel { emptyList() }
        showSearch(viewModel)

        composeRule.onNodeWithText(context.getString(R.string.search_empty_first_run)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.library_pair_spotcore_action)).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/search-first-run.png")
    }

    @Test
    fun mixedSearchResultsScreen_screenshot() {
        val artist = PlexMetadata("artist-1", title = "M83", type = "artist")
        val album = PlexMetadata(
            "album-1",
            title = "Hurry Up, We're Dreaming",
            type = "album",
            parentTitle = "M83"
        )
        val track = PlexMetadata(
            "track-1",
            title = "Midnight City",
            type = "track",
            parentTitle = "Hurry Up, We're Dreaming",
            grandparentTitle = "M83"
        )
        val viewModel = createViewModel { listOf(artist, album, track) }
        showSearch(viewModel, initialQuery = "midnight")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onNodeWithText(track.title).fetchSemanticsNodeOrNull() != null
        }
        composeRule.onNodeWithText(context.getString(R.string.search_section_artists)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.search_section_albums)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.search_section_songs)).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(
                R.string.search_quick_link_count,
                context.getString(R.string.search_section_songs),
                1
            )
        ).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/search-results.png")
    }

    @Test
    fun loadingSearchScreen_screenshot() {
        val resultGate = CompletableDeferred<List<PlexMetadata>>()
        val searchStarted = CompletableDeferred<Unit>()
        PlexSettingsManager(context).apply {
            baseUrl = "http://127.0.0.1:1"
            token = "host-screenshot-fixture"
        }
        val viewModel = createViewModel(loadLibrariesOverride = {
            throw IOException("QA library-load failure fixture")
        }) {
            searchStarted.complete(Unit)
            resultGate.await()
        }
        showSearch(viewModel, initialQuery = "moonlight")

        val loadingMessage = context.getString(R.string.search_loading)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            searchStarted.isCompleted &&
                viewModel.libraryLoadError.value != null &&
                !viewModel.isLoading.value
        }
        assertFalse("Search must not take over the shared route loading flag", viewModel.isLoading.value)
        assertTrue("Search loading must survive completion of the unrelated library request", viewModel.isSearchLoading.value)
        composeRule.onNodeWithText(loadingMessage).assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/search-loading.png")
    }

    @Test
    fun noResultsSearchScreen_screenshot() {
        // This local-only endpoint marks the fixture as configured. Its library-list request is
        // deliberately unreachable; the injected search result keeps this visual test offline.
        PlexSettingsManager(context).apply {
            baseUrl = "http://127.0.0.1:1"
            token = "host-screenshot-fixture"
        }
        val viewModel = createViewModel { emptyList() }
        showSearch(viewModel, initialQuery = "moonlight")
        val noResults = context.getString(R.string.search_no_matches, "moonlight")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onNodeWithText(noResults).fetchSemanticsNodeOrNull() != null
        }
        composeRule.onNodeWithText(context.getString(R.string.search_try_another_query)).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/search-no-results.png")
    }

    @Test
    fun unavailableSearchScreen_screenshot() {
        val viewModel = createViewModel { throw IOException("private host fixture detail") }
        showSearch(viewModel, initialQuery = "moonlight")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onNodeWithText(context.getString(R.string.search_unavailable_title))
                .fetchSemanticsNodeOrNull() != null
        }
        composeRule.onNodeWithText(context.getString(R.string.search_retry)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_open_settings)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.library_pair_spotcore_action)).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/search-unavailable.png")
    }

    @Test
    fun downloadedOnlyEmptySearchScreen_screenshot() {
        val track = PlexMetadata(
            "track-1",
            title = "Midnight City",
            type = "track",
            parentTitle = "Hurry Up, We're Dreaming",
            grandparentTitle = "M83"
        )
        PlexSettingsManager(context).offlineOnly = true
        val viewModel = createViewModel { listOf(track) }
        showSearch(viewModel, initialQuery = "midnight")
        val noDownloads = context.getString(R.string.search_no_downloaded_matches, "midnight")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onNodeWithText(noDownloads).fetchSemanticsNodeOrNull() != null
        }
        composeRule.onNodeWithText(context.getString(R.string.search_show_all_songs)).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/search-downloaded-only-empty.png")
    }

    @Test
    fun offlineCachedResultsNotice_screenshot() {
        composeRule.setContent {
            MyApplicationTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SearchOfflineResultsNotice()
                        SearchSectionHeader(
                            title = context.getString(R.string.search_section_songs),
                            items = listOf(PlexMetadata("track-1", title = "Midnight City", type = "track")),
                            onNavigateToResults = { _, _ -> }
                        )
                        SearchItemRow(
                            title = "Midnight City",
                            subtitle = "M83 • Hurry Up, We're Dreaming",
                            thumb = null,
                            isCircle = false,
                            baseUrl = "",
                            token = "",
                            isDownloaded = true,
                            onMoreClick = {},
                            onClick = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.connection_offline_library)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.connection_offline_body)).assertIsDisplayed()
        composeRule.onNodeWithText("Midnight City").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/search-offline-cached-results.png")
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun warmLightFirstRunSearchAtDefaultText_screenshot() = captureWarmLightSearch(
        fontScale = 1f,
        firstRun = true,
        screenshot = "src/test/screenshots/search-warm-light-first-run-compact-100.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun warmLightFirstRunSearchAtLargeText_screenshot() = captureWarmLightSearch(
        fontScale = 1.3f,
        firstRun = true,
        screenshot = "src/test/screenshots/search-warm-light-first-run-compact-130.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun warmLightSearchResultsAtDefaultText_screenshot() = captureWarmLightSearch(
        fontScale = 1f,
        firstRun = false,
        screenshot = "src/test/screenshots/search-warm-light-results-compact-100.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun warmLightSearchResultsAtLargeText_screenshot() = captureWarmLightSearch(
        fontScale = 1.3f,
        firstRun = false,
        screenshot = "src/test/screenshots/search-warm-light-results-compact-130.png"
    )

    private fun captureWarmLightSearch(fontScale: Float, firstRun: Boolean, screenshot: String) {
        val track = PlexMetadata(
            "track-1",
            title = "Midnight City",
            type = "track",
            parentTitle = "Hurry Up, We're Dreaming",
            grandparentTitle = "M83"
        )
        val viewModel = createViewModel { if (firstRun) emptyList() else listOf(track) }
        showSearch(
            viewModel = viewModel,
            initialQuery = if (firstRun) "" else "midnight",
            activeTheme = "Warm Light",
            fontScale = fontScale
        )
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.search_downloaded_only_accessibility)
        ).assertIsDisplayed()

        if (firstRun) {
            composeRule.onNodeWithText(context.getString(R.string.search_empty_first_run)).assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.library_pair_spotcore_action)).assertIsDisplayed()
        } else {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onNodeWithText(track.title).fetchSemanticsNodeOrNull() != null
            }
            composeRule.onNodeWithText(context.getString(R.string.search_section_songs)).assertIsDisplayed()
            composeRule.onNodeWithTag("search_results_jump_songs").assertIsDisplayed()
            composeRule.onNodeWithText(track.title).assertIsDisplayed()
        }
        composeRule.onRoot().captureRoboImage(filePath = screenshot)
    }

    private fun showSearch(
        viewModel: MusicViewModel,
        initialQuery: String = "",
        activeTheme: String = "Default Dark",
        fontScale: Float = 1f
    ) {
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, fontScale)) {
                MyApplicationTheme(activeTheme = activeTheme) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        SearchScreen(
                            viewModel = viewModel,
                            onNavigateToArtist = { _, _ -> },
                            onNavigateToAlbum = { _, _ -> },
                            onNavigateToResults = { _, _ -> },
                            onNavigateToSettings = {},
                            initialQuery = initialQuery
                        )
                    }
                }
            }
        }
    }

    private fun createViewModel(
        loadLibrariesOverride: (suspend () -> List<com.example.data.PlexDirectory>)? = null,
        searchResultsOverride: suspend (String) -> List<PlexMetadata>
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
                    searchResultsOverride = searchResultsOverride,
                    searchDebounceMillis = 0,
                    loadLibrariesOverride = loadLibrariesOverride
                ) as T
            }
        }
        return ViewModelProvider(owner, factory)[MusicViewModel::class.java]
    }

    private fun clearConnectionSettings() {
        PlexSettingsManager(context).apply {
            clearCompanionCredential()
            baseUrl = ""
            token = ""
            sectionId = ""
            offlineOnly = false
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.fetchSemanticsNodeOrNull() =
        runCatching { fetchSemanticsNode() }.getOrNull()
}
