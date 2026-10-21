package com.example

import android.content.Context
import com.example.data.ProductFailurePresentation
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProductFailurePresentationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `authentication failure directs user to reconnect without exposing protocol detail`() {
        val result = ProductFailurePresentation.from(context, "HTTP 401 Unauthorized: invalid token")

        assertEquals(
            "The saved connection needs attention. Reconnect it in Settings and try again.",
            result.message
        )
        assertFalse(result.message.contains("401"))
        assertFalse(result.message.contains("token"))
    }

    @Test
    fun `missing item suggests refreshing library`() {
        val result = ProductFailurePresentation.from(context, "HTTP 404 Not Found")

        assertEquals(
            "This item is no longer available. Refresh your library and try again.",
            result.message
        )
    }

    @Test
    fun `rate limiting suggests waiting before retry`() {
        val result = ProductFailurePresentation.from(context, "HTTP 429 Too Many Requests")

        assertEquals(
            "SpotCore is busy right now. Wait a moment and try again.",
            result.message
        )
        assertFalse(result.message.contains("429"))
        assertFalse(result.message.contains("requests", ignoreCase = true))
    }

    @Test
    fun `pairing rate limiting recommends waiting before retry`() {
        val result = ProductFailurePresentation.pairing(context, "HTTP 429 Too Many Requests")

        assertEquals(
            "SpotCore is busy right now. Wait a moment, then try pairing again.",
            result.message
        )
        assertFalse(result.message.contains("429"))
        assertFalse(result.message.contains("requests", ignoreCase = true))
    }

    @Test
    fun `server failure gives temporary retry guidance without exposing status`() {
        val result = ProductFailurePresentation.from(context, "Plex request failed: HTTP 503 Service Unavailable")

        assertEquals(
            "SpotCore is temporarily unavailable. Try again in a moment.",
            result.message
        )
        assertFalse(result.message.contains("Plex", ignoreCase = true))
        assertFalse(result.message.contains("503"))
    }

    @Test
    fun `pairing code expiry and pairing service failures point to the right recovery`() {
        val expired = ProductFailurePresentation.pairing(context, "Pairing code expired")
        val unavailable = ProductFailurePresentation.pairing(context, "HTTP 503 Service Unavailable")

        assertEquals(
            "This pairing code is no longer valid. Return to SpotCore, create a new code, and scan it again.",
            expired.message
        )
        assertEquals(
            "SpotCore is temporarily unavailable. Try pairing again in a moment.",
            unavailable.message
        )
        assertFalse(unavailable.message.contains("503"))
    }

    @Test
    fun `network failure gives connection recovery guidance`() {
        val result = ProductFailurePresentation.from(context, "java.net.SocketTimeoutException: timeout")

        assertEquals(
            "Couldn’t reach SpotCore. Check the connection and try again.",
            result.message
        )
    }

    @Test
    fun `bare DNS and routing failures get actionable connection guidance`() {
        listOf(
            "UnknownHostException: spotcore.local",
            "java.net.NoRouteToHostException: No route to host"
        ).forEach { detail ->
            val result = ProductFailurePresentation.from(context, detail)

            assertEquals(
                "Couldn’t reach SpotCore. Check the connection and try again.",
                result.message
            )
        }
    }

    @Test
    fun `DNS technical details redact the configured host`() {
        val result = ProductFailurePresentation.from(
            context,
            "Unable to resolve host \"library.private.example\": No address associated with hostname"
        )

        assertEquals(
            "Couldn’t reach SpotCore. Check the connection and try again.",
            result.message
        )
        assertTrue(result.technicalDetails!!.contains("<redacted-host>"))
        assertFalse(result.technicalDetails.contains("library.private.example"))
    }

    @Test
    fun `pairing DNS failure directs the user to check SpotCore connectivity`() {
        val result = ProductFailurePresentation.pairing(context, "UnknownHostException: spotcore.local")

        assertEquals(
            "Couldn’t reach SpotCore to finish pairing. Check that it’s running and your phone can reach it, then try again.",
            result.message
        )
    }

    @Test
    fun `unauthorized pairing claim sends user back for a fresh code`() {
        val result = ProductFailurePresentation.pairing(context, "HTTP 401 Unauthorized")

        assertEquals(
            "This pairing code is no longer valid. Return to SpotCore, create a new code, and scan it again.",
            result.message
        )
        assertFalse(result.message.contains("Settings"))
        assertFalse(result.message.contains("saved connection"))
    }

    @Test
    fun `invalid pairing claim response points user to a fresh code`() {
        val result = ProductFailurePresentation.pairing(context, "HTTP 400 Bad Request")

        assertEquals(
            "This pairing code is no longer valid. Return to SpotCore, create a new code, and scan it again.",
            result.message
        )
    }

    @Test
    fun `pairing network failure names SpotCore and keeps secret out of recovery copy`() {
        val result = ProductFailurePresentation.pairing(
            context,
            "SocketTimeoutException: timeout at https://spotcore.example/pair?secret=pairing-secret"
        )

        assertEquals(
            "Couldn’t reach SpotCore to finish pairing. Check that it’s running and your phone can reach it, then try again.",
            result.message
        )
        assertNotNull(result.technicalDetails)
        assertTrue(result.technicalDetails!!.contains("secret=<redacted>"))
        assertFalse(result.technicalDetails.contains("pairing-secret"))
    }

    @Test
    fun `download write permission failure suggests checking phone storage`() {
        val result = ProductFailurePresentation.from(
            context,
            "java.io.FileNotFoundException: open failed: EACCES (Permission denied)",
            fallback = "The download failed. Check the connection and try again."
        )

        assertEquals(
            "SpotAmp couldn’t save this download on this phone. Check available storage and try again.",
            result.message
        )
        assertFalse(result.message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `download file promotion failure does not suggest a network retry`() {
        val result = ProductFailurePresentation.from(
            context,
            "Unable to promote downloaded file.",
            fallback = "The download failed. Check the connection and try again."
        )

        assertEquals(
            "SpotAmp couldn’t save this download on this phone. Check available storage and try again.",
            result.message
        )
    }

    @Test
    fun `empty download response uses listener copy rather than provider detail`() {
        val result = ProductFailurePresentation.from(
            context,
            "Plex returned an empty audio file.",
            fallback = "The download failed. Try again."
        )

        assertEquals(
            "The download was empty. Check the connection and try again.",
            result.message
        )
        assertFalse(result.message.contains("Plex", ignoreCase = true))
    }

    @Test
    fun `no-space errors explain the local storage recovery`() {
        listOf(
            "No space left on device",
            "open failed: ENOSPC"
        ).forEach { detail ->
            val result = ProductFailurePresentation.from(context, detail)

            assertEquals(
                "There may not be enough space to save this download. Check device storage and try again.",
                result.message
            )
            assertFalse(result.message.contains("ENOSPC"))
        }
    }

    @Test
    fun `unknown failure uses safe fallback and keeps redacted detail separate`() {
        val result = ProductFailurePresentation.from(
            context,
            "Request failed for https://music.example/stream?token=secret-value",
            fallback = "Could not play this track. Try again."
        )

        assertEquals("Could not play this track. Try again.", result.message)
        assertNotNull(result.technicalDetails)
        assertTrue(result.technicalDetails!!.contains("token=<redacted>"))
        assertFalse(result.technicalDetails.contains("secret-value"))
        assertFalse(result.message.contains("music.example"))
    }
}
