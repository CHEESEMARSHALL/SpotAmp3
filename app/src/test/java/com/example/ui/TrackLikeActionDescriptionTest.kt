package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackLikeActionDescriptionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun likeActionNamesMatchWhetherTrackIsLiked() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    Text(trackLikeActionDescription(isLiked = false))
                    Text(trackLikeActionDescription(isLiked = true))
                }
            }
        }

        composeTestRule.onNodeWithText("Like track", substring = false).assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove from liked tracks", substring = false).assertIsDisplayed()
    }
}
