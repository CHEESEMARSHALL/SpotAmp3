package com.example.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadedOnlyToggleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeTextToggleHasANameRoleCurrentStateAndWorkingAction() {
        val checked = mutableStateOf(false)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    DownloadedOnlyToggle(
                        checked = checked.value,
                        onCheckedChange = { checked.value = it },
                        modifier = androidx.compose.ui.Modifier.testTag("downloaded_only_toggle")
                    )
                }
            }
        }

        val toggle = composeRule.onNodeWithTag("downloaded_only_toggle")
            .assertIsDisplayed()
            .assert(hasClickAction())
        assertEquals(
            context.getString(R.string.search_downloaded_only_accessibility),
            toggle.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        )
        assertEquals(Role.Switch, toggle.fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals(
            context.getString(R.string.search_downloaded_only_state_off),
            toggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        assertEquals(ToggleableState.Off, toggle.fetchSemanticsNode().config[SemanticsProperties.ToggleableState])

        toggle.performClick()
        composeRule.waitForIdle()
        assertEquals(true, checked.value)
        assertEquals(
            context.getString(R.string.search_downloaded_only_state_on),
            toggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        assertEquals(ToggleableState.On, toggle.fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
    }

    @Test
    fun compactLargeTextSearchHeaderKeepsSwitchTargetAndCopyVisible() {
        val downloadedOnly = mutableStateOf(false)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                Box(Modifier.requiredSize(320.dp, 200.dp).padding(horizontal = 16.dp)) {
                    MyApplicationTheme {
                        SearchHeader(
                            query = "ambient",
                            downloadedOnly = downloadedOnly.value,
                            onDownloadedOnlyChange = { downloadedOnly.value = it }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("search_downloaded_only_toggle")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("search_header").assertIsDisplayed()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithText(context.getString(R.string.search_results_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.downloaded_only_filter)).assertIsDisplayed()
        composeRule.onNodeWithTag("search_downloaded_only_toggle").performClick()
        assertEquals(true, downloadedOnly.value)
    }

    @Test
    fun whitespaceOnlyQueryKeepsTheSearchHeaderInItsEmptyState() {
        composeRule.setContent {
            MyApplicationTheme {
                SearchHeader(query = "   ", downloadedOnly = false, onDownloadedOnlyChange = {})
            }
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithText(context.getString(R.string.search_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.search_results_title)).assertDoesNotExist()
    }
}
