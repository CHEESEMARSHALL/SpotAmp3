package com.example.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiscoverSearchControlsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun searchSubmitsTheRetainedQueryAfterTrimmingWhitespace() {
        val query = mutableStateOf("  M83  ")
        var submitted: String? = null
        composeRule.setContent {
            com.example.ui.theme.MyApplicationTheme {
                DiscoverSearchControls(
                    query = query.value,
                    onQueryChange = { query.value = it },
                    onSearch = { submitted = it }
                )
            }
        }

        composeRule.onNodeWithTag("discover_search_input").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_open_search").performClick()
        assertEquals("M83", submitted)
    }
}
