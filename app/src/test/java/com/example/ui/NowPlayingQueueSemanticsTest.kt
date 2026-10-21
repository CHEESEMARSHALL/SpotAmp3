package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasClickAction
import androidx.media3.common.Player
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.PlexSettingsManager
import com.example.data.MusicDatabase
import com.example.data.PlaybackStateEntity
import com.example.R
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class NowPlayingQueueSemanticsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var manager: PlaybackManager

    @Before
    fun setUp() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        PlexSettingsManager(context).offlineOnly = false
        runBlocking { resetSavedPlaybackState(context) }
        manager = PlaybackManager.getInstance(context)
        manager.clearPlaybackError()
        manager.replaceQueueForQa(listOf(track("one")))
    }

    @After
    fun tearDown() {
        PlexSettingsManager(androidx.test.core.app.ApplicationProvider.getApplicationContext()).offlineOnly = false
        manager.release()
        composeTestRule.waitForIdle()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking { resetSavedPlaybackState(context) }
    }

    @Test
    fun singleTrackQueueSaysNothingUpNextAndDoesNotOfferNext() {
        composeTestRule.setContent { MyApplicationTheme { player() } }
        openQueue()

        composeTestRule.onNodeWithText("Nothing up next").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithText("Up next").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Next track").fetchSemanticsNodes().size)
        composeTestRule.onAllNodesWithTag("queue_move_up_0").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("queue_move_down_0").assertCountEquals(0)
    }

    @Test
    fun miniPlayerNamesTheCurrentTrackWithoutRepeatingArtworkText() {
        val current = track("mini")
        manager.replaceQueueForQa(listOf(current))
        composeTestRule.setContent {
            MyApplicationTheme {
                BottomMiniPlayer(
                    playbackManager = manager,
                    baseUrl = "",
                    token = "",
                    onExpand = {}
                )
            }
        }

        val expectedLabel = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.player_open_now_playing_track_accessibility, current.title, current.artist)
        composeTestRule.onNodeWithTag("mini_player")
            .assert(hasClickAction())
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(expectedLabel)
                )
            )
        assertEquals(
            0,
            composeTestRule.onAllNodesWithContentDescription(current.title, useUnmergedTree = true)
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun miniPlayerUsesSharedUnknownArtistInItsAccessibleName() {
        val current = track("mini-missing-artist").copy(artist = "")
        manager.replaceQueueForQa(listOf(current))
        composeTestRule.setContent {
            MyApplicationTheme {
                BottomMiniPlayer(
                    playbackManager = manager,
                    baseUrl = "",
                    token = "",
                    onExpand = {}
                )
            }
        }

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val fallbackArtist = context.getString(R.string.common_unknown_artist)
        val expectedLabel = context.getString(
            R.string.player_open_now_playing_track_accessibility,
            current.title,
            fallbackArtist
        )
        composeTestRule.onNodeWithTag("mini_player")
            .assert(hasClickAction())
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(expectedLabel)
                )
            )
    }

    @Test
    fun multiTrackQueueSeparatesCurrentFromUpcomingTracks() {
        manager.replaceQueueForQa(listOf(track("one"), track("two"), track("three")))
        composeTestRule.setContent { MyApplicationTheme { player() } }
        openQueue()

        composeTestRule.onNodeWithText("2 tracks up next").assertIsDisplayed()
        composeTestRule.onNodeWithText("Now playing").assertIsDisplayed()
        assertEquals(2, composeTestRule.onAllNodesWithText("Up next", substring = false).fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("queue_save_as_playlist_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("queue_move_up_0").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("queue_move_down_0").assertIsEnabled()
    }

    @Test
    fun queueLabelsPlayedHistoryCurrentTrackAndWhatPlaysNext() {
        manager.replaceQueueForQa(listOf(track("one"), track("two"), track("three")), currentIndex = 1)
        composeTestRule.setContent { MyApplicationTheme { player() } }
        openQueue()

        composeTestRule.onNodeWithText("Played earlier").assertIsDisplayed()
        composeTestRule.onNodeWithText("Now playing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Up next", substring = false).assertIsDisplayed()
        composeTestRule.onNodeWithText("1 track up next").assertIsDisplayed()
    }

    @Test
    fun clearingUpcomingRequiresConfirmationAndKeepsTheActiveTrack() {
        manager.replaceQueueForQa(listOf(track("one"), track("two"), track("three")))
        composeTestRule.setContent { MyApplicationTheme { player() } }
        openQueue()

        composeTestRule.onNodeWithTag("queue_clear_upcoming_button").performClick()
        composeTestRule.onNodeWithText("Clear upcoming tracks?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("queue_clear_upcoming_cancel").performClick()
        assertEquals(listOf("one", "two", "three"), manager.queue.value.map { it.ratingKey })

        composeTestRule.onNodeWithTag("queue_clear_upcoming_button").performClick()
        composeTestRule.onNodeWithTag("queue_clear_upcoming_confirm").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("one"), manager.queue.value.map { it.ratingKey })
        assertEquals("one", manager.currentTrack.value?.ratingKey)
        assertEquals(0, manager.currentIndex.value)
    }

    @Test
    fun queueMenuCanRemoveAQueuedTrackWithoutStoppingCurrentPlayback() {
        manager.replaceQueueForQa(listOf(track("one"), track("two"), track("three")))
        composeTestRule.setContent { MyApplicationTheme { player() } }
        openQueue()

        composeTestRule.onNodeWithTag("queue_track_more_1").performClick()
        composeTestRule.onNodeWithText("Remove from queue").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("one", "three"), manager.queue.value.map { it.ratingKey })
        assertEquals("one", manager.currentTrack.value?.ratingKey)
    }

    @Test
    fun queueTrackTapExposesPlayButtonAndKeepsMoveActionsReachable() {
        manager.replaceQueueForQa(listOf(track("one"), track("two"), track("three")))
        composeTestRule.setContent { MyApplicationTheme { player() } }
        openQueue()

        composeTestRule.onNodeWithTag("queue_move_down_1")
            .assertIsEnabled()
            .assert(hasClickAction())
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithTag("queue_track_more_1")
            .assert(hasClickAction())
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        val trackRow = composeTestRule.onNodeWithText("Track two")
            .assert(hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        trackRow.performClick()
        composeTestRule.waitForIdle()

        assertEquals("two", manager.currentTrack.value?.ratingKey)
    }

    @Test
    fun accessibleQueueMoveActionReordersTheProductionQueue() {
        manager.replaceQueueForQa(listOf(track("one"), track("two"), track("three")))
        composeTestRule.setContent { MyApplicationTheme { player() } }
        openQueue()

        val moveDown = composeTestRule.onNodeWithTag("queue_move_down_1")
        moveDown.assertIsEnabled()
            .assert(hasClickAction())
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf("Move Track two down")
                )
            )
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("one", "three", "two"), manager.queue.value.map { it.ratingKey })
        val trackThreeTop = composeTestRule.onNodeWithText("Track three").fetchSemanticsNode().boundsInRoot.top
        val trackTwoTop = composeTestRule.onNodeWithText("Track two").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Move down should place Track three above Track two", trackThreeTop < trackTwoTop)

        composeTestRule.onNodeWithTag("queue_move_up_2").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("one", "two", "three"), manager.queue.value.map { it.ratingKey })
    }

    @Test
    fun finalQueueItemDisablesNextUnlessRepeatAllWillWrap() {
        manager.replaceQueueForQa(listOf(track("one"), track("two")), currentIndex = 1)
        composeTestRule.setContent { MyApplicationTheme { player() } }

        val nextButton = composeTestRule.onNodeWithTag("next_button").assertIsNotEnabled()
        assertEquals("No next track in queue", nextButton.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertEquals(Color.White, nextTrackButtonTint(enabled = true))
        assertEquals(Color.White.copy(alpha = 0.38f), nextTrackButtonTint(enabled = false))
        openQueue()
        composeTestRule.onNodeWithText("Nothing up next").assertIsDisplayed()

        manager.toggleRepeat()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("next_button").assertIsEnabled()
        composeTestRule.onNodeWithTag("repeat_button").assertIsDisplayed()
    }

    @Test
    fun missingOfflineTrackShowsActionableDismissibleRecovery() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        PlexSettingsManager(context).offlineOnly = true
        manager.clearQueue()
        manager.addToQueue(track("offline"))
        composeTestRule.waitForIdle()
        val downloadsNavigationCount = AtomicInteger(0)
        composeTestRule.setContent { MyApplicationTheme { player(onNavigateToDownloads = { downloadsNavigationCount.incrementAndGet() }) } }

        composeTestRule.onNodeWithText("This track isn’t saved on this phone. Download it or turn off Downloaded only, then try again.")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite
                )
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Error,
                    "This track isn’t saved on this phone. Download it or turn off Downloaded only, then try again."
                )
            )
        composeTestRule.onNodeWithTag("playback_open_downloads").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, downloadsNavigationCount.get())
        composeTestRule.onNodeWithText("Dismiss").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithText("This track isn’t saved on this phone. Download it or turn off Downloaded only, then try again.").fetchSemanticsNodes().size)
    }

    @Test
    fun retryablePlaybackFailureLabelsBothPrimaryAndInlineRetryActions() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        manager.replaceQueueForQa(listOf(track("retryable")))
        manager.setPlaybackErrorForQa(context.getString(com.example.R.string.playback_error_generic), retryable = true)
        composeTestRule.setContent { MyApplicationTheme { player() } }

        composeTestRule.onNodeWithTag("play_pause_fab")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(context.getString(com.example.R.string.playback_retry))
                )
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(com.example.R.string.player_failed_state)
                )
            )
        composeTestRule.onNodeWithText(context.getString(com.example.R.string.playback_error_generic))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("playback_retry_button")
            .assertIsDisplayed()
            .assert(hasClickAction())
        assertEquals(0, composeTestRule.onAllNodesWithTag("playback_error_details_toggle").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithText(context.getString(com.example.R.string.playback_retry), substring = false)
            .assertIsDisplayed()
    }

    @Test
    fun nonRetryablePlaybackFailureDisablesPlayUntilDismissed() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        manager.replaceQueueForQa(listOf(track("unplayable")))
        manager.setPlaybackErrorForQa(context.getString(com.example.R.string.playback_audio_unplayable), retryable = false)
        composeTestRule.setContent { MyApplicationTheme { player() } }

        val playPause = composeTestRule.onNodeWithTag("play_pause_fab")
        playPause.assertIsDisplayed()
            .assertIsNotEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(context.getString(com.example.R.string.player_unavailable_action))
                )
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(com.example.R.string.player_failed_state)
                )
            )
        composeTestRule.onNodeWithText(context.getString(com.example.R.string.playback_audio_unplayable))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("playback_dismiss_error")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(null, manager.playbackError.value)
        playPause.assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(context.getString(com.example.R.string.player_play_action))
                )
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(com.example.R.string.player_paused_state)
                )
            )
    }

    @Test
    fun playbackTechnicalDetailsAreOptInAndCollapsedByDefault() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        manager.replaceQueueForQa(listOf(track("diagnostic")))
        manager.setPlaybackErrorForQa(context.getString(com.example.R.string.playback_error_generic), retryable = true)
        manager.setPlaybackDiagnosticForQa("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED")
        composeTestRule.setContent { MyApplicationTheme { player() } }

        assertEquals(0, composeTestRule.onAllNodesWithTag("playback_error_details").fetchSemanticsNodes().size)
        val detailsToggle = composeTestRule.onNodeWithTag("playback_error_details_toggle")
            .assertIsDisplayed()
        assertEquals(
            context.getString(com.example.R.string.recovery_details_collapsed),
            detailsToggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        detailsToggle.performClick()
        assertEquals(
            context.getString(com.example.R.string.recovery_details_expanded),
            detailsToggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        composeTestRule.onNodeWithTag("playback_error_details")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.Text,
                listOf(androidx.compose.ui.text.AnnotatedString("Playback error code: ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"))
            ))
    }

    @Test
    fun coreTransportControlsRemainVisibleAt130PercentFontScale() {
        manager.replaceQueueForQa(listOf(track("one"), track("two")))
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme { player() }
            }
        }

        listOf(
            "playback_slider",
            "shuffle_button",
            "prev_button",
            "play_pause_fab",
            "next_button",
            "repeat_button",
            "like_button"
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test
    fun favoriteControlAnnouncesLikeActionWhenTrackIsNotLiked() {
        manager.replaceQueueForQa(listOf(track("one")))
        composeTestRule.setContent { MyApplicationTheme { player() } }

        composeTestRule.onNodeWithTag("like_button").assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithContentDescription("Like track").assertIsDisplayed()
    }

    @Test
    fun currentTrackOptionsAnnounceWhichTrackTheyAffect() {
        manager.replaceQueueForQa(listOf(track("one")))
        composeTestRule.setContent { MyApplicationTheme { player() } }

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val expectedLabel = context.getString(com.example.R.string.player_track_options_accessibility, "Track one")
        composeTestRule.onNodeWithTag("more_button")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(expectedLabel)
                )
            )
    }

    @Test
    fun transportControlsAnnounceTheirCurrentStateAsItChanges() {
        manager.replaceQueueForQa(listOf(track("one"), track("two")))
        if (manager.shuffleModeEnabled.value) manager.toggleShuffleForQa()
        while (manager.repeatMode.value != Player.REPEAT_MODE_OFF) manager.toggleRepeat()
        Thread.sleep(100)
        manager.clearPlaybackError()
        composeTestRule.setContent { MyApplicationTheme { player() } }

        val shuffle = composeTestRule.onNodeWithTag("shuffle_button")
        assertEquals("Shuffle", shuffle.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single())
        assertEquals("Off", shuffle.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        shuffle.performClick()
        composeTestRule.waitForIdle()
        assertEquals("On", shuffle.fetchSemanticsNode().config[SemanticsProperties.StateDescription])

        val repeat = composeTestRule.onNodeWithTag("repeat_button")
        assertEquals("Repeat off", repeat.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single())
        repeat.performClick()
        composeTestRule.waitForIdle()
        assertEquals("Repeat all", repeat.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single())
        assertEquals("On: all tracks", repeat.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        repeat.performClick()
        composeTestRule.waitForIdle()
        assertEquals("Repeat one", repeat.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single())
        assertEquals("On: one track", repeat.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        repeat.performClick()
        composeTestRule.waitForIdle()
        assertEquals("Repeat off", repeat.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single())
        assertEquals("Off", repeat.fetchSemanticsNode().config[SemanticsProperties.StateDescription])

        val playPause = composeTestRule.onNodeWithTag("play_pause_fab")
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(
            context.getString(R.string.player_play_action),
            playPause.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        )
        assertEquals(
            context.getString(R.string.player_paused_state),
            playPause.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        assertEquals(Role.Button, playPause.fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals("Available", composeTestRule.onNodeWithTag("next_button").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    }

    @Test
    fun artistAndAlbumMetadataAreExposedAsNavigationActions() {
        manager.replaceQueueForQa(listOf(track("one")))
        var collapseCount = 0
        var artistNavigation: Pair<String, String>? = null
        var albumNavigation: Pair<String, String>? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                player(
                    onCollapse = { collapseCount++ },
                    onNavigateToArtist = { key, name -> artistNavigation = key to name },
                    onNavigateToAlbum = { key, name -> albumNavigation = key to name }
                )
            }
        }

        val artist = composeTestRule.onNodeWithTag("player_artist_metadata")
            .assert(hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        val album = composeTestRule.onNodeWithTag("player_album_metadata")
            .assert(hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        artist.performClick()
        assertEquals("one" to "Test artist", artistNavigation)
        assertEquals(1, collapseCount)
        album.performClick()
        assertEquals("one" to "Test album", albumNavigation)
        assertEquals(2, collapseCount)
    }

    @Test
    fun blankArtistAndAlbumUseFallbackLabelsWithoutFakeNavigationActions() {
        manager.replaceQueueForQa(listOf(track("missing-metadata").copy(artist = "", album = "")))
        composeTestRule.setContent { MyApplicationTheme { player() } }

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val artist = composeTestRule.onNodeWithTag("player_artist_metadata")
        val album = composeTestRule.onNodeWithTag("player_album_metadata")
        artist.assertIsDisplayed()
        album.assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.common_unknown_artist)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.common_unknown_album)).assertIsDisplayed()
        assertFalse(artist.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
        assertFalse(album.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
    }

    @Test
    fun coreTransportControlsRemainReachableInCompactViewport() {
        manager.replaceQueueForQa(listOf(track("one"), track("two")))
        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.requiredSize(320.dp, 568.dp)
                ) {
                    player()
                }
            }
        }

        listOf(
            "playback_slider",
            "collapse_button",
            "queue_toggle_button",
            "shuffle_button",
            "prev_button",
            "play_pause_fab",
            "next_button",
            "repeat_button"
        ).forEach { tag ->
            val control = composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
            if (tag != "playback_slider") {
                control.assertWidthIsAtLeast(48.dp)
                    .assertHeightIsAtLeast(48.dp)
            }
        }
        assertPlayerMetadataTargets()
    }

    @Test
    fun coreTransportControlsStayInsideCompactViewportAt130PercentFontScale() {
        manager.replaceQueueForQa(listOf(track("one"), track("two")))
        var viewportBottomPx = 0f
        composeTestRule.setContent {
            val density = LocalDensity.current
            viewportBottomPx = 568f * density.density
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.requiredSize(320.dp, 568.dp)
                    ) {
                        player()
                    }
                }
            }
        }

        listOf(
            "playback_slider",
            "collapse_button",
            "queue_toggle_button",
            "shuffle_button",
            "prev_button",
            "play_pause_fab",
            "next_button",
            "repeat_button",
            "like_button",
            "more_button",
            "player_artist_metadata",
            "player_album_metadata"
        ).forEach { tag ->
            val node = composeTestRule.onNodeWithTag(tag)
            node.assertIsDisplayed()
            if (tag != "playback_slider") {
                node.assertWidthIsAtLeast(48.dp)
                    .assertHeightIsAtLeast(48.dp)
            }
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$tag is clipped by the compact player viewport: $bounds (viewport bottom $viewportBottomPx px)",
                bounds.top >= 0f && bounds.bottom <= viewportBottomPx
            )
        }
        assertPlayerMetadataTargets()
    }

    private fun assertPlayerMetadataTargets() {
        listOf("player_artist_metadata", "player_album_metadata").forEach { tag ->
            composeTestRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun retryActionsStayReachableWithCompactViewportAnd130PercentTextScale() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        manager.replaceQueueForQa(listOf(track("retryable")))
        manager.setPlaybackErrorForQa(context.getString(R.string.playback_error_generic), retryable = true)
        var viewportBottomPx = 0f
        composeTestRule.setContent {
            val density = LocalDensity.current
            viewportBottomPx = 568f * density.density
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.requiredSize(320.dp, 568.dp)
                    ) {
                        player()
                    }
                }
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.playback_error_generic)).assertIsDisplayed()
        listOf("playback_retry_button", "play_pause_fab").forEach { tag ->
            val node = composeTestRule.onNodeWithTag(tag)
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$tag is clipped by the compact player viewport: $bounds (viewport bottom $viewportBottomPx px)",
                bounds.top >= 0f && bounds.bottom <= viewportBottomPx
            )
        }
        composeTestRule.onNodeWithTag("playback_retry_button").assert(hasClickAction())
    }

    @Composable
    private fun player(
        onNavigateToDownloads: () -> Unit = {},
        onCollapse: () -> Unit = {},
        onNavigateToArtist: (String, String) -> Unit = { _, _ -> },
        onNavigateToAlbum: (String, String) -> Unit = { _, _ -> }
    ) = NowPlayingScreen(
        viewModel = androidx.lifecycle.viewmodel.compose.viewModel<MusicViewModel>(),
        baseUrl = "",
        token = "",
        onCollapse = onCollapse,
        onNavigateToArtist = onNavigateToArtist,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToDownloads = onNavigateToDownloads
    )

    private fun openQueue() {
        composeTestRule.onNodeWithTag("now_playing_scroll_container").performScrollToIndex(1)
    }

    private fun track(id: String) = TrackItem(
        ratingKey = id,
        title = "Track $id",
        artist = "Test artist",
        album = "Test album",
        key = "/qa/$id",
        thumb = "",
        duration = 180_000L
    )

    private suspend fun resetSavedPlaybackState(context: android.content.Context) {
        MusicDatabase.getDatabase(context).musicDao().savePlaybackState(
            PlaybackStateEntity(
                queueJson = "[]",
                currentIndex = -1,
                positionMs = 0,
                shuffleEnabled = false,
                repeatMode = Player.REPEAT_MODE_OFF,
                playWhenReady = false
            )
        )
    }
}
