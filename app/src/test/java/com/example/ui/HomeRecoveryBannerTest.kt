package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.FailurePresentation
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
@Config(sdk = [36])
class HomeRecoveryBannerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun recoveryActionsStayClearAndUsableAtLargeTextInCompactWidth() {
        var retryRequests = 0
        var settingsRequests = 0
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 420.dp)) {
                        HomeRecoveryBanners(
                            homeFeedFailure = FailurePresentation(
                                message = "SpotCore is temporarily unavailable. Try again in a moment.",
                                technicalDetails = "HTTP 502 from https://music.example/home?token=<redacted>"
                            ),
                            actionErrorMessage = null,
                            onNavigateToSettings = { settingsRequests++ },
                            onRetryHomeFeed = { retryRequests++ },
                            onDismissActionError = {}
                        )
                    }
                }
            }
        }

        val message = "SpotCore is temporarily unavailable. Try again in a moment."
        val messageNode = composeTestRule.onNodeWithText(message).assertIsDisplayed()
        assertEquals(LiveRegionMode.Polite, messageNode.fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        assertEquals(message, messageNode.fetchSemanticsNode().config[SemanticsProperties.Error])
        composeTestRule.onNodeWithText("Your library couldn’t refresh").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_retry_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("home_settings_button").assertIsDisplayed().performClick()

        assertEquals(1, retryRequests)
        assertEquals(1, settingsRequests)
    }

    @Test
    fun actionErrorIsDismissibleWithoutHomeFeedRetryOrConnectionSettingsActions() {
        var dismissRequests = 0
        var retryRequests = 0
        var settingsRequests = 0
        val message = "The selected station found no matching tracks."
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 420.dp)) {
                        HomeRecoveryBanners(
                            homeFeedFailure = null,
                            actionErrorMessage = message,
                            onRetryHomeFeed = { retryRequests++ },
                            onNavigateToSettings = { settingsRequests++ },
                            onDismissActionError = { dismissRequests++ }
                        )
                    }
                }
            }
        }

        val messageNode = composeTestRule.onNodeWithText(message).assertIsDisplayed()
        assertEquals(LiveRegionMode.Polite, messageNode.fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        assertEquals(message, messageNode.fetchSemanticsNode().config[SemanticsProperties.Error])
        composeTestRule.onNodeWithText("Couldn’t complete that action").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_retry_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_settings_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_action_error_dismiss_button").assertIsDisplayed().performClick()

        assertEquals(1, dismissRequests)
        assertEquals(0, retryRequests)
        assertEquals(0, settingsRequests)
    }
}
