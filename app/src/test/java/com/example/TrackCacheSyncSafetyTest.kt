package com.example

import com.example.data.TrackCacheSyncPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackCacheSyncSafetyTest {
    @Test
    fun emptyRemoteSnapshot_doesNotAuthorizeStaleCachePruning() {
        assertFalse(TrackCacheSyncPolicy.shouldPruneStaleCache(false, 0, true))
    }

    @Test
    fun completeNonEmptySnapshotFromFirstPage_authorizesStaleCachePruning() {
        assertTrue(
            TrackCacheSyncPolicy.shouldPruneStaleCache(
                receivedAnyTracks = true,
                initialOffset = 0,
                terminalPageReceived = true,
                declaredTotalTracks = 1,
                receivedTrackCount = 1,
                snapshotRevisionStable = true
            )
        )
    }

    @Test
    fun failedOrStalledPage_doesNotAuthorizeStaleCachePruning() {
        assertFalse(TrackCacheSyncPolicy.shouldPruneStaleCache(true, 0, false))
    }

    @Test
    fun resumedOffset_doesNotAuthorizeStaleCachePruning() {
        assertFalse(TrackCacheSyncPolicy.shouldPruneStaleCache(true, 500, true))
    }
}