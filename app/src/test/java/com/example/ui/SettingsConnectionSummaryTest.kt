package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.CompanionConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsConnectionSummaryTest {
    @Test
    fun configuredLibraryStatusUsesProductLanguageInsteadOfProviderName() {
        assertFalse("Plex" in resourceText(R.string.connection_library_configured))
        val loading = resourceText(R.string.settings_loading_library, "SpotCore")
        val failed = resourceText(R.string.settings_library_load_failed, "SpotCore")
        val unavailable = resourceText(R.string.settings_no_music_libraries, "SpotCore")

        listOf(loading, failed, unavailable).forEach { copy ->
            assertFalse("Provider name leaked into: $copy", "Plex" in copy)
            assertFalse("Legacy product term leaked into: $copy", "music server" in copy.lowercase())
        }
        assertEquals("Loading your SpotCore library…", loading)
        assertEquals(
            "Your SpotCore library couldn’t be loaded. Check the connection and try again.",
            failed
        )
        assertEquals(
            "No music libraries are available from SpotCore yet. Refresh and try again.",
            unavailable
        )
    }

    @Test
    fun manualConnectionValidationExplainsWhenToUseHttpsOrLocalHttp() {
        val httpsGuidance = resourceText(R.string.settings_spotcore_url_https)
        val localHttpGuidance = resourceText(R.string.settings_spotcore_url_private_http)

        assertEquals(
            "To connect from outside your home network, enter the HTTPS address shown in SpotCore.",
            httpsGuidance
        )
        assertEquals(
            "That address uses HTTP, which works only on your home network. Use SpotCore’s HTTPS address when connecting from outside.",
            localHttpGuidance
        )
        assertFalse(httpsGuidance.contains("endpoint", ignoreCase = true))
        assertFalse(localHttpGuidance.contains("private network", ignoreCase = true))
    }

    private fun resourceText(resourceId: Int, vararg formatArgs: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(resourceId, *formatArgs)

    @Test
    fun connectionCardSummaryMatchesObservedConnectionState() {
        assertEquals(
            R.string.settings_spotcore_pairing_summary,
            spotcoreConnectionSummaryString(false, CompanionConnectionStatus.NOT_PAIRED)
        )
        assertEquals(
            R.string.settings_spotcore_checking_summary,
            spotcoreConnectionSummaryString(true, CompanionConnectionStatus.CHECKING)
        )
        assertEquals(
            R.string.settings_spotcore_connected_summary,
            spotcoreConnectionSummaryString(true, CompanionConnectionStatus.CONNECTED)
        )
        assertEquals(
            R.string.settings_spotcore_unavailable_summary,
            spotcoreConnectionSummaryString(true, CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE)
        )
        assertEquals(
            R.string.settings_spotcore_offline_summary,
            spotcoreConnectionSummaryString(true, CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE)
        )
    }
}
