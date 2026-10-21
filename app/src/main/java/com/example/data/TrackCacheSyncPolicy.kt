package com.example.data

object TrackCacheSyncPolicy {
    /** Replace the local cache only when the sync produced valid indexed tracks. */
    fun shouldReplaceCache(indexedTracks: List<CachedTrack>): Boolean = indexedTracks.isNotEmpty()

    /** Only prune a complete page-zero snapshot with matching count and stable server revision. */
    fun shouldPruneStaleCache(
        receivedAnyTracks: Boolean,
        initialOffset: Int,
        terminalPageReceived: Boolean,
        declaredTotalTracks: Int? = null,
        receivedTrackCount: Int? = null,
        snapshotRevisionStable: Boolean = false
    ): Boolean {
        val countMatches = declaredTotalTracks != null && receivedTrackCount != null &&
            declaredTotalTracks >= 0 && receivedTrackCount >= 0 &&
            declaredTotalTracks == receivedTrackCount
        val explicitEmpty = declaredTotalTracks == 0 && receivedTrackCount == 0
        return initialOffset == 0 && terminalPageReceived && snapshotRevisionStable && countMatches &&
            (receivedAnyTracks || explicitEmpty)
    }

    /** A worker may finalize only while its generation is still current. */
    fun ownsGeneration(workerSyncId: Long, persistedSyncId: Long): Boolean =
        workerSyncId != 0L && workerSyncId == persistedSyncId
}
