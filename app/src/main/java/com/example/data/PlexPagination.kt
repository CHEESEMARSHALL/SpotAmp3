package com.example.data

object PlexPagination {
    suspend fun <T> collect(pageSize: Int = 200, fetch: suspend (offset: Int, limit: Int) -> List<T>): List<T> {
        require(pageSize > 0)
        val result = mutableListOf<T>()
        var offset = 0
        while (true) {
            val page = fetch(offset, pageSize)
            result += page
            if (page.size < pageSize) return result
            offset += page.size
        }
    }
}
