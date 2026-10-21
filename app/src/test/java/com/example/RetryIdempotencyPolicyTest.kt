package com.example

import com.example.data.DeliveryOutcome
import com.example.data.RetryIdempotencyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryIdempotencyPolicyTest {
    @Test
    fun confirmedDeliveryRemovesPendingEvent() {
        assertTrue(RetryIdempotencyPolicy.shouldRemovePendingEvent(DeliveryOutcome.CONFIRMED))
    }

    @Test
    fun permanentRejectionRemovesPendingEvent() {
        assertTrue(RetryIdempotencyPolicy.shouldRemovePendingEvent(DeliveryOutcome.PERMANENTLY_REJECTED))
    }

    @Test
    fun transientAndAmbiguousFailuresKeepPendingEvent() {
        assertFalse(RetryIdempotencyPolicy.shouldRemovePendingEvent(DeliveryOutcome.TRANSIENT_FAILURE))
        assertFalse(RetryIdempotencyPolicy.shouldRemovePendingEvent(DeliveryOutcome.AMBIGUOUS_FAILURE))
    }

    @Test
    fun retryReusesOriginalEventId() {
        assertEquals("event-123", RetryIdempotencyPolicy.eventIdForRetry("event-123"))
    }
}
