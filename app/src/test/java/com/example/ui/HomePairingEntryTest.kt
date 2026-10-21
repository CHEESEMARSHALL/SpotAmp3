package com.example.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.HomeFeedSource
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
class HomePairingEntryTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun connectionStatusDoesNotAnnounceRedundantDecorativeIcon() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeHeader(
                    libraryName = "My Music",
                    isConfigured = true,
                    hasConnectionError = false,
                    source = HomeFeedSource.SPOTCORE,
                    onNavigateToSettings = {},
                    companionConfigured = true,
                    connectionStatus = com.example.data.CompanionConnectionStatus.CONNECTED
                )
            }
        }

        composeTestRule.onNodeWithText("SpotCore available").assertIsDisplayed()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithContentDescription("Server Connection").fetchSemanticsNodes().size
        )
    }

    @Test
    fun emptyLibraryNameUsesTheResourceBackedHomeFallback() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeHeader(
                    libraryName = "",
                    isConfigured = false,
                    hasConnectionError = false,
                    source = HomeFeedSource.PLEX,
                    onNavigateToSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Music Library").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect to SpotCore").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Scan the Add Device QR code in SpotCore to connect this phone and save your music library details here."
        ).assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithText("Offline mode").fetchSemanticsNodes().size)
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText(
                "Browse your saved library offline; download tracks for offline listening."
            ).fetchSemanticsNodes().size
        )
    }

    @Test
    fun homeHeaderUsesOfflineCopyOnlyWhenCachedMusicExists() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeHeader(
                    libraryName = "Saved Library",
                    isConfigured = false,
                    hasConnectionError = false,
                    hasCachedMusic = true,
                    source = HomeFeedSource.PLEX,
                    onNavigateToSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Offline mode").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Browse your saved library offline; download tracks for offline listening."
        ).assertIsDisplayed()
    }

    @Test
    fun homeHeaderConnectionStatesStayActionableAcrossReachabilityChanges() {
        val connectionStatus = androidx.compose.runtime.mutableStateOf(
            com.example.data.CompanionConnectionStatus.CONNECTED
        )
        val hasCachedMusic = androidx.compose.runtime.mutableStateOf(false)
        var settingsRequests = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeHeader(
                    libraryName = "Saved Library",
                    isConfigured = true,
                    hasConnectionError = false,
                    hasCachedMusic = hasCachedMusic.value,
                    source = HomeFeedSource.SPOTCORE,
                    onNavigateToSettings = { settingsRequests++ },
                    companionConfigured = true,
                    connectionStatus = connectionStatus.value
                )
            }
        }

        composeTestRule.onNodeWithText("SpotCore available").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connected to your SpotCore music library.").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("home_reconnect_settings").fetchSemanticsNodes().size)

        connectionStatus.value = com.example.data.CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SpotCore unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reconnect to browse your library and get the latest music.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_reconnect_settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open connection settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_reconnect_settings").performClick()
        assertEquals(1, settingsRequests)

        hasCachedMusic.value = true
        connectionStatus.value = com.example.data.CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Offline library available").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Your saved library details are available while SpotCore is offline. Downloaded tracks can still play."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Open connection settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_reconnect_settings").performClick()
        assertEquals(2, settingsRequests)
    }

    @Test
    fun freshUnpairedEmptyStateExplainsPairingAndStartsIt() {
        var pairingRequests = 0
        var settingsRequests = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeEmptyState(
                    isConfigured = false,
                    hasCachedMusic = false,
                    onNavigateToSettings = { settingsRequests++ },
                    onStartPairing = { pairingRequests++ }
                )
            }
        }

        composeTestRule.onNodeWithText("Connect to SpotCore to get started").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Scan the Add Device QR code in SpotCore to connect this phone and save your music library details here."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_connection_settings_button")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, settingsRequests)
        composeTestRule.onNodeWithText("Scan pairing QR code").performClick()

        assertEquals(1, pairingRequests)
    }

    @Test
    fun offlineUnpairedStateKeepsPairingPathAndExplainsSavedMusic() {
        var pairingRequests = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeEmptyState(
                    isConfigured = false,
                    hasCachedMusic = true,
                    onNavigateToSettings = {},
                    onStartPairing = { pairingRequests++ }
                )
            }
        }

        composeTestRule.onNodeWithText("Offline library ready").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Your saved library is still searchable. Pair with SpotCore to reconnect and refresh it."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan pairing QR code").performClick()
        assertEquals(1, pairingRequests)
    }

    @Test
    fun configuredEmptyLibraryExplainsSetupAndOpensSettings() {
        var settingsRequests = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeEmptyState(
                    isConfigured = true,
                    hasCachedMusic = false,
                    onNavigateToSettings = { settingsRequests++ },
                    onStartPairing = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Finish setting up your library").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "This phone hasn’t saved your music library details yet. Open Settings to connect or update your library so you can browse and search here."
        ).assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("home_pairing_cta").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("home_setup_library_cta").assertIsDisplayed().performClick()
        assertEquals(1, settingsRequests)
    }

    @Test
    fun homeConnectionFailureDetailsAreOptInAndRecoveryActionsWork() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var retryCount = 0
        var settingsCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeErrorBanner(
                    message = "Couldn’t reach SpotCore. Check the connection and try again.",
                    technicalDetails = "HTTP 502 from https://music.example/home?token=<redacted>",
                    onNavigateToSettings = { settingsCount++ },
                    onRetry = { retryCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText(
            "Couldn’t reach SpotCore. Check the connection and try again."
        ).assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("home_error_technical_details").fetchSemanticsNodes().size)

        val detailsToggle = composeTestRule.onNodeWithTag("home_error_details_toggle")
        assertEquals(
            context.getString(R.string.recovery_details_collapsed),
            detailsToggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        detailsToggle.performClick()
        assertEquals(
            context.getString(R.string.recovery_details_expanded),
            detailsToggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        composeTestRule.onNodeWithText("HTTP 502 from https://music.example/home?token=<redacted>").assertIsDisplayed()
        detailsToggle.performClick()
        assertEquals(
            context.getString(R.string.recovery_details_collapsed),
            detailsToggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        assertEquals(0, composeTestRule.onAllNodesWithTag("home_error_technical_details").fetchSemanticsNodes().size)

        composeTestRule.onNodeWithTag("home_retry_button").performClick()
        composeTestRule.onNodeWithTag("home_settings_button").performClick()
        assertEquals(1, retryCount)
        assertEquals(1, settingsCount)
    }
}
