package com.example

import com.example.data.TokenSafety
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenSafetyTest {
    @Test
    fun `redacts Plex token query parameters`() {
        val redacted = TokenSafety.redactedUrl("http://server/library/parts/1?X-Plex-Token=secret123&download=1")
        assertFalse(redacted.contains("secret123"))
        assertTrue(redacted.contains("X-Plex-Token=<redacted>"))
    }
}
