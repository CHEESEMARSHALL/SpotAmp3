package com.example.playback

/** Chooses media without allowing offline mode to fall back to a network URI. */
object PlaybackUriPolicy {
    fun resolve(localUri: String?, remoteUri: String, offlineOnly: Boolean): String? {
        val local = localUri?.trim().orEmpty()
        if (local.isNotEmpty()) return local
        if (offlineOnly) return null
        return remoteUri.takeIf { it.isNotBlank() }
    }
}
