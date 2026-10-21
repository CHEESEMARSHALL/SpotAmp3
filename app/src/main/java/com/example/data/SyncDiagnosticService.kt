package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncDiagnosticInfo(
    val lastSyncAttempt: Long = 0,
    val lastSyncStatus: String = "Idle",
    val lastError: String? = null,
    val currentPage: Int = 0,
    val itemsInLastPage: Int = 0,
    val totalProcessed: Int = 0,
    val log: List<String> = emptyList()
)

object SyncDiagnosticService {
    private val _diagnosticInfo = MutableStateFlow(SyncDiagnosticInfo())
    val diagnosticInfo: StateFlow<SyncDiagnosticInfo> = _diagnosticInfo.asStateFlow()

    fun updateStatus(status: String) {
        _diagnosticInfo.value = _diagnosticInfo.value.copy(
            lastSyncStatus = status,
            lastSyncAttempt = System.currentTimeMillis()
        )
        addLog(status)
    }

    fun updateProgress(page: Int, received: Int) {
        val current = _diagnosticInfo.value

        _diagnosticInfo.value = current.copy(
            currentPage = page,
            itemsInLastPage = received,
            totalProcessed = current.totalProcessed + received
        )
        addLog("Page $page: received $received items.")
    }

    fun setError(error: String) {
        val safeError = TokenSafety.redact(error)
        _diagnosticInfo.value = _diagnosticInfo.value.copy(
            lastSyncStatus = "Failed",
            lastError = safeError
        )
        addLog("ERROR: $safeError")
    }

    fun addLog(message: String) {
        val currentLog = _diagnosticInfo.value.log.toMutableList()
        currentLog.add(0, "${System.currentTimeMillis() % 100000}: ${TokenSafety.redact(message)}")
        if (currentLog.size > 50) currentLog.removeAt(currentLog.size - 1)
        _diagnosticInfo.value = _diagnosticInfo.value.copy(log = currentLog)
    }

    fun reset() {
        _diagnosticInfo.value = SyncDiagnosticInfo(lastSyncStatus = "Starting...")
    }
}
