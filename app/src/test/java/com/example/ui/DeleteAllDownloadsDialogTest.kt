package com.example.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class DeleteAllDownloadsDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clearTemporaryFilesExplainsDownloadedMusicIsPreserved() {
        var confirmCount = 0
        var dismissCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                ClearTemporaryFilesDialog(
                    onConfirm = { confirmCount++ },
                    onDismiss = { dismissCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText("Clear temporary files?").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "This removes temporary app files that can be recreated. Music downloaded for offline listening won’t be deleted."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear files").performClick()

        assertEquals(1, confirmCount)
        assertEquals(0, dismissCount)
    }

    @Test
    fun cancelLeavesTemporaryFilesUntouched() {
        var confirmCount = 0
        var dismissCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                ClearTemporaryFilesDialog(
                    onConfirm = { confirmCount++ },
                    onDismiss = { dismissCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(0, confirmCount)
        assertEquals(1, dismissCount)
    }

    @Test
    fun confirmInvokesDeleteOnlyAfterExplainingTheEffect() {
        var confirmCount = 0
        var dismissCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                DeleteAllDownloadsDialog(
                    onConfirm = { confirmCount++ },
                    onDismiss = { dismissCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText("Delete all downloads?").assertIsDisplayed()
        composeTestRule.onNodeWithText("This removes downloaded tracks from this phone and cancels pending downloads.", substring = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete downloads").performClick()

        assertEquals(1, confirmCount)
        assertEquals(0, dismissCount)
    }

    @Test
    fun cancelLeavesDownloadsUntouched() {
        var confirmCount = 0
        var dismissCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                DeleteAllDownloadsDialog(
                    onConfirm = { confirmCount++ },
                    onDismiss = { dismissCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(0, confirmCount)
        assertEquals(1, dismissCount)
    }

    @Test
    fun removeOfflineCollectionExplainsCountAndScopeBeforeConfirming() {
        var removedTrackCount = 0
        var dismissCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                RemoveOfflineCollectionDialog(
                    collectionName = "Late Night",
                    trackCount = 3,
                    onConfirm = { removedTrackCount += 3 },
                    onDismiss = { dismissCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText("Remove downloads from this phone?").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Remove 3 downloaded tracks from “Late Night” on this phone? The collection stays in your library; these tracks won’t be saved offline here."
        ).assertIsDisplayed()
        assertEquals(0, removedTrackCount)

        composeTestRule.onNodeWithText("Remove downloads").performClick()

        assertEquals(3, removedTrackCount)
        assertEquals(0, dismissCount)
    }

    @Test
    fun cancelKeepsOfflineCollectionDownloads() {
        var removedTrackCount = 0
        var dismissCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                RemoveOfflineCollectionDialog(
                    collectionName = "Late Night",
                    trackCount = 3,
                    onConfirm = { removedTrackCount += 3 },
                    onDismiss = { dismissCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(0, removedTrackCount)
        assertEquals(1, dismissCount)
    }
}
