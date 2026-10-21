package com.example.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class NowPlayingSeekSemanticsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun unknownDurationIsAnnouncedAsUnknownNotZero() {
        composeTestRule.setContent {
            MyApplicationTheme {
                WaveformDurationBar(
                    progress = 195_000L,
                    duration = 0L,
                    trackId = "unknown-duration",
                    onSeek = {},
                    modifier = Modifier.testTag("seek_bar")
                )
            }
        }

        val state = composeTestRule.onNodeWithTag("seek_bar").fetchSemanticsNode().config[
            SemanticsProperties.StateDescription
        ]
        assertEquals("3:15 elapsed, duration unknown", state)
    }

    @Test
    fun knownDurationAnnouncesElapsedAndTotalTime() {
        composeTestRule.setContent {
            MyApplicationTheme {
                WaveformDurationBar(
                    progress = 195_000L,
                    duration = 240_000L,
                    trackId = "known-duration",
                    onSeek = {},
                    modifier = Modifier.testTag("seek_bar")
                )
            }
        }

        val state = composeTestRule.onNodeWithTag("seek_bar").fetchSemanticsNode().config[
            SemanticsProperties.StateDescription
        ]
        assertEquals("3:15 of 4:00", state)
    }

    @Test
    fun seekAnnouncementClampsElapsedTimeToTheKnownDuration() {
        composeTestRule.setContent {
            MyApplicationTheme {
                WaveformDurationBar(
                    progress = 300_000L,
                    duration = 240_000L,
                    trackId = "overrun-duration",
                    onSeek = {},
                    modifier = Modifier.testTag("seek_bar")
                )
            }
        }

        val state = composeTestRule.onNodeWithTag("seek_bar").fetchSemanticsNode().config[
            SemanticsProperties.StateDescription
        ]
        assertEquals("4:00 of 4:00", state)
    }

    @Test
    fun durationFormattingHandlesNegativeProgressAndCompletedTracks() {
        assertEquals("0:00", formatDuration(-1L))
        assertEquals("-3:00", formatRemainingDuration(-1L, 180_000L))
        assertEquals("0:00", formatRemainingDuration(180_000L, 180_000L))
        assertEquals("0:00", formatRemainingDuration(180_500L, 180_000L))
        assertEquals("--:--", formatRemainingDuration(0L, 0L))
        assertEquals("--:--", formatRemainingDuration(0L, -1L))
    }

    @Test
    fun unknownRemainingTimeHasAPlainLanguageAccessibleLabel() {
        composeTestRule.setContent {
            MyApplicationTheme {
                RemainingDurationText(
                    progressMs = 195_000L,
                    durationMs = 0L,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Duration unknown").assertIsDisplayed()
    }
}
