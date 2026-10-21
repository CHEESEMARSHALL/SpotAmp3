package com.example

import com.example.data.CachedTrack
import com.example.data.TrackCacheSyncPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackCacheSyncPolicyTest {
    @Test
    fun emptyOrInvalidSyncDoesNotReplaceExistingCache() {
        assertFalse(TrackCacheSyncPolicy.shouldReplaceCache(emptyList()))
    }

    @Test
    fun validIndexedTracksReplaceTheCache() {
        val track = CachedTrack("1", "Track", "Artist", "Album", "/part", "", 1000)
        assertTrue(TrackCacheSyncPolicy.shouldReplaceCache(listOf(track)))
    }

    @Test
    fun ambiguousEmptySnapshotDoesNotPruneExistingCache() {
        assertFalse(
            TrackCacheSyncPolicy.shouldPruneStaleCache(
                receivedAnyTracks = false,
                initialOffset = 0,
                terminalPageReceived = true
            )
        )
    }

    @Test
    fun snapshotMetadataMissingDoesNotPruneExistingCache() {
        assertFalse(
            TrackCacheSyncPolicy.shouldPruneStaleCache(
                receivedAnyTracks = true,
                initialOffset = 0,
                terminalPageReceived = true
            )
        )
    }

    @Test
    fun revisionStabilityNotConfirmedDoesNotPruneExistingCache() {
        assertFalse(
            TrackCacheSyncPolicy.shouldPruneStaleCache(
                receivedAnyTracks = true,
                initialOffset = 0,
                terminalPageReceived = true,
                declaredTotalTracks = 1,
                receivedTrackCount = 1
            )
        )
    }

    @Test
    fun authoritativeEmptySnapshotCanPruneExistingCache() {
        assertTrue(
            TrackCacheSyncPolicy.shouldPruneStaleCache(
                receivedAnyTracks = false,
                initialOffset = 0,
                terminalPageReceived = true,
                declaredTotalTracks = 0,
                receivedTrackCount = 0,
                snapshotRevisionStable = true
            )
        )
    }

    @Test
    fun incompleteSnapshotDoesNotPruneExistingCache() {
        assertFalse(
            TrackCacheSyncPolicy.shouldPruneStaleCache(
                receivedAnyTracks = true,
                initialOffset = 0,
                terminalPageReceived = false,
                declaredTotalTracks = 4
            )
        )
    }

    @Test
    fun countMismatchDoesNotPruneExistingCache() {
        assertFalse(
            TrackCacheSyncPolicy.shouldPruneStaleCache(
                receivedAnyTracks = true,
                initialOffset = 0,
                terminalPageReceived = true,
                declaredTotalTracks = 503,
                receivedTrackCount = 502
            )
        )
    }

    @Test
    fun changedLibraryRevisionDoesNotPruneExistingCache() {
        assertFalse(
            TrackCacheSyncPolicy.shouldPruneStaleCache(
                receivedAnyTracks = true,
                initialOffset = 0,
                terminalPageReceived = true,
                declaredTotalTracks = 503,
                receivedTrackCount = 503,
                snapshotRevisionStable = false
            )
        )
    }

    @Test
    fun completeStableSnapshotCanPruneExistingCache() {
        assertTrue(
            TrackCacheSyncPolicy.shouldPruneStaleCache(
                receivedAnyTracks = true,
                initialOffset = 0,
                terminalPageReceived = true,
                declaredTotalTracks = 503,
                receivedTrackCount = 503,
                snapshotRevisionStable = true
            )
        )
    }
}
