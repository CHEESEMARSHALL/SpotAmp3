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
        val repeatMode: Int,
        val playWhenReady: Boolean
    )

    fun restore(state: PlaybackStateEntity): RestoredState? {
        return restore(state) { true }
    }

    fun restore(state: PlaybackStateEntity, isTrackAvailable: (TrackItem) -> Boolean): RestoredState? {
        return try {
            val allQueue = Json.decodeFromString<List<TrackItem>>(state.queueJson)
            val queueWithSourceIndex = allQueue.mapIndexedNotNull { sourceIndex, track ->
                val available = track.localPath.isNullOrBlank() || isTrackAvailable(track)
                if (available) sourceIndex to track else null
            }
            if (queueWithSourceIndex.isEmpty()) return null
            val queue = queueWithSourceIndex.map { it.second }
            val requestedIndex = if (allQueue.isEmpty()) 0 else state.currentIndex.coerceIn(0, allQueue.lastIndex)
            val restoredIndex = queueWithSourceIndex.indexOfFirst { it.first == requestedIndex }
                .takeIf { it >= 0 }
                ?: queueWithSourceIndex.indexOfFirst { it.first >= requestedIndex }
                    .takeIf { it >= 0 }
                ?: queue.lastIndex

            RestoredState(
                queue = queue,
                currentIndex = restoredIndex,
                positionMs = state.positionMs.coerceAtLeast(0L),
                shuffleEnabled = state.shuffleEnabled,
                repeatMode = state.repeatMode,
                playWhenReady = state.playWhenReady
            )
        } catch (e: Exception) {
            null
        }
    }
}
