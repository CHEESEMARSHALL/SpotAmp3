package com.example

import android.app.Application
import android.view.KeyEvent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
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
import com.example.data.BackendHomeFeedResponse
import com.example.data.PlexMetadata
import com.example.data.PlexSettingsManager
import com.example.ui.MainAppScreen
import com.example.ui.MusicViewModel
import com.example.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ensures Search query state survives navigation to and from a complete result list. */
@RunWith(AndroidJUnit4::class)
class SearchNavigationStateDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }
    private val query = "QA Backstack Query"
    private val trackFixtures = (0 until 4).map { index ->
        PlexMetadata(
            ratingKey = "qa-search-backstack-track-$index",
            title = "QA Backstack Song ${index.toString().padStart(2, '0')}",
            type = "track",
            parentTitle = "QA Backstack Album",
            grandparentTitle = "QA Backstack Artist"
        )
    }

    private lateinit var settings: PlexSettingsManager
    private lateinit var viewModel: MusicViewModel
    private var viewModelStore: ViewModelStore? = null
    private var originalOfflineOnly: Boolean? = null

    @Before
    fun setUp() {
        runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
        }

        settings = PlexSettingsManager(context)
        originalOfflineOnly = settings.offlineOnly
        settings.offlineOnly = false
        viewModel = createViewModel()

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
        originalOfflineOnly?.let { settings.offlineOnly = it }
        originalOfflineOnly = null
        composeRule.waitForIdle()
    }

    @Test
    fun searchQueryAndResultsReturnAfterOpeningSongsSeeAll() {
        composeRule.onNode(navigationTab("Discover")).performClick()
        composeRule.onNodeWithTag("discover_open_search").performClick()
        composeRule.onNodeWithTag("search_text_input").performTextInput(query)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.searchResults.value == trackFixtures && !viewModel.isSearchLoading.value
        }

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
        val songsLabel = context.getString(R.string.search_section_songs)
        composeRule.onNodeWithTag("search_results_list").performScrollToNode(hasText("See all"))
        composeRule.onNodeWithText("See all").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(songsLabel).assertIsDisplayed()
        composeRule.onNodeWithText(trackFixtures.first().title).assertIsDisplayed()

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("search_text_input").assertTextContains(query)
        composeRule.onNodeWithText(songsLabel).assertIsDisplayed()
        composeRule.onNodeWithText(trackFixtures.first().title).assertIsDisplayed()
        assertEquals(trackFixtures, viewModel.searchResults.value)
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
                    application = application,
                    fetchCompanionHomeFeed = { _, _ -> BackendHomeFeedResponse() },
                    searchResultsOverride = { requestedQuery ->
                        if (requestedQuery == query) trackFixtures else emptyList()
                    },
                    searchDebounceMillis = 0
                ) as T
            }
        }
        return ViewModelProvider(owner, factory)[MusicViewModel::class.java]
    }
}
