package com.example.data

/** Outcome policy for persisted distributed actions. */
enum class DeliveryOutcome {
    CONFIRMED,
    PERMANENTLY_REJECTED,
    TRANSIENT_FAILURE,
    AMBIGUOUS_FAILURE
}

object RetryIdempotencyPolicy {
    /** Remove an outbox row only when the server confirmed it or rejected it permanently. */
    fun shouldRemovePendingEvent(outcome: DeliveryOutcome): Boolean =
        outcome == DeliveryOutcome.CONFIRMED || outcome == DeliveryOutcome.PERMANENTLY_REJECTED

    /** Retries must reuse the original idempotency key, never generate a replacement. */
    fun eventIdForRetry(originalEventId: String): String = originalEventId
}
