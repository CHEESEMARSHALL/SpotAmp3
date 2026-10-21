package com.example.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class SettingsLibraryActionsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun serverRefreshShowsProgressAndCannotBeTriggeredAgainUntilFinished() {
        val refreshInProgress = mutableStateOf(true)
        val syncEnabled = mutableStateOf(true)
        var refreshRequests = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsLibraryActions(
                    syncEnabled = syncEnabled.value,
                    syncIdle = true,
                    serverRefreshInProgress = refreshInProgress.value,
                    onRefreshServer = { refreshRequests++ },
                    onSyncLibrary = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("refresh_server_library_button")
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag("update_phone_library_button")
            .assertIsNotEnabled()
        composeTestRule.onNodeWithText("Refreshing SpotCore library…").assertIsDisplayed()

        refreshInProgress.value = false
        syncEnabled.value = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("refresh_server_library_button")
            .assertIsNotEnabled()
        composeTestRule.onNodeWithText("Refresh SpotCore library").assertIsDisplayed()

        syncEnabled.value = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("refresh_server_library_button")
            .assertIsEnabled()
            .performClick()

        assertEquals(1, refreshRequests)
    }
}
