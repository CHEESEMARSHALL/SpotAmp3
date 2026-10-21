package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.R
import com.example.data.CachedTrack
import com.example.data.PlaylistEntity
import com.example.playback.Lyrics
import com.example.playback.LyricLine
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TouchTargetLayoutDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun denseMusicRowsKeepTheirActionsReachableAtNormalTextScale() =
        assertDenseMusicRows(fontScale = 1f)

    @Test
    fun denseMusicRowsKeepTheirActionsReachableAtLargeTextScale() =
        assertDenseMusicRows(fontScale = 1.3f)

    @Test
    fun syncedLyricSeekTargetMeetsMinimumAtNormalTextScale() =
        assertSyncedLyricTarget(fontScale = 1f)

    @Test
    fun syncedLyricSeekTargetMeetsMinimumAtLargeTextScale() =
        assertSyncedLyricTarget(fontScale = 1.3f)

    @Test
    fun lyricsCloseTargetMeetsMinimumAtNormalTextScale() =
        assertLyricsCloseTarget(fontScale = 1f)

    @Test
    fun lyricsCloseTargetMeetsMinimumAtLargeTextScale() =
        assertLyricsCloseTarget(fontScale = 1.3f)

    @Test
    fun premiumStationsBackTargetMeetsMinimumAtNormalTextScale() =
        assertPremiumStationsBackTarget(fontScale = 1f)

    @Test
    fun premiumStationsBackTargetMeetsMinimumAtLargeTextScale() =
        assertPremiumStationsBackTarget(fontScale = 1.3f)

    @Test
    fun libraryUpdateIntervalsAreSingleRadioActionsAtNormalTextScale() =
        assertLibraryUpdateIntervals(fontScale = 1f)

    @Test
    fun libraryUpdateIntervalsAreSingleRadioActionsAtLargeTextScale() =
        assertLibraryUpdateIntervals(fontScale = 1.3f)

    @Test
    fun miniPlayerPlayPauseTargetMeetsMinimumAtNormalTextScale() =
        assertMiniPlayerPlayPauseTarget(fontScale = 1f)

    @Test
    fun miniPlayerPlayPauseTargetMeetsMinimumAtLargeTextScale() =
        assertMiniPlayerPlayPauseTarget(fontScale = 1.3f)

    @Test
    fun nowPlayingTransportTargetsMeetMinimumAtNormalTextScale() =
        assertNowPlayingTransportTargets(fontScale = 1f)

    @Test
    fun nowPlayingTransportTargetsMeetMinimumAtLargeTextScale() =
        assertNowPlayingTransportTargets(fontScale = 1.3f)

    @Test
    fun searchClearTargetMeetsMinimumAtNormalTextScale() =
        assertSearchClearTarget(fontScale = 1f)

    @Test
    fun searchClearTargetMeetsMinimumAtLargeTextScale() =
        assertSearchClearTarget(fontScale = 1.3f)

    @Test
    fun searchDownloadedOnlyTargetMeetsMinimumAtNormalTextScale() =
        assertSearchDownloadedOnlyTarget(fontScale = 1f)

    @Test
    fun searchDownloadedOnlyTargetMeetsMinimumAtLargeTextScale() =
        assertSearchDownloadedOnlyTarget(fontScale = 1.3f)

    @Test
    fun searchUnavailableRecoveryActionsMeetMinimumAtNormalTextScale() =
        assertSearchUnavailableRecoveryActions(fontScale = 1f)

    @Test
    fun searchUnavailableRecoveryActionsMeetMinimumAtLargeTextScale() =
        assertSearchUnavailableRecoveryActions(fontScale = 1.3f)

    @Test
    fun libraryLoadFailureRetryTargetMeetsMinimumAtNormalTextScale() =
        assertLibraryLoadFailureRetryTarget(fontScale = 1f)

    @Test
    fun libraryLoadFailureRetryTargetMeetsMinimumAtLargeTextScale() =
        assertLibraryLoadFailureRetryTarget(fontScale = 1.3f)

    @Test
    fun staleLibraryLoadWarningRetryTargetMeetsMinimumAtNormalTextScale() =
        assertStaleLibraryLoadWarningRetryTarget(fontScale = 1f)

    @Test
    fun staleLibraryLoadWarningRetryTargetMeetsMinimumAtLargeTextScale() =
        assertStaleLibraryLoadWarningRetryTarget(fontScale = 1.3f)

    @Test
    fun settingsConnectionErrorDismissTargetMeetsMinimumAtNormalTextScale() =
        assertSettingsConnectionErrorDismissTarget(fontScale = 1f)

    @Test
    fun settingsConnectionErrorDismissTargetMeetsMinimumAtLargeTextScale() =
        assertSettingsConnectionErrorDismissTarget(fontScale = 1.3f)

    @Test
    fun settingsTokenVisibilityTargetMeetsMinimumAtNormalTextScale() =
        assertSettingsTokenVisibilityTarget(fontScale = 1f)

    @Test
    fun settingsTokenVisibilityTargetMeetsMinimumAtLargeTextScale() =
        assertSettingsTokenVisibilityTarget(fontScale = 1.3f)

    @Test
    fun settingsConnectionDetailsTargetMeetsMinimumAtNormalTextScale() =
        assertSettingsConnectionDetailsTarget(fontScale = 1f)

    @Test
    fun settingsConnectionDetailsTargetMeetsMinimumAtLargeTextScale() =
        assertSettingsConnectionDetailsTarget(fontScale = 1.3f)

    @Test
    fun settingsLibraryActionsStackWithFullLabelsAtCompactLargeTextScale() {
        var refreshRequested = false
        var syncRequested = false
        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(320.dp, 360.dp)) {
                        SettingsLibraryActions(
                            syncEnabled = true,
                            syncIdle = true,
                            onRefreshServer = { refreshRequested = true },
                            onSyncLibrary = { syncRequested = true }
                        )
                    }
                }
            }
        }

        val refresh = composeRule.onNodeWithTag("refresh_server_library_button")
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val update = composeRule.onNodeWithTag("update_phone_library_button")
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertTrue(
            "Compact phone-width library actions should stack instead of squeezing their labels",
            update.boundsInRoot.top >= refresh.boundsInRoot.bottom
        )
        composeRule.onNodeWithText("Refresh SpotCore library", substring = false)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Update library on this phone", substring = false)
            .assertIsDisplayed()
            .performClick()
        assertTrue("Refresh should invoke its callback", refreshRequested)
        assertTrue("Update should invoke its callback", syncRequested)
    }

    @Test
    fun libraryArtistActionsMeetMinimumAtNormalTextScale() =
        assertLibraryArtistActions(fontScale = 1f)

    @Test
    fun libraryArtistActionsMeetMinimumAtLargeTextScale() =
        assertLibraryArtistActions(fontScale = 1.3f)

    @Test
    fun libraryPlaylistActionsMeetMinimumAtNormalTextScale() =
        assertLibraryPlaylistActions(fontScale = 1f)

    @Test
    fun libraryPlaylistActionsMeetMinimumAtLargeTextScale() =
        assertLibraryPlaylistActions(fontScale = 1.3f)

    @Test
    fun libraryBackActionMeetsMinimumAtNormalTextScale() =
        assertLibraryBackAction(fontScale = 1f)

    @Test
    fun libraryBackActionMeetsMinimumAtLargeTextScale() =
        assertLibraryBackAction(fontScale = 1.3f)

    @Test
    fun playlistDetailActionsMeetMinimumAtNormalTextScale() =
        assertPlaylistDetailActions(fontScale = 1f)

    @Test
    fun playlistDetailActionsMeetMinimumAtLargeTextScale() =
        assertPlaylistDetailActions(fontScale = 1.3f)

    @Test
    fun customPlaylistActionsMeetMinimumAtNormalTextScale() =
        assertCustomPlaylistActions(fontScale = 1f)

    @Test
    fun customPlaylistActionsMeetMinimumAtLargeTextScale() =
        assertCustomPlaylistActions(fontScale = 1.3f)

    private fun assertMiniPlayerPlayPauseTarget(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val track = TrackItem(
            ratingKey = "device-mini-player-touch-target",
            title = "Mini-player test track",
            artist = "QA",
            album = "Generated fixtures",
            key = "/qa/device-mini-player-touch-target",
            thumb = "",
            duration = 180_000L
        )
        var playPauseCount = 0
        var expandCount = 0

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(320.dp, 568.dp)) {
                        BottomMiniPlayerContent(
                            currentTrack = track,
                            isPlaying = false,
                            isLoading = false,
                            progress = 0L,
                            duration = track.duration,
                            baseUrl = "",
                            token = "",
                            onTogglePlayPause = { playPauseCount++ },
                            onExpand = { expandCount++ },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }

        val playPause = composeRule.onNodeWithTag("mini_player_play_pause")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, playPause.fetchSemanticsNode().config[SemanticsProperties.Role])

        val miniPlayer = composeRule.onNodeWithTag("mini_player")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(320.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, miniPlayer.fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals(
            context.getString(R.string.player_open_now_playing_track_accessibility, track.title, track.artist),
            miniPlayer.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        )

        playPause.performClick()
        assertEquals(1, playPauseCount)
        assertEquals(0, expandCount)

        miniPlayer.performClick()
        assertEquals(1, expandCount)
    }

    private fun assertNowPlayingTransportTargets(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = PlaybackManager.getInstance(context)
        while (manager.repeatMode.value != Player.REPEAT_MODE_OFF) manager.toggleRepeat()
        manager.replaceQueueForQa(
            listOf(
                deviceTrack("now-playing-target-one"),
                deviceTrack("now-playing-target-two"),
                deviceTrack("now-playing-target-three")
            ),
            currentIndex = 1
        )

        try {
            composeRule.setContent {
                val deviceDensity = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                    MyApplicationTheme {
                        Box(modifier = Modifier.requiredSize(320.dp, 568.dp)) {
                            NowPlayingScreen(
                                viewModel = androidx.lifecycle.viewmodel.compose.viewModel<MusicViewModel>(),
                                baseUrl = "",
                                token = "",
                                onCollapse = {},
                                onNavigateToArtist = { _, _ -> },
                                onNavigateToAlbum = { _, _ -> },
                                onNavigateToDownloads = {}
                            )
                        }
                    }
                }
            }

            listOf(
                "collapse_button",
                "queue_toggle_button",
                "shuffle_button",
                "prev_button",
                "play_pause_fab",
                "next_button",
                "repeat_button"
            ).forEach { tag ->
                composeRule.onNodeWithTag(tag)
                    .assertIsDisplayed()
                    .assertWidthIsAtLeast(48.dp)
                    .assertHeightIsAtLeast(48.dp)
                    .assert(hasClickAction())
            }

            val repeat = composeRule.onNodeWithTag("repeat_button")
            fun repeatDescription() =
                repeat.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
            fun repeatState() = repeat.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            assertEquals(context.getString(R.string.player_repeat_off), repeatDescription())
            assertEquals(context.getString(R.string.player_off), repeatState())

            repeat.performClick()
            composeRule.waitForIdle()
            assertEquals(context.getString(R.string.player_repeat_all), repeatDescription())
            assertEquals(context.getString(R.string.player_repeat_all_state), repeatState())

            repeat.performClick()
            composeRule.waitForIdle()
            assertEquals(context.getString(R.string.player_repeat_one), repeatDescription())
            assertEquals(context.getString(R.string.player_repeat_one_state), repeatState())

            repeat.performClick()
            composeRule.waitForIdle()
            assertEquals(context.getString(R.string.player_repeat_off), repeatDescription())
            assertEquals(context.getString(R.string.player_off), repeatState())

            composeRule.onNodeWithTag("queue_toggle_button").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("queue_return_to_player_button")
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .assert(hasClickAction())
                .performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("playback_slider").assertIsDisplayed()

            composeRule.onNodeWithTag("queue_toggle_button").performClick()
            composeRule.waitForIdle()
            val moveDown = composeRule.onNodeWithTag("queue_move_down_1")
                .performScrollTo()
                .assertIsDisplayed()
                .assertIsEnabled()
                .assert(hasClickAction())
            assertEquals(
                context.getString(
                    R.string.player_move_track_down_accessibility,
                    "Track now-playing-target-two"
                ),
                moveDown.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
            )
            moveDown.performClick()
            composeRule.waitForIdle()
            assertEquals(
                listOf("now-playing-target-one", "now-playing-target-three", "now-playing-target-two"),
                manager.queue.value.map { it.ratingKey }
            )
            val trackThreeActionTop = composeRule.onNodeWithTag("queue_move_down_1")
                .fetchSemanticsNode().boundsInRoot.top
            val trackTwoActionTop = composeRule.onNodeWithTag("queue_move_down_2")
                .fetchSemanticsNode().boundsInRoot.top
            assertTrue(
                "Move down should place Track three above Track two",
                trackThreeActionTop < trackTwoActionTop
            )

            val moveUp = composeRule.onNodeWithTag("queue_move_up_2")
                .performScrollTo()
                .assertIsDisplayed()
                .assertIsEnabled()
                .assert(hasClickAction())
            moveUp.performClick()
            composeRule.waitForIdle()
            assertEquals(
                listOf("now-playing-target-one", "now-playing-target-two", "now-playing-target-three"),
                manager.queue.value.map { it.ratingKey }
            )
        } finally {
            manager.release()
        }
    }

    private fun deviceTrack(id: String) = TrackItem(
        ratingKey = id,
        title = "Track $id",
        artist = "QA",
        album = "Generated fixtures",
        key = "/qa/$id",
        thumb = "",
        duration = 180_000L
    )

    private fun assertSearchClearTarget(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var clearedQuery: String? = null

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(320.dp, 568.dp)) {
                        SearchQueryField(
                            searchQuery = "ambient",
                            onSearchQueryChange = { clearedQuery = it }
                        )
                    }
                }
            }
        }

        val clearButton = composeRule.onNodeWithContentDescription(
            context.getString(R.string.search_clear_accessibility)
        ).assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, clearButton.fetchSemanticsNode().config[SemanticsProperties.Role])
        clearButton.performClick()
        assertEquals("", clearedQuery)
    }

    private fun assertSearchDownloadedOnlyTarget(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val downloadedOnly = mutableStateOf(false)

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                Box(modifier = Modifier.requiredSize(320.dp, 568.dp)) {
                    MyApplicationTheme {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            SearchHeader(
                                query = "ambient",
                                downloadedOnly = downloadedOnly.value,
                                onDownloadedOnlyChange = { downloadedOnly.value = it }
                            )
                        }
                    }
                }
            }
        }

        val toggle = composeRule.onNodeWithTag("search_downloaded_only_toggle")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Switch, toggle.fetchSemanticsNode().config[SemanticsProperties.Role])
        composeRule.onNodeWithText(context.getString(R.string.search_results_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.downloaded_only_filter)).assertIsDisplayed()

        toggle.performClick()
        composeRule.waitForIdle()
        assertEquals(true, downloadedOnly.value)
    }

    private fun assertSearchUnavailableRecoveryActions(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = context.getString(R.string.search_failure_with_saved_library)
        var retryCount = 0
        var settingsCount = 0

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(288.dp, 200.dp)) {
                        SearchUnavailableState(
                            message = message,
                            onRetry = { retryCount++ },
                            onOpenConnectionSettings = { settingsCount++ }
                        )
                    }
                }
            }
        }

        val retry = composeRule.onNodeWithText(context.getString(R.string.search_retry))
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        val settings = composeRule.onNodeWithText(context.getString(R.string.home_open_settings))
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())

        retry.performClick()
        settings.performClick()
        assertEquals(1, retryCount)
        assertEquals(1, settingsCount)
    }

    private fun assertLibraryLoadFailureRetryTarget(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = context.getString(R.string.product_failure_server_unavailable)
        var retryCount = 0

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
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
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
            .performClick()
        assertEquals(1, retryCount)
    }

    private fun assertStaleLibraryLoadWarningRetryTarget(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = context.getString(R.string.product_failure_server_unavailable)
        var retryCount = 0

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(288.dp, 200.dp)) {
                        Column {
                            LibraryLoadWarning(message = message, onRetry = { retryCount++ })
                            androidx.compose.material3.Text("Previously saved library data")
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onNodeWithText("Previously saved library data").assertIsDisplayed()
        composeRule.onNodeWithTag("library_load_retry")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
            .performClick()
        assertEquals(1, retryCount)
    }

    private fun assertSettingsConnectionErrorDismissTarget(fontScale: Float) {
        var dismissCount = 0

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(320.dp, 568.dp)) {
                        SettingsConnectionErrorDismissButton(onDismiss = { dismissCount++ })
                    }
                }
            }
        }

        val dismissButton = composeRule.onNodeWithTag("settings_connection_error_dismiss")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, dismissButton.fetchSemanticsNode().config[SemanticsProperties.Role])
        dismissButton.performClick()
        assertEquals(1, dismissCount)
    }

    private fun assertSettingsTokenVisibilityTarget(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val isHidden = mutableStateOf(true)

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(320.dp, 568.dp)) {
                        SettingsTokenVisibilityButton(
                            isHidden = isHidden.value,
                            onToggle = { isHidden.value = !isHidden.value }
                        )
                    }
                }
            }
        }

        val visibilityButton = composeRule.onNodeWithTag("settings_device_token_visibility")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, visibilityButton.fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals(
            listOf(context.getString(R.string.settings_show_device_token)),
            visibilityButton.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        )
        visibilityButton.performClick()
        assertEquals(false, isHidden.value)
        assertEquals(
            listOf(context.getString(R.string.settings_hide_device_token)),
            visibilityButton.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        )
    }

    private fun assertSettingsConnectionDetailsTarget(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expanded = mutableStateOf(false)

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(320.dp, 568.dp)) {
                        SettingsConnectionErrorDetailsToggle(
                            expanded = expanded.value,
                            onToggle = { expanded.value = !expanded.value }
                        )
                    }
                }
            }
        }

        val detailsToggle = composeRule.onNodeWithTag("settings_connection_error_details_toggle")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, detailsToggle.fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals(
            context.getString(R.string.recovery_details_collapsed),
            detailsToggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        detailsToggle.performClick()
        assertEquals(true, expanded.value)
        assertEquals(
            context.getString(R.string.recovery_details_expanded),
            detailsToggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
    }

    private fun assertLibraryArtistActions(fontScale: Float) {
        var playCount = 0
        var shuffleCount = 0

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(320.dp, 568.dp)) {
                        ArtistGridActions(
                            onPlayFirstArtist = { playCount++ },
                            onShuffleFirstArtist = { shuffleCount++ }
                        )
                    }
                }
            }
        }

        val play = composeRule.onNodeWithTag("library_artist_play_first")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, play.fetchSemanticsNode().config[SemanticsProperties.Role])
        play.performClick()
        val shuffle = composeRule.onNodeWithTag("library_artist_shuffle_first")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, shuffle.fetchSemanticsNode().config[SemanticsProperties.Role])
        shuffle.performClick()
        assertEquals(1, playCount)
        assertEquals(1, shuffleCount)
    }

    private fun assertLibraryPlaylistActions(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val playlist = PlaylistEntity(id = 47, name = "Road Trip")
        var shareCount = 0
        var downloadCount = 0
        var deleteCount = 0

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(320.dp, 568.dp)) {
                        LibraryPlaylistActions(
                            playlist = playlist,
                            showShareAction = true,
                            onShare = { shareCount++ },
                            onDownload = { downloadCount++ },
                            onDelete = { deleteCount++ }
                        )
                    }
                }
            }
        }

        listOf(
            "library_playlist_share",
            "library_playlist_download",
            "library_playlist_delete"
        ).forEach { tag ->
            val action = composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .assert(hasClickAction())
            assertEquals(Role.Button, action.fetchSemanticsNode().config[SemanticsProperties.Role])
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.library_playlist_share_action, playlist.name)
        ).performClick()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.library_playlist_download_action, playlist.name)
        ).performClick()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.library_playlist_delete_action, playlist.name)
        ).performClick()
        assertEquals(1, shareCount)
        assertEquals(1, downloadCount)
        assertEquals(1, deleteCount)
    }

    private fun assertLibraryBackAction(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var backCount = 0

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(320.dp, 568.dp)) {
                        LibraryBackButton(hasActiveFilter = false, onClick = { backCount++ })
                    }
                }
            }
        }

        val back = composeRule.onNodeWithContentDescription(
            context.getString(R.string.library_back_accessibility)
        ).assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, back.fetchSemanticsNode().config[SemanticsProperties.Role])
        back.performClick()
        assertEquals(1, backCount)
    }

    private fun assertPlaylistDetailActions(fontScale: Float) {
        var actionCount = 0

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(320.dp, 120.dp)) {
                        PlaylistDetailActionsRow(
                            playlistName = "Road Trip",
                            onPlayAll = { actionCount++ },
                            onShuffle = { actionCount++ },
                            onDownload = { actionCount++ }
                        )
                    }
                }
            }
        }

        listOf(
            "playlist_detail_play_all",
            "playlist_detail_shuffle",
            "playlist_detail_download"
        ).forEach { tag ->
            assertTouchTargetAndClick(tag)
        }
        assertEquals(3, actionCount)
    }

    private fun assertCustomPlaylistActions(fontScale: Float) {
        var actionCount = 0

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(320.dp, 120.dp)) {
                        CustomPlaylistDetailActionsRow(
                            playlistName = "Evening Mix",
                            trackCount = 4,
                            isSaved = false,
                            isDownloading = false,
                            onPlayAll = { actionCount++ },
                            onShuffle = { actionCount++ },
                            onSave = { actionCount++ },
                            onDownload = { actionCount++ },
                            onAddToQueue = { actionCount++ }
                        )
                    }
                }
            }
        }

        listOf(
            "custom_playlist_play_all",
            "custom_playlist_shuffle",
            "custom_playlist_save",
            "custom_playlist_download",
            "custom_playlist_add_to_queue"
        ).forEach { tag ->
            assertTouchTargetAndClick(tag)
        }
        assertEquals(5, actionCount)
    }

    private fun assertTouchTargetAndClick(tag: String) {
        val target = composeRule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, target.fetchSemanticsNode().config[SemanticsProperties.Role])
        target.performClick()
    }

    private fun assertSyncedLyricTarget(fontScale: Float) {
        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    LyricsPanel(
                        lyrics = Lyrics(
                            listOf(LyricLine(12_500L, "Wait for the light")),
                            isSynced = true
                        ),
                        loading = false,
                        positionMs = 0L,
                        onSeek = {},
                        onClose = {},
                        onReload = {},
                        modifier = Modifier.requiredSize(320.dp, 480.dp)
                    )
                }
            }
        }

        composeRule.onNodeWithText("Wait for the light")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    private fun assertLyricsCloseTarget(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var closeCount = 0

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    LyricsPanel(
                        lyrics = Lyrics(emptyList(), isSynced = false),
                        loading = false,
                        positionMs = 0L,
                        onSeek = {},
                        onClose = { closeCount++ },
                        onReload = {},
                        modifier = Modifier.requiredSize(320.dp, 480.dp)
                    )
                }
            }
        }

        val close = composeRule.onNodeWithContentDescription(
            context.getString(R.string.lyrics_close_accessibility)
        ).assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, close.fetchSemanticsNode().config[SemanticsProperties.Role])
        close.performClick()
        assertEquals(1, closeCount)
    }

    private fun assertPremiumStationsBackTarget(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var backCount = 0

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    PremiumStationsBackButton(onBack = { backCount++ })
                }
            }
        }

        val back = composeRule.onNodeWithTag("premium_stations_back")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, back.fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals(
            context.getString(R.string.details_back_accessibility),
            back.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
        )
        back.performClick()
        assertEquals(1, backCount)
    }

    private fun assertLibraryUpdateIntervals(fontScale: Float) {
        var selectedInterval: Long? = null
        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Column(
                        modifier = Modifier
                            .requiredSize(320.dp, 600.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        BackgroundLibraryUpdatesCard(
                            intervalHours = 6L,
                            onIntervalSelected = { selectedInterval = it }
                        )
                    }
                }
            }
        }

        listOf(0L, 1L, 6L, 12L, 24L).forEach { hours ->
            val option = composeRule.onNodeWithTag("settings_library_update_interval_$hours")
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
            option.assert(hasClickAction())
            assertEquals(Role.RadioButton, option.fetchSemanticsNode().config[SemanticsProperties.Role])
            assertEquals(hours == 6L, option.fetchSemanticsNode().config[SemanticsProperties.Selected])
        }
        assertEquals(5, composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("settings_library_update_interval_12").performClick()
        assertEquals(12L, selectedInterval)
    }

    private fun assertDenseMusicRows(fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val track = CachedTrack(
            ratingKey = "device-touch-target-track",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            key = "/library/parts/device-touch-target",
            thumb = "",
            duration = 241_000
        )
        val queueTrack = TrackItem(
            ratingKey = "device-queue-track",
            title = "Wait",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            key = "/library/parts/device-queue-track",
            thumb = "",
            duration = 300_000
        )

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Column(
                        modifier = Modifier
                            .requiredSize(320.dp, 480.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        SearchItemRow(
                            title = "Search Result",
                            subtitle = "M83 • Hurry Up, We're Dreaming",
                            thumb = null,
                            isCircle = false,
                            baseUrl = "",
                            token = "",
                            onMoreClick = {},
                            onClick = {}
                        )
                        TrackRow(
                            track = track,
                            baseUrl = "",
                            token = "",
                            onMoreClick = {},
                            onClick = {},
                            onCompanionFavoriteChange = {},
                            onCompanionRatingChange = {}
                        )
                        NowPlayingQueueRow(
                            track = queueTrack,
                            index = 1,
                            isCurrent = false,
                            positionLabel = context.getString(R.string.player_queue_up_next),
                            isPlaying = false,
                            queueSize = 2,
                            baseUrl = "",
                            token = "",
                            context = context,
                            onClick = {},
                            onMove = { _, _ -> },
                            canMoveDown = true,
                            canReorder = true,
                            onMoreClick = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.search_more_accessibility, "Search Result"))
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription(context.getString(R.string.library_spotcore_favorite_add))
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription(context.getString(R.string.library_track_more_options_accessibility, track.title))
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(context.getString(R.string.library_spotcore_rating_rate_track))
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        listOf("queue_move_up_1", "queue_move_down_1").forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.player_queue_options_accessibility, queueTrack.title)
        )
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }
}
