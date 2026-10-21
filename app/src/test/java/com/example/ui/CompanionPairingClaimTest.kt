package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.data.CompanionPairingPayload
import com.example.data.PlexSettingsManager
import com.example.playback.PlaybackManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.os.Looper
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CompanionPairingClaimTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }
    private var server: HttpServer? = null
    private var viewModelStore: ViewModelStore? = null

    @Before
    fun setUp() {
        runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        PlexSettingsManager(context).apply {
            clearCompanionCredential()
            baseUrl = ""
            token = ""
            sectionId = ""
            libraryName = ""
        }
    }

    @After
    fun tearDown() {
        server?.stop(0)
        viewModelStore?.clear()
        viewModelStore = null
        shadowOf(Looper.getMainLooper()).idle()
        runCatching { WorkManager.getInstance(context).cancelAllWork() }
        PlexSettingsManager(context).apply {
            clearCompanionCredential()
            baseUrl = ""
            token = ""
            sectionId = ""
            libraryName = ""
        }
        PlaybackManager.getInstance(context).release()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun expiredClaimKeepsSetupUnpairedAndFreshClaimSavesCredentialsAndAdvances() {
        val requestCount = AtomicInteger(0)
        val requestBodies = mutableListOf<String>()
        val requestAuthorization = AtomicReference<String?>(null)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/v1/pairing/claim") { exchange: HttpExchange ->
                val requestBody = exchange.requestBody.bufferedReader().use { it.readText() }
                synchronized(requestBodies) { requestBodies += requestBody }
                requestAuthorization.set(exchange.requestHeaders.getFirst("Authorization"))
                val attempt = requestCount.incrementAndGet()
                val (status, response) = if (attempt == 1) {
                    401 to """{"error":"pairing code expired"}"""
                } else {
                    200 to """{"device":{"id":"device-42","name":"Living Room"},"token":"device-token-42"}"""
                }
                val body = response.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        val application = ApplicationProvider.getApplicationContext<Application>()
        val isolatedStore = ViewModelStore()
        viewModelStore = isolatedStore
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = isolatedStore
        }
        val viewModel = ViewModelProvider(
            owner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MusicViewModel::class.java]
        val baseUrl = "http://127.0.0.1:${server!!.address.port}"

        val rejected = CountDownLatch(1)
        val rejectionMessage = AtomicReference<String?>(null)
        viewModel.claimCompanionPairing(
            payload = CompanionPairingPayload(baseUrl, "expired-pairing", "expired-secret"),
            deviceName = "Living Room",
            onFailure = { message -> rejectionMessage.set(message); rejected.countDown() }
        )

        assertTrue("expired claim should return an actionable failure", awaitCallback(rejected))
        assertEquals(
            "This pairing code is no longer valid. Return to SpotCore, create a new code, and scan it again.",
            rejectionMessage.get()
        )
        assertFalse(viewModel.companionConfigured.value)
        assertFalse(PlexSettingsManager(context).isCompanionConfigured)

        val paired = CountDownLatch(1)
        val successCount = AtomicInteger(0)
        val secondFailure = AtomicReference<String?>(null)
        viewModel.claimCompanionPairing(
            payload = CompanionPairingPayload(baseUrl, "fresh-pairing", "fresh-secret"),
            deviceName = "Living Room",
            onSuccess = { successCount.incrementAndGet(); paired.countDown() },
            onFailure = { secondFailure.set(it); paired.countDown() }
        )

        assertTrue("fresh claim should complete", awaitCallback(paired))
        assertEquals(null, secondFailure.get())
        assertEquals(1, successCount.get())
        assertTrue(viewModel.companionConfigured.value)
        assertEquals(baseUrl, PlexSettingsManager(context).companionBackendUrl)
        assertEquals("device-token-42", PlexSettingsManager(context).companionBackendToken)
        assertEquals("device-42", PlexSettingsManager(context).companionDeviceId)
        assertEquals("spotcore", viewModel.selectedSectionId.value)
        assertEquals("SpotCore Library", viewModel.selectedLibraryName.value)
        assertEquals("spotcore", PlexSettingsManager(context).sectionId)
        assertEquals("SpotCore Library", PlexSettingsManager(context).libraryName)
        assertEquals(null, requestAuthorization.get())
        assertEquals(2, requestCount.get())
        synchronized(requestBodies) {
            assertTrue(requestBodies[0].contains("expired-pairing"))
            assertTrue(requestBodies[1].contains("fresh-pairing"))
            assertTrue(requestBodies[1].contains("Living Room"))
        }
    }

    @Test
    fun syncEnqueueFailureIsShownAndClearedWhenRetrySucceeds() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val isolatedStore = ViewModelStore()
        viewModelStore = isolatedStore
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = isolatedStore
        }
        val viewModel = ViewModelProvider(
            owner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MusicViewModel::class.java]
        val enqueueAttempts = AtomicInteger(0)
        viewModel.enqueueLibrarySyncForQa = { section, restart ->
            assertEquals("spotcore", section)
            assertTrue(restart)
            if (enqueueAttempts.incrementAndGet() == 1) throw IllegalStateException("private request detail")
        }

        viewModel.syncLibraryCache()
        assertTrue("enqueue failure should be exposed", awaitCondition { viewModel.libraryLoadError.value != null })
        assertEquals(
            "SpotAmp couldn’t start saving your SpotCore library. Try again.",
            viewModel.libraryLoadError.value
        )
        assertFalse("raw exception detail must not reach the UI", viewModel.libraryLoadError.value.orEmpty().contains("private request detail"))

        viewModel.syncLibraryCache()
        assertTrue(
            "successful retry should clear stale enqueue error",
            awaitCondition { enqueueAttempts.get() == 2 && viewModel.libraryLoadError.value == null }
        )
        assertEquals(2, enqueueAttempts.get())
    }

    private fun awaitCallback(latch: CountDownLatch): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (latch.count > 0 && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return latch.count == 0L
    }

    private fun awaitCondition(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return condition()
    }
}
