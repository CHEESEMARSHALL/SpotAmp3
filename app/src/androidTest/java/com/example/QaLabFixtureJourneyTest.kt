package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies bounded fixture loading and QA-only playback controls through Compose. */
@RunWith(AndroidJUnit4::class)
class QaLabFixtureJourneyTest {
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
    fun fixtureControlsRemainBoundedAndResettable() {
        awaitMainShell()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithTag("open-qa-lab").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("qa-lab").assertIsDisplayed()
        composeRule.onNodeWithTag("qa-fixture-1k").performClick()
        composeRule.onNodeWithText("Queue items: 1000").assert(hasText("Queue items: 1000"))
        composeRule.onNodeWithTag("qa-fixture-unicode").performClick()
        composeRule.onNodeWithTag("qa-playback-smoke").performClick()
        composeRule.onNodeWithText("Playback smoke complete: 200 deterministic toggles").assert(hasText("Playback smoke complete: 200 deterministic toggles"))
        composeRule.onNodeWithTag("qa-reset").performClick()
        composeRule.onNodeWithText("Queue items: 0").assert(hasText("Queue items: 0"))
    }
}
