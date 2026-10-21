package com.example.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.unit.dp
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
class ArtistDetailCopyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun missingArtistMetadataDoesNotInventDurationsOrYearRanges() {
        assertNull(artistPopularTrackDurationLabel(null))
        assertNull(artistPopularTrackDurationLabel(0L))
        assertEquals("3:45", artistPopularTrackDurationLabel(225_000L))

        assertNull(artistAlbumYearRange(listOf(null, 0, -1)))
        assertEquals("2018", artistAlbumYearRange(listOf(2018)))
        assertEquals("2006 – 2024", artistAlbumYearRange(listOf(2024, null, 0, 2006)))
    }

    @Test
    fun albumArtistFallbackNeverUsesTheAlbumTitleAsAnArtist() {
        assertEquals("Album Artist", albumDetailArtistName("Album Artist", "Track Artist"))
        assertEquals("Track Artist", albumDetailArtistName("  ", "Track Artist"))
        assertEquals("Various Artists", albumDetailArtistName("", null))
    }

    @Test
    fun artistAlbumRowsDoNotInjectAlbumSpecificStarRatings() {
        val title = "POST HUMAN: NeX GEn"
        var albumOpened = false
        composeRule.setContent {
            MyApplicationTheme {
                ArtistAlbumListItem(
                    album = PlexMetadata(ratingKey = "album-1", title = title, type = "album"),
                    artistName = "Bring Me The Horizon",
                    normalizedBaseUrl = "",
                    token = "",
                    context = context,
                    onMoreClick = {},
                    onClick = { albumOpened = true }
                )
            }
        }

        composeRule.onNodeWithText(title)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(true, albumOpened)
        assertEquals(0, composeRule.onAllNodesWithText("★★★★★").fetchSemanticsNodes().size)
    }

    @Test
    fun inlineArtistSectionsDoNotAdvertiseMissingSeeAllActions() {
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    ArtistDetailSectionHeading(R.string.artist_detail_popular_tracks)
                    ArtistDetailSectionHeading(R.string.artist_detail_similar_artists)
                    ArtistDetailSectionHeading(R.string.artist_detail_styles)
                }
            }
        }

        composeRule.onNodeWithText("POPULAR TRACKS").assertIsDisplayed()
        composeRule.onNodeWithText("SIMILAR ARTISTS").assertIsDisplayed()
        composeRule.onNodeWithText("ARTIST STYLES").assertIsDisplayed()

        listOf(
            "See all popular tracks",
            "See all similar artists",
            "See all artist styles"
        ).forEach { description ->
            assertEquals(
                0,
                composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size
            )
        }
    }

    @Test
    fun artistStyleRowHasOneClearlyNamedRadioAction() {
        var playedRadio = false
        composeRule.setContent {
            MyApplicationTheme {
                ArtistStyleRow(styleName = "Shoegaze", onClick = { playedRadio = true })
            }
        }

        val playLabel = context.getString(R.string.artist_detail_play_style_accessibility, "Shoegaze")
        composeRule.onNodeWithContentDescription(playLabel)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(true, playedRadio)
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Options").fetchSemanticsNodes().size)
    }

    @Test
    fun artistPlayButtonNamesItsTargetAndInvokesPlayback() {
        var played = false
        composeRule.setContent {
            MyApplicationTheme {
                ArtistDetailPlayButton(
                    artistName = "M83",
                    isLoading = false,
                    onClick = { played = true }
                )
            }
        }

        val playLabel = context.getString(R.string.artist_detail_play_accessibility, "M83")
        composeRule.onNodeWithContentDescription(playLabel)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(true, played)
    }

    @Test
    fun artistPlayButtonKeepsItsTargetAndAnnouncesLoadingWhenDisabled() {
        var played = false
        composeRule.setContent {
            MyApplicationTheme {
                ArtistDetailPlayButton(
                    artistName = "M83",
                    isLoading = true,
                    onClick = { played = true }
                )
            }
        }

        val playLabel = context.getString(R.string.artist_detail_play_accessibility, "M83")
        composeRule.onNodeWithContentDescription(playLabel)
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(R.string.player_loading_state)
                )
            )
        assertEquals(false, played)
    }

    @Test
    fun albumTrackAndArtistTargetsExposeSeparateNamedButtonActions() {
        val track = PlexMetadata(
            ratingKey = "album-track-1",
            title = "Blue in Green",
            grandparentTitle = "Miles Davis"
        )
        var played = false
        var artistOpened = false
        composeRule.setContent {
            MyApplicationTheme {
                TrackListItem(
                    index = 1,
                    track = track,
                    onMoreClick = {},
                    onClick = { played = true },
                    onArtistClick = { artistOpened = true }
                )
            }
        }

        val row = composeRule.onNodeWithTag("track_item_${track.ratingKey}").assertIsDisplayed()
        val rowSemantics = row.fetchSemanticsNode().config
        assertEquals(Role.Button, rowSemantics[SemanticsProperties.Role])
        assertEquals(
            context.getString(R.string.details_track_play_accessibility, track.title),
            rowSemantics[SemanticsActions.OnClick].label
        )
        row.performClick()
        assertEquals(true, played)

        val artist = composeRule.onNodeWithText("Miles Davis").assertIsDisplayed()
        val artistSemantics = artist.fetchSemanticsNode().config
        assertEquals(Role.Button, artistSemantics[SemanticsProperties.Role])
        assertEquals(
            context.getString(R.string.details_artist_open_accessibility, "Miles Davis"),
            artistSemantics[SemanticsActions.OnClick].label
        )
        artist.performClick()
        assertEquals(true, artistOpened)
    }

    @Test
    fun trackOptionsHaveA48DpTargetAndInvokeTheirCallback() {
        val track = PlexMetadata(ratingKey = "track-options", title = "Blue in Green")
        var optionsOpened = false
        composeRule.setContent {
            MyApplicationTheme {
                TrackListItem(
                    index = 1,
                    track = track,
                    onMoreClick = { optionsOpened = true },
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.details_more_options_accessibility, track.title)
        )
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(true, optionsOpened)
    }

    @Test
    fun artistAndAlbumActionsNameTheMusicTheyAffect() {
        val artist = "M83"
        val album = "Hurry Up, We're Dreaming"

        assertEquals("Play $artist", context.getString(R.string.artist_detail_play_accessibility, artist))
        assertEquals("Play radio for $artist", context.getString(R.string.artist_detail_radio_accessibility, artist))
        assertEquals("More options for $artist", context.getString(R.string.details_more_options_accessibility, artist))
        assertEquals(
            "Play all tracks in $album",
            context.getString(R.string.album_detail_play_all_accessibility, album)
        )
        assertEquals("Shuffle $album", context.getString(R.string.album_detail_shuffle_accessibility, album))
        assertEquals("Play radio from $album", context.getString(R.string.album_detail_radio_accessibility, album))
        assertEquals("Download $album", context.getString(R.string.album_detail_download_accessibility, album))
        assertEquals(
            "Add tracks from $album to a playlist",
            context.getString(R.string.album_detail_add_to_playlist_accessibility, album)
        )
    }
}
