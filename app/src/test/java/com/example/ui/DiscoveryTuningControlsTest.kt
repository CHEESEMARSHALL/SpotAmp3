package com.example.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.DiscoveryTuning
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiscoveryTuningControlsTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun tuningControlsExposeFamiliarityDepthAndRemovableLocalExclusions() {
        val tuning = mutableStateOf(DiscoveryTuning())
        composeRule.setContent {
            MyApplicationTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DiscoveryTuningControls(tuning = tuning.value, onTuningChange = { tuning.value = it })
                }
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.discover_tuning_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("discover_familiarity_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_depth_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_exclude_artist_input").performTextInput("Radiohead")
        composeRule.onNodeWithTag("discover_add_excluded_artist").performClick()
        assertEquals(listOf("Radiohead"), tuning.value.excludedArtists)
        composeRule.onNodeWithText(context.getString(R.string.discover_remove_exclusion, "Radiohead"))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(emptyList<String>(), tuning.value.excludedArtists)
    }
}
