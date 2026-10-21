package com.example.playback

/** Tracks monotonic wall time spent actively playing, excluding pauses and buffering. */
internal class PlaybackListenDurationAccumulator {
    private var accumulatedMs = 0L
    private var lastSampleElapsedRealtimeMs: Long? = null

    val totalMs: Long
        get() = accumulatedMs

    fun sample(elapsedRealtimeMs: Long, isPlaying: Boolean): Long {
        if (!isPlaying) {
            resetSample()
            return accumulatedMs
        }

        val previousSample = lastSampleElapsedRealtimeMs
        if (previousSample != null && elapsedRealtimeMs >= previousSample) {
            accumulatedMs += elapsedRealtimeMs - previousSample
        }
        lastSampleElapsedRealtimeMs = elapsedRealtimeMs
        return accumulatedMs
    }

    fun resetSample() {
        lastSampleElapsedRealtimeMs = null
    }

    fun reset() {
        accumulatedMs = 0L
        resetSample()
    }
}
