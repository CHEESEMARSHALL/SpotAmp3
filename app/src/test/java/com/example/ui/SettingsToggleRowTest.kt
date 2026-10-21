package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsToggleRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun toggleRowMergesNameDescriptionAndStateIntoOneLargeTextTarget() {
        val enabled = mutableStateOf(false)
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 180.dp)) {
                        SettingsToggleRow(
                            label = "Downloaded only",
                            description = "Only play tracks saved on this phone.",
                            checked = enabled.value,
                            onCheckedChange = { enabled.value = it },
                            tag = "downloaded_only_toggle"
                        )
                    }
                }
            }
        }

        val toggle = composeTestRule.onNodeWithTag("downloaded_only_toggle")
        toggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        toggle.assertTextContains("Downloaded only")
        toggle.assertTextContains("Only play tracks saved on this phone.")
        toggle.assertIsOff().assertHasClickAction().performClick().assertIsOn()
        composeTestRule.waitForIdle()
        assertTrue(enabled.value)
    }
}
