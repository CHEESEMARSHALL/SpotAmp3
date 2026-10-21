package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real pairing permission preflight on-device without requesting camera access. */
@RunWith(AndroidJUnit4::class)
class PairingPermissionPreflightDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun permissionPreflightActionsFitAtNormalTextScale() = assertPermissionPreflight(fontScale = 1f)

    @Test
    fun permissionPreflightRemainsScrollableAndActionableAtLargeTextScale() =
        assertPermissionPreflight(fontScale = 1.3f)

    private fun assertPermissionPreflight(fontScale: Float) {
        var permissionRequests = 0
        var settingsRequests = 0
        var cancellations = 0

        composeRule.setContent {
            MyApplicationTheme {
                val deviceDensity = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                    Box(Modifier.requiredSize(320.dp, 480.dp)) {
                        PairingDialogBody {
                            CameraPermissionPrompt(
                                cameraPermissionDenied = true,
                                onRequestPermission = { permissionRequests++ },
                                onOpenAppSettings = { settingsRequests++ }
                            )
                            OutlinedButton(onClick = { cancellations++ }) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText(
            "SpotCore must be running. Open Add Device on your SpotCore server, then scan its QR code here. Camera access is used only to read that code; decoding happens on this phone."
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Continue to camera").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Open app settings").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().performClick()

        assertEquals(1, permissionRequests)
        assertEquals(1, settingsRequests)
        assertEquals(1, cancellations)
    }
}
