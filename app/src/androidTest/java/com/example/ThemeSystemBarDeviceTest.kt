package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.PlexSettingsManager
import com.example.playback.PlaybackStateRestoration
import com.example.playback.TrackItem
import com.example.ui.MusicViewModel
import com.example.data.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies app theme changes also keep Android system-bar icons legible. */
@RunWith(AndroidJUnit4::class)
class ThemeSystemBarDeviceTest {
    @get:org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun warmLightAndNowPlayingUseLegibleSystemBarIconsAndDarkThemeRestoresLightIcons() {
        val settings = PlexSettingsManager(composeRule.activity)
        val originalTheme = settings.activeTheme
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val viewModel = ViewModelProvider(composeRule.activity)[MusicViewModel::class.java]
        val playback = viewModel.playbackManager
        val persistedPlayback = runBlocking(Dispatchers.IO) {
            MusicDatabase.getDatabase(composeRule.activity).musicDao().getPlaybackState()
        }?.let { state ->
            PlaybackStateRestoration.restore(state) { track ->
                track.localPath.isNullOrBlank() || File(track.localPath).isFile
            }
        }
        val persistedTrack = persistedPlayback?.queue?.getOrNull(persistedPlayback.currentIndex)
        if (playback.currentTrack.value == null && persistedTrack != null) {
            composeRule.waitUntil(10_000L) {
                playback.currentTrack.value?.ratingKey == persistedTrack.ratingKey
            }
        }
        val seededFixture = playback.currentTrack.value == null
        if (seededFixture) {
            assumeTrue(
                "Do not replace a queued or active playback session to seed the system-bar fixture",
                playback.queue.value.isEmpty() && !playback.isPlaying.value && persistedTrack == null
            )
            playback.replaceQueueForQa(
                listOf(
                    TrackItem(
                        ratingKey = "qa-system-bar-fixture",
                        title = "QA system bar fixture",
                        artist = "SpotAmp QA",
                        album = "Generated test audio",
                        key = "/qa/system-bar-fixture",
                        thumb = "",
                        duration = 180_000L
                    )
                )
            )
        }

        try {
            composeRule.runOnUiThread { viewModel.updateTheme("Warm Light") }
            assertSystemBarAppearance(expectedLightStatusAppearance = true, expectedLightNavigationAppearance = true, description = "Warm Light")

            composeRule.waitUntil(10_000L) {
                composeRule.onAllNodesWithTag("mini_player").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("mini_player").assertIsDisplayed().performClick()
            composeRule.onNodeWithTag("now_playing_scroll_container").assertIsDisplayed()
            assertSystemBarAppearance(expectedLightStatusAppearance = false, expectedLightNavigationAppearance = true, description = "Warm Light Now Playing")
            composeRule.onNodeWithTag("collapse_button").performClick()
            assertSystemBarAppearance(expectedLightStatusAppearance = true, expectedLightNavigationAppearance = true, description = "Warm Light after Now Playing")

            composeRule.runOnUiThread { viewModel.updateTheme("Default Dark") }
            assertSystemBarAppearance(expectedLightStatusAppearance = false, expectedLightNavigationAppearance = false, description = "Default Dark")
        } finally {
            runCatching {
                if (composeRule.onAllNodesWithTag("collapse_button").fetchSemanticsNodes().isNotEmpty()) {
                    composeRule.onNodeWithTag("collapse_button").performClick()
                }
            }
            if (seededFixture) playback.clearQueue()
            composeRule.runOnUiThread { viewModel.updateTheme(originalTheme) }
            composeRule.waitForIdle()
            assertEquals("Theme preference must be restored", originalTheme, settings.activeTheme)
        }
    }

    private fun assertSystemBarAppearance(
        expectedLightStatusAppearance: Boolean,
        expectedLightNavigationAppearance: Boolean,
        description: String
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        composeRule.waitUntil(3_000L) {
            var matches = false
            composeRule.runOnUiThread {
                val bars = WindowInsetsControllerCompat(composeRule.activity.window, composeRule.activity.window.decorView)
                matches = bars.isAppearanceLightStatusBars == expectedLightStatusAppearance &&
                    bars.isAppearanceLightNavigationBars == expectedLightNavigationAppearance
            }
            matches
        }
        instrumentation.waitForIdleSync()
        composeRule.runOnIdle {
            val bars = WindowInsetsControllerCompat(composeRule.activity.window, composeRule.activity.window.decorView)
            assertTrue("$description must set matching status-bar appearance", bars.isAppearanceLightStatusBars == expectedLightStatusAppearance)
            assertTrue("$description must set matching navigation-bar appearance", bars.isAppearanceLightNavigationBars == expectedLightNavigationAppearance)
        }
    }
}
