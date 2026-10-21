package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import com.example.ui.theme.MyApplicationTheme
import com.example.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistGenerationErrorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyAndUnmatchedLibraryMessagesUseListenerLanguage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notReady = context.getString(R.string.playlist_generation_library_not_ready)
        val noMatches = context.getString(R.string.playlist_generation_no_matches)

        assertEquals(
            "Your saved library isn’t available on this phone yet. Update your library, then try again.",
            notReady
        )
        assertEquals(
            "No tracks in your music library match that playlist request. Try a different description.",
            noMatches
        )
        listOf(notReady, noMatches).forEach { message ->
            assertFalse("Unexpected implementation detail: $message", "cache" in message.lowercase())
            assertFalse("Unexpected provider reference: $message", "LocalAI" in message || "artificial intelligence" in message.lowercase())
        }
    }

    @Test
    fun safeGenerationFailureIsDisplayedInline() {
        val message = "SpotAmp couldn’t generate this playlist. Check your library and try again."
        composeTestRule.setContent {
            MyApplicationTheme {
                PlaylistGenerationError(message)
            }
        }

        composeTestRule.onNodeWithTag("playlist_generation_error").assertIsDisplayed()
        val failureNode = composeTestRule.onNodeWithText(message).assertIsDisplayed()
        assertEquals(LiveRegionMode.Polite, failureNode.fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        assertEquals(message, failureNode.fetchSemanticsNode().config[SemanticsProperties.Error])
        assertEquals(0, composeTestRule.onAllNodesWithText("HTTP", substring = true).fetchSemanticsNodes().size)
    }
}
