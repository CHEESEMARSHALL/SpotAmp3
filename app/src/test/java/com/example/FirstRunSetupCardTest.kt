package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.runtime.mutableStateOf
import com.example.data.LibrarySyncState
import com.example.data.CompanionConnectionStatus
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class FirstRunSetupCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun changingSetupProgressIsAnnouncedAsAPoliteLiveUpdate() {
        val syncState = mutableStateOf<LibrarySyncState>(
            LibrarySyncState.Fetching(page = 1, receivedCount = 12)
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                FirstRunSetupCard(
                    syncState = syncState.value,
                    cachedCount = 0,
                    onRetry = {},
                    onFinish = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("first_run_setup_status")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite
                )
            )
        composeTestRule.onNodeWithText("Loading music from your library…").assertIsDisplayed()

        syncState.value = LibrarySyncState.Saving(validCount = 12)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Saving your library on this phone…").assertIsDisplayed()
    }

    @Test
    fun idleStateOffersStartAndNotNowActions() {
        var retryCount = 0
        var dismissCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                FirstRunSetupCard(
                    syncState = LibrarySyncState.Idle,
                    cachedCount = 0,
                    onRetry = { retryCount++ },
                    onFinish = {},
                    onDismiss = { dismissCount++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("first_run_setup_card").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Your SpotCore connection is ready. Save your library details on this phone to browse them here."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Save library details").performClick()
        composeTestRule.onNodeWithText("Not now").performClick()

        assertEquals(1, retryCount)
        assertEquals(1, dismissCount)
    }

    @Test
    fun idleStateExplainsSyncEnqueueFailureAndOffersRetry() {
        var retryCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                FirstRunSetupCard(
                    syncState = LibrarySyncState.Idle,
                    cachedCount = 0,
                    syncEnqueueError = "SpotAmp couldn’t start saving your SpotCore library. Try again.",
                    onRetry = { retryCount++ },
                    onFinish = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("first_run_sync_enqueue_error")
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
                    "SpotAmp couldn’t start saving your SpotCore library. Try again."
                )
            )
        composeTestRule.onNodeWithTag("first_run_retry_index").assertIsDisplayed().performClick()
        assertEquals(1, retryCount)
    }

    @Test
    fun failedStateOffersRetryAndBackgroundExit() {
        var retryCount = 0
        var dismissCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                FirstRunSetupCard(
                    syncState = LibrarySyncState.Failed("failed", "network error", "fetch"),
                    cachedCount = 12,
                    companionConnectionStatus = CompanionConnectionStatus.CONNECTED,
                    onRetry = { retryCount++ },
                    onFinish = {},
                    onDismiss = { dismissCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText(
            "SpotCore is reachable, but we couldn’t finish saving your library details on this phone. Try again."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").performClick()
        composeTestRule.onNodeWithText("Not now").performClick()

        assertEquals(1, retryCount)
        assertEquals(1, dismissCount)
    }

    @Test
    fun failedSyncWithUnavailableSpotCoreExplainsConnectionRecovery() {
        composeTestRule.setContent {
            MyApplicationTheme {
                FirstRunSetupCard(
                    syncState = LibrarySyncState.Failed("failed", "network error", "fetch"),
                    cachedCount = 0,
                    companionConnectionStatus = CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE,
                    onRetry = {},
                    onFinish = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText(
            "SpotCore is unavailable, so this library sync couldn’t finish. Check that SpotCore is running, then try again."
        ).assertIsDisplayed()
    }

    @Test
    fun emptyCompletedLibraryOffersAnotherCheck() {
        var retryCount = 0
        var dismissCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                FirstRunSetupCard(
                    syncState = LibrarySyncState.Complete(indexedCount = 0, skippedCount = 0, elapsedMs = 100),
                    cachedCount = 0,
                    onRetry = { retryCount++ },
                    onFinish = {},
                    onDismiss = { dismissCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText(
            "SpotCore is connected, but its library has no tracks yet. Add music to your SpotCore library, then check again."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Check library again").performClick()
        composeTestRule.onNodeWithText("Not now").performClick()
        composeTestRule.onNodeWithText("Continue to Home").assertDoesNotExist()

        assertEquals(1, retryCount)
        assertEquals(1, dismissCount)
    }

    @Test
    fun completedLibraryOffersHomeAndUsesBrowseReadyCountCopy() {
        var finishCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                FirstRunSetupCard(
                    syncState = LibrarySyncState.Complete(indexedCount = 15, skippedCount = 0, elapsedMs = 100),
                    cachedCount = 15,
                    onRetry = {},
                    onFinish = { finishCount++ },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Your library is ready to browse").assertIsDisplayed()
        composeTestRule.onNodeWithText("15 tracks are ready to browse on this phone.", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Browse and stream from Home. Download tracks to listen offline.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue to Home").performClick()

        assertEquals(1, finishCount)
    }

    @Test
    fun inProgressStateOffersContinueInBackground() {
        var finishCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                FirstRunSetupCard(
                    syncState = LibrarySyncState.Processing(totalReceived = 20, validCount = 12, skippedCount = 0),
                    cachedCount = 12,
                    onRetry = {},
                    onFinish = { finishCount++ },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("12 tracks are already ready to browse on this phone.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue in background").performClick()

        assertEquals(1, finishCount)
    }

    @Test
    fun firstRunProgressAndNextActionsRemainReachableAtLargeText() {
        val syncState = mutableStateOf<LibrarySyncState>(
            LibrarySyncState.Saving(validCount = 12)
        )
        var finishCount = 0
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 520.dp)) {
                        Column(
                            Modifier.fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            FirstRunSetupCard(
                                syncState = syncState.value,
                                cachedCount = 12,
                                onRetry = {},
                                onFinish = { finishCount++ },
                                onDismiss = {}
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Saving your library on this phone…")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue in background")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("first_run_continue_home")
            .performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, finishCount)

        syncState.value = LibrarySyncState.Complete(indexedCount = 12, skippedCount = 0, elapsedMs = 100)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Your library is ready to browse").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("first_run_setup_next_step")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue to Home").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("first_run_continue_home").performScrollTo().assertIsDisplayed()
    }
}
