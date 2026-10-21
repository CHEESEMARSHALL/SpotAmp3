package com.example.data

/** Keeps server-provided identifiers confined to one download-file path segment. */
object DownloadPathPolicy {
    fun fileStem(ratingKey: String): String? {
        val candidate = ratingKey.trim()
        if (candidate.isEmpty() || candidate == "." || candidate == "..") return null
        if (candidate.any { it == '/' || it == '\\' || it.isISOControl() }) return null
        return candidate
    }
}
