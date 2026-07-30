package com.example.data

sealed class LibrarySyncState {
    object Idle : LibrarySyncState()
    object Connecting : LibrarySyncState()
    data class Fetching(val page: Int, val receivedCount: Int) : LibrarySyncState()
    data class Processing(val totalReceived: Int, val validCount: Int, val skippedCount: Int) : LibrarySyncState()
    data class Saving(val validCount: Int) : LibrarySyncState()
    data class Complete(val indexedCount: Int, val skippedCount: Int, val elapsedMs: Long) : LibrarySyncState()
    data class Failed(val userMessage: String, val technicalMessage: String, val stage: String) : LibrarySyncState()
}
