package com.example.ui

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommonUiCopyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun missingArtistUsesSharedUnknownArtistCopy() {
        val expected = context.getString(R.string.common_unknown_artist)
        composeRule.setContent {
            MyApplicationTheme { Text(localizedArtistPresentation("  ")) }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun compilationArtistMarkerUsesLocalizedVariousArtistsCopy() {
        val expected = context.getString(R.string.common_various_artists)
        composeRule.setContent {
            MyApplicationTheme { Text(localizedArtistPresentation(" Various Artists ")) }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun realArtistNameIsPreserved() {
        composeRule.setContent {
            MyApplicationTheme { Text(localizedArtistPresentation("Nujabes")) }
        }

        composeRule.onNodeWithText("Nujabes").assertIsDisplayed()
    }

    @Test
    fun missingAlbumUsesSharedUnknownAlbumCopy() {
        val expected = context.getString(R.string.common_unknown_album)
        composeRule.setContent {
            MyApplicationTheme { Text(localizedAlbumPresentation(null)) }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun artistAlbumSubtitleUsesSharedFormattedResourceAndMetadataFallbacks() {
        val expected = context.getString(
            R.string.common_artist_album_subtitle,
            "M83",
            context.getString(R.string.common_unknown_album)
        )
        composeRule.setContent {
            MyApplicationTheme { Text(localizedArtistAlbumSubtitle("M83", null)) }
        }

        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }
}
