package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.MusicRepository
import com.example.ui.MusicViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises playlist create/delete through the production Library on an isolated local QA profile. */
@RunWith(AndroidJUnit4::class)
class LibraryPlaylistJourneyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun newPlaylistPersistsAndDeleteRequiresConfirmation() = runBlocking {
        val viewModel = ViewModelProvider(composeRule.activity)[MusicViewModel::class.java]
        val repository: MusicRepository = viewModel.repository
        assumeFalse(
            "This local playlist fixture requires an unpaired QA profile so it cannot mutate a connected SpotCore server",
            repository.settings.isCompanionConfigured
        )

        val originalSectionId = repository.settings.sectionId
        val originalLibraryName = repository.settings.libraryName
        val playlistName = "QA playlist ${System.currentTimeMillis()}"
        var createdPlaylistId: Int? = null

        try {
            // The playlist shelf belongs to a selected library. The test selects the existing
            // SpotCore section locally without credentials, so playlist writes stay device-only.
            viewModel.selectLibrary("spotcore", "SpotCore Library")
            composeRule.onNode(navigationTab("Library")).performClick()
            composeRule.onNodeWithTag("library_categories_list")
                .performScrollToNode(hasText("Playlists"))
            composeRule.onNodeWithText("Playlists").performClick()

            composeRule.onNodeWithTag("library_new_playlist_action").performScrollTo().performClick()
            composeRule.onNodeWithTag("library_playlist_name_input").performTextInput(playlistName)
            composeRule.onNodeWithTag("library_create_playlist_confirm").performClick()

            composeRule.waitUntil(15_000) {
                runBlocking { repository.playlists.first().any { it.name == playlistName } }
            }
            createdPlaylistId = repository.playlists.first().first { it.name == playlistName }.id
            composeRule.onNodeWithText(playlistName).assertIsDisplayed()

            composeRule.onNodeWithText(playlistName).performScrollTo().performClick()
            composeRule.onNodeWithText(playlistName).assertIsDisplayed()
            composeRule.onNode(
                hasContentDescription(composeRule.activity.getString(com.example.R.string.playlist_detail_back))
            ).assertIsDisplayed().performClick()
            composeRule.onNodeWithText(playlistName).assertIsDisplayed()

            composeRule.onNodeWithContentDescription("Delete $playlistName").performScrollTo().performClick()
            composeRule.onNodeWithTag("library_delete_playlist_dialog").assertIsDisplayed()
            composeRule.onNodeWithText("Delete “$playlistName” from this device? This can’t be undone.").assertIsDisplayed()
            composeRule.onNodeWithTag("library_delete_playlist_cancel").performClick()
            assertTrue(repository.playlists.first().any { it.id == createdPlaylistId })
            composeRule.onNodeWithText(playlistName).assertIsDisplayed()

            composeRule.onNodeWithContentDescription("Delete $playlistName").performScrollTo().performClick()
            composeRule.onNodeWithTag("library_delete_playlist_confirm").performClick()
            composeRule.waitUntil(15_000) {
                runBlocking { repository.playlists.first().none { it.id == createdPlaylistId } }
            }
            composeRule.onNodeWithText(playlistName).assertDoesNotExist()
            createdPlaylistId = null
        } finally {
            createdPlaylistId?.let { id -> repository.deletePlaylist(id) }
            repository.settings.sectionId = originalSectionId
            repository.settings.libraryName = originalLibraryName
            viewModel.selectLibrary(originalSectionId, originalLibraryName)
        }
    }

    private fun navigationTab(label: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            (hasText(label) or hasContentDescription(label))
}
