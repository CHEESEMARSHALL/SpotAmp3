package com.example.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
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
class LibraryPickerStatusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loadingAndLibraryEmptyStatesAreDifferent() {
        val status = mutableStateOf(LibraryPickerStatus.LOADING)
        composeTestRule.setContent {
            MyApplicationTheme {
                LibraryPickerStatusView(
                    status = status.value,
                    content = LibraryPickerContent.ARTISTS,
                    onRetry = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Loading artists from your saved library…").assertIsDisplayed()

        composeTestRule.runOnIdle { status.value = LibraryPickerStatus.LIBRARY_EMPTY }
        composeTestRule.onNodeWithText(
            "No artists are available in your saved library yet. Update library details from SpotCore and try again."
        ).assertIsDisplayed()
    }

    @Test
    fun loadFailureOffersWorkingRetryAndFilterEmptyHasSeparateCopy() {
        var retries = 0
        val status = mutableStateOf(LibraryPickerStatus.LOAD_FAILED)
        composeTestRule.setContent {
            MyApplicationTheme {
                LibraryPickerStatusView(
                    status = status.value,
                    content = LibraryPickerContent.ALBUMS,
                    onRetry = { retries++ }
                )
            }
        }
        val failureMessage = "Your saved library couldn’t be loaded. Try again."
        val failureNode = composeTestRule.onNodeWithText(failureMessage).assertIsDisplayed()
        assertEquals(LiveRegionMode.Polite, failureNode.fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        assertEquals(failureMessage, failureNode.fetchSemanticsNode().config[SemanticsProperties.Error])
        composeTestRule.onNodeWithText("Try again").assertIsDisplayed().performClick()
        assertEquals(1, retries)

        composeTestRule.runOnIdle { status.value = LibraryPickerStatus.NO_FILTER_RESULTS }
        composeTestRule.onNodeWithText("No albums match your search.").assertIsDisplayed()
    }
}
