package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class DownloadsEmptyStateTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun explainsOfflineDownloadsAndNavigatesToLibrary() {
        var browseCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                DownloadsEmptyState(onBrowseMusic = { browseCount++ })
            }
        }

        composeTestRule.onNodeWithText("Nothing saved for offline listening yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Browse music").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("downloads_browse_music").assertIsDisplayed()
        composeTestRule.onNodeWithTag("downloads_pair_spotcore_action").assertDoesNotExist()

        assertEquals(1, browseCount)
    }

    @Test
    fun unpairedDownloadsEmptyStateOffersDirectPairingAtLargeText() {
        var pairingStarts = 0
        composeTestRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 1.3f)
            ) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 400.dp)) {
                        DownloadsEmptyState(
                            onBrowseMusic = {},
                            companionConfigured = false,
                            onStartPairing = { pairingStarts++ }
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Nothing saved for offline listening yet").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Connect SpotAmp to SpotCore and choose a music library before saving tracks for offline listening."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan pairing QR code").assertIsDisplayed()
        composeTestRule.onNodeWithTag("downloads_pair_spotcore_action")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(1, pairingStarts)
        composeTestRule.onNodeWithTag("downloads_browse_music").assertDoesNotExist()
    }

    @Test
    fun emptyPromptOnlyAppearsWhenThereAreNoCompletedPendingOrActiveDownloads() {
        assertTrue(
            shouldShowDownloadsEmptyState(
                hasCompletedTracks = false,
                hasPendingTracks = false,
                hasActiveProgress = false
            )
        )
        assertFalse(
            shouldShowDownloadsEmptyState(
                hasCompletedTracks = false,
                hasPendingTracks = true,
                hasActiveProgress = false
            )
        )
        assertFalse(
            shouldShowDownloadsEmptyState(
                hasCompletedTracks = false,
                hasPendingTracks = false,
                hasActiveProgress = true
            )
        )
        assertFalse(
            shouldShowDownloadsEmptyState(
                hasCompletedTracks = true,
                hasPendingTracks = false,
                hasActiveProgress = false
            )
        )
    }
}
