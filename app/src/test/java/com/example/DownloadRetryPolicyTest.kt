package com.example

import com.example.data.DownloadFailureDisposition
import com.example.data.DownloadRetryPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadRetryPolicyTest {
    @Test
    fun earlyAndLastAllowedRetryAttemptsRemainAutomatic() {
        assertEquals(
            DownloadFailureDisposition.RETRY_AUTOMATICALLY,
            DownloadRetryPolicy.disposition(0)
        )
        assertEquals(
            DownloadFailureDisposition.RETRY_AUTOMATICALLY,
            DownloadRetryPolicy.disposition(DownloadRetryPolicy.MAX_AUTOMATIC_RETRIES - 1)
        )
    }

    @Test
    fun attemptAfterAutomaticRetryLimitBecomesFinalFailure() {
        assertEquals(
            DownloadFailureDisposition.FINAL_FAILURE,
            DownloadRetryPolicy.disposition(DownloadRetryPolicy.MAX_AUTOMATIC_RETRIES)
        )
    }
}
