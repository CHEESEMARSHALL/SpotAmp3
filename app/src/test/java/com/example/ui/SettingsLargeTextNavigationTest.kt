package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.PlexSettingsManager
import com.example.data.LibrarySyncState
import com.example.data.CachedTrack
import com.example.data.MusicDatabase
import com.example.R
import com.example.playback.PlaybackManager
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsLargeTextNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    private var settingsViewModelStore: ViewModelStore? = null

    @Before
    fun setUp() {
        val settings = PlexSettingsManager(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        settings.clearCompanionCredential()
        settings.baseUrl = ""
        settings.token = ""
        settings.offlineOnly = false
        settings.sectionId = ""
    }

    @After
    fun tearDown() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        settingsViewModelStore?.clear()
        settingsViewModelStore = null
        PlexSettingsManager(context).apply {
            clearCompanionCredential()
            baseUrl = ""
            token = ""
            offlineOnly = false
            sectionId = ""
        }
        PlaybackManager.getInstance(context).release()
        composeTestRule.waitForIdle()
    }

    @Test
    fun sectionShortcutsRemainVisibleAt130PercentFontScale() {
        showSettingsAtLargeText()

        listOf("connection", "library", "downloadsoffline", "playback", "discovery", "appearance", "advanced").forEach { section ->
            composeTestRule.onNodeWithTag("settings_section_$section").assertIsDisplayed()
        }
    }

    @Test
    fun discoveryProviderChoicesAreSingleAccessibleRadioActions() {
        val settingsViewModel = createSettingsViewModel()
        composeTestRule.setContent {
            MyApplicationTheme { SettingsScreen(viewModel = settingsViewModel) }
        }

        val choices = listOf(
            "settings_ai_provider_on_device" to Role.RadioButton,
            "settings_ai_provider_library_ranking" to Role.RadioButton,
            "settings_ai_provider_experimental" to Role.RadioButton
        )
        choices.forEach { (tag, role) ->
            val option = composeTestRule.onNodeWithTag(tag).performScrollTo()
            option.assert(hasClickAction())
            assertEquals(role, option.fetchSemanticsNode().config[SemanticsProperties.Role])
        }

        composeTestRule.onNodeWithTag("settings_ai_provider_on_device").performClick()
        composeTestRule.waitForIdle()
        assertEquals("LocalAIProvider", settingsViewModel.aiProviderFlow.value)
        assertTrue(
            composeTestRule.onNodeWithTag("settings_ai_provider_on_device")
                .fetchSemanticsNode().config[SemanticsProperties.Selected]
        )
    }

    @Test
    fun experimentalModelSizeProfilesExposeAndUpdateSelectedState() {
        val settingsViewModel = createSettingsViewModel()
        settingsViewModel.updateAiProvider("LlamaCppAIProvider")
        settingsViewModel.updateGgufModelSize("2B")
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme { SettingsScreen(viewModel = settingsViewModel) }
            }
        }

        val twoB = composeTestRule.onNodeWithTag("settings_model_profile_2B").performScrollTo()
        twoB.assert(hasClickAction())
        assertTrue(twoB.fetchSemanticsNode().config[SemanticsProperties.Selected])

        val fourB = composeTestRule.onNodeWithTag("settings_model_profile_4B").performScrollTo()
        assertFalse(fourB.fetchSemanticsNode().config[SemanticsProperties.Selected])
        fourB.performClick()
        composeTestRule.waitForIdle()

        assertEquals("4B", settingsViewModel.ggufModelSizeFlow.value)
        assertTrue(
            composeTestRule.onNodeWithTag("settings_model_profile_4B")
                .fetchSemanticsNode().config[SemanticsProperties.Selected]
        )
        assertFalse(
            composeTestRule.onNodeWithTag("settings_model_profile_2B")
                .fetchSemanticsNode().config[SemanticsProperties.Selected]
        )
    }

    @Test
    fun sectionShortcutsFollowTheSettingsContentOrderAt130PercentFontScale() {
        showSettingsAtLargeText()

        val shortcutTags = listOf(
            "connection", "library", "playback", "downloadsoffline", "discovery", "appearance", "advanced"
        )
        val positions = shortcutTags.map { section ->
            composeTestRule.onNodeWithTag("settings_section_$section").fetchSemanticsNode().boundsInRoot
        }

        assertTrue(
            "Section shortcuts should follow their content's reading order",
            positions.zipWithNext().all { (first, next) ->
                first.top < next.top || (first.top == next.top && first.left < next.left)
            }
        )

    }

    @Test
    fun listenerPreferenceSectionsFollowPlannedOrderAt130PercentFontScale() {
        val settingsViewModel = createSettingsViewModel()
        lateinit var scrollState: androidx.compose.foundation.ScrollState
        composeTestRule.setContent {
            val density = LocalDensity.current
            val settingsScroll = rememberScrollState()
            scrollState = settingsScroll
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        settingsScrollState = settingsScroll
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val orderMarkers = listOf(
            { composeTestRule.onNodeWithTag("settings_heading_library") },
            { composeTestRule.onNodeWithText("Your music library") },
            { composeTestRule.onNodeWithTag("settings_heading_playback") },
            { composeTestRule.onNodeWithTag("settings_heading_downloads_offline") },
            { composeTestRule.onNodeWithTag("settings_heading_discovery") },
            { composeTestRule.onNodeWithTag("settings_heading_appearance") },
            { composeTestRule.onNodeWithTag("settings_heading_advanced") }
        )
        var previousScroll = -1
        orderMarkers.forEach { marker ->
            marker().performScrollTo()
            composeTestRule.waitForIdle()
            val currentScroll = scrollState.value
            assertTrue("Settings sections should follow their listener-oriented order", currentScroll >= previousScroll)
            previousScroll = currentScroll
        }
    }

    @Test
    fun libraryToolsDisclosureAnnouncesItsButtonAndExpandedState() {
        showSettingsAtLargeText()
        val disclosure = composeTestRule.onNodeWithTag("settings_library_tools_disclosure").performScrollTo()

        assertEquals(Role.Button, disclosure.fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals("Collapsed", disclosure.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        disclosure.performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "Expanded",
            composeTestRule.onNodeWithTag("settings_library_tools_disclosure")
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        composeTestRule.onNodeWithText(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(R.string.settings_library_review_title)
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unpairedConnectionCopyExplainsTheSpotCorePairingPath() {
        val settings = PlexSettingsManager(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        settings.clearCompanionCredential()
        settings.baseUrl = ""
        settings.token = ""
        showSettingsAtLargeText()

        composeTestRule.onNodeWithText(
            "Recommended • Pair with SpotCore to connect your music library."
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_connection_pairing_action")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Scan pairing QR code", substring = false)
            .assertCountEquals(2)
        composeTestRule.onNodeWithText(
            "Pair SpotAmp with SpotCore to choose a library, save its details on this phone, and browse your music here."
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Your saved library includes track details, not audio. Download tracks to listen offline."
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun manualConnectionOptionsCanBeExpandedAt130PercentFontScale() {
        showSettingsAtLargeText()

        composeTestRule.onNodeWithTag("manual_connection_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Hide advanced connection options").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.settings_spotcore_url_supporting)
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun libraryActionsStackWithClearLabelsAt130PercentFontScale() {
        var refreshRequested = false
        var syncRequested = false
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 360.dp)) {
                        SettingsLibraryActions(
                            syncEnabled = true,
                            syncIdle = true,
                            onRefreshServer = { refreshRequested = true },
                            onSyncLibrary = { syncRequested = true }
                        )
                    }
                }
            }
        }

        val refreshButton = composeTestRule.onNodeWithTag("refresh_server_library_button")
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val updateButton = composeTestRule.onNodeWithTag("update_phone_library_button")
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertTrue(
            "Phone-width library actions should stack instead of squeezing their labels",
            updateButton.boundsInRoot.top >= refreshButton.boundsInRoot.bottom
        )
        composeTestRule.onNodeWithText("Refresh SpotCore library", substring = false)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText("Update library on this phone", substring = false)
            .assertIsDisplayed()
            .performClick()
        assertTrue(refreshRequested)
        assertTrue(syncRequested)
    }

    @Test
    fun libraryActionsRemainSideBySideOnWideLayouts() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(Modifier.requiredSize(600.dp, 220.dp)) {
                    SettingsLibraryActions(
                        syncEnabled = true,
                        syncIdle = true,
                        onRefreshServer = {},
                        onSyncLibrary = {}
                    )
                }
            }
        }

        val refreshButton = composeTestRule.onNodeWithTag("refresh_server_library_button")
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val updateButton = composeTestRule.onNodeWithTag("update_phone_library_button")
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertTrue(
            "Wide layouts should keep the two library actions in one row",
            updateButton.boundsInRoot.left >= refreshButton.boundsInRoot.right
        )
    }

    @Test
    fun libraryShortcutBringsItsHeadingIntoViewAt130PercentFontScale() {
        showSettingsAtLargeText()

        composeTestRule.onNodeWithTag("settings_section_library").performClick()
        composeTestRule.waitForIdle()
        assertSectionHeadingAtTop("settings_heading_library")
    }

    @Test
    fun connectionShortcutBringsPairingTaskIntoViewAt130PercentFontScale() {
        showSettingsAtLargeText()

        composeTestRule.onNodeWithTag("settings_section_connection").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("SpotCore connection", substring = false).assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_connection_pairing_action").assertIsDisplayed()
    }

    @Test
    fun playbackShortcutBringsItsHeadingIntoViewAt130PercentFontScale() {
        showSettingsAtLargeText()

        composeTestRule.onNodeWithTag("settings_section_playback").performClick()
        composeTestRule.waitForIdle()
        assertSectionHeadingAtTop("settings_heading_playback")
        composeTestRule.onNodeWithText("Sound settings", substring = false).assertIsDisplayed()
    }

    @Test
    fun discoveryShortcutBringsItsHeadingIntoViewAt130PercentFontScale() {
        showSettingsAtLargeText()

        composeTestRule.onNodeWithTag("settings_section_discovery").performClick()
        composeTestRule.waitForIdle()
        assertSectionHeadingAtTop("settings_heading_discovery")
    }

    @Test
    fun appearanceShortcutBringsItsHeadingIntoViewAfterEverydayPreferencesAt130PercentFontScale() {
        showSettingsAtLargeText()

        composeTestRule.onNodeWithTag("settings_section_appearance").performClick()
        composeTestRule.waitForIdle()

        assertSectionHeadingAtTop("settings_heading_appearance")
    }

    @Test
    fun downloadsShortcutBringsItsHeadingNearTheTopAt130PercentFontScale() {
        showSettingsAtLargeText()

        composeTestRule.onNodeWithTag("settings_section_downloadsoffline").performClick()
        composeTestRule.waitForIdle()

        assertSectionHeadingAtTop("settings_heading_downloads_offline")
    }

    @Test
    fun advancedShortcutBringsItsHeadingNearTheTopAt130PercentFontScale() {
        showSettingsAtLargeText()

        composeTestRule.onNodeWithTag("settings_section_advanced").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_heading_advanced").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_library_tools_disclosure").assertIsDisplayed()
    }

    @Test
    fun playbackPreferencesUseListenerLanguageAt130PercentFontScale() {
        showSettingsAtLargeText()

        composeTestRule.onNodeWithTag("settings_section_playback").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Sound settings").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithText("Advanced Audio Settings").fetchSemanticsNodes().size)
    }

    @Test
    fun diagnosticSummarySeparatesItsLabelAndValueAt130PercentFontScale() {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(width = 320.dp, height = 140.dp)) {
                        DiagnosticRow("Saved library details", "43589 tracks ready to browse", healthy = true)
                    }
                }
            }
        }

        val labelBounds = composeTestRule.onNodeWithText("Saved library details").fetchSemanticsNode().boundsInRoot
        val valueBounds = composeTestRule.onNodeWithText("43589 tracks ready to browse").fetchSemanticsNode().boundsInRoot
        assertTrue("At large text, the value should appear below its label", valueBounds.top >= labelBounds.bottom)
        assertEquals("The stacked value should align with its label", labelBounds.left, valueBounds.left, 0.01f)
    }

    @Test
    fun libraryChoicesAnnounceRadioRoleSelectionAndRemainClickableAtLargeText() {
        var selectedLibrary = ""
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Column(Modifier.requiredSize(width = 320.dp, height = 180.dp)) {
                        SettingsLibraryOption("Jazz", selected = true, onClick = { selectedLibrary = "Jazz" })
                        SettingsLibraryOption("Soul", selected = false, onClick = { selectedLibrary = "Soul" })
                    }
                }
            }
        }

        val jazz = composeTestRule.onNodeWithText("Jazz").fetchSemanticsNode()
        val soul = composeTestRule.onNodeWithText("Soul").fetchSemanticsNode()
        assertEquals(Role.RadioButton, jazz.config[SemanticsProperties.Role])
        assertTrue(jazz.config[SemanticsProperties.Selected])
        assertFalse(soul.config[SemanticsProperties.Selected])

        composeTestRule.onNodeWithText("Soul").performClick()
        assertEquals("Soul", selectedLibrary)
    }

    @Test
    fun savedLibraryExplanationRemainsReadableAt130PercentFontScale() {
        var syncRequests = 0
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Column(Modifier.requiredSize(320.dp, 220.dp).verticalScroll(rememberScrollState())) {
                        SettingsLibraryDetailsCard(
                            syncState = LibrarySyncState.Complete(indexedCount = 15, skippedCount = 0, elapsedMs = 100),
                            cachedCount = 15,
                            onSync = { syncRequests++ }
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Library details on this phone").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "SpotAmp saves track details here for faster browsing and offline search. It doesn’t save audio; download tracks to listen offline."
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("15 tracks ready to browse on this phone").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("sync_index_button").performScrollTo().performClick()
        assertEquals(1, syncRequests)
    }

    @Test
    fun libraryDetailsCountAndSyncActionStackAt130PercentFontScale() {
        var syncRequests = 0
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Column(Modifier.requiredSize(320.dp, 620.dp)) {
                        SettingsLibraryDetailsCard(
                            syncState = LibrarySyncState.Complete(indexedCount = 503, skippedCount = 0, elapsedMs = 100),
                            cachedCount = 503,
                            onSync = { syncRequests++ }
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Tracks ready to browse")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(100.dp)
        val countNode = composeTestRule.onNodeWithText("503 tracks ready to browse on this phone")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(100.dp)
            .fetchSemanticsNode()
        val syncButton = composeTestRule.onNodeWithTag("sync_index_button")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode()

        assertTrue(
            "The large-text sync action should follow the full-width track count",
            syncButton.boundsInRoot.top >= countNode.boundsInRoot.bottom
        )
        composeTestRule.onNodeWithTag("sync_index_button").performClick()
        assertEquals(1, syncRequests)
    }

    @Test
    fun downloadsOfflineShortcutBringsItsHeadingIntoViewAt130PercentFontScale() {
        PlexSettingsManager(androidx.test.core.app.ApplicationProvider.getApplicationContext()).offlineOnly = false
        showSettingsAtLargeText()

        composeTestRule.onNodeWithTag("settings_section_downloadsoffline").performClick()
        composeTestRule.waitForIdle()

        assertSectionHeadingAtTop("settings_heading_downloads_offline")
        composeTestRule.onNodeWithTag("settings_downloaded_only_row").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun downloadedOnlyPreferenceUpdatesItsStateAndGuidance() {
        PlexSettingsManager(androidx.test.core.app.ApplicationProvider.getApplicationContext()).offlineOnly = false
        showSettingsAtLargeText()
        composeTestRule.onNodeWithTag("settings_section_downloadsoffline").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_downloaded_only_row").performScrollTo()
        composeTestRule.onNodeWithTag("settings_downloaded_only_row")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_downloaded_only_row").assertIsOn()
        composeTestRule.onNodeWithText("Only play tracks saved on this phone.").assertIsDisplayed()
    }

    @Test
    fun advancedMetadataReviewUsesSavedLibraryLanguage() {
        showSettingsAtLargeText()

        composeTestRule.onNodeWithTag("settings_section_advanced").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Library review tools").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            "Read-only checks and suggestions; nothing changes in your library"
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "No library details need review on this phone."
        ).performScrollTo().assertIsDisplayed()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("local library cache", substring = true).fetchSemanticsNodes().size
        )
    }

    @Test
    fun advancedLibraryToolsRefreshCollectionSuggestionsAfterSameCountLibraryEdit() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = MusicDatabase.getDatabase(context)
        val suffix = System.nanoTime()
        val ratingKeyPrefix = "settings-suggestion-$suffix-"
        val suggestionName = "QA Ideas $suffix"
        val tracks = (0 until 16).map { index ->
            CachedTrack(
                ratingKey = "$ratingKeyPrefix$index",
                title = "Fixture track $index",
                artist = "Fixture artist",
                album = "Fixture album",
                key = "/fixture/$ratingKeyPrefix$index",
                thumb = "",
                duration = 60_000L,
                collections = suggestionName
            )
        }
        runBlocking { database.musicDao().insertCachedTracks(tracks) }
        val settingsViewModel = createSettingsViewModel()

        try {
            composeTestRule.setContent {
                MyApplicationTheme { SettingsScreen(viewModel = settingsViewModel) }
            }
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                settingsViewModel.cachedCount.value >= tracks.size &&
                    settingsViewModel.cachedLibraryRevision.value > 0
            }

            composeTestRule.onNodeWithTag("settings_section_advanced").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("settings_library_tools_disclosure")
                .performScrollTo()
                .performClick()
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText(suggestionName, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }

            val expectedSuggestion = context.resources.getQuantityString(
                R.plurals.settings_collection_suggestion_track_count,
                tracks.size,
                suggestionName,
                tracks.size
            )
            composeTestRule.onNodeWithText(
                context.getString(R.string.settings_collection_suggestions_title),
                substring = false
            ).performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText(expectedSuggestion, substring = false)
                .performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText(
                context.getString(R.string.settings_collection_suggestions_footer),
                substring = false
            ).performScrollTo().assertIsDisplayed()

            val refreshedSuggestionName = "QA Refreshed $suffix"
            runBlocking {
                database.musicDao().insertCachedTracks(
                    tracks.map { it.copy(collections = refreshedSuggestionName) }
                )
            }
            val previousRevision = settingsViewModel.cachedLibraryRevision.value
            settingsViewModel.loadCachedCount()
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                settingsViewModel.cachedLibraryRevision.value > previousRevision
            }
            val refreshedSuggestion = context.resources.getQuantityString(
                R.plurals.settings_collection_suggestion_track_count,
                tracks.size,
                refreshedSuggestionName,
                tracks.size
            )
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText(refreshedSuggestion, substring = false)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(refreshedSuggestion, substring = false)
                .performScrollTo().assertIsDisplayed()
            assertEquals(
                0,
                composeTestRule.onAllNodesWithText(expectedSuggestion, substring = false)
                    .fetchSemanticsNodes().size
            )
        } finally {
            settingsViewModelStore?.clear()
            settingsViewModelStore = null
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM cached_tracks WHERE ratingKey LIKE ?",
                arrayOf("$ratingKeyPrefix%")
            )
        }
    }

    private fun showSettingsAtLargeText() {
        val settingsViewModel = createSettingsViewModel()
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }

    private fun assertSectionHeadingAtTop(tag: String) {
        val heading = composeTestRule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        val maximumTop = with(composeTestRule.density) { 48.dp.toPx() }
        assertTrue(
            "$tag should be aligned near the top of the viewport, was at ${heading.boundsInRoot}",
            heading.boundsInRoot.top <= maximumTop
        )
    }

    private fun createSettingsViewModel(): MusicViewModel {
        val application = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val isolatedStore = ViewModelStore()
        settingsViewModelStore = isolatedStore
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = isolatedStore
        }
        val viewModel = ViewModelProvider(
            owner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MusicViewModel::class.java]
        assertTrue(viewModel.saveCompanionBackend("", ""))
        viewModel.saveCredentials("", "")
        assertFalse(viewModel.companionConfigured.value)
        assertEquals("", viewModel.selectedSectionId.value)
        return viewModel
    }
}
