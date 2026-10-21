package com.example.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PairingAppearanceVisualTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private var previousDenialState: Boolean? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val preferences = context.getSharedPreferences("companion_pairing_permissions", Context.MODE_PRIVATE)
        previousDenialState = if (preferences.contains("camera_denied_before")) {
            preferences.getBoolean("camera_denied_before", false)
        } else {
            null
        }
        preferences.edit().putBoolean("camera_denied_before", false).commit()
    }

    @After
    fun tearDown() {
        val preferences = context.getSharedPreferences("companion_pairing_permissions", Context.MODE_PRIVATE)
        val editor = preferences.edit()
        previousDenialState?.let { editor.putBoolean("camera_denied_before", it) }
            ?: editor.remove("camera_denied_before")
        editor.commit()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun warmLightPairingDialogAtDefaultTextKeepsItsPurposeAndActionsReachable() =
        captureWarmLightPairingDialog(
            fontScale = 1f,
            screenshot = "src/test/screenshots/pairing-warm-light-preflight-compact-100.png"
        )

    @Test
    @Config(qualifiers = "w320dp-h640dp", sdk = [36])
    fun warmLightPairingDialogAtLargeTextKeepsItsPurposeAndActionsReachable() =
        captureWarmLightPairingDialog(
            fontScale = 1.3f,
            screenshot = "src/test/screenshots/pairing-warm-light-preflight-compact-130.png"
        )

    private fun captureWarmLightPairingDialog(fontScale: Float, screenshot: String) {
        composeRule.setContent {
            MyApplicationTheme(activeTheme = "Warm Light") {
                Dialog(onDismissRequest = {}) {
                    val dialogDensity = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(dialogDensity.density, fontScale)
                    ) {
                        Surface(tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                            PairingDialogBody {
                                Text(
                                    context.getString(R.string.pairing_scan_title),
                                    modifier = Modifier.testTag("pairing_dialog_title")
                                )
                                CameraPermissionPrompt(
                                    cameraPermissionDenied = false,
                                    onRequestPermission = {},
                                    onOpenAppSettings = {}
                                )
                                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                                    Text(context.getString(R.string.pairing_cancel))
                                }
                            }
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pairing_dialog_title").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(filePath = screenshot)

        composeRule.onNodeWithText(context.getString(R.string.pairing_camera_help))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.pairing_continue_camera))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.pairing_cancel))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
