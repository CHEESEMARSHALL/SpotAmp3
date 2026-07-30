package com.example.playback

import com.example.data.PlaybackStateEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

object PlaybackStateRestoration {
    data class RestoredState(
        val queue: List<TrackItem>,
        val currentIndex: Int,
        val positionMs: Long,
        val shuffleEnabled: Boolean,
        val repeatMode: Int
    )

    fun restore(state: PlaybackStateEntity): RestoredState? {
        return restore(state) { true }
    }

    fun restore(state: PlaybackStateEntity, isTrackAvailable: (TrackItem) -> Boolean): RestoredState? {
        return try {
            val allQueue = Json.decodeFromString<List<TrackItem>>(state.queueJson)
            val queue = allQueue.filter { track ->
                if (track.localPath.isNullOrBlank()) {
                    true
                } else {
                    isTrackAvailable(track)
                }
            }
            if (queue.isEmpty()) return null
            
            RestoredState(
                queue = queue,
                currentIndex = 0,
                positionMs = state.positionMs.coerceAtLeast(0L),
                shuffleEnabled = state.shuffleEnabled,
                repeatMode = state.repeatMode
            )
        } catch (e: Exception) {
            null
        }
    }
}
