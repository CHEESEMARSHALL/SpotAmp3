package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
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
class LibraryDownloadedOnlyFilterChipTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fullFilterLabelAndActionRemainAvailableInCompactLargeTextRow() {
        val selected = mutableStateOf(true)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Row(
                        modifier = Modifier.requiredWidth(320.dp).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        LibraryDownloadedOnlyFilterChip(
                            selected = selected.value,
                            onClick = { selected.value = !selected.value }
                        )
                    }
                }
            }
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithText(context.getString(R.string.downloaded_only_filter)).assertIsDisplayed()
        composeRule.onNodeWithTag("library_downloaded_only_filter")
            .assertIsDisplayed()
            .assert(hasClickAction())
            .performClick()

        assertEquals(false, selected.value)
    }
}
