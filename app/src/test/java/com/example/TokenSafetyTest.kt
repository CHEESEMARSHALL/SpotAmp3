package com.example

import com.example.data.TokenSafety
import com.example.data.SyncDiagnosticService
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

    @Test
    fun `redacts credentials from URL userinfo sensitive query fields and auth headers`() {
        val input = """Request to https://alice:account-pass@server.local/path?api_key=api-secret&password=pass-value failed.
            |Authorization: Basic basic-secret
            |Authorization: Bearer bearer-secret
            |Cookie: session=session-secret""".trimMargin()

        val redacted = TokenSafety.redact(input)

        listOf("account-pass", "api-secret", "pass-value", "basic-secret", "bearer-secret", "session-secret")
            .forEach { secret -> assertFalse("Should redact $secret", redacted.contains(secret)) }
        assertTrue(redacted.contains("https://<redacted>@server.local"))
        assertTrue(redacted.contains("api_key=<redacted>"))
        assertTrue(redacted.contains("Authorization: Basic <redacted>"))
    }

    @Test
    fun `redacts hostnames in DNS failure details`() {
        val redacted = TokenSafety.redact(
            "Unable to resolve host \"library.private.example\": No address associated with hostname"
        )

        assertFalse(redacted.contains("library.private.example"))
        assertTrue(redacted.contains("Unable to resolve host \"<redacted-host>\""))
    }

    @Test
    fun `sync diagnostics store only redacted errors and log entries`() {
        SyncDiagnosticService.reset()
        SyncDiagnosticService.setError(
            "Request https://alice:account-pass@server.local/?client_secret=client-secret failed; " +
                "Authorization: Bearer bearer-secret"
        )

        val diagnostics = SyncDiagnosticService.diagnosticInfo.value
        val stored = listOfNotNull(diagnostics.lastError, diagnostics.log.firstOrNull()).joinToString("\n")
        listOf("account-pass", "client-secret", "bearer-secret")
            .forEach { secret -> assertFalse("Diagnostics must not retain $secret", stored.contains(secret)) }
        assertTrue(stored.contains("<redacted>"))
    }
}
