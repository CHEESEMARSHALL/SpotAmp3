package com.example.data

import android.content.Context
import com.example.R

/** Last observed companion reachability, kept separate from saved credentials. */
enum class CompanionReachability {
    UNKNOWN,
    CHECKING,
    AVAILABLE,
    UNAVAILABLE
}

enum class CompanionConnectionStatus {
    NOT_PAIRED,
    CHECKING,
    CONNECTED,
    TEMPORARILY_UNAVAILABLE,
    OFFLINE_LIBRARY_AVAILABLE
}

object CompanionConnectionPresentation {
    fun resolve(
        isPaired: Boolean,
        reachability: CompanionReachability,
        cachedTrackCount: Int
    ): CompanionConnectionStatus {
        if (!isPaired) return CompanionConnectionStatus.NOT_PAIRED
        return when (reachability) {
            CompanionReachability.CHECKING,
            CompanionReachability.UNKNOWN -> CompanionConnectionStatus.CHECKING
            CompanionReachability.AVAILABLE -> CompanionConnectionStatus.CONNECTED
            CompanionReachability.UNAVAILABLE -> if (cachedTrackCount > 0) {
                CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE
            } else {
                CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE
            }
        }
    }

    fun label(status: CompanionConnectionStatus): String = when (status) {
        CompanionConnectionStatus.NOT_PAIRED -> "Not paired"
        CompanionConnectionStatus.CHECKING -> "Checking SpotCore"
        CompanionConnectionStatus.CONNECTED -> "SpotCore available"
        CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE -> "SpotCore unavailable"
        CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE -> "Offline library available"
    }

    fun isHealthy(status: CompanionConnectionStatus): Boolean =
        status == CompanionConnectionStatus.CONNECTED
}

data class FailurePresentation(
    val message: String,
    val technicalDetails: String? = null
)

/** Maps implementation and transport errors to short user-facing recovery copy. */
object ProductFailurePresentation {
    private val httpStatusPattern = Regex("\\bHTTP\\s*(\\d{3})\\b", RegexOption.IGNORE_CASE)

    private fun isNetworkFailure(lower: String): Boolean =
        "unable to resolve host" in lower || "unknownhostexception" in lower ||
            "unknown host" in lower || "failed to connect" in lower ||
            "connection refused" in lower || "no route to host" in lower ||
            "timeout" in lower || "network" in lower || "unreachable" in lower ||
            "socket" in lower

    fun from(
        context: Context,
        error: Throwable,
        fallback: String = context.getString(R.string.product_failure_generic)
    ): FailurePresentation = from(context, error.localizedMessage, fallback)

    fun from(
        context: Context,
        detail: String?,
        fallback: String = context.getString(R.string.product_failure_generic)
    ): FailurePresentation {
        val technical = detail?.trim()?.takeIf(String::isNotEmpty)
        val statusCode = technical?.let { httpStatusPattern.find(it) }?.groupValues?.getOrNull(1)?.toIntOrNull()
        val lower = technical.orEmpty().lowercase()

        val message = when {
            statusCode == 401 || statusCode == 403 ||
                "unauthorized" in lower || "invalid token" in lower || "expired token" in lower ->
                context.getString(R.string.product_failure_connection_attention)
            statusCode == 404 || "not found" in lower ->
                context.getString(R.string.product_failure_item_unavailable)
            statusCode == 429 ->
                context.getString(R.string.product_failure_rate_limited)
            statusCode != null && statusCode >= 500 ->
                context.getString(R.string.product_failure_server_unavailable)
            "no music backend is configured" in lower || "not configured" in lower ->
                context.getString(R.string.product_failure_connect_server)
            "empty audio" in lower || "downloaded audio file is empty" in lower ->
                context.getString(R.string.product_failure_download_empty)
            "storage" in lower || "no space" in lower || "enospc" in lower ->
                context.getString(R.string.product_failure_not_enough_storage)
            "permission denied" in lower || "eacces" in lower || "read-only file system" in lower ||
                "unable to replace existing downloaded file" in lower ||
                "unable to promote downloaded file" in lower ->
                context.getString(R.string.product_failure_download_save_failed)
            isNetworkFailure(lower) ->
                context.getString(R.string.product_failure_network_unavailable)
            "qr code may have expired" in lower || "pairing failed" in lower || "claim" in lower ->
                context.getString(R.string.product_failure_pairing_generic)
            else -> fallback
        }

        return FailurePresentation(message, technical?.let(TokenSafety::redact))
    }

    /** Pairing has no saved connection to reconnect to; map claim failures in that context. */
    fun pairing(
        context: Context,
        error: Throwable,
        fallback: String = context.getString(R.string.product_failure_pairing_generic)
    ): FailurePresentation = pairing(context, error.localizedMessage, fallback)

    fun pairing(
        context: Context,
        detail: String?,
        fallback: String = context.getString(R.string.product_failure_pairing_generic)
    ): FailurePresentation {
        val technical = detail?.trim()?.takeIf(String::isNotEmpty)
        val statusCode = technical?.let { httpStatusPattern.find(it) }?.groupValues?.getOrNull(1)?.toIntOrNull()
        val lower = technical.orEmpty().lowercase()
        val message = when {
            statusCode == 400 || statusCode == 401 || statusCode == 403 || statusCode == 404 ||
                "pairing code expired" in lower || "pairing not found" in lower ->
                context.getString(R.string.product_failure_pairing_invalid)
            statusCode == 429 ->
                context.getString(R.string.product_failure_pairing_rate_limited)
            statusCode != null && statusCode >= 500 ->
                context.getString(R.string.product_failure_pairing_server_unavailable)
            isNetworkFailure(lower) ->
                context.getString(R.string.product_failure_pairing_network_unavailable)
            else -> fallback
        }
        return FailurePresentation(message, technical?.let(TokenSafety::redact))
    }
}
