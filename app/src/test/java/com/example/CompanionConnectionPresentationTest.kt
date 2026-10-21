package com.example

import com.example.data.CompanionConnectionPresentation
import com.example.data.CompanionConnectionStatus
import com.example.data.CompanionReachability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionConnectionPresentationTest {
    @Test
    fun `unpaired state does not infer connection from cached music`() {
        assertEquals(
            CompanionConnectionStatus.NOT_PAIRED,
            CompanionConnectionPresentation.resolve(
                isPaired = false,
                reachability = CompanionReachability.AVAILABLE,
                cachedTrackCount = 500
            )
        )
    }

    @Test
    fun `unknown and active checks remain checking`() {
        listOf(CompanionReachability.UNKNOWN, CompanionReachability.CHECKING).forEach { reachability ->
            assertEquals(
                CompanionConnectionStatus.CHECKING,
                CompanionConnectionPresentation.resolve(true, reachability, cachedTrackCount = 20)
            )
        }
    }

    @Test
    fun `unavailable connection distinguishes offline library from no cache`() {
        assertEquals(
            CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE,
            CompanionConnectionPresentation.resolve(true, CompanionReachability.UNAVAILABLE, cachedTrackCount = 0)
        )
        assertEquals(
            CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE,
            CompanionConnectionPresentation.resolve(true, CompanionReachability.UNAVAILABLE, cachedTrackCount = 1)
        )
    }

    @Test
    fun `available companion is the only healthy connection state`() {
        assertEquals(
            CompanionConnectionStatus.CONNECTED,
            CompanionConnectionPresentation.resolve(true, CompanionReachability.AVAILABLE, cachedTrackCount = 0)
        )
        assertTrue(CompanionConnectionPresentation.isHealthy(CompanionConnectionStatus.CONNECTED))
        assertFalse(CompanionConnectionPresentation.isHealthy(CompanionConnectionStatus.CHECKING))
        assertFalse(CompanionConnectionPresentation.isHealthy(CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE))
    }
}
