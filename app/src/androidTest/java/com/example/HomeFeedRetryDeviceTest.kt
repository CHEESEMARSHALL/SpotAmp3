package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.data.BackendClientManager
import com.example.data.BackendHomeFeedResponse
import com.example.data.PlexSettingsManager
import com.example.playback.PlaybackManager
import com.example.ui.HomeScreen
import com.example.ui.MusicViewModel
import com.example.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class HomeFeedRetryDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }
    private var viewModelStore: ViewModelStore? = null
    private var homeFeedServer: LoopbackHomeFeedServer? = null
    private var previousCompanionUrl = ""
    private var previousCompanionToken = ""
    private var previousDeviceId = ""
    private var previousSectionId = ""
    private var previousLibraryName = ""
    private var previousOfflineOnly = false

    @Before
    fun setUp() {
        val settings = PlexSettingsManager(context)
        previousCompanionUrl = settings.companionBackendUrl
        previousCompanionToken = settings.companionBackendToken
        previousDeviceId = settings.companionDeviceId
        previousSectionId = settings.sectionId
        previousLibraryName = settings.libraryName
        previousOfflineOnly = settings.offlineOnly
        runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
    }

    @After
    fun tearDown() {
        viewModelStore?.clear()
        viewModelStore = null
        homeFeedServer?.close()
        homeFeedServer = null
        PlexSettingsManager(context).apply {
            clearCompanionCredential()
            sectionId = previousSectionId
            libraryName = previousLibraryName
            if (previousCompanionUrl.isNotBlank() && previousCompanionToken.isNotBlank()) {
                saveCompanionCredential(previousCompanionUrl, previousDeviceId, previousCompanionToken)
            }
            offlineOnly = previousOfflineOnly
        }
        PlaybackManager.getInstance(context).release()
        composeRule.waitForIdle()
    }

    @Test
    fun productionHomeRetryRecoversAfterRealHttp503OnDevice() {
        val server = LoopbackHomeFeedServer()
        homeFeedServer = server
        val viewModel = createViewModel { backendUrl, authorization ->
            BackendClientManager.getApiService(backendUrl).getHomeFeed(authorization)
        }
        assertTrue(
            viewModel.saveCompanionBackend(server.baseUrl, "qa-device-token", "qa-device")
        )

        composeRule.setContent {
            MyApplicationTheme {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onStartPairing = {},
                    onNavigateToArtist = { _, _ -> },
                    onNavigateToAlbum = { _, _ -> },
                    onNavigateToCustomPlaylist = { _, _, _, _, _, _ -> }
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 20_000) {
            server.feedResponses.isNotEmpty() && viewModel.homeFeedFailure.value != null
        }
        assertEquals("The real device request should first receive HTTP 503", 503, server.feedResponses.first())
        assertTrue(
            viewModel.companionConnectionStatus.value in setOf(
                com.example.data.CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE,
                com.example.data.CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE
            )
        )

        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag("home_retry_button").fetchSemanticsNodes().isNotEmpty()
        }
        server.holdRecoveryUntilRetry()
        composeRule.onNodeWithTag("home_retry_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            viewModel.companionReachability.value == com.example.data.CompanionReachability.CHECKING
        }
        server.allowRecovery()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            server.feedResponses.any { it == 200 } &&
                viewModel.companionConnectionStatus.value == com.example.data.CompanionConnectionStatus.CONNECTED &&
                viewModel.homeFeedFailure.value == null &&
                viewModel.connectionErrorMessage.value == null
        }

        assertTrue("The device retry must make a second Home request", server.feedResponses.size >= 2)
        assertTrue("The production client must send the explicit bearer credential", server.authorizationValid.get())
        assertTrue("The device must perform a connection probe", server.reachabilityProbes.get() > 0)
        composeRule.onAllNodesWithTag("home_retry_button").assertCountEquals(0)
    }

    private fun createViewModel(
        fetchHomeFeed: suspend (String, String) -> BackendHomeFeedResponse
    ): MusicViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = ViewModelStore()
        viewModelStore = store
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(MusicViewModel::class.java))
                return MusicViewModel(application, fetchHomeFeed) as T
            }
        }
        return ViewModelProvider(owner, factory)[MusicViewModel::class.java]
    }

    private class LoopbackHomeFeedServer : Closeable {
        private val serverSocket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val running = AtomicBoolean(true)
        private val recoveryAllowed = AtomicBoolean(false)
        private val recoveryGateEnabled = AtomicBoolean(false)
        private val recoveryReleased = CountDownLatch(1)
        private val feedAttempts = AtomicInteger()
        val feedResponses = CopyOnWriteArrayList<Int>()
        val reachabilityProbes = AtomicInteger()
        val authorizationValid = AtomicBoolean(true)
        val baseUrl = "http://127.0.0.1:${serverSocket.localPort}"
        private val acceptThread = Thread({
            while (running.get()) {
                val client = try {
                    serverSocket.accept()
                } catch (_: IOException) {
                    continue
                }
                runCatching { client.use(::respond) }
            }
        }, "spotamp-home-retry-device-server").apply {
            isDaemon = true
            start()
        }

        fun holdRecoveryUntilRetry() {
            recoveryGateEnabled.set(true)
        }

        fun allowRecovery() {
            recoveryAllowed.set(true)
            recoveryReleased.countDown()
        }

        private fun respond(client: Socket) {
            client.soTimeout = 5_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine().orEmpty()
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(":")
                if (separator > 0) {
                    headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
                }
            }
            val path = requestLine.split(" ").getOrNull(1)?.substringBefore("?")
            if (path == "/api/v1/music/home" && headers["authorization"] != "Bearer qa-device-token") {
                authorizationValid.set(false)
            }
            val reachabilityCheck = headers["x-spotamp-reachability-check"] == "true"
            val status = when {
                path != "/api/v1/music/home" -> 404
                reachabilityCheck -> {
                    reachabilityProbes.incrementAndGet()
                    200
                }
                else -> {
                    val attempt = feedAttempts.incrementAndGet()
                    if (attempt > 1 && recoveryGateEnabled.get()) {
                        recoveryReleased.await(20, TimeUnit.SECONDS)
                    }
                    val responseStatus = if (attempt == 1 || !recoveryAllowed.get()) 503 else 200
                    feedResponses += responseStatus
                    responseStatus
                }
            }
            val body = if (status == 503) """{"error":"temporarily unavailable"}""" else "{}"
            val payload = body.toByteArray(StandardCharsets.UTF_8)
            val statusText = if (status == 200) "OK" else if (status == 503) "Service Unavailable" else "Not Found"
            val output = BufferedOutputStream(client.getOutputStream())
            output.write("HTTP/1.1 $status $statusText\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Type: application/json; charset=utf-8\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Length: ${payload.size}\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write(payload)
            output.flush()
        }

        override fun close() {
            recoveryReleased.countDown()
            running.set(false)
            runCatching { serverSocket.close() }
            runCatching { acceptThread.join(1_000) }
        }
    }
}
