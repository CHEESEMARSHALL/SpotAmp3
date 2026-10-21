package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.RadioType
import com.example.data.RecommendedStation
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PremiumStationTileLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stationTileSubtitleWrapsAtLargeTextOnPhoneWidth() {
        val artistStation = RecommendedStation(
            id = "artist",
            title = "Artist Mix",
            subtitle = "",
            type = RadioType.ARTIST_RADIO,
            gradientColors = listOf(0xFF6366F1)
        )
        val albumStation = RecommendedStation(
            id = "album",
            title = "Album Mix",
            subtitle = "",
            type = RadioType.ALBUM_RADIO,
            gradientColors = listOf(0xFFEC4899)
        )

        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 1.3f)
            ) {
                MyApplicationTheme {
                    Row(
                        modifier = Modifier.requiredWidth(320.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PremiumStationTile(artistStation, onClick = {}, modifier = Modifier.weight(1f))
                        PremiumStationTile(albumStation, onClick = {}, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        composeRule.onNodeWithText("Artist Mix").assertIsDisplayed()
        composeRule.onNodeWithText("Choose an artist")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(30.dp)
        composeRule.onNodeWithText("Explore albums").assertIsDisplayed()
    }
}
