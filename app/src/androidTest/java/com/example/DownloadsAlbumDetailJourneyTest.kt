package com.example

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.playback.TrackItem
import com.example.ui.MusicViewModel
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opens a temporary, local download in the production UI without starting playback. */
@RunWith(AndroidJUnit4::class)
class DownloadsAlbumDetailJourneyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun downloadedAlbumDetailReturnsToDownloadsWithoutPlaybackChanges() = runBlocking {
        val viewModel = ViewModelProvider(composeRule.activity)[MusicViewModel::class.java]
        val repository = viewModel.repository
        val ratingKey = "qa-route-downloaded-album"
        val albumName = "QA Route Album"
        val fixtureFile = File(composeRule.activity.cacheDir, "$ratingKey.bin")
        fixtureFile.writeText("Temporary local route fixture; playback is not part of this check.")
        val track = TrackItem(
            ratingKey = ratingKey,
            title = "QA Route Track",
            artist = "QA Route Artist",
            album = albumName,
            key = "/qa/$ratingKey.bin",
            thumb = "",
            duration = 1_000L
        )
        val originalQueue = viewModel.playbackManager.queue.value.toList()
        val originalTrack = viewModel.playbackManager.currentTrack.value
        val originalPlaying = viewModel.playbackManager.isPlaying.value

        try {
            // This repository method writes only the local downloads index; no download or
            // companion request is started. The non-empty placeholder file lets the album
            // appear as available offline while remaining unplayable test data.
            repository.addDownloadedTrack(track, fixtureFile.length(), fixtureFile.absolutePath)

            composeRule.onNode(navigationTab("Downloads")).performClick()
            composeRule.waitUntil(10_000) {
                runBlocking { repository.downloadedTracks.first().any { it.ratingKey == ratingKey } }
            }
            composeRule.onNodeWithTag("download_album_${albumName.lowercase()}")
                .performScrollTo()
                .performClick()

            composeRule.onNodeWithTag("downloaded_album_detail").assertIsDisplayed()
            composeRule.onNodeWithTag("downloaded_album_back").performClick()
            composeRule.onNodeWithTag("downloads_more_button").assertIsDisplayed()
            assertEquals(originalQueue, viewModel.playbackManager.queue.value.toList())
            assertEquals(originalTrack, viewModel.playbackManager.currentTrack.value)
            assertEquals(originalPlaying, viewModel.playbackManager.isPlaying.value)
        } finally {
            repository.deleteDownloadedTrack(ratingKey)
            fixtureFile.delete()
        }
    }

    private fun navigationTab(label: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            (hasText(label) or hasContentDescription(label))
}
