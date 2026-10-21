package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.DownloadedTrackEntity
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
class DownloadFailureDetailsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun missingFailureTextStillShowsSafeRecoveryAndNoTechnicalDisclosure() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    DownloadFailureDetails(
                        errorMessage = null,
                        showingDetails = false,
                        onToggleDetails = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("The download failed. Check the connection and try again.").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithText("Details").fetchSemanticsNodes().size)
    }

    @Test
    fun technicalFailureDetailsAreHiddenInitiallyAndRedactedWhenExpanded() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val showingDetails = mutableStateOf(false)
        val technicalError = "Unable to promote downloaded file from https://music.example/file?token=secret-value"
        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    DownloadFailureDetails(
                        errorMessage = technicalError,
                        showingDetails = showingDetails.value,
                        onToggleDetails = { showingDetails.value = !showingDetails.value }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(
            "SpotAmp couldn’t save this download on this phone. Check available storage and try again."
        ).assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithText("secret-value", substring = true).fetchSemanticsNodes().size)

        val detailsToggle = composeTestRule.onNodeWithText("Details").assertIsDisplayed()
        assertEquals(
            context.getString(com.example.R.string.recovery_details_collapsed),
            detailsToggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        detailsToggle.performClick()
        composeTestRule.onNodeWithText("token=<redacted>", substring = true).assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithText("secret-value", substring = true).fetchSemanticsNodes().size)

        val hideDetailsToggle = composeTestRule.onNodeWithText("Hide details").assertIsDisplayed()
        assertEquals(
            context.getString(com.example.R.string.recovery_details_expanded),
            hideDetailsToggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        hideDetailsToggle.performClick()
        assertEquals(
            context.getString(com.example.R.string.recovery_details_collapsed),
            composeTestRule.onNodeWithText("Details").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        assertEquals(0, composeTestRule.onAllNodesWithText("token=<redacted>", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun retryAllFailedRetriesOnlyFailedItemsAndRequiresMultipleFailures() {
        val failedA = download("failed-a", "failed")
        val active = download("active", "downloading")
        val failedB = download("failed-b", "failed")
        val retriedKeys = mutableListOf<String>()
        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    RetryFailedDownloadsAction(
                        pendingTracks = listOf(failedA, active, failedB),
                        onRetry = { retriedKeys += it.ratingKey }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Retry all failed").assertIsDisplayed().performClick()
        assertEquals(listOf("failed-a", "failed-b"), retriedKeys)
    }

    @Test
    fun singleFailureKeepsOnlyItsPerItemRetryAction() {
        val failed = download("failed", "failed")
        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    RetryFailedDownloadsAction(pendingTracks = listOf(failed), onRetry = {})
                }
            }
        }
        composeTestRule.onAllNodesWithText("Retry all failed").assertCountEquals(0)
    }

    @Test
    fun perTrackRetryActionNamesTheTrackAndInvokesRetry() {
        var retryCount = 0
        var cancelCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                DownloadPendingTrackAction(
                    trackTitle = "Moonlit Signals",
                    artist = "The Testers",
                    shouldRetry = true,
                    onRetry = { retryCount++ },
                    onCancel = { cancelCount++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("downloads_pending_action")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf("Retry download for Moonlit Signals by The Testers")
                )
            )
            .performClick()
        assertEquals(1, retryCount)
        assertEquals(0, cancelCount)
    }

    @Test
    fun perTrackCancelActionNamesTheTrackAndInvokesCancel() {
        var retryCount = 0
        var cancelCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                DownloadPendingTrackAction(
                    trackTitle = "Moonlit Signals",
                    artist = "The Testers",
                    shouldRetry = false,
                    onRetry = { retryCount++ },
                    onCancel = { cancelCount++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("downloads_pending_action")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf("Cancel download for Moonlit Signals by The Testers")
                )
            )
            .performClick()
        assertEquals(0, retryCount)
        assertEquals(1, cancelCount)
    }

    @Test
    fun clearFailedDownloadsOnlyRemovesFailedRecords() {
        val deletedKeys = mutableListOf<String>()
        var dismissCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    ClearFailedDownloadsMenuItem(
                        downloadedTracks = listOf(
                            download("failed-a", "failed"),
                            download("active", "downloading"),
                            download("completed", "completed"),
                            download("failed-b", "failed")
                        ),
                        onDelete = { deletedKeys += it.ratingKey },
                        onDismiss = { dismissCount++ }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Clear failed items")
            .assertIsDisplayed()
            .performClick()
        assertEquals(listOf("failed-a", "failed-b"), deletedKeys)
        assertEquals(1, dismissCount)
    }

    @Test
    fun clearFailedDownloadsActionIsHiddenWhenThereAreNoFailures() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    ClearFailedDownloadsMenuItem(
                        downloadedTracks = listOf(
                            download("active", "downloading"),
                            download("completed", "completed")
                        ),
                        onDelete = {},
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.onAllNodesWithText("Clear failed items").assertCountEquals(0)
    }

    @Test
    fun failedDownloadRecoveryAndRetryRemainReachableAtLargeText() {
        val showingDetails = mutableStateOf(false)
        var retryCount = 0
        val technicalError = "Unable to connect to SpotCore. HTTP 503"
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Column(
                        Modifier.requiredSize(320.dp, 480.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                androidx.compose.material3.Text("Long track title that wraps at a narrow width")
                                DownloadFailureDetails(
                                    errorMessage = technicalError,
                                    showingDetails = showingDetails.value,
                                    onToggleDetails = { showingDetails.value = !showingDetails.value }
                                )
                            }
                            androidx.compose.material3.TextButton(onClick = { retryCount++ }) {
                                androidx.compose.material3.Text("Retry")
                            }
                        }
                    }
                }
            }
        }

        val failure = composeTestRule.onNodeWithTag("download_failure_message")
        failure.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite
            )
        )
        assertEquals(
            "SpotCore is temporarily unavailable. Try again in a moment.",
            failure.fetchSemanticsNode().config[SemanticsProperties.Error]
        )
        composeTestRule.onNodeWithText(
            "SpotCore is temporarily unavailable. Try again in a moment."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Details").performClick()
        composeTestRule.onNodeWithText("HTTP 503", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed().performClick()
        assertEquals(1, retryCount)
    }

    @Test
    fun expandedTechnicalDetailsAreClearedWhenFailureChangesOrRecovers() {
        val expanded = mapOf(
            "retrying-track" to "HTTP 503",
            "recovered-track" to "Connection timed out",
            "unchanged-track" to "HTTP 404"
        )

        val retained = retainCurrentDownloadFailureDetails(
            expanded = expanded,
            currentFailures = mapOf(
                "retrying-track" to "HTTP 502",
                "unchanged-track" to "HTTP 404"
            )
        )

        assertEquals(mapOf("unchanged-track" to "HTTP 404"), retained)
    }

    private fun download(key: String, status: String) = DownloadedTrackEntity(
        ratingKey = key,
        title = key,
        artist = "Artist",
        album = "Album",
        key = "/$key",
        thumb = "",
        duration = 10_000L,
        fileSize = 1L,
        status = status
    )
}
