package com.example

import com.example.data.syncOrderSampleDiagnostic
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySyncDiagnosticPresentationTest {
    @Test
    fun `order sample contains only useful numeric track position`() {
        assertEquals("Order sample: disc=2 track=7", syncOrderSampleDiagnostic(2, 7))
        assertEquals("Order sample: disc=unknown track=unknown", syncOrderSampleDiagnostic(null, null))
    }
}
