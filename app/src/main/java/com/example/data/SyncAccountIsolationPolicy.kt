package com.example.data

/** Non-secret identity used to prevent an old account's worker from committing data. */
data class SyncAccountIdentity(
    val backendOrigin: String,
    val accountId: String
)

object SyncAccountIsolationPolicy {
    fun identity(backendUrl: String, accountId: String): SyncAccountIdentity? {
        val origin = companionOrigin(backendUrl)?.toString() ?: return null
        val normalizedAccount = accountId.trim()
        if (normalizedAccount.isEmpty()) return null
        return SyncAccountIdentity(origin, normalizedAccount)
    }

    fun mayCommit(worker: SyncAccountIdentity, current: SyncAccountIdentity?): Boolean =
        current != null && worker == current
}
