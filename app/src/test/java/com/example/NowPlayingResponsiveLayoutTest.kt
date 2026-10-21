package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.PlexSettingsManager
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem
import com.example.ui.MusicViewModel
import com.example.ui.NowPlayingScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NowPlayingResponsiveLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var playbackManager: PlaybackManager

    @Before
    fun preparePlayer() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        PlexSettingsManager(context).apply {
            clearCompanionCredential()
            baseUrl = ""
            token = ""
            offlineOnly = false
        }
        playbackManager = PlaybackManager.getInstance(context)
        playbackManager.replaceQueueForQa(
            listOf(
                TrackItem(
                    "qa-responsive-player",
                    "An Extremely Long Track Name That Needs Truncation",
                    "The Extremely Long Artist Name",
                    "A Long Companion Album Title",
                    "/qa/responsive-player",
                    "",
                    214_000L
                ),
                TrackItem("qa-responsive-next", "Glass Satellites", "The Testers", "Companion Sessions", "/qa/next", "", 198_000L)
            )
        )
    }

    @After
    fun releasePlayer() {
        playbackManager.release()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun compactViewportAtDefaultText_keepsCoreControlsVisible() = assertPlayerLayout(
        fontScale = 1f,
        screenshot = "src/test/screenshots/now-playing-responsive-compact-100.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun compactViewportAtLargeText_keepsCoreControlsVisible() = assertPlayerLayout(
        fontScale = 1.3f,
        screenshot = "src/test/screenshots/now-playing-responsive-compact-130.png"
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp", sdk = [36])
    fun tallViewportAtDefaultText_keepsCoreControlsVisible() = assertPlayerLayout(
        fontScale = 1f,
        screenshot = "src/test/screenshots/now-playing-responsive-tall-100.png"
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp", sdk = [36])
    fun tallViewportAtLargeText_keepsCoreControlsVisible() = assertPlayerLayout(
        fontScale = 1.3f,
        screenshot = "src/test/screenshots/now-playing-responsive-tall-130.png"
    )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun queueHeaderProvidesAVisibleReturnToPlayerAtLargeText() {
        setPlayerContent(fontScale = 1.3f)

        composeRule.onNodeWithTag("queue_toggle_button").performClick()
        composeRule.waitUntil(5_000L) {
            runCatching {
                composeRule.onNodeWithTag("queue_return_to_player_button").fetchSemanticsNode()
            }.isSuccess
        }

        composeRule.onNodeWithTag("queue_return_to_player_button")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/now-playing-responsive-compact-queue-130.png"
        )

        composeRule.onNodeWithTag("queue_return_to_player_button").performClick()
        composeRule.waitUntil(5_000L) {
            runCatching {
                composeRule.onNodeWithTag("collapse_button").assertIsDisplayed()
            }.isSuccess
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun compactLargeTextCanEnterAndReturnFromLyrics() {
        setPlayerContent(fontScale = 1.3f)

        composeRule.onNodeWithTag("player_visual_surface_container").performClick()
        composeRule.onNodeWithTag("lyrics_panel").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close lyrics")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("player_visual_surface_container").assertIsDisplayed()
    }

    private fun assertPlayerLayout(fontScale: Float, screenshot: String) {
        setPlayerContent(fontScale)

        composeRule.waitForIdle()
        val viewport = composeRule.onNodeWithTag("now_playing_scroll_container")
            .fetchSemanticsNode().boundsInRoot
        listOf("collapse_button", "playback_slider", "play_pause_fab", "repeat_button").forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$tag must remain inside the visible player viewport", bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
        }

        composeRule.onRoot().captureRoboImage(filePath = screenshot)
    }

    private fun setPlayerContent(fontScale: Float) {
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale)
            ) {
                MyApplicationTheme {
                    NowPlayingScreen(
                        viewModel = androidx.lifecycle.viewmodel.compose.viewModel<MusicViewModel>(),
                        baseUrl = "",
                        token = "",
                        onCollapse = {},
                        onNavigateToArtist = { _, _ -> },
                        onNavigateToAlbum = { _, _ -> },
                        modifier = Modifier,
                        onNavigateToDownloads = {}
                    )
                }
            }
        }
    }
}
