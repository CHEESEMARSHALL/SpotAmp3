package com.example.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
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
class LastFmAuthorizationStatusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun failedAuthorizationIsMarkedAsAnAccessibleError() {
        val message = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.settings_lastfm_complete_failed)
        composeTestRule.setContent {
            MyApplicationTheme {
                LastFmAuthorizationStatusText(message = message, isError = true)
            }
        }

        val status = composeTestRule.onNodeWithTag("lastfm_authorization_status")
        status.assertIsDisplayed()
        assertEquals("Error", status.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertEquals(message, status.fetchSemanticsNode().config[SemanticsProperties.Error])
    }
}
