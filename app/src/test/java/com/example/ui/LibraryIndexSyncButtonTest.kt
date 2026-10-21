package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.LibrarySyncState
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryIndexSyncButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun idleEmptyIndexOffersStartSync() {
        showButton(LibrarySyncState.Idle, cachedCount = 0)

        composeTestRule.onNodeWithTag("sync_index_button").assertIsEnabled()
        composeTestRule.onNodeWithText("Save library details").assertIsDisplayed()
    }

    @Test
    fun failedEmptyIndexOffersRetry() {
        showButton(
            LibrarySyncState.Failed("Sync failed", "network error", "indexing"),
            cachedCount = 0
        )

        composeTestRule.onNodeWithTag("sync_index_button").assertIsEnabled()
        composeTestRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun completedIndexOffersPhoneIndexUpdate() {
        showButton(
            LibrarySyncState.Complete(indexedCount = 12, skippedCount = 0, elapsedMs = 100),
            cachedCount = 12
        )

        composeTestRule.onNodeWithTag("sync_index_button").assertIsEnabled()
        composeTestRule.onNodeWithText("Update library on this phone").assertIsDisplayed()
    }

    @Test
    fun activeSyncShowsProgressAndDisablesAnotherRequest() {
        showButton(LibrarySyncState.Processing(totalReceived = 4, validCount = 3, skippedCount = 0), cachedCount = 3)

        composeTestRule.onNodeWithTag("sync_index_button").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Updating your music library…").assertIsDisplayed()
    }

    private fun showButton(state: LibrarySyncState, cachedCount: Int) {
        composeTestRule.setContent {
            MyApplicationTheme {
                LibraryIndexSyncButton(syncState = state, cachedCount = cachedCount, onSync = {})
            }
        }
    }
}
