package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchScreenJourneyTest {
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
        clearSavedConnection()
    }

    @After
    fun tearDown() {
        viewModelStore?.clear()
        viewModelStore = null
        clearSavedConnection()
        composeRule.waitForIdle()
    }

    @Test
    fun productionSearchMovesFromFirstRunGuidanceThroughResultsAndClear() {
        val resultGate = CompletableDeferred<List<PlexMetadata>>()
        val attempts = AtomicInteger()
        val track = PlexMetadata(
            ratingKey = "ambient-track",
            title = "Ambient Horizon",
            type = "track"
        )
        val viewModel = createViewModel { query ->
            when (query) {
                "ambient" -> {
                    attempts.incrementAndGet()
                    resultGate.await()
                }
                "nothing" -> {
                    attempts.incrementAndGet()
                    emptyList()
                }
                else -> error("Unexpected search query: $query")
            }
        }
        showSearch(viewModel, initialQuery = "ambient")

        val firstRunMessage = context.getString(R.string.search_empty_first_run)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            attempts.get() == 1 &&
                composeRule.onAllNodesWithText(firstRunMessage).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(firstRunMessage).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.library_pair_spotcore_action)).assertIsDisplayed()

        resultGate.complete(listOf(track))
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(track.title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(track.title).assertIsDisplayed()

        val downloadedOnlyLabel = context.getString(R.string.search_downloaded_only_accessibility)
        composeRule.onNodeWithContentDescription(downloadedOnlyLabel).performClick()
        val filteredEmptyMessage = context.getString(R.string.search_no_downloaded_matches, "ambient")
        composeRule.onNodeWithText(filteredEmptyMessage).assertIsDisplayed()
        composeRule.onNodeWithText(track.title).assertDoesNotExist()

        composeRule.onNodeWithText(context.getString(R.string.search_show_all_songs)).performClick()
        composeRule.onNodeWithText(track.title).assertIsDisplayed()

        composeRule.onNodeWithTag("search_text_input").performTextClearance()
        composeRule.onNodeWithTag("search_text_input").performTextInput("nothing")
        val trueEmptyMessage = context.getString(R.string.search_no_matches, "nothing")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            attempts.get() == 2 &&
                composeRule.onAllNodesWithText(firstRunMessage).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(trueEmptyMessage).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.library_pair_spotcore_action)).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(context.getString(R.string.search_clear_accessibility)).performClick()
        composeRule.onNodeWithText(firstRunMessage).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.library_pair_spotcore_action)).assertIsDisplayed()
    }

    @Test
    fun changingQueryHidesPreviousMatchesUntilTheNewSearchCompletes() {
        // Model an existing searchable library; an unpaired first-run profile intentionally keeps
        // its pairing guidance visible while there is no saved catalog to search.
        PlexSettingsManager(context).apply {
            baseUrl = "http://127.0.0.1:1"
            token = "host-query-change-fixture"
        }
        val nextQueryStarted = CompletableDeferred<Unit>()
        val nextQueryResult = CompletableDeferred<List<PlexMetadata>>()
        val previousTrack = PlexMetadata(
            ratingKey = "first-query-track",
            title = "First Query Result",
            type = "track"
        )
        val currentTrack = PlexMetadata(
            ratingKey = "second-query-track",
            title = "Second Query Result",
            type = "track"
        )
        val viewModel = createViewModel { query ->
            when (query) {
                "first query" -> listOf(previousTrack)
                "second query" -> {
                    nextQueryStarted.complete(Unit)
                    nextQueryResult.await()
                }
                else -> error("Unexpected search query: $query")
            }
        }

        showSearch(viewModel, initialQuery = "first query")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(previousTrack.title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(previousTrack.title).assertIsDisplayed()

        composeRule.onNodeWithTag("search_text_input").performTextClearance()
        composeRule.onNodeWithTag("search_text_input").performTextInput("second query")

        val searching = context.getString(R.string.search_loading)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            nextQueryStarted.isCompleted &&
                composeRule.onAllNodesWithText(searching).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(searching).assertIsDisplayed()
        composeRule.onNodeWithText(previousTrack.title).assertDoesNotExist()
        composeRule.onNodeWithText(currentTrack.title).assertDoesNotExist()

        nextQueryResult.complete(listOf(currentTrack))
        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.searchResults.value == listOf(currentTrack) && !viewModel.isSearchLoading.value
        }
        composeRule.onNodeWithText(currentTrack.title).assertIsDisplayed()
        composeRule.onNodeWithText(previousTrack.title).assertDoesNotExist()
        composeRule.onNodeWithText(searching).assertDoesNotExist()
    }

    @Test
    fun songResultsQuickLinkOpensFullSongListWithoutScrollingMixedResults() {
        val artist = PlexMetadata("artist-1", title = "M83", type = "artist")
        val album = PlexMetadata("album-1", title = "Hurry Up, We're Dreaming", type = "album", parentTitle = "M83")
        val track = PlexMetadata(
            "track-1",
            title = "Midnight City",
            type = "track",
            parentTitle = "Hurry Up, We're Dreaming",
            grandparentTitle = "M83"
        )
        var openedCategory: String? = null
        var openedItems: List<PlexMetadata> = emptyList()
        val viewModel = createViewModel { listOf(artist, album, track) }

        showSearch(
            viewModel,
            initialQuery = "midnight",
            onNavigateToResults = { category, items ->
                openedCategory = category
                openedItems = items
            }
        )

        val songsShortcut = context.getString(
            R.string.search_quick_link_count,
            context.getString(R.string.search_section_songs),
            1
        )
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(songsShortcut).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("search_results_quick_links").assertIsDisplayed()
        composeRule.onNodeWithTag("search_results_jump_songs").performClick()

        assertEquals(context.getString(R.string.search_section_songs), openedCategory)
        assertEquals(listOf(track), openedItems)
    }

    @Test
    fun firstRunSearchKeepsPairingGuidanceAfterEnteringQuery() {
        val attempts = AtomicInteger()
        val viewModel = createViewModel { query ->
            assertEquals("wave1check", query)
            attempts.incrementAndGet()
            emptyList()
        }

        showSearch(viewModel, initialQuery = "")
        composeRule.onNodeWithTag("search_text_input").performTextInput("wave1check")

        val firstRunMessage = context.getString(R.string.search_empty_first_run)
        val pairingAction = context.getString(R.string.library_pair_spotcore_action)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            attempts.get() == 1 &&
                composeRule.onAllNodesWithText(firstRunMessage).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.search_title)).assertIsDisplayed()
        composeRule.onNodeWithText("wave1check").assertIsDisplayed()
        composeRule.onNodeWithText(firstRunMessage).assertIsDisplayed()
        composeRule.onNodeWithText(pairingAction).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.search_no_matches, "wave1check"))
            .assertDoesNotExist()

        composeRule.onNodeWithContentDescription(context.getString(R.string.search_clear_accessibility)).performClick()
        composeRule.onNodeWithText(firstRunMessage).assertIsDisplayed()
        composeRule.onNodeWithText(pairingAction).assertIsDisplayed()
    }

    @Test
    fun productionSearchFailureCanOpenSettingsWithQueryAndRetryInPlace() {
        val attempts = AtomicInteger()
        val track = PlexMetadata(
            ratingKey = "recovered-track",
            title = "Recovered Result",
            type = "track"
        )
        val viewModel = createViewModel { query ->
            assertEquals("ambient", query)
            if (attempts.incrementAndGet() == 1) throw IOException("private endpoint detail")
            listOf(track)
        }
        var settingsQuery: String? = null
        var pairingStarts = 0

        showSearch(
            viewModel,
            initialQuery = "ambient",
            onNavigateToSettings = { settingsQuery = it },
            onStartPairing = { pairingStarts++ }
        )

        val unavailableTitle = context.getString(R.string.search_unavailable_title)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            attempts.get() == 1 &&
                composeRule.onAllNodesWithText(unavailableTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(unavailableTitle).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.search_failure_without_saved_library)).assertIsDisplayed()
        composeRule.onNodeWithTag("search_pair_spotcore_action").assertIsDisplayed().performClick()
        assertEquals("An unpaired search failure should offer pairing directly", 1, pairingStarts)

        composeRule.onNodeWithText(context.getString(R.string.home_open_settings)).performClick()
        assertEquals("Settings recovery must retain the current query", "ambient", settingsQuery)

        composeRule.onNodeWithText(context.getString(R.string.search_retry)).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            attempts.get() == 2 &&
                composeRule.onAllNodesWithText(track.title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(track.title).assertIsDisplayed()
        composeRule.onNodeWithText(unavailableTitle).assertDoesNotExist()
        assertTrue("The second request must use the unchanged query", attempts.get() == 2)
    }

    private fun showSearch(
        viewModel: MusicViewModel,
        initialQuery: String,
        onNavigateToResults: (String, List<PlexMetadata>) -> Unit = { _, _ -> },
        onNavigateToSettings: (String) -> Unit = {},
        onStartPairing: () -> Unit = {}
    ) {
        composeRule.setContent {
            MyApplicationTheme {
                SearchScreen(
                    viewModel = viewModel,
                    onNavigateToArtist = { _, _ -> },
                    onNavigateToAlbum = { _, _ -> },
                    onNavigateToResults = onNavigateToResults,
                    onNavigateToSettings = onNavigateToSettings,
                    onStartPairing = onStartPairing,
                    initialQuery = initialQuery
                )
            }
        }
    }

    private fun createViewModel(
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
                    searchDebounceMillis = 0
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
            offlineOnly = false
        }
    }
}
