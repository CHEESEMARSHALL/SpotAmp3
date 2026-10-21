package com.example

import com.example.data.QaNetworkPolicy
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QaNetworkPolicyTest {
    @Test
    fun qaBuildAllowsOnlyLoopbackTestOrigins() {
        assertTrue(QaNetworkPolicy.isAllowed("http://127.0.0.1:8765/healthz".toHttpUrl(), qaBuild = true))
        assertTrue(QaNetworkPolicy.isAllowed("http://10.0.2.2:8765/healthz".toHttpUrl(), qaBuild = true))
        assertFalse(QaNetworkPolicy.isAllowed("https://example.invalid/api".toHttpUrl(), qaBuild = true))
        assertTrue(QaNetworkPolicy.isAllowed("https://example.invalid/api".toHttpUrl(), qaBuild = false))
    }
    @Test
    fun qaBuildRejectsLoopbackLookalikeHosts() {
        assertFalse(QaNetworkPolicy.isAllowed("https://localhost.evil.invalid/api".toHttpUrl(), qaBuild = true))
        assertFalse(QaNetworkPolicy.isAllowed("https://127.0.0.1.evil.invalid/api".toHttpUrl(), qaBuild = true))
        assertFalse(QaNetworkPolicy.isAllowed("https://10.0.2.2.evil.invalid/api".toHttpUrl(), qaBuild = true))
    }

    @Test
    fun rejectsAllNamedExternalIntegrationOriginsInQa() {
        listOf(
            "https://plex.example.invalid/library",
            "https://spotcore.example.invalid/api/v1/library",
            "https://audio.example.invalid/stream",
            "https://artwork.example.invalid/cover.jpg",
            "https://downloads.example.invalid/file",
            "https://pairing.example.invalid/claim",
            "https://events.example.invalid/api/v1/events/playback",
            "https://ws.audioscrobbler.com/2.0/",
            "https://generativelanguage.googleapis.com/v1beta/models"
        ).forEach { origin ->
            assertFalse("QA must reject external integration origin $origin", QaNetworkPolicy.isAllowed(origin.toHttpUrl(), qaBuild = true))
        }
    }
    @Test
    fun qrApprovedCompanionTunnelIsAllowedOnlyAtItsExactOrigin() {
        try {
            val tunnel = "https://spotcore.example.invalid:443".toHttpUrl()
            QaNetworkPolicy.allowExplicitCompanionOrigin(tunnel)
            assertTrue(QaNetworkPolicy.isAllowed("https://spotcore.example.invalid/api/v1/library".toHttpUrl(), qaBuild = true))
            assertFalse(QaNetworkPolicy.isAllowed("https://spotcore.example.invalid:8443/api/v1/library".toHttpUrl(), qaBuild = true))
            assertFalse(QaNetworkPolicy.isAllowed("https://other.example.invalid/api".toHttpUrl(), qaBuild = true))
            assertTrue(QaNetworkPolicy.isAllowed("https://spotcore.example.invalid/api/v1/library".toHttpUrl(), qaBuild = false))
        } finally {
            QaNetworkPolicy.clearExplicitOriginsForTesting()
        }
    }
}
