package com.example

import com.example.data.TrackCacheSyncPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncGenerationSafetyTest {
    @Test
    fun obsoleteWorker_cannotFinalizeNewerGeneration() {
        assertFalse(TrackCacheSyncPolicy.ownsGeneration(workerSyncId = 10L, persistedSyncId = 11L))
    }

    @Test
    fun currentWorker_canFinalizeItsGeneration() {
        assertTrue(TrackCacheSyncPolicy.ownsGeneration(workerSyncId = 11L, persistedSyncId = 11L))
    }

    @Test
    fun zeroGeneration_isNeverOwned() {
        assertFalse(TrackCacheSyncPolicy.ownsGeneration(workerSyncId = 0L, persistedSyncId = 0L))
    }
}
