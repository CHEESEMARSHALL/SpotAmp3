package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import com.example.data.PlaylistSyncState
import com.example.playback.TrackItem
import com.example.ui.MusicViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Protects the real app shell's primary production navigation destinations. */
@RunWith(AndroidJUnit4::class)
class ProductionNavigationJourneyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun primaryDestinationsOpenFromTheRealShell() {
        composeRule.onNode(navigationTab("Home")).performClick()
        composeRule.onNodeWithTag("home_feed").assertIsDisplayed()

        composeRule.onNode(navigationTab("Library")).performClick()
        composeRule.onNodeWithTag("library_title").assertIsDisplayed()

        composeRule.onNode(navigationTab("Discover")).performClick()
        composeRule.onNodeWithTag("discover_feed").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_open_search").performClick()
        composeRule.onNodeWithTag("search_text_input").assertIsDisplayed()

        composeRule.onNode(navigationTab("Downloads")).performClick()
        composeRule.onNodeWithTag("downloads_more_button").assertIsDisplayed()

        composeRule.onNode(navigationTab("Settings")).performClick()
        composeRule.onNodeWithTag("settings_section_connection").assertIsDisplayed()
    }

    @Test
    fun settingsTaskShortcutsReachEachSectionWithoutChangingSettings() {
        val settings = ViewModelProvider(composeRule.activity)[MusicViewModel::class.java].repository.settings
        composeRule.onNode(navigationTab("Settings")).performClick()

        val sections = listOf(
            "connection" to "connection",
            "library" to "library",
            "playback" to "playback",
            "downloadsoffline" to "downloads_offline",
            "discovery" to "discovery",
            "appearance" to "appearance",
            "advanced" to "advanced"
        )
        sections.forEach { (shortcut, heading) ->
            composeRule.onNodeWithTag("settings_section_$shortcut")
                .performScrollTo()
                .performClick()
            composeRule.waitForIdle()
            try {
                if (shortcut == "connection" && !settings.isCompanionConfigured) {
                    composeRule.onNodeWithTag("settings_connection_pairing_action").assertIsDisplayed()
                } else {
                    composeRule.onNodeWithTag("settings_heading_$heading").assertIsDisplayed()
                }
            } catch (error: AssertionError) {
                throw AssertionError("Settings shortcut '$shortcut' did not bring its task into view: ${error.message}")
            }
        }
    }

    @Test
    fun manualConnectionFieldsCanBeOpenedAndClosedWithoutSaving() {
        val settings = ViewModelProvider(composeRule.activity)[MusicViewModel::class.java].repository.settings
        val originalUrl = settings.companionBackendUrl
        val originalToken = settings.companionBackendToken

        composeRule.onNode(navigationTab("Settings")).performClick()
        composeRule.onNodeWithTag("settings_section_connection")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("manual_connection_toggle")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("companion_url_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("companion_token_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("manual_connection_toggle")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("companion_url_input").assertDoesNotExist()
        composeRule.onNodeWithTag("companion_token_input").assertDoesNotExist()

        assertTrue(
            "Opening and closing manual connection fields must not save credentials",
            settings.companionBackendUrl == originalUrl && settings.companionBackendToken == originalToken
        )
    }

    @Test
    fun settingsPairingPreflightCanBeCanceledWithoutPermissionOrSessionChanges() {
        val activity = composeRule.activity
        val settings = ViewModelProvider(activity)[MusicViewModel::class.java].repository.settings
        val originallyConfigured = settings.isCompanionConfigured
        val originalCameraPermission = activity.checkSelfPermission(android.Manifest.permission.CAMERA)
        assumeTrue(
            "Camera permission is already granted, so this action would start the scanner instead of the permission preflight",
            originalCameraPermission == android.content.pm.PackageManager.PERMISSION_DENIED
        )

        composeRule.onNode(navigationTab("Settings")).performClick()
        composeRule.onNodeWithTag("settings_section_connection")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("settings_connection_pairing_action")
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag("pairing_dialog_title").assertIsDisplayed()
        val cameraActionIsExplained =
            composeRule.onAllNodesWithText("Continue to camera").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Open app settings").fetchSemanticsNodes().isNotEmpty()
        assertTrue("Pairing preflight should explain the next camera action", cameraActionIsExplained)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("pairing_dialog_title").assertDoesNotExist()

        assertEquals("Cancel must preserve the existing pairing state", originallyConfigured, settings.isCompanionConfigured)
        assertEquals(
            "Opening and canceling the pairing preflight must not change camera permission",
            originalCameraPermission,
            activity.checkSelfPermission(android.Manifest.permission.CAMERA)
        )
    }

    @Test
    fun libraryPlaylistsCanBeOpenedAndReturnedToWithoutEditing() {
        withLocalLibrarySelection {
            val playlistsTitle = composeRule.activity.getString(R.string.library_category_playlists)
            val libraryBackLabel = composeRule.activity.getString(R.string.library_back_accessibility)

            composeRule.onNode(navigationTab("Library")).performClick()
            composeRule.onNodeWithTag("library_categories_list")
                .performScrollToNode(hasText(playlistsTitle))
            composeRule.onNodeWithText(playlistsTitle).performClick()

            composeRule.onNodeWithTag("library_new_playlist_action").assertIsDisplayed()
            composeRule.onNode(hasContentDescription(libraryBackLabel)).assertIsDisplayed().performClick()
            composeRule.onNodeWithTag("library_categories_list").assertIsDisplayed()
        }
    }

    @Test
    fun eachLibraryCategoryCanBeOpenedAndReturnedWithoutEditing() {
        withLocalLibrarySelection {
            val categoryResources = listOf(
                R.string.library_category_artists,
                R.string.library_category_albums,
                R.string.library_category_compilations,
                R.string.library_category_soundtracks,
                R.string.library_category_tracks,
                R.string.library_category_playlists,
                R.string.library_category_artist_genres,
                R.string.library_category_album_genres,
                R.string.library_category_styles,
                R.string.library_category_moods
            )
            val libraryBackLabel = composeRule.activity.getString(R.string.library_back_accessibility)

            composeRule.onNode(navigationTab("Library")).performClick()
            composeRule.onNodeWithTag("library_categories_list").assertIsDisplayed()

            categoryResources.forEach { resourceId ->
                val title = composeRule.activity.getString(resourceId)
                composeRule.onNodeWithTag("library_categories_list")
                    .performScrollToNode(hasText(title))
                composeRule.onNodeWithText(title).assertIsDisplayed().performClick()
                composeRule.onNodeWithText(title).assertIsDisplayed()
                composeRule.onNode(hasContentDescription(libraryBackLabel))
                    .assertIsDisplayed()
                    .performClick()
                composeRule.onNodeWithTag("library_categories_list").assertIsDisplayed()
            }
        }
    }

    @Test
    fun cleanPlaylistDetailReturnsToLibraryWithoutPlaybackChanges() {
        withLocalLibrarySelection {
            val viewModel = ViewModelProvider(composeRule.activity)[MusicViewModel::class.java]
            val playlistsTitle = composeRule.activity.getString(R.string.library_category_playlists)

            composeRule.onNode(navigationTab("Library")).performClick()
            composeRule.onNodeWithTag("library_categories_list")
                .performScrollToNode(hasText(playlistsTitle))
            composeRule.onNodeWithText(playlistsTitle).performClick()
            composeRule.onNodeWithTag("library_new_playlist_action").assertIsDisplayed()

            val safePlaylist = viewModel.playlists.value.firstOrNull {
                it.remoteId == null || it.syncState == PlaylistSyncState.SYNCED
            }
            assumeTrue(
                "No local or clean-synced playlist is available; avoid refreshing dirty/conflicted data during this read-only route check",
                safePlaylist != null
            )

            val playlistName = checkNotNull(safePlaylist).name
            composeRule.onNodeWithText(playlistName)
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithText(playlistName).assertIsDisplayed()
            composeRule.onNode(
                hasContentDescription(composeRule.activity.getString(R.string.playlist_detail_back))
            ).assertIsDisplayed().performClick()
            composeRule.onNodeWithTag("library_new_playlist_action").assertIsDisplayed()
        }
    }

    private fun withLocalLibrarySelection(block: () -> Unit) {
        val viewModel = ViewModelProvider(composeRule.activity)[MusicViewModel::class.java]
        val settings = viewModel.repository.settings
        val originalSectionId = settings.sectionId
        val originalLibraryName = settings.libraryName
        val needsTemporarySelection = originalSectionId.isBlank() || originalLibraryName.isBlank()
        var selectionMayNeedRestore = false

        try {
            if (needsTemporarySelection) {
                assumeTrue(
                    "A local Library route fixture is only safe without configured server credentials",
                    !settings.isCompanionConfigured && !settings.isPlexConfigured
                )
                selectionMayNeedRestore = true
                viewModel.selectLibrary("qa-route-audit", "QA Route Audit Library")
                composeRule.waitForIdle()
            }
            block()
        } finally {
            if (selectionMayNeedRestore) {
                settings.sectionId = originalSectionId
                settings.libraryName = originalLibraryName
                viewModel.selectLibrary(originalSectionId, originalLibraryName)
                composeRule.waitForIdle()
            }
        }
    }

    @Test
    fun downloadsOfflineModeCanBeToggled() {
        composeRule.onNode(navigationTab("Downloads")).performClick()
        val summary = composeRule.onNodeWithTag("downloads_playback_mode_summary")
        fun downloadedOnlyIsOn() = summary.fetchSemanticsNode().config[SemanticsProperties.Text]
            .any { it.text.contains("Downloaded-only mode is on") }
        fun toggleDownloadedOnly() {
            composeRule.onNodeWithTag("downloads_more_button").performClick()
            composeRule.onNodeWithTag("downloads_offline_mode_menu_item")
                .assertIsDisplayed()
                .performClick()
        }

        val initiallyDownloadedOnly = downloadedOnlyIsOn()
        try {
            if (initiallyDownloadedOnly) toggleDownloadedOnly()
            summary.assertTextContains("Streaming is allowed when SpotCore is available.")
            toggleDownloadedOnly()
            summary.assertTextContains("Downloaded-only mode is on")
            toggleDownloadedOnly()
            summary.assertTextContains("Streaming is allowed when SpotCore is available.")
        } finally {
            if (downloadedOnlyIsOn() != initiallyDownloadedOnly) {
                toggleDownloadedOnly()
            }
        }
    }

    @Test
    fun clearingTemporaryFilesRequiresConfirmationAndCanBeCanceled() {
        composeRule.onNode(navigationTab("Downloads")).performClick()
        composeRule.onNodeWithTag("downloads_more_button").performClick()
        composeRule.onNodeWithText("Clear temporary files").performClick()

        composeRule.onNodeWithText("Clear temporary files?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "This removes temporary app files that can be recreated. Music downloaded for offline listening won’t be deleted."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Clear temporary files?").assertDoesNotExist()
    }

    @Test
    fun searchOfflineModeCanBeToggledAndSurvivesNavigation() {
        openSearchFromDiscover()
        val offlineToggle = composeRule.onNodeWithTag("search_downloaded_only_toggle")
        fun downloadedOnlyIsOn() = offlineToggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "On"

        val initiallyDownloadedOnly = downloadedOnlyIsOn()
        try {
            if (initiallyDownloadedOnly) offlineToggle.performClick().assertIsOff()
            offlineToggle.performClick().assertIsOn()

            composeRule.onNode(navigationTab("Library")).performClick()
            openSearchFromDiscover()
            offlineToggle.assertIsOn().performClick().assertIsOff()
        } finally {
            openSearchFromDiscover()
            if (downloadedOnlyIsOn() != initiallyDownloadedOnly) offlineToggle.performClick()
        }
    }

    @Test
    fun searchQueryCanBeEnteredAndClearedOffline() {
        openSearchFromDiscover()
        composeRule.onNodeWithTag("search_text_input").performTextInput("Björk")
        composeRule.onNodeWithTag("search_text_input").assertTextContains("Björk")
        composeRule.onNode(hasContentDescription("Clear search"), useUnmergedTree = true).assertIsDisplayed().performClick()
        composeRule.onNode(hasContentDescription("Clear search"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun miniPlayerOpensNowPlayingWithoutStartingOrChangingPlayback() {
        val playback = ViewModelProvider(composeRule.activity)[MusicViewModel::class.java].playbackManager
        val originalQueue = playback.queue.value.toList()
        val originalIndex = playback.currentIndex.value
        val currentTrackBefore = playback.currentTrack.value
        val wasPlayingBefore = playback.isPlaying.value
        val seededFixture = currentTrackBefore == null

        if (seededFixture) {
            assumeTrue("Do not replace a non-empty or active playback queue for this UI check", originalQueue.isEmpty() && !wasPlayingBefore)
            playback.replaceQueueForQa(
                listOf(
                    TrackItem(
                        ratingKey = "wave2-mini-player-route-fixture",
                        title = "Wave 2 mini-player route fixture",
                        artist = "SpotAmp QA",
                        album = "Generated test audio",
                        key = "/qa/wave2-mini-player-route-fixture",
                        thumb = "",
                        duration = 180_000L
                    )
                )
            )
        }

        try {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("mini_player").fetchSemanticsNodes().isNotEmpty()
            }
            val selectedTrackKey = playback.currentTrack.value?.ratingKey
            val playingBeforeClick = playback.isPlaying.value

            composeRule.onNodeWithTag("mini_player").assertIsDisplayed().performClick()
            composeRule.onNodeWithTag("now_playing_scroll_container").assertIsDisplayed()

            assertEquals(selectedTrackKey, playback.currentTrack.value?.ratingKey)
            assertEquals("Opening Now Playing must not toggle playback", playingBeforeClick, playback.isPlaying.value)

            composeRule.activityRule.scenario.recreate()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("now_playing_scroll_container").assertIsDisplayed()
            assertEquals("Recreation must preserve the selected track", selectedTrackKey, playback.currentTrack.value?.ratingKey)
            assertEquals("Recreation must preserve playback state", playingBeforeClick, playback.isPlaying.value)

            composeRule.onNodeWithTag("collapse_button").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("mini_player").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
            composeRule.onNodeWithTag("now_playing_scroll_container").assertDoesNotExist()
            assertEquals("Collapsing must preserve the selected track", selectedTrackKey, playback.currentTrack.value?.ratingKey)
            assertEquals("Collapsing must preserve playback state", playingBeforeClick, playback.isPlaying.value)
        } finally {
            if (seededFixture) playback.clearQueue()
        }
    }

    private fun navigationTab(label: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            (hasText(label) or hasContentDescription(label))

    private fun openSearchFromDiscover() {
        composeRule.onNode(navigationTab("Discover")).performClick()
        composeRule.onNodeWithTag("discover_open_search").performClick()
    }
}
