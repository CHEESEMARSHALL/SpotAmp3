package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.data.MusicRepository
import com.example.playback.TrackItem
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real failed-download row without retrying or accessing the network. */
@RunWith(AndroidJUnit4::class)
class DownloadsFailureRecoveryDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun failedDownloadOffersSafeGuidanceAndOptInRedactedDetails() {
        runBlocking {
            val repository = MusicRepository(composeRule.activity.applicationContext)
            val ratingKey = "qa-failed-download-${System.currentTimeMillis()}"
            val artist = "QA test $ratingKey"
            val track = TrackItem(
                ratingKey = ratingKey,
                title = "QA failed download row",
                artist = artist,
                album = "QA recovery",
                key = "/qa/offline-test.mp3",
                thumb = "",
                duration = 1000L
            )

            try {
                repository.queueDownload(track)
                repository.markDownloadStatus(ratingKey, "failed", "HTTP 503 from test server")

                composeRule.onNode(
                    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
                        (hasText("Downloads") or hasContentDescription("Downloads"))
                ).performClick()
                val albumTag = "download_album_qa recovery"
                composeRule.waitUntil(10_000) {
                    composeRule.onAllNodesWithTag(albumTag).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithTag(albumTag).performClick()
                composeRule.waitUntil(10_000) {
                    composeRule.onAllNodesWithText(track.title).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText(track.title).assertIsDisplayed()
                composeRule.onNodeWithText(
                    composeRule.activity.getString(
                        R.string.downloads_track_status,
                        artist,
                        composeRule.activity.getString(R.string.downloads_failed_status)
                    )
                ).assertIsDisplayed()
                composeRule.onAllNodesWithText("Nothing saved for offline listening yet").assertCountEquals(0)
                composeRule.onNodeWithText(
                    composeRule.activity.getString(R.string.product_failure_server_unavailable)
                ).assertIsDisplayed()
                composeRule.onAllNodesWithText("HTTP 503", substring = true).assertCountEquals(0)

                composeRule.onNodeWithTag("download_failure_details_toggle_$ratingKey").performClick()
                composeRule.onNodeWithText("HTTP 503 from test server").assertIsDisplayed()
                composeRule.onNodeWithText("Hide details").performClick()
                composeRule.onAllNodesWithText("HTTP 503 from test server").assertCountEquals(0)

                // Target this row's action directly; retained QA data may contain other failed rows.
                composeRule.onNodeWithContentDescription(
                    composeRule.activity.getString(
                        R.string.downloads_retry_track_accessibility,
                        track.title,
                        track.artist
                    )
                ).assertIsDisplayed()
            } finally {
                repository.deleteDownloadedTrack(ratingKey)
            }
        }
    }
}
