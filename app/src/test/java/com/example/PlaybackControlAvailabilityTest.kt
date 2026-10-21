package com.example

import com.example.playback.PlaybackControlAvailability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControlAvailabilityTest {
    @Test
    fun nextIsAvailableBeforeTheFinalQueueItem() {
        assertTrue(PlaybackControlAvailability.canSkipToNext(queueSize = 3, currentIndex = 0, repeatAll = false))
        assertTrue(PlaybackControlAvailability.canSkipToNext(queueSize = 3, currentIndex = 1, repeatAll = false))
    }

    @Test
    fun nextIsUnavailableAtTheFinalItemUnlessRepeatAllIsOn() {
        assertFalse(PlaybackControlAvailability.canSkipToNext(queueSize = 3, currentIndex = 2, repeatAll = false))
        assertTrue(PlaybackControlAvailability.canSkipToNext(queueSize = 3, currentIndex = 2, repeatAll = true))
    }

    @Test
    fun nextIsUnavailableForSingleTrackOrInvalidIndex() {
        assertFalse(PlaybackControlAvailability.canSkipToNext(queueSize = 1, currentIndex = 0, repeatAll = true))
        assertFalse(PlaybackControlAvailability.canSkipToNext(queueSize = 2, currentIndex = -1, repeatAll = false))
        assertFalse(PlaybackControlAvailability.canSkipToNext(queueSize = 2, currentIndex = 2, repeatAll = false))
    }
}
