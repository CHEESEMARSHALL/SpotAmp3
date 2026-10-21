package com.example.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.util.LinkedHashMap

/** A normalized scheme/host/port tuple used for credential attachment. */
data class CompanionOrigin(
    val scheme: String,
    val host: String,
    val port: Int
) {
    fun matches(url: HttpUrl): Boolean =
        scheme.equals(url.scheme, ignoreCase = true) &&
            host.equals(url.host, ignoreCase = true) &&
            port == url.port

    override fun toString(): String {
        val renderedHost = if (host.contains(':')) "[$host]" else host
        val defaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
        return "$scheme://$renderedHost${if (defaultPort) "" else ":$port"}"
    }
}

data class CompanionPairingPayload(
    val baseUrl: String,
    val pairingId: String,
    val secret: String
)

data class OriginCredentialDecision(
    val headers: Map<String, String> = emptyMap(),
    val isCompanion: Boolean = false,
    val isPlex: Boolean = false
)

private fun InetAddress.isCgnatAddress(): Boolean {
    val bytes = address
    return bytes.size == 4 &&
        (bytes[0].toInt() and 0xff) == 100 &&
        (bytes[1].toInt() and 0xff) in 64..127
}

private fun isPrivateHost(host: String): Boolean {
    if (host.equals("localhost", ignoreCase = true) || host.endsWith(".local", ignoreCase = true)) return true
    val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
    if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isAnyLocalAddress || address.isCgnatAddress()) return true
    if (address is Inet6Address && address.address.isNotEmpty()) {
        val first = address.address[0].toInt() and 0xff
        if ((first and 0xfe) == 0xfc) return true // IPv6 ULA fc00::/7
    }
    return address.isSiteLocalAddress
}

private fun HttpUrl.toOrigin(): CompanionOrigin = CompanionOrigin(scheme, host, port)

private class OriginCache(
    private val resolve: (String) -> CompanionOrigin?
) {
    private val values = object : LinkedHashMap<String, CompanionOrigin>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CompanionOrigin>?): Boolean = size > 8
    }

    fun get(raw: String): CompanionOrigin? {
        val key = raw.trim()
        if (key.isEmpty()) return null
        synchronized(values) { values[key]?.let { return it } }
        val resolved = resolve(key) ?: return null
        synchronized(values) {
            values[key] = resolved
            return resolved
        }
    }
}

private val companionOriginCache = OriginCache { raw ->
    runCatching { normalizeCompanionUrl(raw, allowPrivateHttp = true) }
        .getOrNull()
        ?.toHttpUrlOrNull()
        ?.toOrigin()
}

private val exactOriginCache = OriginCache { raw ->
    raw.let { if (it.contains("://")) it else "http://$it" }
        .toHttpUrlOrNull()
        ?.toOrigin()
}

/**
 * Normalizes a user-entered SpotCore endpoint and rejects public plaintext HTTP.
 * Private-LAN HTTP remains available for existing local-only installations.
 */
fun normalizeCompanionUrl(raw: String, allowPrivateHttp: Boolean = true): String {
    val candidate = raw.trim().let { if (it.contains("://")) it else "http://$it" }
    require(candidate.isNotBlank()) { "SpotCore URL is empty" }
    val parsed = candidate.toHttpUrlOrNull() ?: throw IllegalArgumentException("SpotCore URL is invalid")
    require(parsed.username.isEmpty() && parsed.password.isEmpty()) { "SpotCore URL must not contain credentials" }
    require(parsed.encodedPath.isEmpty() || parsed.encodedPath == "/") { "SpotCore URL must not contain a path" }
    require(parsed.query == null && parsed.fragment == null) { "SpotCore URL must not contain query or fragment" }
    require(parsed.scheme == "https" || parsed.scheme == "http") { "SpotCore URL must use HTTPS" }
    if (parsed.scheme == "http" && (!allowPrivateHttp || !isPrivateHost(parsed.host))) {
        throw IllegalArgumentException("Public SpotCore HTTP is not allowed; pair with the HTTPS endpoint")
    }
    return parsed.toOrigin().toString()
}

fun companionOrigin(raw: String): CompanionOrigin? {
    return companionOriginCache.get(raw)
}

fun isExactCompanionOrigin(url: String, configuredBaseUrl: String): Boolean {
    val target = url.toHttpUrlOrNull() ?: return false
    return isExactCompanionOrigin(target, configuredBaseUrl)
}

fun isExactCompanionOrigin(url: HttpUrl, configuredBaseUrl: String): Boolean =
    companionOrigin(configuredBaseUrl)?.matches(url) == true

/** Exact-origin comparison for Plex, whose configured scheme may remain HTTP on a LAN. */
fun isExactOrigin(url: String, configuredBaseUrl: String): Boolean {
    val target = url.toHttpUrlOrNull() ?: return false
    return isExactOrigin(target, configuredBaseUrl)
}

fun isExactOrigin(url: HttpUrl, configuredBaseUrl: String): Boolean =
    exactOriginCache.get(configuredBaseUrl)?.matches(url) == true

/** Returns only the headers allowed for this exact origin. */
fun companionHeadersForUrl(url: String, configuredBaseUrl: String, deviceToken: String): Map<String, String> {
    val target = url.toHttpUrlOrNull() ?: return emptyMap()
    return companionHeadersForUrl(target, configuredBaseUrl, deviceToken)
}

fun companionHeadersForUrl(url: HttpUrl, configuredBaseUrl: String, deviceToken: String): Map<String, String> {
    if (deviceToken.isBlank() || !isExactCompanionOrigin(url, configuredBaseUrl)) return emptyMap()
    return mapOf("Authorization" to "Bearer ${deviceToken.trim()}")
}

/** Central credential decision used by artwork, downloads, API redirects, and playback. */
fun originCredentialsForUrl(
    url: String,
    companionBaseUrl: String,
    companionToken: String,
    plexBaseUrl: String,
    plexToken: String
): OriginCredentialDecision = url.toHttpUrlOrNull()?.let {
    originCredentialsForUrl(it, companionBaseUrl, companionToken, plexBaseUrl, plexToken)
} ?: OriginCredentialDecision()

fun originCredentialsForUrl(
    url: HttpUrl,
    companionBaseUrl: String,
    companionToken: String,
    plexBaseUrl: String,
    plexToken: String
): OriginCredentialDecision {
    if (isExactCompanionOrigin(url, companionBaseUrl)) {
        return OriginCredentialDecision(
            headers = companionHeadersForUrl(url, companionBaseUrl, companionToken),
            isCompanion = true
        )
    }
    if (isExactOrigin(url, plexBaseUrl)) {
        return OriginCredentialDecision(
            headers = if (plexToken.isBlank()) emptyMap() else mapOf("X-Plex-Token" to plexToken.trim()),
            isPlex = true
        )
    }
    return OriginCredentialDecision()
}

fun companionDeviceIdFromToken(token: String): String? {
    val parts = token.trim().split('.')
    if (parts.size != 3 || parts[0] != "scd1" || !parts[1].matches(Regex("[0-9a-fA-F]{32}"))) return null
    return parts[1].lowercase()
}

fun parseCompanionPairingPayload(raw: String): CompanionPairingPayload {
    val uri = runCatching { URI(raw.trim()) }.getOrElse { throw IllegalArgumentException("This QR code is not a valid URI") }
    require(uri.scheme.equals("spotamp", ignoreCase = true) && uri.host.equals("pair", ignoreCase = true)) {
        "This QR code is not a SpotAmp pairing code"
    }
    val query = uri.rawQuery.orEmpty().split('&').filter(String::isNotBlank).associate { item ->
        val pieces = item.split('=', limit = 2)
        val key = URLDecoder.decode(pieces[0], Charsets.UTF_8.name())
        val value = URLDecoder.decode(pieces.getOrElse(1) { "" }, Charsets.UTF_8.name())
        key to value
    }
    require(query["v"] == "1") { "Unsupported SpotAmp pairing code" }
    val baseUrl = normalizeCompanionUrl(query["baseUrl"].orEmpty(), allowPrivateHttp = true)
    val pairingId = query["pairingId"].orEmpty()
    val secret = query["secret"].orEmpty()
    require(pairingId.matches(Regex("[0-9a-fA-F-]{16,128}"))) { "Pairing code is invalid" }
    require(secret.matches(Regex("[A-Za-z0-9_-]{40,64}"))) { "Pairing secret is invalid" }
    return CompanionPairingPayload(baseUrl, pairingId, secret)
}
