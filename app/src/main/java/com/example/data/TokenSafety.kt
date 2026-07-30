package com.example.data

object TokenSafety {
    private val tokenQuery = Regex("([?&]X-Plex-Token=)[^&\\s]+", RegexOption.IGNORE_CASE)

    fun redact(value: String?): String {
        if (value.isNullOrEmpty()) return value.orEmpty()
        return value.replace(tokenQuery, "$1<redacted>")
    }

    fun redactedUrl(url: String): String = redact(url)
}
