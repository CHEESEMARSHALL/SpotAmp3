package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchRecoveryTouchTargetDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun retryAndSettingsTargetsRemainUsableAtNormalTextScale() =
        assertRecoveryActions(fontScale = 1f)

    @Test
    fun retryAndSettingsTargetsRemainUsableAtLargeTextScale() =
        assertRecoveryActions(fontScale = 1.3f)

    private fun assertRecoveryActions(fontScale: Float) {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        var retryCount = 0
        var settingsCount = 0

        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                MyApplicationTheme {
                    Box(modifier = Modifier.requiredSize(288.dp, 200.dp)) {
                        SearchUnavailableState(
                            message = context.getString(R.string.search_failure_with_saved_library),
                            onRetry = { retryCount++ },
                            onOpenConnectionSettings = { settingsCount++ }
                        )
                    }
                }
            }
        }

        val retry = composeRule.onNodeWithText(context.getString(R.string.search_retry))
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        val settings = composeRule.onNodeWithText(context.getString(R.string.home_open_settings))
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())

        retry.performClick()
        settings.performClick()
        assertEquals("Retry should invoke one action", 1, retryCount)
        assertEquals("Settings should invoke one action", 1, settingsCount)
    }
}
