package com.example

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import com.example.data.MusicDatabase
import com.example.data.PlexSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real unpaired Home-to-camera-explanation route without granting camera access. */
@RunWith(AndroidJUnit4::class)
class HomeFirstRunJourneyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun connectionSettingsRouteHasReachableTargetAndReturnsWithoutEditing() {
        val settings = PlexSettingsManager(composeRule.activity)
        assumeFalse(
            "Home connection-settings route requires an unpaired QA profile",
            settings.isCompanionConfigured
        )
        val originalUrl = settings.companionBackendUrl
        val originalToken = settings.companionBackendToken
        val originalSection = settings.sectionId
        val originalLibraryName = settings.libraryName

        composeRule.onNode(navigationTab("Home")).performClick()
        composeRule.waitUntil(15_000) {
            runCatching {
                composeRule.onNodeWithTag("home_connection_settings_button").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("home_connection_settings_button")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("settings_heading_connection").assertIsDisplayed()

        Espresso.pressBack()
        composeRule.onNodeWithTag("home_connection_settings_button").assertIsDisplayed()
        assertTrue(
            "Opening and closing connection settings must not change saved connection or library settings",
            settings.companionBackendUrl == originalUrl &&
                settings.companionBackendToken == originalToken &&
                settings.sectionId == originalSection &&
                settings.libraryName == originalLibraryName
        )
    }

    @Test
    fun unpairedLibraryPairingControlMeetsMinimumWithoutOpeningScanner() {
        val activity = composeRule.activity
        val settings = PlexSettingsManager(activity)
        assumeFalse(
            "Library pairing control check requires an unpaired QA profile",
            settings.isCompanionConfigured
        )
        assumeTrue(
            "Library pairing control check requires no selected library",
            settings.sectionId.isEmpty()
        )
        val originalCameraPermission =
            ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)

        composeRule.onNode(navigationTab("Library")).performClick()
        composeRule.waitUntil(15_000) {
            runCatching {
                composeRule.onNodeWithTag("library_pair_spotcore_action").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Connect to SpotCore").assertIsDisplayed()
        composeRule.onNodeWithTag("library_pair_spotcore_action")
            .assertHeightIsAtLeast(48.dp)

        // Preserve the granted permission state by leaving the camera CTA untouched.
        composeRule.onNode(navigationTab("Home")).performClick()
        composeRule.onNodeWithTag("home_connection_settings_button").assertIsDisplayed()
        assertEquals(
            "Inspecting the library pairing target must not request camera permission",
            originalCameraPermission,
            ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
        )
    }

    @Test
    fun pairingActionAndStationsAreAvailableOnTheirDestinations() {
        assumeFalse(
            "First-run pairing journey requires an unpaired QA profile",
            PlexSettingsManager(composeRule.activity).isCompanionConfigured
        )
        val cachedTrackCount = runBlocking(Dispatchers.IO) {
            MusicDatabase.getDatabase(composeRule.activity).musicDao().getCachedTracksCount()
        }
        assumeTrue(
            "First-run pairing journey requires an empty QA library",
            cachedTrackCount == 0
        )
        composeRule.onNode(navigationTab("Home")).performClick()
        composeRule.waitUntil(15_000) {
            runCatching {
                composeRule.onNodeWithText("Settings").assertIsDisplayed()
                composeRule.onNodeWithTag("home_pairing_cta").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithText("Stations").assertDoesNotExist()
        composeRule.onNodeWithText("Connect to SpotCore to get started").assertIsDisplayed()
        composeRule.onNodeWithTag("home_connection_settings_button")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        assertEquals(
            "First-run setup instructions should not be repeated in the header and card",
            1,
            composeRule.onAllNodesWithText(
                "Scan the Add Device QR code in SpotCore to connect this phone and save your music library details here."
            ).fetchSemanticsNodes().size
        )
        composeRule.onNodeWithTag("home_pairing_cta").performClick()

        composeRule.onNodeWithTag("pairing_dialog_title").assertIsDisplayed()
        composeRule.onNodeWithText("SpotCore must be running. Open Add Device on your SpotCore server, then scan its QR code here. Camera access is used only to read that code; decoding happens on this phone.").assertIsDisplayed()
        composeRule.onNodeWithText("Continue to camera").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(
            "The preflight route must not request camera permission by itself",
            PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(composeRule.activity, Manifest.permission.CAMERA)
        )

        composeRule.onNode(navigationTab("Discover")).performClick()
        composeRule.onNodeWithTag("discover_feed").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_feed").performScrollToNode(hasText("Stations"))
        composeRule.onNodeWithText("Stations").assertIsDisplayed()
    }

    @Test
    fun unpairedLibraryOffersDirectPairingPreflight() {
        val settings = PlexSettingsManager(composeRule.activity)
        assumeFalse(
            "Library pairing journey requires an unpaired QA profile",
            settings.isCompanionConfigured
        )
        assumeTrue(
            "Library pairing journey requires no selected library",
            settings.sectionId.isEmpty()
        )

        composeRule.onNode(navigationTab("Library")).performClick()
        composeRule.waitUntil(15_000) {
            runCatching {
                composeRule.onNodeWithTag("library_pair_spotcore_action").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Connect to SpotCore").assertIsDisplayed()
        composeRule.onNodeWithText("Pair SpotAmp with SpotCore to choose a music library and browse it here.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("library_pair_spotcore_action")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.onNodeWithTag("pairing_dialog_title").assertIsDisplayed()
        composeRule.onNodeWithText("Continue to camera").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(
            "Opening pairing from Library must not request camera permission until the user continues",
            PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(composeRule.activity, Manifest.permission.CAMERA)
        )
    }

    @Test
    fun unpairedSearchOffersDirectPairingPreflight() {
        val settings = PlexSettingsManager(composeRule.activity)
        assumeFalse(
            "Search pairing journey requires an unpaired QA profile",
            settings.isCompanionConfigured
        )
        val cachedTrackCount = runBlocking(Dispatchers.IO) {
            MusicDatabase.getDatabase(composeRule.activity).musicDao().getCachedTracksCount()
        }
        assumeTrue(
            "Search pairing journey requires no saved library tracks",
            cachedTrackCount == 0
        )

        composeRule.onNode(navigationTab("Discover")).performClick()
        composeRule.onNodeWithTag("discover_open_search").performClick()
        composeRule.waitUntil(15_000) {
            runCatching {
                composeRule.onNodeWithTag("search_pair_spotcore_action").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Connect to SpotCore and save your library details on this phone to search here.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("search_pair_spotcore_action")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.onNodeWithTag("pairing_dialog_title").assertIsDisplayed()
        composeRule.onNodeWithText("Continue to camera").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(
            "Opening pairing from Search must not request camera permission until the user continues",
            PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(composeRule.activity, Manifest.permission.CAMERA)
        )
    }

    @Test
    fun unpairedDownloadsOffersDirectPairingPreflight() {
        val settings = PlexSettingsManager(composeRule.activity)
        assumeFalse(
            "Downloads pairing journey requires an unpaired QA profile",
            settings.isCompanionConfigured
        )
        val savedDownloads = runBlocking(Dispatchers.IO) {
            MusicDatabase.getDatabase(composeRule.activity).musicDao().getDownloadedTracksList()
        }
        assumeTrue(
            "Downloads pairing journey requires no saved downloads",
            savedDownloads.isEmpty()
        )

        composeRule.onNode(navigationTab("Downloads")).performClick()
        composeRule.waitUntil(15_000) {
            runCatching {
                composeRule.onNodeWithTag("downloads_pair_spotcore_action").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Nothing saved for offline listening yet").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Connect SpotAmp to SpotCore and choose a music library before saving tracks for offline listening."
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("downloads_pair_spotcore_action")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.onNodeWithTag("pairing_dialog_title").assertIsDisplayed()
        composeRule.onNodeWithText("Continue to camera").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(
            "Opening pairing from Downloads must not request camera permission until the user continues",
            PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(composeRule.activity, Manifest.permission.CAMERA)
        )
    }

    private fun navigationTab(label: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            (hasText(label) or hasContentDescription(label))
}
