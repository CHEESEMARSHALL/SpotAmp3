package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.R
import com.example.data.FailurePresentation
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeRecoveryAppearanceVisualTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun warmLightHomeRetryBannerAtDefaultTextRemainsReadable() {
        showRecovery(fontScale = 1f, homeFeedFailure = homeFailure(), actionErrorMessage = null)

        composeRule.onNodeWithText(context.getString(R.string.home_error_title)).assertIsDisplayed()
        composeRule.onNodeWithText(homeFailureMessage).assertIsDisplayed()
        composeRule.onNodeWithTag("home_retry_button").assertIsDisplayed()
        composeRule.onNodeWithTag("home_settings_button").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/home-warm-light-recovery-compact-100.png"
        )
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun warmLightHomeRetryBannerAtLargeTextRemainsReadableAndReachable() {
        showRecovery(fontScale = 1.3f, homeFeedFailure = homeFailure(), actionErrorMessage = null)

        composeRule.onNodeWithText(context.getString(R.string.home_error_title)).assertIsDisplayed()
        composeRule.onNodeWithText(homeFailureMessage).assertIsDisplayed()
        composeRule.onNodeWithTag("home_retry_button").assertIsDisplayed()
        composeRule.onNodeWithTag("home_settings_button").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/home-warm-light-recovery-compact-130.png"
        )
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun warmLightHomeActionErrorAtDefaultTextRemainsReadable() {
        showRecovery(fontScale = 1f, homeFeedFailure = null, actionErrorMessage = actionErrorMessage)

        composeRule.onNodeWithText(context.getString(R.string.home_action_error_title)).assertIsDisplayed()
        composeRule.onNodeWithText(actionErrorMessage).assertIsDisplayed()
        composeRule.onNodeWithTag("home_action_error_dismiss_button").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/home-warm-light-action-error-compact-100.png"
        )
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun warmLightHomeActionErrorAtLargeTextRemainsReadableAndReachable() {
        showRecovery(fontScale = 1.3f, homeFeedFailure = null, actionErrorMessage = actionErrorMessage)

        composeRule.onNodeWithText(context.getString(R.string.home_action_error_title)).assertIsDisplayed()
        composeRule.onNodeWithText(actionErrorMessage).assertIsDisplayed()
        composeRule.onNodeWithTag("home_action_error_dismiss_button").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/home-warm-light-action-error-compact-130.png"
        )
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun defaultDarkHomeRetryBannerRemainsReadable() {
        showRecovery(
            fontScale = 1f,
            homeFeedFailure = homeFailure(),
            actionErrorMessage = null,
            activeTheme = "Default Dark"
        )

        composeRule.onNodeWithText(context.getString(R.string.home_error_title)).assertIsDisplayed()
        composeRule.onNodeWithText(homeFailureMessage).assertIsDisplayed()
        composeRule.onNodeWithTag("home_retry_button").assertIsDisplayed()
        composeRule.onNodeWithTag("home_settings_button").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/home-default-dark-recovery-compact-100.png"
        )
    }

    private fun showRecovery(
        fontScale: Float,
        homeFeedFailure: FailurePresentation?,
        actionErrorMessage: String?,
        activeTheme: String = "Warm Light"
    ) {
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, fontScale)) {
                MyApplicationTheme(activeTheme = activeTheme) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        HomeRecoveryBanners(
                            homeFeedFailure = homeFeedFailure,
                            actionErrorMessage = actionErrorMessage,
                            onNavigateToSettings = {},
                            onRetryHomeFeed = {},
                            onDismissActionError = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun homeFailure() = FailurePresentation(
        message = homeFailureMessage,
        technicalDetails = "HTTP 503 from https://music.example/home"
    )

    private companion object {
        const val homeFailureMessage = "SpotCore is temporarily unavailable. Try again in a moment."
        const val actionErrorMessage = "The selected station found no matching tracks."
    }
}
