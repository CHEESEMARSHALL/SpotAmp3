package com.example.data

object PlexPagination {
    suspend fun <T> collect(pageSize: Int = 200, fetch: suspend (offset: Int, limit: Int) -> List<T>): List<T> {
        require(pageSize > 0)
        val result = mutableListOf<T>()
        var offset = 0
        var pageCount = 0
        var previousFingerprint: Int? = null
        var repeatedPageCount = 0
        while (true) {
            if (++pageCount > 10_000) error("Pagination exceeded the safety page limit")
            val page = fetch(offset, pageSize)
            val fingerprint = page.hashCode()
            if (fingerprint == previousFingerprint) {
                repeatedPageCount++
                if (repeatedPageCount >= 2) error("Pagination stalled on a repeated page")
            } else {
                repeatedPageCount = 0
                previousFingerprint = fingerprint
            }
            result += page
            if (page.size < pageSize) return result
            offset += page.size
        }
    }
}
