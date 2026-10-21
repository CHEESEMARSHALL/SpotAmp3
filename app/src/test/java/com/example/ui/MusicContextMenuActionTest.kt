package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MusicContextMenuActionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contextMenuOptionExposesItsActionAsAButton() {
        var selected = false
        composeRule.setContent {
            MyApplicationTheme {
                ContextMenuOption(
                    icon = Icons.Rounded.PlayArrow,
                    label = "Play next",
                    onClick = { selected = true }
                )
            }
        }

        composeRule.onNodeWithText("Play next", substring = false)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickAction())
            .performClick()
        assertEquals(true, selected)
    }
}
