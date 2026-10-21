package com.example.playback

/** Shared availability rules for playback controls in the player and system media notification. */
object PlaybackControlAvailability {
    fun canSkipToNext(queueSize: Int, currentIndex: Int, repeatAll: Boolean): Boolean =
        queueSize > 1 &&
            currentIndex in 0 until queueSize &&
            (currentIndex < queueSize - 1 || repeatAll)
}
