package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.LastFmAuthorizationFailurePresentation
import com.example.data.LastFmAuthorizationStep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LastFmAuthorizationFailurePresentationTest {
    @Test
    fun startFailureUsesSafeActionableCopyInsteadOfResponseDetails() {
        val message = LastFmAuthorizationFailurePresentation.message(
            ApplicationProvider.getApplicationContext(),
            LastFmAuthorizationStep.START
        )

        assertTrue(message.contains("Check the key and secret above and your connection"))
        assertFalse(message.contains("HTTP"))
        assertFalse(message.contains("token="))
    }

    @Test
    fun completionFailureExplainsBrowserApprovalWithoutLeakingResponseDetails() {
        val message = LastFmAuthorizationFailurePresentation.message(
            ApplicationProvider.getApplicationContext(),
            LastFmAuthorizationStep.COMPLETE
        )

        assertTrue(message.contains("Approve SpotAmp in your browser"))
        assertFalse(message.contains("HTTP"))
        assertFalse(message.contains("token="))
    }
}
