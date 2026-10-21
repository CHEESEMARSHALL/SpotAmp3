package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.PlexSettingsManager
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem
import com.example.ui.MusicViewModel
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NowPlayingShellLifecycleTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun expandedPlayerSurvivesRecreationAndCollapsesWithoutChangingPlayback() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PlexSettingsManager(context).apply {
            clearCompanionCredential()
            baseUrl = ""
            token = ""
            offlineOnly = false
        }
        val playback = ViewModelProvider(composeRule.activity)[MusicViewModel::class.java].playbackManager
        val originalQueue = playback.queue.value.toList()
        val wasPlaying = playback.isPlaying.value
        val seededFixture = playback.currentTrack.value == null
        assumeTrue(
            "Do not replace a populated or active QA playback queue",
            seededFixture && originalQueue.isEmpty() && !wasPlaying
        )
        playback.replaceQueueForQa(
            listOf(
                TrackItem(
                    ratingKey = "qa-shell-lifecycle-fixture",
                    title = "QA shell lifecycle fixture",
                    artist = "SpotAmp QA",
                    album = "Generated test audio",
                    key = "/qa/qa-shell-lifecycle-fixture",
                    thumb = "",
                    duration = 180_000L
                )
            )
        )

        try {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("mini_player").fetchSemanticsNodes().isNotEmpty()
            }
            val selectedTrackKey = playback.currentTrack.value?.ratingKey
            val playingBeforeExpand = playback.isPlaying.value
            composeRule.onNodeWithTag("mini_player").performClick()
            composeRule.onNodeWithTag("now_playing_scroll_container").assertIsDisplayed()

            composeRule.activityRule.scenario.recreate()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("now_playing_scroll_container").assertIsDisplayed()
            assertEquals(selectedTrackKey, playback.currentTrack.value?.ratingKey)
            assertEquals(playingBeforeExpand, playback.isPlaying.value)

            composeRule.onNodeWithTag("collapse_button").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("mini_player").fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithTag("now_playing_scroll_container").fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
            composeRule.onNodeWithTag("now_playing_scroll_container").assertDoesNotExist()
            assertEquals(selectedTrackKey, playback.currentTrack.value?.ratingKey)
            assertEquals(playingBeforeExpand, playback.isPlaying.value)
        } finally {
            if (seededFixture) playback.clearQueue()
            playback.release()
        }
    }
}
