package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.HomeRecentPlay
import com.example.data.JumpBackInItem
import com.example.data.MadeForYouItem
import com.example.data.NewReleaseItem
import com.example.data.OnThisDayItem
import com.example.data.PlexMetadata
import com.example.data.RadioType
import com.example.data.RecentTrack
import com.example.data.RecommendedStation
import com.example.playback.TrackItem
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeFeedAppearanceVisualTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun populatedHomeCardsAtDefaultTextUseReadableWarmLightSurfaces() =
        capturePopulatedHomeCards(
            fontScale = 1f,
            suffix = "100"
        )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun populatedHomeCardsAtLargeTextUseReadableWarmLightSurfaces() =
        capturePopulatedHomeCards(
            fontScale = 1.3f,
            suffix = "130"
        )

    private fun capturePopulatedHomeCards(fontScale: Float, suffix: String) {
        val track = TrackItem(
            ratingKey = "home-appearance-track",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            key = "",
            thumb = "",
            duration = 241_000L
        )
        val station = RecommendedStation(
            id = "home-appearance-station",
            title = "Library Radio",
            subtitle = "A mix from the full library",
            type = RadioType.LIBRARY_RADIO,
            gradientColors = listOf(0xFF1E3A8A, 0xFF0D9488)
        )

        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, fontScale)) {
                MyApplicationTheme(activeTheme = "Warm Light") {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .background(MaterialTheme.colorScheme.background)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HomeCardPreview("recent_play") {
                                RecentPlayCard(
                                    play = HomeRecentPlay(
                                        id = "home-appearance-recent-play",
                                        title = "Midnight City",
                                        artist = "M83",
                                        thumb = "",
                                        relativeTime = "Yesterday",
                                        type = "track",
                                        tracks = listOf(track)
                                    ),
                                    baseUrl = "",
                                    token = "",
                                    onPlayClick = {},
                                    onMoreClick = {}
                                )
                            }
                            HomeCardPreview("album") {
                                AlbumCard(
                                    album = PlexMetadata(
                                        ratingKey = "home-appearance-album",
                                        title = "Hurry Up, We're Dreaming",
                                        type = "album",
                                        parentTitle = "M83"
                                    ),
                                    baseUrl = "",
                                    token = "",
                                    onClick = {},
                                    onMoreClick = {}
                                )
                            }
                            HomeCardPreview("jump_back_in") {
                                JumpBackInCard(
                                    jbi = JumpBackInItem(
                                        id = "home-appearance-jump-back-in",
                                        title = "M83 Essentials",
                                        subtitle = "Playlist · 24 tracks",
                                        thumb = "",
                                        type = "playlist"
                                    ),
                                    baseUrl = "",
                                    token = "",
                                    onClick = {}
                                )
                            }
                            HomeCardPreview("recommended_station") {
                                PremiumStationTile(station = station, onClick = {})
                            }
                            HomeCardPreview("made_for_you") {
                                LargeRecommendationCard(
                                    mfyItem = MadeForYouItem(
                                        id = "home-appearance-made-for-you",
                                        title = "Recommended for you",
                                        description = "A focused mix based on the artists and albums you play most.",
                                        artists = listOf("M83"),
                                        tracks = listOf(track),
                                        thumb = ""
                                    ),
                                    baseUrl = "",
                                    token = "",
                                    onPlayClick = {},
                                    onAddQueueClick = {}
                                )
                            }
                            HomeCardPreview("track") {
                                TrackCard(track = track, baseUrl = "", token = "", onClick = {}, onMoreClick = {})
                            }
                            HomeCardPreview("on_this_day") {
                                OnThisDaySection(
                                    onThisDay = OnThisDayItem(
                                        title = "Hurry Up, We're Dreaming",
                                        artist = "M83",
                                        year = 2011,
                                        timeAgo = "15 years ago",
                                        thumb = "",
                                        tracks = listOf(track),
                                        matchReason = "A landmark album that still sounds fresh today."
                                    ),
                                    baseUrl = "",
                                    token = "",
                                    onPlayClick = {}
                                )
                            }
                            HomeCardPreview("new_release") {
                                NewReleaseCard(
                                    release = NewReleaseItem(
                                        title = "The New Album",
                                        artist = "M83",
                                        year = 2026,
                                        thumb = "",
                                        tracks = listOf(track)
                                    ),
                                    baseUrl = "",
                                    token = "",
                                    onClick = {}
                                )
                            }
                            HomeCardPreview("history") {
                                RecentHistoryRow(
                                    recent = RecentTrack(
                                        ratingKey = track.ratingKey,
                                        title = track.title,
                                        artist = track.artist,
                                        album = track.album,
                                        key = track.key,
                                        thumb = ""
                                    ),
                                    baseUrl = "",
                                    token = "",
                                    onClick = {},
                                    onMoreClick = {}
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        captureCard("recent_play", "home-warm-light-populated-recent-play-compact-$suffix.png")
        captureCard("album", "home-warm-light-populated-album-compact-$suffix.png")
        captureCard("jump_back_in", "home-warm-light-populated-jump-back-in-compact-$suffix.png")
        captureCard("recommended_station", "home-warm-light-populated-station-compact-$suffix.png")
        captureCard("made_for_you", "home-warm-light-populated-made-for-you-compact-$suffix.png")
        captureCard("track", "home-warm-light-populated-track-compact-$suffix.png")
        captureCard("on_this_day", "home-warm-light-populated-on-this-day-compact-$suffix.png")
        captureCard("new_release", "home-warm-light-populated-new-release-compact-$suffix.png")
        captureCard("history", "home-warm-light-populated-history-compact-$suffix.png")
    }

    private fun captureCard(tag: String, filename: String) {
        composeRule.onNodeWithTag("home_card_preview_$tag").performScrollTo().assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$filename")
    }
}

@Composable
private fun HomeCardPreview(tag: String, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().testTag("home_card_preview_$tag")) {
        content()
    }
}
