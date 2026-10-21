package com.example.data

private val URI_SCHEME = Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE)
private const val HEX = "0123456789ABCDEF"

/**
 * Joins a server base URL and an artwork reference without corrupting either
 * absolute URLs or custom/data references.
 */
fun resolveArtworkReference(baseUrl: String, reference: String): String {
    val value = reference.trim()
    if (value.isEmpty()) return ""
    if (value.startsWith("//")) {
        val scheme = baseUrl.trim().substringBefore("://", "")
        return if (scheme.isNotBlank()) "$scheme:$value" else value
    }
    if (URI_SCHEME.containsMatchIn(value)) return value

    val base = baseUrl.trim().trimEnd('/')
    if (base.isEmpty()) return value
    return if (value.startsWith('/')) "$base$value" else "$base/$value"
}

/** Resolves relative media routes returned by the paired SpotCore API against its paired origin. */
fun resolveCompanionMediaReference(companionBaseUrl: String, mediaReference: String): String {
    val value = mediaReference.trim()
    if (value.isEmpty() || URI_SCHEME.containsMatchIn(value)) return value
    val base = companionBaseUrl.trim().trimEnd('/')
    if (base.isEmpty()) return value
    if (value.startsWith("//")) {
        val scheme = base.substringBefore("://", "")
        return if (scheme.isNotBlank()) "$scheme:$value" else value
    }
    return if (value.startsWith('/')) "$base$value" else "$base/$value"
}
/**
 * Resolves an artist profile's companion artwork reference.
 *
 * Artist profiles may return an absolute URL, a protocol-relative URL, or a
 * path relative to SpotCore. Unlike track artwork, there is no guaranteed
 * artist-id proxy endpoint, so unusable desktop/data references are omitted
 * and the caller can try its next artwork source.
 */fun normalizeCompanionArtistArtworkUrl(
    companionBaseUrl: String,
    artworkReference: String?
): String? {
    val value = artworkReference?.trim().orEmpty()
    if (value.isEmpty() ||
        value.startsWith("data:", ignoreCase = true) ||
        value.startsWith("file:", ignoreCase = true) ||
        value.startsWith("spotcore-artwork:", ignoreCase = true)
    ) {
        return null
    }

    val resolved = resolveArtworkReference(companionBaseUrl, value)
    return resolved.takeIf {
        it.startsWith("http://", ignoreCase = true) ||
            it.startsWith("https://", ignoreCase = true)
    }
}

/** Returns the authenticated companion proxy URL for one track's artwork. */
fun companionArtworkProxyUrl(baseUrl: String, trackId: String): String {
    val base = baseUrl.trim().trimEnd('/')
    if (base.isEmpty() || trackId.isBlank()) return ""
    return "$base/api/v1/media/art/${encodePathSegment(trackId)}"
}

/**
 * Converts the companion's cover reference into a URL that Android can load.
 *
 * SpotCore deliberately keeps external HTTP(S) artwork direct. References
 * that Android cannot resolve (embedded data, desktop-only artwork schemes,
 * local files, or relative paths) use the authenticated companion proxy.
 */
fun normalizeCompanionArtworkUrl(
    companionBaseUrl: String,
    trackId: String,
    coverUrl: String?
): String {
    val fallback = companionArtworkProxyUrl(companionBaseUrl, trackId)
    if (coverUrl == null) return ""
    val value = coverUrl?.trim().orEmpty()
    if (value.isEmpty()) return fallback

    // Qt returns a versioned authenticated path for local artwork. Keep the
    // revision in the URL so image caches naturally invalidate after edits.
    if (value.startsWith("/api/v1/media/art/")) {
        return resolveArtworkReference(companionBaseUrl, value)
    }

    if (value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true)
    ) {
        return value
    }

    // These values belong to SpotCore or to the desktop filesystem. They are
    // valid server-side references, but they are not Android-loadable URLs.
    if (value.startsWith("data:", ignoreCase = true) ||
        value.startsWith("spotcore-artwork:", ignoreCase = true) ||
        value.startsWith("file:", ignoreCase = true)
    ) {
        return fallback
    }

    // Canonical companion responses are absolute URLs. Keep protocol-relative
    // external references useful, but never reinterpret an unknown relative
    // path as an endpoint on the companion server.
    if (!value.startsWith("//")) return fallback

    val joined = resolveArtworkReference(companionBaseUrl, value)
    return if (joined.startsWith("http://", ignoreCase = true) ||
        joined.startsWith("https://", ignoreCase = true)
    ) joined else fallback
}

private fun encodePathSegment(value: String): String {
    return buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            val character = unsigned.toChar()
            if ((unsigned in 0x30..0x39) ||
                (unsigned in 0x41..0x5A) ||
                (unsigned in 0x61..0x7A) ||
                character in "-._~"
            ) {
                append(character)
            } else {
                append('%')
                append(HEX[unsigned ushr 4])
                append(HEX[unsigned and 0x0f])
            }
        }
    }
}
