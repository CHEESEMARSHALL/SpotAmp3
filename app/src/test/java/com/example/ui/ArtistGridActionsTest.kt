package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtistGridActionsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun artistActionsAreTruthfullyNamedAndEveryVisibleActionWorks() {
        var playRequests = 0
        var shuffleRequests = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                ArtistGridActions(
                    onPlayFirstArtist = { playRequests++ },
                    onShuffleFirstArtist = { shuffleRequests++ }
                )
            }
        }

        assertEquals(
            0,
            composeTestRule.onAllNodes(hasContentDescription("Sort artists")).fetchSemanticsNodes().size
        )
        val playFirst = composeTestRule.onNode(hasContentDescription("Play first artist"))
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, playFirst.fetchSemanticsNode().config[SemanticsProperties.Role])
        playFirst.performClick()
        val shuffleFirst = composeTestRule.onNode(hasContentDescription("Shuffle first artist"))
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, shuffleFirst.fetchSemanticsNode().config[SemanticsProperties.Role])
        shuffleFirst.performClick()
        assertEquals(1, playRequests)
        assertEquals(1, shuffleRequests)

    }
}
