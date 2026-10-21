package com.example.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadsAddToPlaylistButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun actionIsAbsentWhenThereAreNoCompletedDownloads() {
        composeTestRule.setContent {
            MyApplicationTheme {
                DownloadsAddToPlaylistButton(hasCompletedTracks = false, onClick = {})
            }
        }

        assertEquals(0, composeTestRule.onAllNodesWithTag("downloads_add_all_button").fetchSemanticsNodes().size)
    }

    @Test
    fun actionIsLabeledAndWorksWhenCompletedDownloadsExist() {
        var clicks = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                DownloadsAddToPlaylistButton(hasCompletedTracks = true, onClick = { clicks++ })
            }
        }

        composeTestRule.onNodeWithContentDescription("Add downloaded tracks to a playlist")
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun albumActionNamesAlbumAndRunsItsClickHandler() {
        var clicks = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                DownloadedAlbumAddToPlaylistAction("Open Roads") { clicks++ }
            }
        }

        composeTestRule.onNodeWithContentDescription("Add album Open Roads to a playlist")
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun trackActionNamesTrackAndArtistAndRunsItsClickHandler() {
        var clicks = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                DownloadedTrackAddToPlaylistAction("First Light", "Northbound") { clicks++ }
            }
        }

        val action = composeTestRule.onNodeWithContentDescription(
            "Add First Light by Northbound to a playlist"
        ).assertIsDisplayed()
        val bounds = action.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Track add-to-playlist action should have a 48dp-wide target",
            bounds.width >= with(composeTestRule.density) { 48.dp.toPx() }
        )
        assertTrue(
            "Track add-to-playlist action should have a 48dp-tall target",
            bounds.height >= with(composeTestRule.density) { 48.dp.toPx() }
        )
        action.performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun feedbackIconButtonPreservesMinimumTargetForCompactCallers() {
        var clicks = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                FeedbackIconButton(onClick = { clicks++ }, modifier = Modifier.size(32.dp)) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Compact caller"
                    )
                }
            }
        }

        val action = composeTestRule.onNodeWithContentDescription("Compact caller").assertIsDisplayed()
        val bounds = action.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Shared feedback icon actions should retain a 48dp-wide minimum target",
            bounds.width >= with(composeTestRule.density) { 48.dp.toPx() }
        )
        assertTrue(
            "Shared feedback icon actions should retain a 48dp-tall minimum target",
            bounds.height >= with(composeTestRule.density) { 48.dp.toPx() }
        )
        action.performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun playlistActionNamesItsCollectionAndRunsItsClickHandler() {
        var clicks = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                DownloadedPlaylistAddToPlaylistAction("Late Night") { clicks++ }
            }
        }

        composeTestRule.onNodeWithContentDescription("Add tracks from Late Night to a playlist")
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, clicks)
    }

}
