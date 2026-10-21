package com.example

import com.example.data.mergeCompanionTrackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionTrackStateMappingTest {
    @Test
    fun librarySnapshotValuesMapToDistinctSpotCoreState() {
        assertEquals(
            true to 4,
            mergeCompanionTrackStateSnapshot(
                snapshotFavorite = true,
                snapshotRating = 4,
                previousFavorite = false,
                previousRating = 1
            ).let { it.favorite to it.rating }
        )
    }

    @Test
    fun absentSnapshotValuesRetainCacheAndRatingsStayWithinContractRange() {
        assertEquals(
            true to 3,
            mergeCompanionTrackStateSnapshot(
                snapshotFavorite = null,
                snapshotRating = null,
                previousFavorite = true,
                previousRating = 3
            ).let { it.favorite to it.rating }
        )
        assertEquals(
            5,
            mergeCompanionTrackStateSnapshot(
                snapshotFavorite = null,
                snapshotRating = 9,
                previousFavorite = false,
                previousRating = 0
            ).rating
        )
    }
}
