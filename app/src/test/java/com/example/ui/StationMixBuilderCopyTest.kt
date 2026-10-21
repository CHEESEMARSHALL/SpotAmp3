package com.example.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StationMixBuilderCopyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun instructionsExplainTheSavedLibrarySourceWithoutAnOfflineGuarantee() {
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    StationMixBuilderInstructions(isAlbum = false)
                    StationMixBuilderInstructions(isAlbum = true)
                }
            }
        }

        listOf(
            R.string.station_artist_mix_instructions_title,
            R.string.station_artist_mix_instructions_body,
            R.string.station_album_mix_instructions_title,
            R.string.station_album_mix_instructions_body
        ).map { context.getString(it) }.forEach { copy ->
            composeRule.onNodeWithText(copy).assertIsDisplayed()
        }

        val instructionCopy = listOf(
            context.getString(R.string.station_artist_mix_instructions_body),
            context.getString(R.string.station_album_mix_instructions_body)
        )
        instructionCopy.forEach { copy ->
            assertFalse("premium" in copy.lowercase())
            assertFalse("offline-first" in copy.lowercase())
        }
    }

    @Test
    fun mixCountsAndActionsUseSingularAndPluralForms() {
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    StationMixTrackLimitLabel(count = 1)
                    StationMixTrackLimitLabel(count = 40)
                    StationMixCreateActionLabel(isAlbum = false, selectedCount = 1)
                    StationMixCreateActionLabel(isAlbum = false, selectedCount = 3)
                    StationMixCreateActionLabel(isAlbum = true, selectedCount = 1)
                    StationMixCreateActionLabel(isAlbum = true, selectedCount = 2)
                    StationMixLibraryTrackCountLabel(count = 1)
                    StationMixLibraryTrackCountLabel(count = 5)
                }
            }
        }

        val expectedCopy = listOf(
            context.resources.getQuantityString(R.plurals.station_mix_playlist_length, 1, 1),
            context.resources.getQuantityString(R.plurals.station_mix_playlist_length, 40, 40),
            context.resources.getQuantityString(R.plurals.station_artist_mix_create_action, 1, 1),
            context.resources.getQuantityString(R.plurals.station_artist_mix_create_action, 3, 3),
            context.resources.getQuantityString(R.plurals.station_album_mix_create_action, 1, 1),
            context.resources.getQuantityString(R.plurals.station_album_mix_create_action, 2, 2),
            context.resources.getQuantityString(R.plurals.station_mix_library_track_count, 1, 1),
            context.resources.getQuantityString(R.plurals.station_mix_library_track_count, 5, 5)
        )
        expectedCopy.forEach { copy -> composeRule.onNodeWithText(copy).assertIsDisplayed() }

        assertEquals("Artist Mix", context.getString(R.string.station_artist_mix_playlist_title))
        assertEquals("Album Mix", context.getString(R.string.station_album_mix_playlist_title))
        assertEquals(
            "Selected tracks from Nina Simone, Ella Fitzgerald",
            context.getString(R.string.station_mix_playlist_description, "Nina Simone, Ella Fitzgerald")
        )
    }
}
