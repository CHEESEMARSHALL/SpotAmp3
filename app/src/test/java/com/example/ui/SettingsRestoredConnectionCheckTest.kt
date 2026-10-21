package com.example.ui

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.R
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.data.CompanionConnectionStatus
import com.example.data.PlexSettingsManager
import com.example.data.SyncDiagnosticService
import com.example.playback.PlaybackManager
import com.example.ui.theme.MyApplicationTheme
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsRestoredConnectionCheckTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }
    private var server: HttpServer? = null
    private var viewModelStore: ViewModelStore? = null

    @Before
    fun clearSavedConnection() {
        runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        PlexSettingsManager(context).apply {
            clearCompanionCredential()
            baseUrl = ""
            token = ""
            sectionId = ""
        }
    }

    @After
    fun cleanUp() {
        server?.stop(0)
        runCatching { WorkManager.getInstance(context).cancelAllWork() }
        viewModelStore?.clear()
        viewModelStore = null
        PlexSettingsManager(context).apply {
            clearCompanionCredential()
            baseUrl = ""
            token = ""
            sectionId = ""
        }
        PlaybackManager.getInstance(context).release()
        SyncDiagnosticService.reset()
        composeTestRule.waitForIdle()
    }

    @Test
    fun duplicateServerRefreshIsIgnoredAndFailureReturnsToRetryableState() {
        val scanRequests = AtomicInteger(0)
        val scanStarted = CountDownLatch(1)
        val releaseScan = CountDownLatch(1)
        val responseSent = CountDownLatch(1)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/v1/library/scan") { exchange: HttpExchange ->
                scanRequests.incrementAndGet()
                scanStarted.countDown()
                releaseScan.await(5, TimeUnit.SECONDS)
                val body = "temporarily unavailable".toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(503, body.size.toLong())
                try {
                    exchange.responseBody.use { it.write(body) }
                } finally {
                    exchange.close()
                    responseSent.countDown()
                }
            }
            createContext("/") { exchange: HttpExchange ->
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            start()
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val isolatedStore = ViewModelStore()
        viewModelStore = isolatedStore
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = isolatedStore
        }
        val settingsViewModel = ViewModelProvider(
            owner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MusicViewModel::class.java]
        val savedConnection = settingsViewModel.saveCompanionBackend(
            "http://127.0.0.1:${server!!.address.port}",
            "qa-device-token-${server!!.address.port}",
            "qa-device"
        )
        assertTrue(
            "loopback fixture connection was rejected: ${settingsViewModel.errorMessage.value}",
            savedConnection
        )

        settingsViewModel.triggerServerRescan()
        assertTrue(settingsViewModel.isRefreshingServerLibrary.value)
        settingsViewModel.triggerServerRescan()

        waitForCondition(timeoutMillis = 10_000) { scanStarted.count == 0L }
        assertEquals("only one remote scan should be sent", 1, scanRequests.get())
        assertTrue(settingsViewModel.isRefreshingServerLibrary.value)
        releaseScan.countDown()

        waitForCondition(timeoutMillis = 10_000) { responseSent.count == 0L }
        waitForCondition(timeoutMillis = 10_000) { !settingsViewModel.isRefreshingServerLibrary.value }
        val recoveryMessage = settingsViewModel.connectionErrorMessage.value
        assertNotNull(recoveryMessage)
        assertFalse(recoveryMessage!!.contains("503"))
        assertFalse(settingsViewModel.isLoading.value)
    }

    @Test
    fun reachableServerStaysConnectedWhenLocalIndexEnqueueFailsAfterRefresh() {
        val scanRequests = AtomicInteger(0)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/v1/library/scan") { exchange: HttpExchange ->
                scanRequests.incrementAndGet()
                val body = "{}".toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
                exchange.close()
            }
            createContext("/") { exchange: HttpExchange ->
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            start()
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val isolatedStore = ViewModelStore()
        viewModelStore = isolatedStore
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = isolatedStore
        }
        val settingsViewModel = ViewModelProvider(
            owner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MusicViewModel::class.java]
        assertTrue(
            settingsViewModel.saveCompanionBackend(
                "http://127.0.0.1:${server!!.address.port}",
                "qa-device-token-${server!!.address.port}",
                "qa-device"
            )
        )
        settingsViewModel.enqueueLibrarySyncForQa = { section, restart ->
            assertEquals("spotcore", section)
            assertTrue(restart)
            throw IllegalStateException("enqueue rejected")
        }

        settingsViewModel.triggerServerRescan()
        waitForCondition(timeoutMillis = 10_000) {
            !settingsViewModel.isRefreshingServerLibrary.value &&
                settingsViewModel.libraryLoadError.value == "SpotAmp couldn’t start saving your SpotCore library. Try again."
        }

        assertEquals(1, scanRequests.get())
        assertEquals(CompanionConnectionStatus.CONNECTED, settingsViewModel.companionConnectionStatus.value)
        assertEquals(null, settingsViewModel.connectionErrorMessage.value)
    }

    private fun waitForCondition(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("condition should be met within ${timeoutMillis}ms", condition())
    }

    @Test
    fun settingsEntryChecksReachabilityWhenHomeIsNotCollected() {
        val homeRequests = AtomicInteger(0)
        val healthyReachabilityResponses = AtomicInteger(0)
        val returnHealthy = AtomicBoolean(false)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange: HttpExchange ->
                val isHomeRequest = exchange.requestURI.path == "/api/v1/music/home"
                val isReachabilityRequest = exchange.requestHeaders.getFirst("X-SpotAmp-Reachability-Check") == "true"
                if (isHomeRequest) homeRequests.incrementAndGet()
                val isHealthy = isHomeRequest && returnHealthy.get()
                exchange.responseHeaders.add("Content-Type", "application/json")
                if (isHealthy) {
                    exchange.sendResponseHeaders(204, -1)
                } else {
                    val body = "unavailable".toByteArray(StandardCharsets.UTF_8)
                    exchange.sendResponseHeaders(503, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                if (isHealthy && isReachabilityRequest) healthyReachabilityResponses.incrementAndGet()
                exchange.close()
            }
            start()
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val isolatedStore = ViewModelStore()
        viewModelStore = isolatedStore
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = isolatedStore
        }
        val settingsViewModel = ViewModelProvider(
            owner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MusicViewModel::class.java]
        val savedConnection = settingsViewModel.saveCompanionBackend(
            "http://127.0.0.1:${server!!.address.port}",
            "qa-device-token-${server!!.address.port}",
            "qa-device"
        )
        assertTrue(
            "loopback fixture connection was rejected: ${settingsViewModel.errorMessage.value}",
            savedConnection
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }

        assertTrue(settingsViewModel.companionConfigured.value)
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            homeRequests.get() > 0
        }
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            settingsViewModel.companionConnectionStatus.value in setOf(
                CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE,
                CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE
            )
        }
        assertTrue("Settings should request the companion feed to resolve reachability", homeRequests.get() > 0)
        val statusCopy = when (settingsViewModel.companionConnectionStatus.value) {
            CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE -> "Offline library available"
            else -> "SpotCore unavailable"
        }
        composeTestRule.onNodeWithText(statusCopy, substring = false).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "SpotCore is temporarily unavailable. Try again in a moment.",
            substring = false
        ).performScrollTo().assertIsDisplayed()
        val connectionError = composeTestRule.onNodeWithTag("settings_connection_error").performScrollTo()
        connectionError.assertIsDisplayed()
        assertEquals(
            LiveRegionMode.Polite,
            connectionError.fetchSemanticsNode().config[SemanticsProperties.LiveRegion]
        )
        assertEquals(
            settingsViewModel.connectionErrorMessage.value,
            connectionError.fetchSemanticsNode().config[SemanticsProperties.Error]
        )
        val dismissError = composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.settings_dismiss_connection_error)
        ).performScrollTo()
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, dismissError.fetchSemanticsNode().config[SemanticsProperties.Role])
        val detailsToggle = composeTestRule.onNodeWithTag("settings_connection_error_details_toggle")
            .performScrollTo()
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, detailsToggle.fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals(
            context.getString(R.string.recovery_details_collapsed),
            detailsToggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        composeTestRule.onAllNodesWithTag("settings_connection_error_technical_details").assertCountEquals(0)
        detailsToggle.performClick()
        assertEquals(
            context.getString(R.string.recovery_details_expanded),
            detailsToggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        val technicalDetails = composeTestRule.onNodeWithTag("settings_connection_error_technical_details")
            .performScrollTo()
            .assertIsDisplayed()
            .fetchSemanticsNode().config[SemanticsProperties.Text]
            .joinToString(" ") { it.text }
        assertFalse("support detail must not contain the saved device token", technicalDetails.contains("qa-device-token"))

        dismissError.performClick()
        composeTestRule.waitForIdle()
        assertEquals(null, settingsViewModel.connectionErrorMessage.value)
        composeTestRule.onAllNodesWithTag("settings_connection_error").assertCountEquals(0)

        val manualConnectionToggle = composeTestRule.onNodeWithTag("manual_connection_toggle")
            .performScrollTo()
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, manualConnectionToggle.fetchSemanticsNode().config[SemanticsProperties.Role])
        manualConnectionToggle.performClick()
        composeTestRule.onNodeWithTag("companion_token_input")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.settings_device_token),
            substring = false
        ).performScrollTo().assertIsDisplayed()
        val tokenVisibilityButton = composeTestRule.onNodeWithTag("settings_device_token_visibility")
            .performScrollTo()
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, tokenVisibilityButton.fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals(
            listOf(context.getString(R.string.settings_show_device_token)),
            tokenVisibilityButton.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        )
        tokenVisibilityButton.performClick()
        assertEquals(
            listOf(context.getString(R.string.settings_hide_device_token)),
            tokenVisibilityButton.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        )
        tokenVisibilityButton.performClick()
        assertEquals(
            listOf(context.getString(R.string.settings_show_device_token)),
            tokenVisibilityButton.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        )

        val retryConnectionButton = composeTestRule.onNodeWithTag("settings-retry-connection")
            .performScrollTo()
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(hasClickAction())
        assertEquals(Role.Button, retryConnectionButton.fetchSemanticsNode().config[SemanticsProperties.Role])
        val initialRequests = homeRequests.get()
        val initialHealthyReachabilityResponses = healthyReachabilityResponses.get()
        returnHealthy.set(true)
        composeTestRule.onNodeWithTag("settings-retry-connection").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            healthyReachabilityResponses.get() > initialHealthyReachabilityResponses
        }
        assertTrue("retry should make a new Home request", homeRequests.get() > initialRequests)
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            settingsViewModel.reachabilityProbePhase.value == MusicViewModel.ReachabilityProbePhase.SUCCEEDED
        }
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            settingsViewModel.companionReachability.value == com.example.data.CompanionReachability.AVAILABLE
        }
        composeTestRule.waitUntil(timeoutMillis = 2_000) {
            settingsViewModel.companionConnectionStatus.value == CompanionConnectionStatus.CONNECTED
        }
        assertEquals(null, settingsViewModel.connectionErrorMessage.value)
        composeTestRule.onAllNodesWithTag("settings_connection_error_technical_details").assertCountEquals(0)
    }

    @Test
    fun syncDiagnosticsAreHiddenUntilAccessibleDisclosureIsExpanded() {
        SyncDiagnosticService.reset()
        SyncDiagnosticService.updateStatus("Waiting for retry")
        SyncDiagnosticService.setError("HTTP 503 temporary server failure")
        val application = ApplicationProvider.getApplicationContext<Application>()
        val totalProcessedLabel = application.getString(R.string.settings_sync_diagnostic_total_processed)
        val detailedLogTitle = application.getString(R.string.settings_sync_diagnostic_log_title)
        val isolatedStore = ViewModelStore()
        viewModelStore = isolatedStore
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = isolatedStore
        }
        val settingsViewModel = ViewModelProvider(
            owner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MusicViewModel::class.java]
        composeTestRule.setContent {
            MyApplicationTheme { SettingsScreen(viewModel = settingsViewModel) }
        }

        val disclosure = composeTestRule.onNodeWithTag("settings_sync_diagnostics_disclosure")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(Role.Button, disclosure.fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals("Collapsed", disclosure.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        composeTestRule.onAllNodesWithText(totalProcessedLabel, substring = true).assertCountEquals(0)

        disclosure.performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            "Expanded",
            composeTestRule.onNodeWithTag("settings_sync_diagnostics_disclosure")
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        composeTestRule.onNodeWithText(totalProcessedLabel, substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(detailedLogTitle, substring = true).performScrollTo().assertIsDisplayed()
    }
}
