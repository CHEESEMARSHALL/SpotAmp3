package com.example.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.CompanionConnectionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.ui.theme.MyApplicationTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectionStatusUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeAndSettingsUseOneResourceForEachSharedConnectionState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedLabels = mapOf(
            CompanionConnectionStatus.NOT_PAIRED to "Not paired",
            CompanionConnectionStatus.CHECKING to "Checking SpotCore",
            CompanionConnectionStatus.CONNECTED to "SpotCore available",
            CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE to "SpotCore unavailable",
            CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE to "Offline library available"
        )

        expectedLabels.forEach { (status, label) ->
            assertEquals(label, context.getString(connectionStatusLabelResource(status)))
        }
    }

    @Test
    fun noticesAreLimitedToConfiguredConnectionsThatNeedAttention() {
        assertTrue(
            shouldShowCompanionConnectionNotice(
                companionConfigured = true,
                status = CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE
            )
        )
        assertTrue(
            shouldShowCompanionConnectionNotice(
                companionConfigured = true,
                status = CompanionConnectionStatus.CHECKING
            )
        )
        assertFalse(
            shouldShowCompanionConnectionNotice(
                companionConfigured = false,
                status = CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE
            )
        )
        assertFalse(
            shouldShowCompanionConnectionNotice(
                companionConfigured = true,
                status = CompanionConnectionStatus.CONNECTED
            )
        )
    }

    @Test
    fun nowPlayingOffersConnectionRecoveryOnlyForFailedPlaybackAndAnUnhealthyCompanion() {
        assertTrue(
            shouldShowNowPlayingConnectionRecovery(
                playbackFailed = true,
                companionConfigured = true,
                status = CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE
            )
        )
        assertTrue(
            shouldShowNowPlayingConnectionRecovery(
                playbackFailed = true,
                companionConfigured = true,
                status = CompanionConnectionStatus.CHECKING
            )
        )
        assertFalse(
            shouldShowNowPlayingConnectionRecovery(
                playbackFailed = false,
                companionConfigured = true,
                status = CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE
            )
        )
        assertFalse(
            shouldShowNowPlayingConnectionRecovery(
                playbackFailed = true,
                companionConfigured = true,
                status = CompanionConnectionStatus.CONNECTED
            )
        )
        assertFalse(
            shouldShowNowPlayingConnectionRecovery(
                playbackFailed = true,
                companionConfigured = false,
                status = CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE
            )
        )
    }

    @Test
    fun offlineNoticeUsesSharedCopyAndItsCheckActionRetriesConnection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var retryCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                CompanionConnectionNotice(
                    status = CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE,
                    bodyResource = R.string.connection_offline_body,
                    onCheckConnection = { retryCount++ },
                    testTag = "connection_notice_test"
                )
            }
        }

        composeRule.onNodeWithTag("connection_notice_test").assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.connection_offline_library),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.connection_offline_body),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_check_connection)).performClick()

        assertEquals(1, retryCount)
    }

    @Test
    fun detailRoutesShowSharedOfflineRecoveryAndCheckConnectionAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var retryCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                CompanionDetailConnectionNotice(
                    companionConfigured = true,
                    status = CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE,
                    onCheckConnection = { retryCount++ },
                    testTag = "detail_connection_notice_test"
                )
            }
        }

        composeRule.onNodeWithTag("detail_connection_notice_test").assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.connection_offline_library),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.connection_offline_body),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("detail_connection_notice_test_check_connection")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, retryCount)
    }

    @Test
    fun detailNoticeIsHiddenForUnpairedProfile() {
        composeRule.setContent {
            MyApplicationTheme {
                CompanionDetailConnectionNotice(
                    companionConfigured = false,
                    status = CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE,
                    onCheckConnection = {},
                    testTag = "detail_connection_notice_test"
                )
            }
        }
        composeRule.onNodeWithTag("detail_connection_notice_test").assertDoesNotExist()
    }

    @Test
    fun detailNoticeIsHiddenForHealthyConnection() {
        composeRule.setContent {
            MyApplicationTheme {
                CompanionDetailConnectionNotice(
                    companionConfigured = true,
                    status = CompanionConnectionStatus.CONNECTED,
                    onCheckConnection = {},
                    testTag = "detail_connection_notice_test"
                )
            }
        }
        composeRule.onNodeWithTag("detail_connection_notice_test").assertDoesNotExist()
    }

    @Test
    fun detailLoadFailureOffersAnAccessibleRetryAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var retryCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                DetailLoadFailureNotice(
                    message = "Could not load this album.",
                    isRetrying = false,
                    onRetry = { retryCount++ },
                    testTag = "detail_load_failure_test"
                )
            }
        }

        composeRule.onNodeWithTag("detail_load_failure_test").assertIsDisplayed()
        composeRule.onNodeWithText("Could not load this album.").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_load_failure_test_retry")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.home_retry)).assertIsDisplayed()

        assertEquals(1, retryCount)
    }

    @Test
    fun offlineNoticeAndRecoveryActionRemainVisibleAtLargeTextScale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var retryCount = 0
        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale = 1.3f)) {
                MyApplicationTheme {
                    Column(
                        modifier = Modifier
                            .width(320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        CompanionConnectionNotice(
                            status = CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE,
                            bodyResource = R.string.connection_offline_body,
                            onCheckConnection = { retryCount++ },
                            testTag = "connection_notice_large_text_test"
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("connection_notice_large_text_test").assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.connection_offline_library),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.connection_offline_body),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("connection_notice_large_text_test_check_connection")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
            .performClick()

        assertEquals(1, retryCount)
    }

    @Test
    fun checkingNoticeShowsProgressCopyAndDoesNotOfferAnotherCheck() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var retryCount = 0
        composeRule.setContent {
            MyApplicationTheme {
                CompanionConnectionNotice(
                    status = CompanionConnectionStatus.CHECKING,
                    bodyResource = R.string.connection_checking_body,
                    onCheckConnection = { retryCount++ },
                    testTag = "connection_checking_notice_test"
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.settings_checking_connection))
            .assertIsDisplayed()
            .assertIsNotEnabled()

        assertEquals(0, retryCount)
    }
}
