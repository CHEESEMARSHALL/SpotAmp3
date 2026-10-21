package com.example.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.material3.Text
import androidx.test.core.app.ApplicationProvider
import com.example.R
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
class DownloadsCopyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun playlistOfflineCountUsesSingularWhenOneTrackIsDownloaded() {
        val expected = context.resources.getQuantityString(R.plurals.downloads_playlist_offline_count, 1, 1)
        composeRule.setContent {
            MyApplicationTheme { DownloadsPlaylistOfflineCount(trackCount = 1) }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun playlistOfflineCountUsesPluralForMultipleTracks() {
        val expected = context.resources.getQuantityString(R.plurals.downloads_playlist_offline_count, 4, 4)
        composeRule.setContent {
            MyApplicationTheme { DownloadsPlaylistOfflineCount(trackCount = 4) }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun downloadsShowProgressUntilTheSavedRecordsHaveLoaded() {
        composeRule.setContent {
            MyApplicationTheme { DownloadsLoadingState() }
        }

        composeRule.onNodeWithTag("downloads_loading_state").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.downloads_loading_saved_tracks)).assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText(context.getString(R.string.downloads_empty_title))
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun clickableDownloadRowNamesItsActionAndKeepsNestedOptionsReachable() {
        var playCount = 0
        var optionsCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                DownloadsActionRow(onClick = { playCount++ }) {
                    Text("Saved track")
                    androidx.compose.material3.IconButton(
                        onClick = { optionsCount++ },
                        modifier = Modifier.testTag("downloads_row_nested_options")
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Track options"
                        )
                    }
                }
            }
        }

        val row = composeRule.onNodeWithText("Saved track")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        row.performSemanticsAction(SemanticsActions.OnClick) { it() }
        assertEquals(1, playCount)

        composeRule.onNodeWithTag("downloads_row_nested_options")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(1, optionsCount)
    }

    @Test
    fun removeOfflineCollectionDialogUsesSingularTrackCount() {
        val expected = context.resources.getQuantityString(
            R.plurals.downloads_remove_offline_collection_message,
            1,
            "Blue Train",
            1
        )
        composeRule.setContent {
            MyApplicationTheme {
                RemoveOfflineCollectionDialog(
                    collectionName = "Blue Train",
                    trackCount = 1,
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun failedTrackStatusShowsFallbackArtistInsteadOfLeadingSeparator() {
        val expected = context.getString(
            R.string.downloads_track_status,
            context.getString(R.string.common_unknown_artist),
            context.getString(R.string.downloads_failed_status)
        )
        composeRule.setContent {
            MyApplicationTheme {
                Text(DownloadsTrackStatusLabel(artist = "", status = "failed"))
            }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun missingLocalFileShowsActionableUnavailableStatus() {
        val expected = context.getString(
            R.string.downloads_track_status,
            "Artist",
            context.getString(R.string.downloads_missing_file_status)
        )
        composeRule.setContent {
            MyApplicationTheme {
                Text(DownloadsTrackStatusLabel(artist = "Artist", status = "completed", fileMissing = true))
            }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun sizeAndDurationSummaryUsesFormattedResource() {
        val expected = context.getString(
            R.string.downloads_size_duration,
            "1.3 MB",
            "1 hr, 47 min"
        )
        composeRule.setContent {
            MyApplicationTheme { Text(downloadSizeDurationLabel(1_250_000L, 107 * 60_000L)) }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun playbackModeCopyDoesNotClaimStreamingIsCurrentlyAvailable() {
        val streamingAllowed = context.getString(R.string.downloads_downloaded_only_off)
        val downloadedOnly = context.getString(R.string.downloads_downloaded_only_on)
        val offlineOnly = mutableStateOf(false)
        assertEquals("Streaming is allowed when SpotCore is available.", streamingAllowed)

        composeRule.setContent {
            MyApplicationTheme { DownloadsPlaybackModeSummary(offlineOnly = offlineOnly.value) }
        }
        composeRule.onNodeWithTag("downloads_playback_mode_summary").assertIsDisplayed()
        composeRule.onNodeWithText(streamingAllowed).assertIsDisplayed()

        composeRule.runOnIdle { offlineOnly.value = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("downloads_playback_mode_summary").assertIsDisplayed()
        composeRule.onNodeWithText(downloadedOnly).assertIsDisplayed()
    }

    @Test
    fun albumDisclosureButtonAnnouncesActionAndExpandedState() {
        val expandLabel = context.getString(R.string.downloads_expand_album_tracks_accessibility, "Blue Train")
        val collapseLabel = context.getString(R.string.downloads_collapse_album_tracks_accessibility, "Blue Train")
        val collapsedState = context.getString(R.string.downloads_collapsed_state)
        val expandedState = context.getString(R.string.downloads_expanded_state)
        composeRule.setContent {
            var expanded by remember { mutableStateOf(false) }
            MyApplicationTheme {
                DownloadsDisclosureButton(
                    expanded = expanded,
                    expandDescription = expandLabel,
                    collapseDescription = collapseLabel,
                    onToggle = { expanded = !expanded }
                )
            }
        }

        composeRule.onNodeWithContentDescription(expandLabel)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, collapsedState))
            .performClick()
        composeRule.onNodeWithContentDescription(collapseLabel)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expandedState))
    }

    @Test
    fun playlistDisclosureButtonUsesPlaylistSpecificAccessibleCopy() {
        val expandLabel = context.getString(
            R.string.downloads_expand_playlist_tracks_accessibility,
            "Sunday Mix"
        )
        composeRule.setContent {
            MyApplicationTheme {
                DownloadsDisclosureButton(
                    expanded = false,
                    expandDescription = expandLabel,
                    collapseDescription = context.getString(
                        R.string.downloads_collapse_playlist_tracks_accessibility,
                        "Sunday Mix"
                    ),
                    onToggle = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(expandLabel)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun downloadOverflowActionsNameTheirAlbumTrackAndPlaylist() {
        var openedMenus = 0
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    DownloadedAlbumOptionsAction("Blue Train") { openedMenus++ }
                    DownloadedTrackOptionsAction(
                        "So What",
                        "Miles Davis",
                        onClick = { openedMenus++ },
                        modifier = Modifier.size(32.dp)
                    )
                    DownloadedPlaylistOptionsAction("Sunday Mix") { openedMenus++ }
                }
            }
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.downloads_album_options_accessibility, "Blue Train")
        ).assertIsDisplayed().performClick()
        val trackOptions = composeRule.onNodeWithContentDescription(
            context.getString(R.string.downloads_track_options_accessibility, "So What", "Miles Davis")
        ).assertIsDisplayed()
        val bounds = trackOptions.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Track options should retain a 48dp-wide target when a compact modifier is supplied",
            bounds.width >= with(composeRule.density) { 48.dp.toPx() }
        )
        assertTrue(
            "Track options should retain a 48dp-tall target when a compact modifier is supplied",
            bounds.height >= with(composeRule.density) { 48.dp.toPx() }
        )
        trackOptions.performClick()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.downloads_playlist_options_accessibility, "Sunday Mix")
        ).assertIsDisplayed().performClick()

        assertEquals(3, openedMenus)
    }
}
