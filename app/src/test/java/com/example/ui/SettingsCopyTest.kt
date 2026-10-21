package com.example.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsCopyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun screenTitleUsesTheSharedNavigationLabel() {
        composeRule.setContent {
            MyApplicationTheme { SettingsScreenTitle() }
        }

        composeRule.onNodeWithText(context.getString(R.string.nav_settings), substring = false)
            .assertIsDisplayed()
    }

    @Test
    fun appearanceThemeNamesUseResourcesAndKeepStableStoredKeys() {
        composeRule.setContent {
            MyApplicationTheme {
                SettingsAppearanceSection(
                    activeTheme = "Warm Light",
                    onThemeSelected = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.settings_theme_title), substring = false)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_theme_current), substring = false)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_theme_warm_light), substring = false)
            .assertIsDisplayed()
    }

    @Test
    fun unknownThemeNameFallsBackToDefaultLabel() {
        composeRule.setContent {
            MyApplicationTheme {
                SettingsAppearanceSection(
                    activeTheme = "unexpected stored value",
                    onThemeSelected = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.settings_theme_default_dark), substring = false)
            .assertIsDisplayed()
    }

    @Test
    fun metadataSuggestionShowsTrackTitleAndLocalizedIssueInsteadOfInternalId() {
        val expected = context.getString(
            R.string.settings_metadata_review_item,
            "Blue in Green",
            context.getString(R.string.settings_metadata_issue_missing_artist)
        )
        composeRule.setContent {
            MyApplicationTheme {
                CleanupSuggestionLabel(trackTitle = "Blue in Green", issue = "Missing artist")
            }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun metadataSuggestionUsesSafeFallbacksForUnknownTrackAndIssue() {
        assertEquals(
            R.string.settings_metadata_issue_generic,
            cleanupIssueString("Unexpected internal issue")
        )
        val expected = context.getString(
            R.string.settings_metadata_review_item,
            context.getString(R.string.settings_metadata_review_unknown_track),
            context.getString(R.string.settings_metadata_issue_generic)
        )
        composeRule.setContent {
            MyApplicationTheme { CleanupSuggestionLabel(trackTitle = null, issue = "Unexpected internal issue") }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun advancedLibraryToolsUseListenerLanguageAndExplainTheirLimits() {
        assertEquals("Library details to review", context.getString(R.string.settings_library_review_title))
        assertEquals(
            "Collection suggestions",
            context.getString(R.string.settings_collection_suggestions_title)
        )
        val copy = listOf(
            R.string.settings_library_review_title,
            R.string.settings_library_review_footer,
            R.string.settings_collection_suggestions_title,
            R.string.settings_collection_suggestions_summary,
            R.string.settings_collection_suggestions_footer
        ).map(context::getString)

        copy.forEach { text ->
            assertFalse("Internal metadata terminology leaked into: $text", "metadata" in text.lowercase())
            assertFalse("Cache terminology leaked into: $text", "local library" in text.lowercase())
        }
        assertTrue(context.getString(R.string.settings_collection_suggestions_footer).contains("SpotCore"))
    }

    @Test
    fun collectionSuggestionsRenderResourceBackedSingularAndPluralTrackCounts() {
        val singular = context.resources.getQuantityString(
            R.plurals.settings_collection_suggestion_track_count,
            1,
            "Jazz",
            1
        )
        val plural = context.resources.getQuantityString(
            R.plurals.settings_collection_suggestion_track_count,
            3,
            "Synthwave",
            3
        )
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    CollectionSuggestionLabel("Jazz", 1)
                    CollectionSuggestionLabel("Synthwave", 3)
                }
            }
        }

        composeRule.onNodeWithText(singular).assertIsDisplayed()
        composeRule.onNodeWithText(plural).assertIsDisplayed()
    }

    @Test
    fun connectionExampleAndAdvancedSyncDiagnosticsUseSafeResources() {
        assertEquals(
            "http://192.168.1.50:4182",
            context.getString(R.string.settings_spotcore_url_placeholder)
        )
        assertEquals(
            "Paste the server address shown in SpotCore. Use SpotCore’s HTTPS address when connecting from outside your home network. An HTTP address only works on your home network.",
            context.getString(R.string.settings_spotcore_url_supporting)
        )
        assertEquals("Status", context.getString(R.string.settings_sync_diagnostic_status))
        assertEquals("Total processed", context.getString(R.string.settings_sync_diagnostic_total_processed))
        assertEquals("Items on last page", context.getString(R.string.settings_sync_diagnostic_last_page_items))
        assertEquals("Current page", context.getString(R.string.settings_sync_diagnostic_current_page))
        assertEquals(
            "Last error: Connection timed out",
            context.getString(R.string.settings_sync_diagnostic_last_error, "Connection timed out")
        )
        assertEquals(
            "Detailed log (last 50 events)",
            context.getString(R.string.settings_sync_diagnostic_log_title)
        )
        assertEquals(
            "1 track",
            context.resources.getQuantityString(R.plurals.settings_sync_diagnostic_tracks_processed, 1, 1)
        )
        assertEquals(
            "12 tracks",
            context.resources.getQuantityString(R.plurals.settings_sync_diagnostic_tracks_processed, 12, 12)
        )
    }

    @Test
    fun manualConnectionUsesAccessKeyLanguageConsistently() {
        assertEquals("Access key", context.getString(R.string.settings_device_token))
        assertEquals("Show access key", context.getString(R.string.settings_show_device_token))
        assertEquals("Hide access key", context.getString(R.string.settings_hide_device_token))
        assertEquals(
            "Remove the username and password from the SpotCore address; use the access key field instead.",
            context.getString(R.string.settings_spotcore_url_credentials)
        )
        assertEquals(
            "Enter both the SpotCore address and access key, or clear both fields.",
            context.getString(R.string.settings_spotcore_credentials_incomplete)
        )
    }

    @Test
    fun lastFmSettingsExplainListeningHistoryWithoutScrobbleJargon() {
        assertEquals(
            "Last.fm listening history",
            context.getString(R.string.settings_lastfm_title)
        )
        assertEquals(
            "Send listening history to Last.fm",
            context.getString(R.string.settings_lastfm_scrobble_listens)
        )
        val body = context.getString(R.string.settings_lastfm_body)
        assertTrue(body.contains("Connect SpotAmp"))
        assertTrue(body.contains("Last.fm account"))
        assertFalse(body.contains("scrobbl", ignoreCase = true))
        assertEquals("Connect to Last.fm", context.getString(R.string.settings_lastfm_start_authorization))
        assertEquals("Finish Last.fm connection", context.getString(R.string.settings_lastfm_complete_authorization))

        val startFailure = context.getString(R.string.settings_lastfm_start_failed)
        assertTrue(startFailure.contains("key and secret above"))
        assertFalse(startFailure.contains("API", ignoreCase = true))

        val completeFailure = context.getString(R.string.settings_lastfm_complete_failed)
        assertTrue(completeFailure.contains("Approve SpotAmp in your browser"))
        assertTrue(completeFailure.contains("return here"))
    }

    @Test
    fun lastFmSharingHintExplainsStoredPreferencesWhileMasterIsOff() {
        val lastFmEnabled = mutableStateOf(false)
        val hasConnectedAccount = mutableStateOf(false)
        composeRule.setContent {
            MyApplicationTheme {
                SettingsLastFmSharingHint(
                    lastFmEnabled = lastFmEnabled.value,
                    hasConnectedAccount = hasConnectedAccount.value
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.settings_lastfm_preferences_inactive),
            substring = false
        ).assertIsDisplayed()

        hasConnectedAccount.value = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_lastfm_sharing_paused),
            substring = false
        ).assertIsDisplayed()

        lastFmEnabled.value = true
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("lastfm_sharing_hint").fetchSemanticsNodes().size)
    }

    @Test
    fun lastFmAuthorizationActionsOnlyShowTheNextRelevantAction() {
        val connected = mutableStateOf(false)
        val authorizationPending = mutableStateOf(false)
        composeRule.setContent {
            MyApplicationTheme {
                SettingsLastFmAuthorizationActions(
                    hasConnectedAccount = connected.value,
                    authorizationPending = authorizationPending.value,
                    onSaveCredentials = {},
                    onStartAuthorization = {},
                    onCompleteAuthorization = {},
                    onDisconnect = {}
                )
            }
        }

        val connect = context.getString(R.string.settings_lastfm_start_authorization)
        val finish = context.getString(R.string.settings_lastfm_complete_authorization)
        val disconnect = context.getString(R.string.settings_lastfm_disconnect)
        val save = context.getString(R.string.settings_lastfm_save_credentials)

        composeRule.onNodeWithText(connect, substring = false).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText(finish, substring = false).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText(disconnect, substring = false).fetchSemanticsNodes().size)
        composeRule.onNodeWithText(save, substring = false).assertIsDisplayed()

        authorizationPending.value = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText(finish, substring = false).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText(connect, substring = false).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText(disconnect, substring = false).fetchSemanticsNodes().size)

        connected.value = true
        authorizationPending.value = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText(disconnect, substring = false).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText(connect, substring = false).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText(finish, substring = false).fetchSemanticsNodes().size)
    }

    @Test
    fun automaticLibraryUpdatesUseListenerLanguageAndKeepIntervalSelectionWorking() {
        var selectedInterval: Long? = null
        composeRule.setContent {
            MyApplicationTheme {
                BackgroundLibraryUpdatesCard(
                    intervalHours = 6L,
                    onIntervalSelected = { selectedInterval = it }
                )
            }
        }

        composeRule.onNodeWithText("Automatic library updates").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Check SpotCore for new music while SpotAmp is in the background. Choose how often to check."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Off").assertIsDisplayed()
        composeRule.onNodeWithText("Every hour").assertIsDisplayed()
        composeRule.onNodeWithText("Every 6 hours").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Background Sync").fetchSemanticsNodes().size)

        listOf(0L, 1L, 6L, 12L, 24L).forEach { hours ->
            val option = composeRule.onNodeWithTag("settings_library_update_interval_$hours")
            option.assert(hasClickAction())
            assertEquals(Role.RadioButton, option.fetchSemanticsNode().config[SemanticsProperties.Role])
            assertEquals(hours == 6L, option.fetchSemanticsNode().config[SemanticsProperties.Selected])
        }
        assertEquals(5, composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("settings_library_update_interval_12").performClick()
        assertEquals(12L, selectedInterval)
    }
}
