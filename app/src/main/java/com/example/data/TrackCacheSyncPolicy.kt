package com.example.data

object TrackCacheSyncPolicy {
    /** Replace the local cache only when the sync produced valid indexed tracks. */
    fun shouldReplaceCache(indexedTracks: List<CachedTrack>): Boolean = indexedTracks.isNotEmpty()
}
