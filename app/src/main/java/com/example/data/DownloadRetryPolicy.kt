package com.example.data

enum class DownloadFailureDisposition {
    RETRY_AUTOMATICALLY,
    FINAL_FAILURE
}

object DownloadRetryPolicy {
    /** Number of retry attempts allowed after the initial worker attempt. */
    const val MAX_AUTOMATIC_RETRIES = 3

    fun disposition(runAttemptCount: Int): DownloadFailureDisposition =
        if (runAttemptCount < MAX_AUTOMATIC_RETRIES) {
            DownloadFailureDisposition.RETRY_AUTOMATICALLY
        } else {
            DownloadFailureDisposition.FINAL_FAILURE
        }
}
