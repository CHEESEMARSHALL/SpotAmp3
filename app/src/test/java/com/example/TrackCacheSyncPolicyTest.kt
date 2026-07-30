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
}
