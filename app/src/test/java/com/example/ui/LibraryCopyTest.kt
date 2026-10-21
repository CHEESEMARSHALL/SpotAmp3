package com.example.ui

import android.content.Context
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.CachedTrack
import com.example.data.DownloadPresentationState
import com.example.data.PlaylistEntity
import com.example.data.PlaylistSyncState
import com.example.data.PlexMetadata
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryCopyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun mainMenuTitleUsesTheSharedLibraryNavigationLabel() {
        val expected = context.getString(R.string.nav_library)
        composeRule.setContent {
            MyApplicationTheme {
                LibraryMainMenuTitle()
            }
        }

        composeRule.onNodeWithTag("library_title").assertIsDisplayed()
        composeRule.onNodeWithText(expected, substring = false).assertIsDisplayed()
    }

    @Test
    fun libraryFacetRowsUseResourceBackedExploreCopyAndRemainClickable() {
        val facet = "Ambient"
        val expected = context.getString(R.string.library_category_explore, facet)
        var opened = false
        composeRule.setContent {
            MyApplicationTheme {
                LibraryCategoryRow(
                    title = facet,
                    subtitle = libraryCategoryExploreCopy(facet),
                    onClick = { opened = true }
                )
            }
        }

        composeRule.onNodeWithText(expected, substring = false)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(true, opened)
    }

    @Test
    fun artistGridItemExposesOpenAndMoreActionsSeparately() {
        val artist = PlexMetadata(ratingKey = "artist-42", title = "M83", type = "artist")
        var opened = false
        var moreOpened = false
        composeRule.setContent {
            MyApplicationTheme {
                ArtistGridItem(
                    artist = artist,
                    baseUrl = "",
                    token = "",
                    onClick = { opened = true },
                    onMoreClick = { moreOpened = true }
                )
            }
        }

        composeRule.onNodeWithTag("artist_item_${artist.ratingKey}")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(true, opened)

        val moreAction = composeRule.onNodeWithContentDescription(
            context.getString(R.string.library_artist_more_options_accessibility, artist.title)
        ).assertIsDisplayed()
        val moreBounds = moreAction.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Artist More options should retain a 48dp-wide touch target",
            moreBounds.width >= with(composeRule.density) { 48.dp.toPx() }
        )
        assertTrue(
            "Artist More options should retain a 48dp-tall touch target",
            moreBounds.height >= with(composeRule.density) { 48.dp.toPx() }
        )
        moreAction.performClick()
        assertEquals(true, moreOpened)
    }

    @Test
    fun artistAlphabetButtonsNameTheirJumpDestination() {
        var selectedLetter: String? = null
        composeRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.width(48.dp)) {
                    ArtistAlphabetLetterButton("A") { selectedLetter = "A" }
                    ArtistAlphabetLetterButton("#") { selectedLetter = "#" }
                }
            }
        }

        val letterButton = composeRule.onNodeWithText("A", substring = false)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val letterBounds = letterButton.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Artist letter jump should provide a 48dp-wide hit area",
            letterBounds.width >= with(composeRule.density) { 48.dp.toPx() }
        )
        assertTrue(
            "Artist letter jump should provide a 48dp-tall hit area",
            letterBounds.height >= with(composeRule.density) { 48.dp.toPx() }
        )
        assertEquals(
            context.getString(R.string.library_jump_to_letter_accessibility, "A"),
            letterButton.fetchSemanticsNode().config[SemanticsActions.OnClick].label
        )
        letterButton.performClick()
        assertEquals("A", selectedLetter)

        val otherButton = composeRule.onNodeWithText("#", substring = false)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals(
            context.getString(R.string.library_jump_to_other_initials_accessibility),
            otherButton.fetchSemanticsNode().config[SemanticsActions.OnClick].label
        )
        assertEquals(
            "Jump to artists whose names start outside A to Z",
            context.getString(R.string.library_jump_to_other_initials_accessibility)
        )
        otherButton.performClick()
        assertEquals("#", selectedLetter)
    }

    @Test
    fun unavailableArtistLetterIsDisabled() {
        composeRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.width(48.dp)) {
                    ArtistAlphabetLetterButton("Q", enabled = false) { error("Unavailable letters must not jump") }
                }
            }
        }

        composeRule.onNodeWithText("Q", substring = false)
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun artistAlphabetBucketsInitialsOutsideAToZUnderHash() {
        assertEquals("A", artistAlphabetBucket("Alpha"))
        assertEquals("#", artistAlphabetBucket("Éclair"))
        assertEquals("#", artistAlphabetBucket("東京"))
        assertEquals("#", artistAlphabetBucket("9 Lives"))
        assertEquals("#", artistAlphabetBucket("   "))
    }

    @Test
    fun downloadedOnlyEmptyStateDoesNotImplySyncedLibraryIsEmpty() {
        val expected = context.getString(R.string.library_empty_downloaded)
        composeRule.setContent {
            MyApplicationTheme {
                LibraryEmptyState(LibraryCategory.TRACKS, downloadedOnly = true, filter = null)
            }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun downloadedOnlyFilterEmptyStateNamesTheFilter() {
        val filter = "Jazz"
        val expected = context.getString(R.string.library_empty_downloaded_filter, filter)
        composeRule.setContent {
            MyApplicationTheme {
                LibraryEmptyState(LibraryCategory.ALBUMS, downloadedOnly = true, filter = filter)
            }
        }
        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun regularEmptyStateIdentifiesTheMissingLibraryContent() {
        val expected = context.getString(R.string.library_empty_artists)
        composeRule.setContent {
            MyApplicationTheme {
                LibraryEmptyState(LibraryCategory.ARTISTS, downloadedOnly = false, filter = null)
            }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun emptyFilterStateNamesTheFilterRatherThanTheEntireLibrary() {
        val filter = "Jazz"
        val expected = context.getString(R.string.library_empty_filter, filter)
        composeRule.setContent {
            MyApplicationTheme {
                LibraryEmptyState(LibraryCategory.TRACKS, downloadedOnly = false, filter = filter)
            }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun libraryCategoryCountsShowActualZeroAndUseSingularWording() {
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    Text(libraryCategoryCountLabel(LibraryCategory.ARTISTS, 0))
                    Text(libraryCategoryCountLabel(LibraryCategory.TRACKS, 1))
                    Text(libraryCategoryCountLabel(LibraryCategory.ALBUMS, 2))
                }
            }
        }

        composeRule.onNodeWithText("0 artists").assertIsDisplayed()
        composeRule.onNodeWithText("1 track").assertIsDisplayed()
        composeRule.onNodeWithText("2 albums").assertIsDisplayed()
    }

    @Test
    fun savedPlaylistHeadingUsesAccurateSingularAndPluralCounts() {
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    Text(librarySavedPlaylistsHeading(0))
                    Text(librarySavedPlaylistsHeading(1))
                    Text(librarySavedPlaylistsHeading(2))
                }
            }
        }

        composeRule.onNodeWithText("Saved playlists (0)").assertIsDisplayed()
        composeRule.onNodeWithText("Saved playlist (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Saved playlists (2)").assertIsDisplayed()
    }

    @Test
    fun emptyPlaylistGuidanceNamesTheVisibleActionInsteadOfItsIcon() {
        val expected = context.getString(R.string.library_empty_playlists)
        composeRule.setContent {
            MyApplicationTheme { Text(libraryEmptyPlaylistsCopy()) }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
        assertEquals(false, expected.contains("Tap '+'"))
        assertEquals(true, expected.contains(context.getString(R.string.library_new_playlist)))
    }

    @Test
    fun smartPlaylistBuilderCopyUsesMusicLibraryLanguage() {
        assertEquals("Smart Playlist Generator", context.getString(R.string.library_smart_playlist_heading))
        assertEquals(
            "Describe a vibe, genre, or mood to make a playlist from your music.",
            context.getString(R.string.library_smart_playlist_description)
        )
        assertEquals("e.g. Chill synthwave vibes", context.getString(R.string.library_smart_playlist_prompt_hint))
        assertEquals("Generate Smart Playlist", context.getString(R.string.library_smart_playlist_generate))
    }

    @Test
    fun createPlaylistDialogRequiresNameAndReturnsTrimmedValue() {
        val currentName = mutableStateOf("")
        val createdName = AtomicReference<String?>(null)
        composeRule.setContent {
            MyApplicationTheme {
                LibraryCreatePlaylistDialog(
                    name = currentName.value,
                    onNameChange = { currentName.value = it },
                    onDismiss = {},
                    onCreate = { createdName.set(it) }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.library_new_playlist)).assertIsDisplayed()
        composeRule.onNodeWithTag("library_create_playlist_confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("library_playlist_name_input").performTextInput("  Road Trip  ")
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals("Road Trip", currentName.value.trim()) }
        composeRule.onNodeWithTag("library_create_playlist_confirm").performClick()
        assertEquals("Road Trip", createdName.get())
    }

    @Test
    fun createPlaylistDialogCancelInvokesDismissAction() {
        var dismissed = false
        composeRule.setContent {
            MyApplicationTheme {
                LibraryCreatePlaylistDialog(
                    name = "",
                    onNameChange = {},
                    onDismiss = { dismissed = true },
                    onCreate = {}
                )
            }
        }

        composeRule.onNodeWithTag("library_create_playlist_cancel").performClick()
        assertEquals(true, dismissed)
    }

    @Test
    fun createPlaylistDialogKeepsRecoveryCopyAndBlocksDuplicateSubmissionWhileSaving() {
        composeRule.setContent {
            MyApplicationTheme {
                LibraryCreatePlaylistDialog(
                    name = "Road Trip",
                    isCreating = true,
                    showError = true,
                    onNameChange = {},
                    onDismiss = {},
                    onCreate = {}
                )
            }
        }

        val failureMessage = context.getString(R.string.library_create_playlist_error)
        val failureNode = composeRule.onNodeWithText(failureMessage).assertIsDisplayed()
        assertEquals(LiveRegionMode.Polite, failureNode.fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        assertEquals(failureMessage, failureNode.fetchSemanticsNode().config[SemanticsProperties.Error])
        composeRule.onNodeWithTag("library_create_playlist_confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("library_create_playlist_cancel").assertIsNotEnabled()
    }

    @Test
    fun playlistActionsNameTheirTargetAndInvokeTheRequestedOperation() {
        val playlist = PlaylistEntity(id = 7, name = "Road Trip")
        var shareCount = 0
        var downloadCount = 0
        var deleteCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                LibraryPlaylistActions(
                    playlist = playlist,
                    showShareAction = true,
                    onShare = { shareCount++ },
                    onDownload = { downloadCount++ },
                    onDelete = { deleteCount++ }
                )
            }
        }

        val shareAction = composeRule.onNodeWithContentDescription(
            context.getString(R.string.library_playlist_share_action, playlist.name)
        ).assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, shareAction.fetchSemanticsNode().config[SemanticsProperties.Role])
        shareAction.performClick()
        val downloadAction = composeRule.onNodeWithContentDescription(
            context.getString(R.string.library_playlist_download_action, playlist.name)
        ).assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, downloadAction.fetchSemanticsNode().config[SemanticsProperties.Role])
        downloadAction.performClick()
        val deleteAction = composeRule.onNodeWithContentDescription(
            context.getString(R.string.library_playlist_delete_action, playlist.name)
        ).assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, deleteAction.fetchSemanticsNode().config[SemanticsProperties.Role])
        deleteAction.performClick()

        assertEquals(1, shareCount)
        assertEquals(1, downloadCount)
        assertEquals(1, deleteCount)
    }

    @Test
    fun playlistSyncStatusesUseClearResourceBackedLanguage() {
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    Text(libraryPlaylistStatusLabel(PlaylistEntity(name = "Local")))
                    Text(libraryPlaylistStatusLabel(PlaylistEntity(name = "Shared", remoteId = "r1")))
                    Text(libraryPlaylistStatusLabel(PlaylistEntity(name = "Pending", remoteId = "r2", syncState = PlaylistSyncState.DIRTY)))
                    Text(libraryPlaylistStatusLabel(PlaylistEntity(name = "Conflict", remoteId = "r3", syncState = PlaylistSyncState.CONFLICT)))
                }
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.library_playlist_status_local)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.library_playlist_status_shared)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.library_playlist_status_pending)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.library_playlist_status_conflict)).assertIsDisplayed()
    }

    @Test
    fun playlistDeletionRequiresExplicitConfirmationAndCancelDoesNotDelete() {
        val playlist = PlaylistEntity(id = 12, name = "Road Trip")
        var confirmed = false
        var dismissed = false
        composeRule.setContent {
            MyApplicationTheme {
                LibraryDeletePlaylistDialog(
                    playlist = playlist,
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = true }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.library_delete_playlist_title)).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.library_delete_local_playlist_message, playlist.name)
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("library_delete_playlist_cancel").performClick()
        assertEquals(true, dismissed)
        assertEquals(false, confirmed)
    }

    @Test
    fun sharedPlaylistDeletionNamesSpotCoreAndExposesFailureRecovery() {
        val playlist = PlaylistEntity(id = 13, name = "Shared Mix", remoteId = "remote-13")
        composeRule.setContent {
            MyApplicationTheme {
                LibraryDeletePlaylistDialog(
                    playlist = playlist,
                    isDeleting = true,
                    showError = true,
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.library_delete_shared_playlist_message, playlist.name)
        ).assertIsDisplayed()
        val failureMessage = context.getString(R.string.library_delete_playlist_error)
        val failureNode = composeRule.onNodeWithText(failureMessage).assertIsDisplayed()
        assertEquals(LiveRegionMode.Polite, failureNode.fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        assertEquals(failureMessage, failureNode.fetchSemanticsNode().config[SemanticsProperties.Error])
        composeRule.onNodeWithTag("library_delete_playlist_confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("library_delete_playlist_cancel").assertIsNotEnabled()
    }

    @Test
    fun libraryOverviewCategoryTitlesComeFromResources() {
        val categories = listOf(
            LibraryCategory.ARTISTS to R.string.library_category_artists,
            LibraryCategory.ALBUMS to R.string.library_category_albums,
            LibraryCategory.COMPILATIONS to R.string.library_category_compilations,
            LibraryCategory.SOUNDTRACKS to R.string.library_category_soundtracks,
            LibraryCategory.TRACKS to R.string.library_category_tracks,
            LibraryCategory.PLAYLISTS to R.string.library_category_playlists,
            LibraryCategory.ARTIST_GENRES to R.string.library_category_artist_genres,
            LibraryCategory.ALBUM_GENRES to R.string.library_category_album_genres,
            LibraryCategory.STYLES to R.string.library_category_styles,
            LibraryCategory.MOODS to R.string.library_category_moods
        )
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    categories.forEach { (category, _) -> Text(libraryCategoryTitle(category)) }
                }
            }
        }

        categories.forEach { (_, resource) ->
            composeRule.onNodeWithText(context.getString(resource)).assertIsDisplayed()
        }
    }

    @Test
    fun categorySortActionsNameTheirTargetAndInvokeSorting() {
        var sortCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    LibraryCategorySortButton(
                        LibraryCategory.ARTISTS,
                        onClick = { sortCount++ },
                        modifier = Modifier.size(32.dp)
                    )
                    LibraryCategorySortButton(LibraryCategory.ALBUMS, onClick = { sortCount++ })
                    LibraryCategorySortButton(LibraryCategory.TRACKS, onClick = { sortCount++ })
                }
            }
        }

        val artistSort = composeRule.onNodeWithContentDescription(context.getString(R.string.library_sort_artists))
            .assertIsDisplayed()
        val sortBounds = artistSort.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Library sort actions should retain a 48dp-wide target when passed a compact modifier",
            sortBounds.width >= with(composeRule.density) { 48.dp.toPx() }
        )
        assertTrue(
            "Library sort actions should retain a 48dp-tall target when passed a compact modifier",
            sortBounds.height >= with(composeRule.density) { 48.dp.toPx() }
        )
        artistSort.performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.library_sort_albums))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.library_sort_tracks))
            .assertIsDisplayed()
            .performClick()

        assertEquals(3, sortCount)
        assertEquals(3, composeRule.onAllNodesWithTag("library_category_sort").fetchSemanticsNodes().size)
    }

    @Test
    fun libraryViewModeActionHasA48dpTargetAndNamesTheNextMode() {
        var toggles = 0
        composeRule.setContent {
            MyApplicationTheme {
                LibraryViewModeButton(isGrid = true) { toggles++ }
            }
        }

        val action = composeRule.onNodeWithContentDescription(
            context.getString(R.string.library_switch_to_list_view)
        ).assertIsDisplayed()
        val bounds = action.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Library view-mode action should have a 48dp-wide touch target",
            bounds.width >= with(composeRule.density) { 48.dp.toPx() }
        )
        assertTrue(
            "Library view-mode action should have a 48dp-tall touch target",
            bounds.height >= with(composeRule.density) { 48.dp.toPx() }
        )
        action.performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun libraryBackButtonCopyMatchesFilterAndNavigationBehavior() {
        var clearFilterCount = 0
        var backToLibraryCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    LibraryBackButton(hasActiveFilter = true) { clearFilterCount++ }
                    LibraryBackButton(hasActiveFilter = false) { backToLibraryCount++ }
                }
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.library_clear_filter_accessibility))
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.library_back_accessibility))
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
            .performClick()

        assertEquals(1, clearFilterCount)
        assertEquals(1, backToLibraryCount)
    }

    @Test
    fun categoriesWithoutSortingDoNotShowAnInertSortButton() {
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    LibraryCategorySortButton(LibraryCategory.PLAYLISTS, onClick = {})
                    LibraryCategorySortButton(LibraryCategory.ARTIST_GENRES, onClick = {})
                    LibraryCategorySortButton(LibraryCategory.ALBUM_GENRES, onClick = {})
                    LibraryCategorySortButton(LibraryCategory.STYLES, onClick = {})
                    LibraryCategorySortButton(LibraryCategory.MOODS, onClick = {})
                }
            }
        }

        assertEquals(0, composeRule.onAllNodesWithTag("library_category_sort").fetchSemanticsNodes().size)
    }

    @Test
    fun artistOptionsNameTheirTargetAndInvokeItsMenu() {
        val artistName = "Explosions in the Sky"
        var selectedArtist: String? = null
        composeRule.setContent {
            MyApplicationTheme {
                ArtistGridItem(
                    artist = PlexMetadata(ratingKey = "artist-1", title = artistName, type = "artist"),
                    baseUrl = "",
                    token = "",
                    onMoreClick = { selectedArtist = artistName },
                    onClick = {}
                )
            }
        }

        val expectedLabel = context.getString(R.string.library_artist_more_options_accessibility, artistName)
        composeRule.onNodeWithContentDescription(expectedLabel)
            .assertIsDisplayed()
            .performClick()

        assertEquals(artistName, selectedArtist)
    }

    @Test
    fun downloadedArtistBadgeNamesOfflineAvailabilityClearly() {
        composeRule.setContent {
            MyApplicationTheme {
                DownloadedArtistBadge()
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.library_saved_offline_badge)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.library_saved_offline_badge_accessibility)
        ).assertIsDisplayed()
    }

    @Test
    fun libraryTrackRowAndOptionsExposeTheirActions() {
        val track = CachedTrack(
            ratingKey = "track-42",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            key = "/library/parts/42",
            thumb = "",
            duration = 241_000
        )
        var optionsCount = 0
        var playCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                TrackRow(
                    track = track,
                    baseUrl = "",
                    token = "",
                    onMoreClick = { optionsCount++ },
                    onClick = { playCount++ }
                )
            }
        }

        val trackRow = composeRule.onNodeWithText(track.title)
        assertEquals(Role.Button, trackRow.fetchSemanticsNode().config[SemanticsProperties.Role])
        trackRow.performClick()
        assertEquals(1, playCount)

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.library_track_more_options_accessibility, track.title)
        )
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, optionsCount)
    }

    @Test
    fun libraryTrackRowShowsAudioDownloadStateAndAction() {
        val track = CachedTrack(
            ratingKey = "track-download-state",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            key = "/library/parts/download-state",
            thumb = "",
            duration = 241_000
        )
        var downloadActionCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                TrackRow(
                    track = track,
                    baseUrl = "",
                    token = "",
                    onMoreClick = {},
                    onClick = {},
                    downloadState = DownloadPresentationState.AVAILABLE,
                    onDownloadAction = { downloadActionCount++ }
                )
            }
        }

        composeRule.onNodeWithTag("library_track_download_state", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.download_state_available))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.download_action_download, track.title)
        )
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, downloadActionCount)
    }

    @Test
    fun removingDownloadedAudioRequiresAnExplicitConfirmation() {
        var removeCount = 0
        var dismissCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                LibraryRemoveDownloadedAudioDialog(
                    trackTitle = "Midnight City",
                    onDismiss = { dismissCount++ },
                    onConfirm = { removeCount++ }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.library_remove_download_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.library_remove_download_message, "Midnight City"))
            .assertIsDisplayed()
        assertEquals(0, removeCount)
        composeRule.onNodeWithText(context.getString(R.string.library_remove_download_confirm)).performClick()
        assertEquals(1, removeCount)
        assertEquals(0, dismissCount)
    }

    @Test
    fun trackRowFavoriteAndRatingActionsHave48DpTargets() {
        val track = CachedTrack(
            ratingKey = "track-actions-48",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            key = "/library/parts/48",
            thumb = "",
            duration = 241_000
        )
        var favoriteState: Boolean? = null
        var selectedRating: Int? = null
        composeRule.setContent {
            MyApplicationTheme {
                TrackRow(
                    track = track,
                    baseUrl = "",
                    token = "",
                    onMoreClick = {},
                    onClick = {},
                    onCompanionFavoriteChange = { favoriteState = it },
                    onCompanionRatingChange = { selectedRating = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.library_spotcore_favorite_add)
        )
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(true, favoriteState)

        composeRule.onNodeWithText(context.getString(R.string.library_spotcore_rating_rate_track))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.library_spotcore_rating_option, 3))
            .performClick()
        assertEquals(3, selectedRating)
    }

    @Test
    fun trackArtworkDoesNotRepeatItsVisibleTitleAsAnImageDescription() {
        val track = CachedTrack(
            ratingKey = "artwork-track",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            key = "/library/parts/artwork-track",
            thumb = "/artwork-track.jpg",
            duration = 241_000
        )
        composeRule.setContent {
            MyApplicationTheme {
                TrackRow(
                    track = track,
                    baseUrl = "http://127.0.0.1:1",
                    token = "",
                    onMoreClick = {},
                    onClick = {}
                )
            }
        }

        assertEquals(
            0,
            composeRule.onAllNodesWithContentDescription(track.title, useUnmergedTree = true)
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun activeTrackRowExposesItsPlaybackStatusAccessibly() {
        val track = CachedTrack(
            ratingKey = "playing-track",
            title = "Teardrop",
            artist = "Massive Attack",
            album = "Mezzanine",
            key = "/library/parts/playing-track",
            thumb = "",
            duration = 330_000
        )
        composeRule.setContent {
            MyApplicationTheme {
                TrackRow(
                    track = track,
                    baseUrl = "",
                    token = "",
                    onMoreClick = {},
                    onClick = {},
                    isPlaying = true
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.library_currently_playing_accessibility)
        ).assertIsDisplayed()
    }

    @Test
    fun noSelectedLibraryStateUsesTheActionableResourceCopy() {
        var settingsOpenCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                LibraryNoSelectionState(
                    companionConfigured = true,
                    onOpenSettings = { settingsOpenCount++ },
                    onStartPairing = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.library_no_selection)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.library_open_settings_action)).performClick()
        assertEquals(1, settingsOpenCount)
    }

    @Test
    fun unpairedLibraryStateOffersDirectSpotCorePairing() {
        var settingsOpenCount = 0
        var pairingRequestCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                LibraryNoSelectionState(
                    companionConfigured = false,
                    onOpenSettings = { settingsOpenCount++ },
                    onStartPairing = { pairingRequestCount++ }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.library_connect_spotcore_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.library_connect_spotcore_body)).assertIsDisplayed()
        composeRule.onNodeWithTag("library_pair_spotcore_action")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, pairingRequestCount)
        assertEquals(0, settingsOpenCount)
    }

    @Test
    fun libraryLoadFailureExplainsTheProblemAndOffersRetry() {
        val message = "SpotCore is temporarily unavailable. Try again in a moment."
        var retryCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                LibraryLoadFailureState(message = message, onRetry = { retryCount++ })
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.library_load_failure_title)).assertIsDisplayed()
        val error = composeRule.onNodeWithText(message).assertIsDisplayed()
        assertEquals(message, error.fetchSemanticsNode().config[SemanticsProperties.Error])
        assertEquals(LiveRegionMode.Polite, error.fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        composeRule.onNodeWithTag("library_load_retry")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, retryCount)
    }

    @Test
    fun libraryLoadFailureRetryStaysReachableAtLargeTextInCompactViewport() {
        val message = "SpotCore is temporarily unavailable. Try again in a moment."
        var retryCount = 0
        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, 1.3f)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(288.dp, 200.dp)) {
                        LibraryLoadFailureState(message = message, onRetry = { retryCount++ })
                    }
                }
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.library_load_failure_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("library_load_retry")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, retryCount)
        composeRule.onNodeWithText(message).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun staleLibraryRowsStayVisibleWithRetryableLoadWarning() {
        val message = "SpotCore is temporarily unavailable. Try again in a moment."
        var retryCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    LibraryLoadWarning(message = message, onRetry = { retryCount++ })
                    Text("Previously saved artist")
                }
            }
        }

        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onNodeWithText("Previously saved artist").assertIsDisplayed()
        composeRule.onNodeWithTag("library_load_retry")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, retryCount)
    }
}
