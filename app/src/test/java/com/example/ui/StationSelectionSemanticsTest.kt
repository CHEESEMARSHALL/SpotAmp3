package com.example.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.SemanticsMatcher
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StationSelectionSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun artistMixChoiceExposesCheckboxStateAndTogglesWhenSelected() {
        val selected = mutableStateOf(false)
        composeRule.setContent {
            MyApplicationTheme {
                ArtistGridItem(
                    artist = ArtistItem("Nina Simone", "", 12),
                    normalizedBaseUrl = "",
                    token = "",
                    isSelected = selected.value,
                    onClick = { selected.value = !selected.value }
                )
            }
        }

        val choice = composeRule.onNodeWithTag("station_artist_selection_Nina Simone")
        choice.assert(hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsNotSelected()
            .performClick()
        composeRule.waitForIdle()

        choice.assertIsSelected()
    }

    @Test
    fun albumMixChoiceExposesCheckboxStateAndTogglesWhenSelected() {
        val selected = mutableStateOf(false)
        composeRule.setContent {
            MyApplicationTheme {
                AlbumGridItem(
                    album = AlbumItem("Little Girl Blue", "Nina Simone", "", 12),
                    normalizedBaseUrl = "",
                    token = "",
                    isSelected = selected.value,
                    onClick = { selected.value = !selected.value }
                )
            }
        }

        val choice = composeRule.onNodeWithTag("station_album_selection_Little Girl Blue")
        choice.assert(hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsNotSelected()
            .performClick()
        composeRule.waitForIdle()

        choice.assertIsSelected()
    }
}
