package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ui.HomeActionErrorBanner
import com.example.ui.HomeErrorBanner
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the production Home recovery action on a connected Android device without changing app settings. */
@RunWith(AndroidJUnit4::class)
class HomeRecoveryBannerDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recoveryActionsMeetMinimumAtNormalTextScale() = assertRecoveryActions(fontScale = 1f)

    @Test
    fun recoveryActionsMeetMinimumAtLargeTextScaleAndRetryClearsTheBanner() =
        assertRecoveryActions(fontScale = 1.3f)

    @Test
    fun actionErrorDismissMeetsMinimumAtNormalTextScale() = assertActionErrorDismiss(fontScale = 1f)

    @Test
    fun actionErrorDismissMeetsMinimumAtLargeTextScale() = assertActionErrorDismiss(fontScale = 1.3f)

    private fun assertRecoveryActions(fontScale: Float) {
        var retryRequests = 0
        var settingsRequests = 0
        val unavailable = mutableStateOf(true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = context.getString(R.string.product_failure_server_unavailable)

        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = fontScale)
            ) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 420.dp)) {
                        if (unavailable.value) {
                            HomeErrorBanner(
                                message = message,
                                technicalDetails = "Temporary connection failure",
                                onNavigateToSettings = { settingsRequests++ },
                                onRetry = {
                                    retryRequests++
                                    unavailable.value = false
                                }
                            )
                        } else {
                            Text("Recovery completed", modifier = Modifier.testTag("home_recovery_complete"))
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.home_error_title)).assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
        listOf(
            "home_error_details_toggle",
            "home_retry_button",
            "home_settings_button"
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .assert(hasClickAction())
        }

        composeRule.onNodeWithTag("home_error_details_toggle").performClick()
        composeRule.onNodeWithText("Temporary connection failure").assertIsDisplayed()
        composeRule.onNodeWithTag("home_settings_button").performClick()
        assertEquals("Connection settings should invoke its action once", 1, settingsRequests)
        composeRule.onNodeWithTag("home_retry_button").performClick()
        composeRule.waitForIdle()

        assertEquals("Retry should invoke the Home recovery action once", 1, retryRequests)
        composeRule.onAllNodesWithTag("home_retry_button").assertCountEquals(0)
        composeRule.onNodeWithTag("home_recovery_complete").assertIsDisplayed()
    }

    private fun assertActionErrorDismiss(fontScale: Float) {
        var dismissRequests = 0
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = fontScale)
            ) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 260.dp)) {
                        HomeActionErrorBanner(
                            message = "Couldn’t save that change.",
                            onDismiss = { dismissRequests++ }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("home_action_error_dismiss_button")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
            .performClick()
        assertEquals("Dismiss should invoke its callback once", 1, dismissRequests)
    }
}
