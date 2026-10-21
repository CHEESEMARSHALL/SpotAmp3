package com.example.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.R
import com.example.data.PlexMetadata
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtistSimilarArtistNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exactLocalArtistMatchOpensItsDetail() {
        var artistNavigation: Pair<String, String>? = null
        var searchQuery: String? = null

        navigateToSimilarArtist(
            name = "  RADIOHEAD ",
            artists = listOf(PlexMetadata(ratingKey = "artist-7", title = "Radiohead", type = "artist")),
            onNavigateToArtist = { id, title -> artistNavigation = id to title },
            onNavigateToSearch = { searchQuery = it }
        )

        assertEquals("artist-7" to "Radiohead", artistNavigation)
        assertNull(searchQuery)
    }

    @Test
    fun artistNotInLocalListOpensSearchWithNormalizedName() {
        var artistNavigation: Pair<String, String>? = null
        var searchQuery: String? = null

        navigateToSimilarArtist(
            name = "  The National  ",
            artists = listOf(PlexMetadata(ratingKey = "artist-7", title = "The National Parks", type = "artist")),
            onNavigateToArtist = { id, title -> artistNavigation = id to title },
            onNavigateToSearch = { searchQuery = it }
        )

        assertNull(artistNavigation)
        assertEquals("The National", searchQuery)
    }

    @Test
    fun blankSimilarArtistDoesNothing() {
        var navigated = false

        navigateToSimilarArtist(
            name = "  ",
            artists = emptyList(),
            onNavigateToArtist = { _, _ -> navigated = true },
            onNavigateToSearch = { navigated = true }
        )

        assertEquals(false, navigated)
    }

    @Test
    fun searchQueryFieldDisplaysThePrefilledArtistName() {
        val query = "The National"

        composeRule.setContent {
            MyApplicationTheme {
                SearchQueryField(searchQuery = query, onSearchQueryChange = {})
            }
        }

        composeRule.onNodeWithTag("search_text_input")
            .assertIsDisplayed()
            .assertTextContains(query)
    }

    @Test
    fun searchClearActionMeetsMinimumAndClearsTheQuery() {
        var clearedQuery: String? = null
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeRule.setContent {
            MyApplicationTheme {
                SearchQueryField(
                    searchQuery = "ambient",
                    onSearchQueryChange = { clearedQuery = it }
                )
            }
        }

        val clearButton = composeRule.onNodeWithContentDescription(
            context.getString(R.string.search_clear_accessibility)
        ).assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, clearButton.fetchSemanticsNode().config[SemanticsProperties.Role])

        clearButton.performClick()

        assertEquals("", clearedQuery)
    }
}
