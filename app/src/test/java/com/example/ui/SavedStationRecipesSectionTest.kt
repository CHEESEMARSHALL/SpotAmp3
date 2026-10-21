package com.example.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.RadioType
import com.example.data.SavedStationRecipe
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedStationRecipesSectionTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun emptySavedStationsExplainThatRecipesAreLocal() {
        composeRule.setContent {
            MyApplicationTheme {
                SavedStationRecipesSection(emptyList(), {}, { _, _ -> }, {})
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.discover_saved_stations_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("saved_station_recipes_empty").assertIsDisplayed()
    }

    @Test
    fun stationBuilderRequestValuesAreTheValuesStoredForReplay() {
        val genreRequest = stationRadioRequestForOption(RadioType.GENRE_RADIO, "Synthpop", 35)
        val decadeRequest = stationRadioRequestForOption(RadioType.DECADE_RADIO, "1980s", 20)
        val timeTravelRequest = stationRadioRequestForOption(
            RadioType.TIME_TRAVEL,
            "All Time (Earliest to Present)",
            45
        )

        assertEquals("Synthpop", SavedStationRecipe.fromRequest("Synthpop Radio", genreRequest).genre)
        assertEquals(1980, SavedStationRecipe.fromRequest("Eighties Radio", decadeRequest).decadeStart)
        assertEquals(null, SavedStationRecipe.fromRequest("All Time", timeTravelRequest).decadeStart)
        assertEquals(45, SavedStationRecipe.fromRequest("All Time", timeTravelRequest).trackCount)
    }

    @Test
    fun savedStationCanRestartRenameAndDeleteWithConfirmation() {
        val recipe = SavedStationRecipe(
            id = "saved-station-1",
            name = "Warm Synth Radio",
            radioType = RadioType.GENRE_RADIO.name,
            genre = "Synthpop"
        )
        var playedId: String? = null
        var renamed: Pair<String, String>? = null
        var deletedId: String? = null
        composeRule.setContent {
            MyApplicationTheme {
                SavedStationRecipesSection(
                    recipes = listOf(recipe),
                    onPlay = { playedId = it.id },
                    onRename = { id, name -> renamed = id to name },
                    onDelete = { deletedId = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.discover_restart_station, recipe.name)
        ).performClick()
        assertEquals(recipe.id, playedId)

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.discover_station_options, recipe.name)
        ).performClick()
        composeRule.onNodeWithText(context.getString(R.string.discover_rename_station_action)).performClick()
        composeRule.onNodeWithTag("saved_station_name_input").performTextClearance()
        composeRule.onNodeWithTag("saved_station_name_input").performTextInput("Late Night Radio")
        composeRule.onNodeWithTag("saved_station_rename_confirm").performClick()
        assertEquals(recipe.id to "Late Night Radio", renamed)

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.discover_station_options, recipe.name)
        ).performClick()
        composeRule.onNodeWithText(context.getString(R.string.discover_delete_station_action)).performClick()
        composeRule.onNodeWithTag("saved_station_delete_dialog").assertIsDisplayed()
        assertEquals(null, deletedId)
        composeRule.onNodeWithTag("saved_station_delete_confirm").performClick()
        assertEquals(recipe.id, deletedId)
    }
}
