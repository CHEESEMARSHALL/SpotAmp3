package com.example.data

object TokenSafety {
    private val tokenQuery = Regex(
        "([?&](?:X-Plex-Token|access[_-]?token|token|secret|password|client[_-]?secret|api[_-]?key|key)=)[^&#\\s]+",
        RegexOption.IGNORE_CASE
    )
    private val authorizationCredential = Regex(
        "(Authorization\\s*[:=]\\s*(?:Bearer|Basic)\\s+)[^\\s,;]+",
        RegexOption.IGNORE_CASE
    )
    private val credentialUrl = Regex("([a-z][a-z0-9+.-]*://)[^/@\\s]+:[^/@\\s]+@", RegexOption.IGNORE_CASE)
    private val credentialHeader = Regex(
        "(?im)^((?:X-Plex-Token|X-Api-Key|Api-Key|Access-Token|Cookie|Set-Cookie)\\s*[:=]\\s*)[^\\r\\n]+"
    )
    private val unresolvedHost = Regex(
        """(?i)(\b(?:unable to resolve host|unknownhostexception:)\s*["']?)([^"'\s:]+)(["']?)"""
    )

    fun redact(value: String?): String {
        if (value.isNullOrEmpty()) return value.orEmpty()
        return value
            .replace(tokenQuery, "$1<redacted>")
            .replace(authorizationCredential, "$1<redacted>")
            .replace(credentialUrl, "$1<redacted>@")
            .replace(credentialHeader, "$1<redacted>")
            .replace(unresolvedHost, "$1<redacted-host>$3")
    }

    fun redactedUrl(url: String): String = redact(url)
}
