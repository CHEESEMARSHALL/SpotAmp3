package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.example.data.CompanionPairingPayload
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompanionPairingDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun cameraPurposeIsExplainedBeforePermissionAction() {
        composeTestRule.setContent {
            MyApplicationTheme {
                CompanionPairingDialog(
                    viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Scan SpotCore Add Device QR").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "SpotCore must be running. Open Add Device on your SpotCore server, then scan its QR code here. Camera access is used only to read that code; decoding happens on this phone."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue to camera").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pairing_dialog_title").assertIsDisplayed()
    }

    @Test
    fun deniedCameraPermissionOffersRetryAndAppSettings() {
        composeTestRule.setContent {
            MyApplicationTheme {
                CameraPermissionPrompt(
                    cameraPermissionDenied = true,
                    onRequestPermission = {},
                    onOpenAppSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Continue to camera").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open app settings").assertIsDisplayed()
    }

    @Test
    fun permanentlyDeniedCameraPermissionRoutesToSettingsInsteadOfOfferingRetry() {
        var permissionRequests = 0
        var settingsRequests = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                CameraPermissionPrompt(
                    cameraPermissionDenied = true,
                    cameraPermissionBlocked = true,
                    onRequestPermission = { permissionRequests++ },
                    onOpenAppSettings = { settingsRequests++ }
                )
            }
        }

        composeTestRule.onNodeWithText(
            "Camera access is turned off for SpotAmp. Open app settings to allow it, then return here to scan your SpotCore QR code."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Open app settings").assertIsDisplayed().performClick()
        org.junit.Assert.assertEquals(
            0,
            composeTestRule.onAllNodesWithText("Continue to camera").fetchSemanticsNodes().size
        )
        org.junit.Assert.assertEquals(0, permissionRequests)
        org.junit.Assert.assertEquals(1, settingsRequests)
    }

    @Test
    fun cameraPermissionIsBlockedOnlyAfterDenialWhenRationaleIsUnavailable() {
        org.junit.Assert.assertTrue(cameraPermissionIsBlocked(false, true, false))
        org.junit.Assert.assertFalse(cameraPermissionIsBlocked(false, true, true))
        org.junit.Assert.assertFalse(cameraPermissionIsBlocked(false, false, false))
        org.junit.Assert.assertFalse(cameraPermissionIsBlocked(true, true, false))
    }

    @Test
    fun permissionStatusRefreshSwitchesToSettingsWhenRationaleChangesAfterRepeatDenial() {
        var shouldShowRationale = true
        val permissionStatusRevision = mutableStateOf(0)
        composeTestRule.setContent {
            MyApplicationTheme {
                val blocked = rememberCameraPermissionBlocked(
                    permissionStatusRevision = permissionStatusRevision.value,
                    hasPermission = false,
                    cameraDeniedBefore = true,
                    shouldShowRationale = shouldShowRationale
                )
                Text(if (blocked) "Settings only" else "Continue to camera")
            }
        }

        composeTestRule.onNodeWithText("Continue to camera").assertIsDisplayed()
        shouldShowRationale = false
        permissionStatusRevision.value++
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Settings only").assertIsDisplayed()
        org.junit.Assert.assertEquals(
            0,
            composeTestRule.onAllNodesWithText("Continue to camera").fetchSemanticsNodes().size
        )
    }

    @Test
    fun deniedCameraRecoveryRemainsReachableAt130PercentTextScale() {
        val denialMessage = "Camera access wasn’t allowed. You can enable it in app settings or cancel and pair later."
        var retryRequests = 0
        var settingsRequests = 0
        var cancelRequests = 0
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 480.dp).verticalScroll(rememberScrollState())) {
                        Column {
                            CameraPermissionPrompt(
                                cameraPermissionDenied = true,
                                onRequestPermission = { retryRequests++ },
                                onOpenAppSettings = { settingsRequests++ }
                            )
                            PairingInlineError(denialMessage)
                            TextButton(onClick = { cancelRequests++ }) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText(
            "SpotCore must be running. Open Add Device on your SpotCore server, then scan its QR code here. Camera access is used only to read that code; decoding happens on this phone."
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue to camera").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Open app settings").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText(denialMessage).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().performClick()

        org.junit.Assert.assertEquals(1, retryRequests)
        org.junit.Assert.assertEquals(1, settingsRequests)
        org.junit.Assert.assertEquals(1, cancelRequests)
    }

    @Test
    fun cameraReadErrorsAreAnnouncedAsPoliteLiveUpdates() {
        val error = "Couldn’t read the QR code. Center SpotCore’s Add Device code in the frame and try again."
        composeTestRule.setContent {
            MyApplicationTheme { PairingInlineError(error) }
        }

        val errorNode = composeTestRule.onNodeWithTag("pairing_error")
        errorNode.assertIsDisplayed()
        errorNode.assert(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite
            )
        )
        org.junit.Assert.assertEquals(
            error,
            errorNode.fetchSemanticsNode().config[SemanticsProperties.Error]
        )
    }

    @Test
    fun pairingConfirmationActionsRemainReachableOnShortLargeTextScreen() {
        var claimRequests = 0
        var scanAgainRequests = 0
        var cancelRequests = 0
        val claiming = mutableStateOf(false)
        val failureMessage = mutableStateOf<String?>(null)
        val payload = CompanionPairingPayload(
            baseUrl = "https://spotcore.example.net:8443",
            pairingId = "pairing-id",
            secret = "one-time-secret"
        )
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 400.dp)) {
                        PairingDialogBody {
                            PairingConfirmationContent(
                                payload = payload,
                                deviceName = "SpotAmp Test device",
                                onDeviceNameChange = {},
                                claiming = claiming.value,
                                onScanAgain = { scanAgainRequests++ },
                                onCancel = { cancelRequests++ },
                                onClaim = {
                                    claimRequests++
                                    claiming.value = true
                                    failureMessage.value = null
                                }
                            )
                            failureMessage.value?.let { PairingInlineError(it) }
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText(
            "Make sure this is your SpotCore server before connecting. A one-time access key is stored securely on this phone."
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "SpotCore address: https://spotcore.example.net:8443"
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Device name").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("SpotAmp Test device").performScrollTo().assertIsDisplayed()
        org.junit.Assert.assertEquals(
            0,
            composeTestRule.onAllNodesWithText("one-time-secret").fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithTag("pairing_scan_again").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("pairing_cancel_confirm").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("pairing_claim").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Connecting SpotAmp to SpotCore…").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("pairing_claim").assertIsNotEnabled()

        failureMessage.value = "Pairing could not be completed. Check SpotCore and scan a fresh pairing code."
        claiming.value = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(
            "Pairing could not be completed. Check SpotCore and scan a fresh pairing code."
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("pairing_claim").assertIsDisplayed()

        org.junit.Assert.assertEquals(1, scanAgainRequests)
        org.junit.Assert.assertEquals(1, cancelRequests)
        org.junit.Assert.assertEquals(1, claimRequests)
    }
}
