package com.example

import com.example.data.SyncDiagnosticService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncDiagnosticServicePrivacyTest {
    @Test
    fun progressDiagnosticsExposeCountsWithoutTrackIdentifiers() {
        SyncDiagnosticService.reset()
        try {
            SyncDiagnosticService.updateProgress(page = 88, received = 90)

            val info = SyncDiagnosticService.diagnosticInfo.value
            assertEquals(88, info.currentPage)
            assertEquals(90, info.itemsInLastPage)
            assertEquals(90, info.totalProcessed)
            assertEquals("Page 88: received 90 items.", info.log.single().substringAfter(": "))

            val diagnostics = "$info ${info.log.joinToString()}"
            assertFalse(diagnostics.contains("signature", ignoreCase = true))
            assertFalse(diagnostics.contains("prefix", ignoreCase = true))
        } finally {
            SyncDiagnosticService.reset()
        }
    }
}
