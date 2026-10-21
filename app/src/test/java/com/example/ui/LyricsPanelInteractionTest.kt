package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import com.example.playback.Lyrics
import com.example.playback.LyricLine
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class LyricsPanelInteractionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tappingSyncedLyricSeeksWithoutClosingPanel() {
        var seekPosition = -1L
        var closed = false
        composeTestRule.setContent {
            MyApplicationTheme {
                LyricsPanel(
                    lyrics = Lyrics(listOf(LyricLine(12_500L, "A timed lyric")), isSynced = true),
                    loading = false,
                    positionMs = 0L,
                    onSeek = { seekPosition = it },
                    onClose = { closed = true },
                    onReload = {}
                )
            }
        }

        val lyric = composeTestRule.onNodeWithText("A timed lyric").assertIsDisplayed()
        lyric.assertHeightIsAtLeast(48.dp)
        val lyricSemantics = lyric.fetchSemanticsNode().config
        assertEquals(Role.Button, lyricSemantics[SemanticsProperties.Role])
        assertEquals("Seek to this lyric", lyricSemantics[SemanticsActions.OnClick].label)
        lyric.performClick()

        assertEquals(12_500L, seekPosition)
        assertEquals(false, closed)
        composeTestRule.onNodeWithTag("lyrics_panel").assertIsDisplayed()
    }

    @Test
    fun closeButtonClosesLyricsPanel() {
        var closed = false
        composeTestRule.setContent {
            MyApplicationTheme {
                LyricsPanel(
                    lyrics = Lyrics(emptyList(), isSynced = false),
                    loading = false,
                    positionMs = 0L,
                    onSeek = {},
                    onClose = { closed = true },
                    onReload = {}
                )
            }
        }

        val close = composeTestRule.onNodeWithContentDescription("Close lyrics")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        assertEquals(Role.Button, close.fetchSemanticsNode().config[SemanticsProperties.Role])
        close.performClick()

        assertEquals(true, closed)
    }

    @Test
    fun emptyLyricsOffersAUsefulSearchActionThatRetriesLookup() {
        var reloadCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                LyricsPanel(
                    lyrics = null,
                    loading = false,
                    positionMs = 0L,
                    onSeek = {},
                    onClose = {},
                    onReload = { reloadCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText("No lyrics are available.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search for lyrics")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, reloadCount)
        composeTestRule.onNodeWithContentDescription("Close lyrics").assertIsDisplayed()
    }
}
