package com.example.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.test.core.app.ApplicationProvider
import com.example.R
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
class DownloadsExpandablePlaylistHeaderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playlistHeaderAnnouncesButtonRoleAndExpansionState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expanded = mutableStateOf(false)

        composeTestRule.setContent {
            MyApplicationTheme {
                DownloadsExpandablePlaylistHeader(
                    expanded = expanded.value,
                    onToggle = { expanded.value = !expanded.value },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sunday Mix")
                    TextButton(onClick = {}) { Text("Options") }
                }
            }
        }

        val header = composeTestRule.onNodeWithText("Sunday Mix")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals(
            context.getString(R.string.downloads_collapsed_state),
            header.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        composeTestRule.onNodeWithText("Options")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        header.performClick()
        assertEquals(
            context.getString(R.string.downloads_expanded_state),
            header.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )

        header.performClick()
        assertEquals(
            context.getString(R.string.downloads_collapsed_state),
            header.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
    }
}
