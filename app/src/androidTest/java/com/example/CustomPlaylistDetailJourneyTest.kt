package com.example

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.CachedTrack
import com.example.data.MusicDatabase
import com.example.ui.MusicViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reaches CustomPlaylistDetail through a disposable, local-only artist-mix fixture. */
@RunWith(AndroidJUnit4::class)
class CustomPlaylistDetailJourneyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun localArtistMixOpensAndReturnsWithoutStartingPlayback() = runBlocking {
        val activity = composeRule.activity
        val viewModel = ViewModelProvider(activity)[MusicViewModel::class.java]
        val repository = viewModel.repository
        assumeFalse(
            "This route fixture requires an unpaired QA profile so no SpotCore state can be changed",
            repository.settings.isCompanionConfigured
        )

        val database = MusicDatabase.getDatabase(activity)
        val dao = database.musicDao()
        val fixtureSuffix = System.currentTimeMillis()
        val fixtureRatingKey = "qa-route-audit-$fixtureSuffix"
        val fixtureArtist = "QA Route Artist $fixtureSuffix"
        val fixtureTitle = "QA Route Track $fixtureSuffix"
        assertNull(dao.getTrackByRatingKey(fixtureRatingKey))

        val fixtureTrack = CachedTrack(
            ratingKey = fixtureRatingKey,
            title = fixtureTitle,
            artist = fixtureArtist,
            album = "QA Route Album $fixtureSuffix",
            key = "/qa/route-audit/$fixtureSuffix",
            thumb = "",
            duration = 1_000,
            syncId = fixtureSuffix
        )
        val playback = viewModel.playbackManager
        val originalQueue = playback.queue.value
        val originalTrack = playback.currentTrack.value
        val originalPlaying = playback.isPlaying.value

        try {
            dao.insertCachedTrack(fixtureTrack)

            composeRule.onNode(navigationTab("Discover")).performClick()
            composeRule.onNodeWithTag("discover_feed")
                .performScrollToNode(hasText("Artist Mix"))
            composeRule.onNodeWithText("Artist Mix").performClick()
            composeRule.onNodeWithText("Choose artists").assertIsDisplayed()
            composeRule.onNodeWithTag("station_artist_selection_$fixtureArtist")
                .assertIsDisplayed()
                .performClick()
            composeRule.onNodeWithText("Create mix (1 artist)")
                .assertIsDisplayed()
                .performClick()

            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithTag("custom_playlist_back_button")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("custom_playlist_back_button").assertIsDisplayed()
            composeRule.onNodeWithText("Artist Mix").assertIsDisplayed()
            composeRule.onNodeWithTag("custom_playlist_back_button").performClick()
            composeRule.onNodeWithTag("discover_feed").assertIsDisplayed()

            assertEquals("Opening and closing the route must preserve the playback queue", originalQueue, playback.queue.value)
            assertEquals("Opening and closing the route must preserve the current track", originalTrack, playback.currentTrack.value)
            assertEquals("Opening and closing the route must preserve playback state", originalPlaying, playback.isPlaying.value)
        } finally {
            try {
                val backTargets = composeRule.onAllNodesWithTag("custom_playlist_back_button").fetchSemanticsNodes()
                if (backTargets.isNotEmpty()) {
                    composeRule.onNodeWithTag("custom_playlist_back_button").performClick()
                } else {
                    val builderBackTargets = composeRule.onAllNodesWithTag("premium_stations_back").fetchSemanticsNodes()
                    if (builderBackTargets.isNotEmpty()) {
                        composeRule.onNodeWithTag("premium_stations_back").performClick()
                    }
                }
            } finally {
                database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM cached_tracks WHERE ratingKey = ?",
                    arrayOf(fixtureRatingKey)
                )
                assertNull("The temporary cached track must be deleted", dao.getTrackByRatingKey(fixtureRatingKey))
            }
        }
    }

    private fun navigationTab(label: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            hasText(label)
}
