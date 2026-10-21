package com.example.ui

import android.content.Context
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.PlaylistEntity
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistSelectionDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun emptyChooserExplainsHowToCreateAPlaylist() {
        composeRule.setContent {
            MyApplicationTheme {
                PlaylistSelectionDialog(
                    playlists = emptyList(),
                    onDismiss = {},
                    onPlaylistSelected = {},
                    onCreateNewPlaylist = { _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.library_add_to_playlist)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.library_context_empty_playlists)).assertIsDisplayed()

        val createAction = composeRule.onNodeWithTag("playlist_selection_create_new")
        createAction.assertIsDisplayed()
        assertEquals(Role.Button, createAction.fetchSemanticsNode().config[SemanticsProperties.Role])
        createAction.performClick()
        composeRule.onNodeWithText(context.getString(R.string.library_new_playlist)).assertIsDisplayed()
        composeRule.onNodeWithTag("library_playlist_name_input").assertIsDisplayed()
    }

    @Test
    fun failedPlaylistCreationKeepsDraftAndAllowsRetry() {
        val requests = mutableListOf<String>()
        var failCreation: (() -> Unit)? = null
        var completeCreation: (() -> Unit)? = null
        var dismissCount = 0

        composeRule.setContent {
            MyApplicationTheme {
                PlaylistSelectionDialog(
                    playlists = emptyList(),
                    onDismiss = { dismissCount++ },
                    onPlaylistSelected = {},
                    onCreateNewPlaylist = { name, onFailure, onCreated ->
                        requests += name
                        failCreation = onFailure
                        completeCreation = onCreated
                    }
                )
            }
        }

        composeRule.onNodeWithTag("playlist_selection_create_new").performClick()
        composeRule.onNodeWithTag("library_playlist_name_input").performTextInput("Road Trip")
        composeRule.onNodeWithTag("library_create_playlist_confirm").performClick()
        assertEquals(listOf("Road Trip"), requests)

        composeRule.runOnIdle { failCreation?.invoke() }
        val failureMessage = context.getString(R.string.library_create_playlist_error)
        val failureNode = composeRule.onNodeWithText(failureMessage).assertIsDisplayed()
        assertEquals(LiveRegionMode.Polite, failureNode.fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        assertEquals(failureMessage, failureNode.fetchSemanticsNode().config[SemanticsProperties.Error])
        composeRule.onNodeWithTag("library_playlist_name_input").assertTextContains("Road Trip")
        composeRule.onNodeWithTag("library_create_playlist_confirm").assertIsEnabled().performClick()
        assertEquals(listOf("Road Trip", "Road Trip"), requests)

        composeRule.runOnIdle { completeCreation?.invoke() }
        assertEquals(1, dismissCount)
        assertEquals(0, composeRule.onAllNodesWithText(context.getString(R.string.library_new_playlist)).fetchSemanticsNodes().size)
    }
}
