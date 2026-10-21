package com.example

import com.example.data.companionHeadersForUrl
import com.example.data.isExactCompanionOrigin
import com.example.data.normalizeCompanionUrl
import com.example.data.originCredentialsForUrl
import com.example.data.parseCompanionPairingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionRemoteAccessTest {
    @Test
    fun `public http is rejected while private LAN http remains supported`() {
        assertEquals("http://192.168.1.50:4182", normalizeCompanionUrl("192.168.1.50:4182"))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            normalizeCompanionUrl("http://8.8.8.8:4182")
        }
    }

    @Test
    fun `credentials attach only to exact companion origin`() {
        val base = "https://203.0.113.10"
        assertTrue(isExactCompanionOrigin("https://203.0.113.10/api/v1/media", base))
        assertFalse(isExactCompanionOrigin("https://203.0.113.11/api/v1/media", base))
        assertEquals(
            mapOf("Authorization" to "Bearer scd1.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.secret"),
            companionHeadersForUrl("https://203.0.113.10/api/v1/media", base, "scd1.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.secret")
        )
        assertTrue(companionHeadersForUrl("https://203.0.113.11/api/v1/media", base, "secret").isEmpty())
    }

    @Test
    fun `central credential decision keeps companion and Plex secrets on their own origins`() {
        val companion = originCredentialsForUrl(
            url = "https://203.0.113.10/api/v1/media",
            companionBaseUrl = "https://203.0.113.10",
            companionToken = "companion-secret",
            plexBaseUrl = "http://192.168.1.25:32400",
            plexToken = "plex-secret"
        )
        assertTrue(companion.isCompanion)
        assertFalse(companion.isPlex)
        assertEquals(mapOf("Authorization" to "Bearer companion-secret"), companion.headers)

        val plex = originCredentialsForUrl(
            url = "http://192.168.1.25:32400/library/metadata/1",
            companionBaseUrl = "https://203.0.113.10",
            companionToken = "companion-secret",
            plexBaseUrl = "http://192.168.1.25:32400",
            plexToken = "plex-secret"
        )
        assertFalse(plex.isCompanion)
        assertTrue(plex.isPlex)
        assertEquals(mapOf("X-Plex-Token" to "plex-secret"), plex.headers)

        val unrelated = originCredentialsForUrl(
            url = "https://redirect.example/media",
            companionBaseUrl = "https://203.0.113.10",
            companionToken = "companion-secret",
            plexBaseUrl = "http://192.168.1.25:32400",
            plexToken = "plex-secret"
        )
        assertTrue(unrelated.headers.isEmpty())
        assertFalse(unrelated.isCompanion)
        assertFalse(unrelated.isPlex)
    }

    @Test
    fun `pairing URI is parsed and normalized`() {
        val payload = parseCompanionPairingPayload(
            "spotamp://pair?v=1&baseUrl=https%3A%2F%2F203.0.113.10&pairingId=01234567-89ab-cdef-0123-456789abcdef&secret=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq_"
        )
        assertEquals("https://203.0.113.10", payload.baseUrl)
        assertEquals("01234567-89ab-cdef-0123-456789abcdef", payload.pairingId)
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq_", payload.secret)
    }
}
