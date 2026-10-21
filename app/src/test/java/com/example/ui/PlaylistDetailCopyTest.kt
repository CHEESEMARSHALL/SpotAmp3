package com.example.ui

import android.content.Context
import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.PlaylistTrackEntity
import com.example.playback.TrackItem
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistDetailCopyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private var viewModelStore: ViewModelStore? = null

    @After
    fun clearViewModels() {
        viewModelStore?.clear()
        viewModelStore = null
    }

    @Test
    fun customPlaylistBackActionHasA48DpTargetAndNavigates() {
        var wentBack = false
        val viewModel = createMusicViewModel()
        composeRule.setContent {
            MyApplicationTheme {
                CustomPlaylistDetailScreen(
                    type = "daily_mix",
                    playlistId = "qa-playlist",
                    playlistName = "Evening Mix",
                    description = "",
                    tracks = emptyList(),
                    colors = emptyList(),
                    viewModel = viewModel,
                    onBack = { wentBack = true }
                )
            }
        }

        composeRule.onNodeWithTag("custom_playlist_back_button")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertTrue(wentBack)
    }

    @Test
    fun playlistActionsNameTheirTargetAndInvokeEachAction() {
        val playlistName = "Road Trip"
        var playCount = 0
        var shuffleCount = 0
        var downloadCount = 0

        composeRule.setContent {
            MyApplicationTheme {
                PlaylistDetailActionsRow(
                    playlistName = playlistName,
                    onPlayAll = { playCount++ },
                    onShuffle = { shuffleCount++ },
                    onDownload = { downloadCount++ }
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.playlist_detail_play_all, playlistName)
        ).performClick()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.playlist_detail_shuffle, playlistName)
        ).performClick()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.playlist_detail_download, playlistName)
        ).performClick()

        assertEquals(1, playCount)
        assertEquals(1, shuffleCount)
        assertEquals(1, downloadCount)
    }

    @Test
    fun emptyCustomPlaylistDoesNotShowInactiveActions() {
        composeRule.setContent {
            MyApplicationTheme {
                CustomPlaylistDetailActionsRow(
                    playlistName = "Empty Mix",
                    trackCount = 0,
                    isSaved = false,
                    isDownloading = false,
                    onPlayAll = {},
                    onShuffle = {},
                    onSave = {},
                    onDownload = {},
                    onAddToQueue = {}
                )
            }
        }

        listOf(
            "custom_playlist_play_all",
            "custom_playlist_shuffle",
            "custom_playlist_save",
            "custom_playlist_download",
            "custom_playlist_add_to_queue"
        ).forEach { tag ->
            assertEquals(0, composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun customPlaylistActionsNameTheirTargetAndSavedStatePreventsDuplicateSave() {
        val playlistName = "Evening Mix"
        var actionCount = 0
        val isSaved = mutableStateOf(false)

        composeRule.setContent {
            MyApplicationTheme {
                CustomPlaylistDetailActionsRow(
                    playlistName = playlistName,
                    trackCount = 4,
                    isSaved = isSaved.value,
                    isDownloading = false,
                    onPlayAll = { actionCount++ },
                    onShuffle = { actionCount++ },
                    onSave = {
                        actionCount++
                        isSaved.value = true
                    },
                    onDownload = { actionCount++ },
                    onAddToQueue = { actionCount++ }
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.playlist_detail_play_all, playlistName)
        ).performClick()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.playlist_detail_shuffle, playlistName)
        ).performClick()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.custom_playlist_save_action, playlistName)
        ).performClick()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.custom_playlist_download_action, playlistName)
        ).performClick()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.custom_playlist_queue_action, playlistName)
        ).performClick()
        assertEquals(5, actionCount)

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.custom_playlist_saved_status, playlistName)
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("custom_playlist_save").assertIsNotEnabled()
        assertEquals(5, actionCount)
    }

    @Test
    fun playlistHeaderUsesSingularAndPluralTrackCounts() {
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    Text(playlistDetailHeaderLabel("Playlist", 1))
                    Text(playlistDetailHeaderLabel("Daily Mix", 2))
                }
            }
        }

        composeRule.onNodeWithText("Playlist • 1 track").assertIsDisplayed()
        composeRule.onNodeWithText("Daily Mix • 2 tracks").assertIsDisplayed()
    }

    @Test
    fun playlistTrackQueryShowsLoadingInsteadOfAnEmptyPlaylistBeforeItsFirstEmission() {
        composeRule.setContent {
            MyApplicationTheme {
                PlaylistDetailTracksPlaceholder(isLoading = true)
            }
        }

        composeRule.onNodeWithTag("playlist_detail_loading_state").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.playlist_detail_loading_tracks)).assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText(context.getString(R.string.playlist_detail_empty))
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun playlistTrackQueryShowsEmptyCopyAfterItCompletesWithoutTracks() {
        composeRule.setContent {
            MyApplicationTheme {
                PlaylistDetailTracksPlaceholder(isLoading = false)
            }
        }

        composeRule.onNodeWithTag("playlist_detail_empty_state").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.playlist_detail_empty)).assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText(context.getString(R.string.playlist_detail_loading_tracks))
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun removeTrackActionIdentifiesBothTrackAndPlaylist() {
        val track = PlaylistTrackEntity(
            playlistId = 3,
            ratingKey = "track-1",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            key = "/library/parts/1",
            thumb = "",
            duration = 240_000
        )
        var deleted = false
        var played = false
        composeRule.setContent {
            MyApplicationTheme {
                PlaylistTrackRow(
                    index = 1,
                    track = track,
                    playlistName = "Road Trip",
                    onDelete = { deleted = true },
                    onClick = { played = true },
                    onMove = {}
                )
            }
        }

        val row = composeRule.onNodeWithText(track.title)
        assertEquals(Role.Button, row.fetchSemanticsNode().config[SemanticsProperties.Role])
        row.performClick()
        assertEquals(true, played)

        val removeAction = composeRule.onNodeWithContentDescription(
            context.getString(R.string.playlist_detail_remove_track, track.title, "Road Trip")
        ).assertIsDisplayed()
        val bounds = removeAction.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Playlist track removal should have a 48dp-wide touch target",
            bounds.width >= with(composeRule.density) { 48.dp.toPx() }
        )
        assertTrue(
            "Playlist track removal should have a 48dp-tall touch target",
            bounds.height >= with(composeRule.density) { 48.dp.toPx() }
        )
        removeAction.performClick()
        assertEquals(true, deleted)
    }

    @Test
    fun customPlaylistTrackRowExposesPlaybackAsAButton() {
        val track = TrackItem(
            ratingKey = "custom-playlist-track",
            title = "A Love Supreme",
            artist = "John Coltrane",
            album = "A Love Supreme",
            key = "",
            thumb = "",
            duration = 252_000L
        )
        var played = false
        composeRule.setContent {
            MyApplicationTheme {
                CustomTrackRow(index = 0, track = track, onClick = { played = true })
            }
        }

        val row = composeRule.onNodeWithText(track.title).assertIsDisplayed()
        assertEquals(Role.Button, row.fetchSemanticsNode().config[SemanticsProperties.Role])
        row.performClick()
        assertEquals(true, played)
    }

    private fun createMusicViewModel(): MusicViewModel {
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
                return MusicViewModel(application) as T
            }
        }
        return ViewModelProvider(owner, factory)[MusicViewModel::class.java]
    }
}
