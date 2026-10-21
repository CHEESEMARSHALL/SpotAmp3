package com.example.playback

enum class QueueItemPosition {
    PLAYED_EARLIER,
    NOW_PLAYING,
    UP_NEXT
}

object PlaybackQueuePresentation {
    fun itemPosition(index: Int, currentIndex: Int): QueueItemPosition = when {
        currentIndex < 0 || index > currentIndex -> QueueItemPosition.UP_NEXT
        index == currentIndex -> QueueItemPosition.NOW_PLAYING
        else -> QueueItemPosition.PLAYED_EARLIER
    }

    fun upNextCount(queueSize: Int, currentIndex: Int): Int =
        (queueSize - currentIndex - 1).coerceIn(0, queueSize)

    fun canSaveAsPlaylist(name: String): Boolean = name.isNotBlank()

    /** Keeps the current item and prior queue context, removing only what follows it. */
    fun keepCurrentAndEarlier(queue: List<TrackItem>, currentIndex: Int): List<TrackItem> =
        if (currentIndex in queue.indices) queue.take(currentIndex + 1) else emptyList()
}
