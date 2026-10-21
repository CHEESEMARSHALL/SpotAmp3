package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that the debug-only QA Lab can be reached through the real app shell. */
@RunWith(AndroidJUnit4::class)
class QaLabJourneyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun awaitMainShell() {
        composeRule.waitUntil(15_000) {
            runCatching {
                composeRule.onNodeWithText("Settings").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun settingsOpensQaLabAndShowsRuntimeState() {
        awaitMainShell()
        composeRule
            .onNodeWithText("Settings")
            .performClick()
        composeRule.onNodeWithTag("open-qa-lab").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("qa-lab").assertIsDisplayed()
        composeRule.onNodeWithTag("qa-lab-state").assertIsDisplayed()
        composeRule.onNodeWithTag("qa-fixture-100").assertIsDisplayed()
    }

    @Test
    fun fixtureActionsWrapAndRuntimeStateIsReachable() {
        awaitMainShell()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithTag("open-qa-lab").performScrollTo().performClick()

        val largeFixture = composeRule.onNodeWithTag("qa-fixture-100k").fetchSemanticsNode().boundsInRoot
        assertTrue("100K fixture action collapsed into a tall narrow button", largeFixture.width > largeFixture.height)

        composeRule.onNodeWithText("Sync state:", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("qa-lab-state").assertIsDisplayed()
        composeRule.onNodeWithText("com.example.data.LibrarySyncState", substring = true).assertDoesNotExist()
    }

    @Test
    fun returningFromQaLabRestoresSettingsScrollPosition() {
        awaitMainShell()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithTag("settings_section_advanced").performClick()
        composeRule.onNodeWithTag("open-qa-lab").performScrollTo().performClick()
        composeRule.onNodeWithTag("qa-lab").assertIsDisplayed()

        Espresso.pressBack()
        composeRule.waitUntil(15_000) {
            runCatching {
                composeRule.onNodeWithTag("settings_heading_advanced").assertIsDisplayed()
                composeRule.onNodeWithTag("open-qa-lab").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }
}
