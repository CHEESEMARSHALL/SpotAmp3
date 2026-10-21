package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackArtworkPolicyTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun smallArtworkIsPresentedAtReducedSize() {
        assertTrue(isLowResolutionArtwork(300, 300))
        assertTrue(isLowResolutionArtwork(1200, 256))
    }

    @Test
    fun artworkAtOrAboveThresholdKeepsFullPresentation() {
        assertFalse(isLowResolutionArtwork(512, 512))
        assertFalse(isLowResolutionArtwork(1200, 800))
        assertFalse(isLowResolutionArtwork(0, 0))
    }

    @Test
    fun missingArtworkShowsTheProductionFallbackCopy() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(Modifier.size(240.dp)) {
                    PlaybackVisualSurface(
                        artworkUrl = null,
                        spectrum = floatArrayOf(0.2f, 0.4f),
                        waveform = floatArrayOf(0.1f, 0.3f),
                        mode = PlaybackArtworkMode.ARTWORK,
                        onModeChange = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Artwork unavailable").assertIsDisplayed()
    }

    @Test
    fun visualModeControlCyclesThroughEveryProductionMode() {
        val mode = mutableStateOf(PlaybackArtworkMode.ARTWORK)
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(Modifier.size(240.dp)) {
                    PlaybackVisualSurface(
                        artworkUrl = null,
                        spectrum = floatArrayOf(0.2f, 0.4f),
                        waveform = floatArrayOf(-0.6f, 0.7f),
                        mode = mode.value,
                        onModeChange = { mode.value = it },
                        onClick = {},
                        onClickLabel = "Show lyrics"
                    )
                }
            }
        }

        val modeButton = composeTestRule.onNodeWithTag("player_visual_mode_button")
        modeButton.assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertContentDescriptionEquals("Change player visual; current mode: Artwork")
        modeButton.performClick()
        composeTestRule.waitForIdle()
        assertEquals(PlaybackArtworkMode.SPECTRUM, mode.value)
        modeButton.assertContentDescriptionEquals("Change player visual; current mode: Spectrum")
        composeTestRule.onNodeWithText("Spectrum").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_visualizer_spectrum", useUnmergedTree = true).assertIsDisplayed()

        modeButton.performClick()
        composeTestRule.waitForIdle()
        assertEquals(PlaybackArtworkMode.WAVEFORM, mode.value)
        modeButton.assertContentDescriptionEquals("Change player visual; current mode: Waveform")
        composeTestRule.onNodeWithText("Waveform").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_visualizer_waveform", useUnmergedTree = true).assertIsDisplayed()

        modeButton.performClick()
        composeTestRule.waitForIdle()
        assertEquals(PlaybackArtworkMode.RADIAL, mode.value)
        modeButton.assertContentDescriptionEquals("Change player visual; current mode: Radial spectrum")
        composeTestRule.onNodeWithText("Radial spectrum").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_visualizer_radial", useUnmergedTree = true).assertIsDisplayed()

        modeButton.performClick()
        composeTestRule.waitForIdle()
        assertEquals(PlaybackArtworkMode.ARTWORK, mode.value)
        modeButton.assertContentDescriptionEquals("Change player visual; current mode: Artwork")
        composeTestRule.onNodeWithText("Artwork").assertIsDisplayed()
    }
}
