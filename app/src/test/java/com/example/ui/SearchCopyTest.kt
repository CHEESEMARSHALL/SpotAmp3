package com.example.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import com.example.R
import com.example.data.CompanionConnectionStatus
import com.example.data.PlexMedia
import com.example.data.PlexMetadata
import com.example.data.PlexPart
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchCopyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun downloadedTrackSubtitleUsesFormattedResource() {
        val subtitle = context.getString(
            R.string.common_artist_album_subtitle,
            "M83",
            "Hurry Up, We're Dreaming"
        )
        val expected = context.getString(R.string.search_track_downloaded_subtitle, subtitle)

        composeRule.setContent {
            MyApplicationTheme {
                SearchItemRow(
                    title = "Midnight City",
                    subtitle = subtitle,
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

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun downloadedOnlyEmptyStateOffersShowAllSongsAtLargeText() {
        val query = "ambient"
        val message = context.getString(R.string.search_no_downloaded_matches, query)
        val action = context.getString(R.string.search_show_all_songs)
        var showAllSongsCount = 0

        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 1.3f)
            ) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 200.dp)) {
                        SearchDownloadedOnlyEmptyState(
                            query = query,
                            onShowAllSongs = { showAllSongsCount++ }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onNodeWithText(action).assertIsDisplayed().performClick()
        assertEquals(1, showAllSongsCount)
    }

    @Test
    fun searchGroupingSkipsBlankAlbumArtistAndUsesTrackArtist() {
        val result = PlexMetadata(
            ratingKey = "track-1",
            title = "Blue Train",
            type = "track",
            albumArtist = " ",
            grandparentTitle = "John Coltrane"
        )

        assertEquals("John Coltrane", searchGroupingArtist(result))
    }

    @Test
    fun emptyHintMatchesConnectedOfflineAndFirstRunStates() {
        val connected = context.getString(R.string.search_empty_connected)
        val offline = context.getString(R.string.search_empty_offline)
        val firstRun = context.getString(R.string.search_empty_first_run)
        val state = mutableStateOf(0)
        composeRule.setContent {
            MyApplicationTheme {
                SearchEmptyStateHint(isConfigured = state.value == 0, hasSavedLibrary = state.value != 2)
            }
        }
        composeRule.onNodeWithText(connected).assertIsDisplayed()
        state.value = 1
        composeRule.waitForIdle()
        composeRule.onNodeWithText(offline).assertIsDisplayed()
        state.value = 2
        composeRule.waitForIdle()
        composeRule.onNodeWithText(firstRun).assertIsDisplayed()
    }

    @Test
    fun unpairedFirstRunSearchOffersLargeDirectPairingAction() {
        val firstRun = context.getString(R.string.search_empty_first_run)
        val pairingAction = context.getString(R.string.library_pair_spotcore_action)
        var pairingStarts = 0
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 1.3f)
            ) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 260.dp)) {
                        SearchEmptyStateHint(
                            isConfigured = false,
                            hasSavedLibrary = false,
                            companionConfigured = false,
                            onStartPairing = { pairingStarts++ }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(firstRun).assertIsDisplayed()
        composeRule.onNodeWithText(pairingAction).assertIsDisplayed()
        composeRule.onNodeWithTag("search_pair_spotcore_action")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, pairingStarts)
    }

    @Test
    fun blankSearchCompactsForImeAtLargeTextSoPairingActionRemainsVisible() {
        val firstRun = context.getString(R.string.search_empty_first_run)
        val pairingAction = context.getString(R.string.library_pair_spotcore_action)
        var pairingStarts = 0
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 1.3f)
            ) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 180.dp)) {
                        SearchBlankQueryState(
                            modifier = Modifier.fillMaxSize(),
                            compactForIme = true,
                            isConfigured = false,
                            hasSavedLibrary = false,
                            companionConfigured = false,
                            connectionStatus = CompanionConnectionStatus.NOT_PAIRED,
                            onOpenConnectionSettings = {},
                            onStartPairing = { pairingStarts++ }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(firstRun).assertIsDisplayed()
        composeRule.onNodeWithText(pairingAction).assertIsDisplayed()
        composeRule.onNodeWithTag("search_pair_spotcore_action")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, pairingStarts)
    }

    @Test
    fun noResultsStateKeepsQuerySpecificCopyAndClearAction() {
        val query = "wave1check"
        val noMatches = context.getString(R.string.search_no_matches, query)
        val clear = context.getString(R.string.search_clear)
        var clearCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                Box(Modifier.requiredSize(320.dp, 240.dp)) {
                    SearchNoResultsState(query = query, onClearQuery = { clearCount++ })
                }
            }
        }

        composeRule.onNodeWithText(noMatches).assertIsDisplayed()
        composeRule.onNodeWithText(clear)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.search_try_another_query))
            .assertIsDisplayed()
        assertEquals(1, clearCount)
    }

    @Test
    fun companionOfflineSearchHintExplainsSavedDetailsAndPlayableDownloads() {
        val offlineGuidance = context.getString(R.string.connection_offline_body)
        composeRule.setContent {
            MyApplicationTheme {
                SearchEmptyStateHint(
                    isConfigured = true,
                    hasSavedLibrary = true,
                    companionConfigured = true,
                    connectionStatus = CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE
                )
            }
        }

        composeRule.onNodeWithText(offlineGuidance).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.search_empty_connected)).assertDoesNotExist()
        composeRule.onNodeWithTag("search_pair_spotcore_action").assertDoesNotExist()
    }

    @Test
    fun companionUnavailableEmptySearchOffersConnectionSettings() {
        val unavailableGuidance = context.getString(R.string.connection_unavailable_body)
        val connectionSettings = context.getString(R.string.home_open_settings)
        var settingsOpens = 0
        composeRule.setContent {
            MyApplicationTheme {
                SearchEmptyStateHint(
                    isConfigured = true,
                    hasSavedLibrary = false,
                    companionConfigured = true,
                    connectionStatus = CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE,
                    onOpenConnectionSettings = { settingsOpens++ }
                )
            }
        }

        composeRule.onNodeWithText(unavailableGuidance).assertIsDisplayed()
        composeRule.onNodeWithText(connectionSettings).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("search_pair_spotcore_action").assertDoesNotExist()
        assertEquals(1, settingsOpens)
    }

    @Test
    fun offlineSearchResultsExplainThatCachedDetailsAreNotAudioAtLargeText() {
        val statusLabel = context.getString(R.string.connection_offline_library)
        val guidance = context.getString(R.string.connection_offline_body)
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 1.3f)
            ) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(288.dp, 220.dp)) {
                        SearchOfflineResultsNotice()
                    }
                }
            }
        }

        composeRule.onNodeWithTag("search_offline_results_notice")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite
                )
            )
        composeRule.onNodeWithText(statusLabel).assertIsDisplayed()
        composeRule.onNodeWithText(guidance).assertIsDisplayed()
    }

    @Test
    fun searchShowsOfflineResultsNoticeOnlyWhenSpotCoreIsOfflineWithSavedLibrary() {
        assertTrue(
            shouldShowSearchOfflineNotice(
                companionConfigured = true,
                connectionStatus = CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE
            )
        )
        assertFalse(
            shouldShowSearchOfflineNotice(
                companionConfigured = false,
                connectionStatus = CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE
            )
        )
        assertFalse(
            shouldShowSearchOfflineNotice(
                companionConfigured = true,
                connectionStatus = CompanionConnectionStatus.CONNECTED
            )
        )
        assertFalse(
            shouldShowSearchOfflineNotice(
                companionConfigured = true,
                connectionStatus = CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE
            )
        )
    }

    @Test
    fun searchArtworkDoesNotRepeatTheVisibleResultTitle() {
        val title = "Midnight City"
        composeRule.setContent {
            MyApplicationTheme {
                SearchItemRow(
                    title = title,
                    subtitle = "M83",
                    thumb = "/midnight-city.jpg",
                    isCircle = false,
                    baseUrl = "http://127.0.0.1:1",
                    token = "",
                    onMoreClick = {},
                    onClick = {}
                )
            }
        }

        assertEquals(
            0,
            composeRule.onAllNodesWithContentDescription(title, useUnmergedTree = true)
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun searchResultOptionsOpenTargetSpecificTrackAlbumAndArtistMenus() {
        val artist = PlexMetadata(ratingKey = "artist-1", title = "M83", type = "artist", thumb = "/artist.jpg")
        val album = PlexMetadata(
            ratingKey = "album-1",
            title = "Hurry Up, We're Dreaming",
            type = "album",
            thumb = "/album.jpg",
            parentTitle = "M83"
        )
        val track = PlexMetadata(
            ratingKey = "track-1",
            title = "Midnight City",
            type = "track",
            thumb = "/track.jpg",
            parentTitle = "Hurry Up, We're Dreaming",
            grandparentTitle = "M83",
            duration = 241_000,
            media = listOf(PlexMedia(part = listOf(PlexPart(key = "/library/parts/track-part")))),
            parentRatingKey = "album-1",
            grandparentRatingKey = "artist-1",
            albumArtist = "M83"
        )
        val selectedItems = mutableListOf<ContextMenuItem>()
        val artistsCategory = context.getString(R.string.search_section_artists)
        val albumsCategory = context.getString(R.string.search_section_albums)
        val tracksCategory = context.getString(R.string.search_section_songs)

        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    listOf(artist to artistsCategory, album to albumsCategory, track to tracksCategory)
                        .forEach { (item, category) ->
                            SearchResultsItemRow(
                                item = item,
                                category = category,
                                baseUrl = "",
                                token = "",
                                isPlaying = false,
                                onMoreItemSelected = { selectedItems.add(it) },
                                onClick = {}
                            )
                        }
                }
            }
        }

        listOf(artist, album, track).forEach { item ->
            composeRule.onNodeWithContentDescription(
                context.getString(R.string.search_more_accessibility, item.title)
            )
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .performClick()
        }

        assertEquals(3, selectedItems.size)
        assertEquals(ContextMenuItem.Artist("artist-1", "M83", "/artist.jpg"), selectedItems[0])
        assertEquals(
            ContextMenuItem.Album("album-1", "Hurry Up, We're Dreaming", "M83", "/album.jpg"),
            selectedItems[1]
        )
        assertEquals(
            ContextMenuItem.Track(
                ratingKey = "track-1",
                title = "Midnight City",
                artist = "M83",
                album = "Hurry Up, We're Dreaming",
                key = "/library/parts/track-part",
                thumb = "/track.jpg",
                duration = 241_000,
                albumRatingKey = "album-1",
                albumArtist = "M83",
                artistRatingKey = "artist-1"
            ),
            selectedItems[2]
        )
    }

    @Test
    fun trackWithoutPrimaryArtistUsesAlbumArtistInsteadOfAlbumTitle() {
        val track = PlexMetadata(
            ratingKey = "track-no-primary-artist",
            title = "Track Without Artist Credit",
            type = "track",
            parentTitle = "Actual Album",
            grandparentTitle = "",
            albumArtist = "Correct Artist",
            media = listOf(PlexMedia(part = listOf(PlexPart(key = "/library/parts/track-part")))),
            parentRatingKey = "album-1"
        )
        val selectedItems = mutableListOf<ContextMenuItem>()
        var trackPlayed = false
        val expectedSubtitle = context.getString(
            R.string.common_artist_album_subtitle,
            "Correct Artist",
            "Actual Album"
        )

        composeRule.setContent {
            MyApplicationTheme {
                SearchResultsItemRow(
                    item = track,
                    category = context.getString(R.string.search_section_songs),
                    baseUrl = "",
                    token = "",
                    isPlaying = false,
                    onMoreItemSelected = selectedItems::add,
                    onClick = { trackPlayed = true }
                )
            }
        }

        composeRule.onNodeWithText(expectedSubtitle)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(true, trackPlayed)
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.search_more_accessibility, track.title)
        ).performClick()

        assertEquals("Correct Artist", (selectedItems.single() as ContextMenuItem.Track).artist)
    }

    @Test
    fun unavailableBodyShowsRecoveryCopyForTheCurrentLibraryState() {
        val savedLibraryMessage = context.getString(searchFailureString(hasSavedLibrary = true))
        val noLibraryMessage = context.getString(searchFailureString(hasSavedLibrary = false))
        assertEquals(
            "Couldn’t reach SpotCore to search. Try again, or browse your saved library on this phone.",
            savedLibraryMessage
        )
        assertEquals(
            "Couldn’t reach SpotCore. Check the connection or try again.",
            noLibraryMessage
        )
        val message = mutableStateOf(savedLibraryMessage)
        composeRule.setContent {
            MyApplicationTheme { SearchUnavailableBody(message.value) }
        }
        val failure = composeRule.onNodeWithText(savedLibraryMessage)
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite
                )
            )
        assertEquals(savedLibraryMessage, failure.fetchSemanticsNode().config[SemanticsProperties.Error])
        message.value = noLibraryMessage
        composeRule.waitForIdle()
        val changedFailure = composeRule.onNodeWithText(noLibraryMessage).assertIsDisplayed()
        assertEquals(noLibraryMessage, changedFailure.fetchSemanticsNode().config[SemanticsProperties.Error])
    }

    @Test
    fun unavailableBodyHasSafeFallbackWhenNoSpecificMessageIsAvailable() {
        val fallback = context.getString(R.string.search_unavailable_body)
        assertEquals("Try again after checking your SpotCore connection.", fallback)
        composeRule.setContent {
            MyApplicationTheme { SearchUnavailableBody(message = null) }
        }
        composeRule.onNodeWithText(fallback).assertIsDisplayed()
    }

    @Test
    fun unavailableStateRetryInvokesItsSearchAction() {
        var retries = 0
        composeRule.setContent {
            MyApplicationTheme {
                SearchUnavailableState(
                    message = context.getString(R.string.search_failure_without_saved_library),
                    onRetry = { retries++ },
                    onOpenConnectionSettings = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.search_retry))
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, retries)
    }

    @Test
    fun unavailableStateOpensConnectionSettings() {
        var settingsOpens = 0
        composeRule.setContent {
            MyApplicationTheme {
                SearchUnavailableState(
                    message = context.getString(R.string.search_failure_without_saved_library),
                    onRetry = {},
                    onOpenConnectionSettings = { settingsOpens++ }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.home_open_settings))
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, settingsOpens)
    }

    @Test
    fun unavailableStateKeepsRetryVisibleAtLargeTextInShortViewport() {
        val message = context.getString(R.string.search_failure_with_saved_library)
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 1.3f)
            ) {
                Box(Modifier.requiredSize(288.dp, 200.dp)) {
                    SearchUnavailableState(
                        message = message,
                        onRetry = {},
                        onOpenConnectionSettings = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText(message).assertIsDisplayed()
        val viewport = composeRule.onNodeWithTag("search_unavailable_state")
            .fetchSemanticsNode().boundsInRoot
        val retry = composeRule.onNodeWithText(context.getString(R.string.search_retry))
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Retry must stay within the short viewport", retry.top >= viewport.top && retry.bottom <= viewport.bottom)
    }

    @Test
    fun unavailableStateKeepsBothRecoveryActionsReachableAtLargeTextInCompactViewport() {
        val message = context.getString(R.string.search_failure_with_saved_library)
        var settingsOpens = 0
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 1.3f)
            ) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(288.dp, 200.dp)) {
                        SearchUnavailableState(
                            message = message,
                            onRetry = {},
                            onOpenConnectionSettings = { settingsOpens++ }
                        )
                    }
                }
            }
        }

        val viewport = composeRule.onNodeWithTag("search_unavailable_state")
            .fetchSemanticsNode().boundsInRoot
        val retry = composeRule.onNodeWithText(context.getString(R.string.search_retry))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        val settings = composeRule.onNodeWithText(context.getString(R.string.home_open_settings))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot

        assertTrue("Retry must stay within the compact viewport", retry.top >= viewport.top && retry.bottom <= viewport.bottom)
        assertTrue("Connection settings must stay within the compact viewport", settings.top >= viewport.top && settings.bottom <= viewport.bottom)

        composeRule.onNodeWithText(context.getString(R.string.home_open_settings)).performClick()
        assertEquals(1, settingsOpens)
    }
}
