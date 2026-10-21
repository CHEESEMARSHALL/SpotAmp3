package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.data.BackendHomeFeedResponse
import com.example.data.PlexMetadata
import com.example.data.PlexSettingsManager
import com.example.ui.MusicViewModel
import com.example.ui.SearchScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SearchRecoveryDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }
    private var viewModelStore: ViewModelStore? = null
    private var previousOfflineOnly: Boolean? = null

    @Before
    fun setUp() {
        previousOfflineOnly = PlexSettingsManager(context).offlineOnly
        runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
    }

    @After
    fun tearDown() {
        viewModelStore?.clear()
        viewModelStore = null
        previousOfflineOnly?.let { PlexSettingsManager(context).offlineOnly = it }
        previousOfflineOnly = null
        composeRule.waitForIdle()
    }

    @Test
    fun retryKeepsQueryAndMovesFromUnavailableToResults() {
        val query = "wave two retry fixture"
        val attempts = AtomicInteger()
        val result = PlexMetadata(
            ratingKey = "wave-two-recovered-track",
            title = "Recovered Search Result",
            type = "track"
        )
        val viewModel = createViewModel { submittedQuery ->
            assertEquals(query, submittedQuery)
            when (attempts.incrementAndGet()) {
                1 -> throw IOException("QA fixture returns a temporary search failure")
                2 -> listOf(result)
                else -> error("Unexpected retry count")
            }
        }
        // The recovery result is synthetic, so show it regardless of the user's saved filter.
        viewModel.repository.settings.offlineOnly = false

        composeRule.setContent {
            MyApplicationTheme {
                SearchScreen(
                    viewModel = viewModel,
                    onNavigateToArtist = { _, _ -> },
                    onNavigateToAlbum = { _, _ -> },
                    onNavigateToResults = { _, _ -> },
                    onNavigateToSettings = { _ -> },
                    initialQuery = query
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 30_000) {
            viewModel.searchError.value != null && !viewModel.isSearchLoading.value
        }
        assertEquals("Initial search should issue exactly one request", 1, attempts.get())
        composeRule.onNodeWithTag("search_unavailable_state").assertIsDisplayed()
        composeRule.onNodeWithTag("search_text_input").assertTextContains(query)
        composeRule.onNodeWithText(context.getString(R.string.search_retry)).assertIsDisplayed().performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            viewModel.searchResults.value == listOf(result) &&
                viewModel.searchError.value == null &&
                !viewModel.isSearchLoading.value
        }
        composeRule.onNodeWithTag("search_text_input").assertTextContains(query)
        composeRule.onNodeWithText(result.title).assertIsDisplayed()
        composeRule.onNodeWithTag("search_unavailable_state").assertDoesNotExist()
        assertEquals("Retry should issue exactly one new search", 2, attempts.get())
    }

    private fun createViewModel(
        searchResultsOverride: suspend (String) -> List<PlexMetadata>
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
                    fetchCompanionHomeFeed = { _, _ -> BackendHomeFeedResponse() },
                    searchResultsOverride = searchResultsOverride
                ) as T
            }
        }
        return ViewModelProvider(owner, factory)[MusicViewModel::class.java]
    }
}
