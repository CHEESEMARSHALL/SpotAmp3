package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import com.example.data.DailyMix
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
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class HomeSectionCopyTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeSettingsActionHasA48dpTargetAndNavigates() {
        var settingsRequests = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeHeader(
                    libraryName = "SpotCore Library",
                    isConfigured = true,
                    hasConnectionError = false,
                    source = com.example.data.HomeFeedSource.SPOTCORE,
                    onNavigateToSettings = { settingsRequests++ },
                    companionConfigured = true
                )
            }
        }

        val action = composeTestRule.onNodeWithTag("home_settings_button").assertIsDisplayed()
        val bounds = action.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Home settings should have a 48dp-wide touch target",
            bounds.width >= with(composeTestRule.density) { 48.dp.toPx() }
        )
        assertTrue(
            "Home settings should have a 48dp-tall touch target",
            bounds.height >= with(composeTestRule.density) { 48.dp.toPx() }
        )
        action.performClick()
        assertEquals(1, settingsRequests)
    }

    @Test
    fun primaryHomeHeadingsRenderResourceBackedListenerCopy() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    RecentPlaysSection(emptyList(), "", "", {}, {})
                    RecentlyAddedSection(emptyList(), "", "", {}, {})
                    DailyMixesSection(emptyList<DailyMix>(), "", "", {})
                    JumpBackInSection(emptyList(), "", "", {})
                    RecommendedStationsSection(emptyList(), {})
                }
            }
        }

        assertEquals(0, composeTestRule.onAllNodesWithText("Recently Added in Music").fetchSemanticsNodes().size)
        listOf("Recent Plays", "Recently Added", "Daily Mixes", "Jump Back In", "Stations").forEach { heading ->
            composeTestRule.onNodeWithText(heading, substring = false).assertIsDisplayed()
        }
        assertEquals(0, composeTestRule.onAllNodesWithText("STATIONS", substring = false).fetchSemanticsNodes().size)
    }

    @Test
    fun playCountCopyUsesSingularAndPluralForms() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    Text(localizedPlayCountLabel(0))
                    Text(localizedPlayCountLabel(1))
                    Text(localizedPlayCountLabel(2))
                }
            }
        }

        composeTestRule.onNodeWithText("0 plays", substring = false).assertIsDisplayed()
        composeTestRule.onNodeWithText("1 play", substring = false).assertIsDisplayed()
        composeTestRule.onNodeWithText("2 plays", substring = false).assertIsDisplayed()
    }

    @Test
    fun seeAllIsExposedAsOneButtonAndRunsItsAction() {
        var requests = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                SectionHeader(title = "Recently Added", onSeeAllClick = { requests++ })
            }
        }

        composeTestRule.onNodeWithTag("home_see_all_button")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickAction())
            .performClick()
        assertEquals(1, requests)
        assertEquals(1, composeTestRule.onAllNodesWithText("See all", substring = false).fetchSemanticsNodes().size)
    }

    @Test
    fun stationTilesDescribeTheirChoiceAndRemainClickable() {
        var clicked = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    PremiumStationTile(
                        station(RadioType.DEEP_CUTS, "Deep Cuts"),
                        onClick = { clicked++ },
                        modifier = Modifier.testTag("deep_cuts_tile")
                    )
                    PremiumStationTile(
                        station(RadioType.GENRE_RADIO, "Genre Mix"),
                        onClick = { clicked++ },
                        modifier = Modifier.testTag("genre_tile")
                    )
                    PremiumStationTile(
                        station(RadioType.COLLECTION_RADIO, "Collection Radio"),
                        onClick = { clicked++ },
                        modifier = Modifier.testTag("collection_tile")
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Less-played tracks").assertIsDisplayed()
        composeTestRule.onNodeWithText("Choose a genre").assertIsDisplayed()
        composeTestRule.onNodeWithText("Collection Radio").assertIsDisplayed()
        composeTestRule.onNodeWithText("Music station").assertIsDisplayed()
        composeTestRule.onNodeWithTag("genre_tile")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickAction())
            .performClick()
        assertEquals(1, clicked)
    }

    @Test
    fun recommendationActionsIdentifyTheirTargetAndInvokeTheCorrectCallbacks() {
        var playCount = 0
        var queueCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                LargeRecommendationCard(
                    mfyItem = MadeForYouItem(
                        id = "chill-mix",
                        title = "Chill Mix",
                        description = "A relaxed selection",
                        artists = emptyList(),
                        tracks = emptyList(),
                        thumb = ""
                    ),
                    baseUrl = "",
                    token = "",
                    onPlayClick = { playCount++ },
                    onAddQueueClick = { queueCount++ }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Play Chill Mix")
            .assertIsDisplayed()
            .performClick()
        val addToQueue = composeTestRule.onNodeWithContentDescription("Add Chill Mix to queue")
            .assertIsDisplayed()
        val queueBounds = addToQueue.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Home add-to-queue should have a 48dp-wide touch target",
            queueBounds.width >= with(composeTestRule.density) { 48.dp.toPx() }
        )
        assertTrue(
            "Home add-to-queue should have a 48dp-tall touch target",
            queueBounds.height >= with(composeTestRule.density) { 48.dp.toPx() }
        )
        addToQueue.performClick()
        assertEquals(1, playCount)
        assertEquals(1, queueCount)
    }

    @Test
    fun dailyMixCardAndSpotlightSeparateOpenFromPlayActions() {
        var cardOpenCount = 0
        var spotlightOpenCount = 0
        var spotlightPlayCount = 0
        val mix = DailyMix(
            id = "chill-mix",
            title = "Chill Mix",
            reason = "Relaxed favorites",
            tracks = emptyList(),
            colors = listOf(0xFF312E81, 0xFF0E7490)
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    MixCard(mix = mix, baseUrl = "", token = "", onClick = { cardOpenCount++ })
                    HomeSpotlight(
                        mix = mix,
                        source = com.example.data.HomeFeedSource.SPOTCORE,
                        baseUrl = "",
                        token = "",
                        onPlay = { spotlightPlayCount++ },
                        onOpen = { spotlightOpenCount++ }
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Open daily mix: Chill Mix")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickAction())
            .performClick()
        composeTestRule.onNodeWithContentDescription("Open featured mix: Chill Mix")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickAction())
            .performClick()
        composeTestRule.onNodeWithContentDescription("Play Chill Mix")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText("Based on Relaxed favorites", substring = false).assertIsDisplayed()
        composeTestRule.onNodeWithText("From your library").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mix", substring = false).assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithText("FROM YOUR LIBRARY").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("MIX", substring = false).fetchSemanticsNodes().size)
        assertEquals(1, cardOpenCount)
        assertEquals(1, spotlightOpenCount)
        assertEquals(1, spotlightPlayCount)
        assertEquals(0, composeTestRule.onAllNodesWithContentDescription("Play Mix").fetchSemanticsNodes().size)
    }

    @Test
    fun spotlightPlayActionRemainsReachableAtLargeTextScale() {
        var playCount = 0
        val mix = DailyMix(
            id = "listening-history-mix",
            title = "Because You Played",
            reason = "Built from your listening history: QA Artist",
            tracks = emptyList(),
            colors = listOf(0xFF312E81, 0xFF0E7490)
        )
        composeTestRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Column(modifier = Modifier.width(320.dp)) {
                        HomeSpotlight(
                            mix = mix,
                            source = com.example.data.HomeFeedSource.PLEX,
                            baseUrl = "",
                            token = "",
                            onPlay = { playCount++ },
                            onOpen = {}
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Play Because You Played")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
            .performClick()

        assertEquals(1, playCount)
    }

    @Test
    fun jumpBackInTileExposesItsTapActionAsButton() {
        var openCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                JumpBackInCard(
                    jbi = JumpBackInItem(
                        id = "jump-back-album",
                        title = "Jump Back Album",
                        subtitle = "Saved album",
                        thumb = "",
                        type = "album"
                    ),
                    baseUrl = "",
                    token = "",
                    onClick = { openCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText("Jump Back Album", substring = false)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickAction())
            .performClick()
        assertEquals(1, openCount)
    }

    @Test
    fun newReleaseCardExposesOpenActionAsButton() {
        var openCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                NewReleaseCard(
                    release = NewReleaseItem(
                        title = "New Album",
                        artist = "New Artist",
                        year = 2026,
                        thumb = "",
                        tracks = emptyList()
                    ),
                    baseUrl = "",
                    token = "",
                    onClick = { openCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText("New Album", substring = false)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickAction())
            .performClick()
        assertEquals(1, openCount)
    }

    @Test
    fun onThisDayCardClearlyLabelsItsPlayAction() {
        var playCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                OnThisDaySection(
                    onThisDay = OnThisDayItem(
                        title = "Kind of Blue",
                        artist = "Miles Davis",
                        year = 1959,
                        timeAgo = "67 years ago",
                        thumb = "",
                        tracks = emptyList(),
                        matchReason = "Added to your music library on this date."
                    ),
                    baseUrl = "",
                    token = "",
                    onPlayClick = { playCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText("Play album again", substring = false)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText("Added to your music library on this date.", substring = false).assertIsDisplayed()
        assertEquals(1, playCount)
    }

    @Test
    fun homeMusicTilesNamePlayOrOpenAndKeepTargetSpecificOptionsActions() {
        var recentPlayCount = 0
        var recentOptionsCount = 0
        var albumOpenCount = 0
        var albumOptionsCount = 0
        var trackPlayCount = 0
        var trackOptionsCount = 0
        var historyPlayCount = 0
        var historyOptionsCount = 0
        val recentTrack = TrackItem(
            ratingKey = "recent-track-id",
            title = "Recent tile track",
            artist = "Tile Artist",
            album = "Home fixture",
            key = "",
            thumb = "",
            duration = 180_000L
        )
        val track = TrackItem(
            ratingKey = "track-id",
            title = "Library tile track",
            artist = "Tile Artist",
            album = "Home fixture",
            key = "",
            thumb = "",
            duration = 180_000L
        )
        val album = PlexMetadata(ratingKey = "album-id", title = "Home fixture album", type = "album")
        val history = RecentTrack(
            ratingKey = "history-track-id",
            title = "History tile track",
            artist = "Tile Artist",
            album = "Home fixture",
            key = "",
            thumb = ""
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    RecentPlayCard(
                        play = HomeRecentPlay(
                            id = "recent-play-id",
                            title = recentTrack.title,
                            artist = recentTrack.artist,
                            thumb = "",
                            relativeTime = "Yesterday",
                            type = "track",
                            tracks = listOf(recentTrack)
                        ),
                        baseUrl = "",
                        token = "",
                        onPlayClick = { recentPlayCount++ },
                        onMoreClick = { recentOptionsCount++ }
                    )
                    AlbumCard(
                        album = album,
                        baseUrl = "",
                        token = "",
                        onClick = { albumOpenCount++ },
                        onMoreClick = { albumOptionsCount++ }
                    )
                    TrackCard(
                        track = track,
                        baseUrl = "",
                        token = "",
                        onClick = { trackPlayCount++ },
                        onMoreClick = { trackOptionsCount++ }
                    )
                    RecentHistoryRow(
                        recent = history,
                        baseUrl = "",
                        token = "",
                        onClick = { historyPlayCount++ },
                        onMoreClick = { historyOptionsCount++ }
                    )
                }
            }
        }

        assertHomeTileActions("Recent tile track", "Play Recent tile track", "More options for Recent tile track")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        assertHomeTileActions("Home fixture album", "Open album Home fixture album", "More options for Home fixture album")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        assertHomeTileActions("Library tile track", "Play Library tile track", "More options for Library tile track")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        assertHomeTileActions("History tile track", "Play History tile track", "More options for History tile track")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }

        assertEquals(1, recentPlayCount)
        assertEquals(1, recentOptionsCount)
        assertEquals(1, albumOpenCount)
        assertEquals(1, albumOptionsCount)
        assertEquals(1, trackPlayCount)
        assertEquals(1, trackOptionsCount)
        assertEquals(1, historyPlayCount)
        assertEquals(1, historyOptionsCount)
    }

    private fun assertHomeTileActions(
        title: String,
        clickLabel: String,
        longClickLabel: String
    ): SemanticsNodeInteraction {
        val tile = composeTestRule.onNodeWithText(title, substring = false)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickAction())
        val actions = tile.fetchSemanticsNode().config
        assertEquals(clickLabel, actions[SemanticsActions.OnClick].label)
        assertEquals(longClickLabel, actions[SemanticsActions.OnLongClick].label)
        return tile
    }

    private fun station(type: RadioType, title: String) = RecommendedStation(
        id = "test-${type.name}",
        title = title,
        subtitle = "Unused fixture subtitle",
        type = type,
        gradientColors = listOf(0xFF6366F1)
    )
}
