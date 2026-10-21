package com.example

import com.example.playback.PlaybackQueuePresentation
import com.example.playback.QueueItemPosition
import com.example.playback.TrackItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueuePresentationTest {
    @Test
    fun identifiesPlayedCurrentAndUpcomingTracksAroundActiveIndex() {
        assertEquals(QueueItemPosition.PLAYED_EARLIER, PlaybackQueuePresentation.itemPosition(0, 1))
        assertEquals(QueueItemPosition.NOW_PLAYING, PlaybackQueuePresentation.itemPosition(1, 1))
        assertEquals(QueueItemPosition.UP_NEXT, PlaybackQueuePresentation.itemPosition(2, 1))
        assertEquals(QueueItemPosition.UP_NEXT, PlaybackQueuePresentation.itemPosition(0, -1))
    }

    @Test
    fun clearingUpcomingKeepsTheCurrentTrackAndEarlierQueueContext() {
        val queue = listOf(track("one"), track("two"), track("three"), track("four"))

        assertEquals(listOf("one", "two"), PlaybackQueuePresentation.keepCurrentAndEarlier(queue, 1).map { it.ratingKey })
        assertEquals(emptyList<TrackItem>(), PlaybackQueuePresentation.keepCurrentAndEarlier(queue, -1))
        assertEquals(2, PlaybackQueuePresentation.upNextCount(queue.size, 1))
        assertEquals(0, PlaybackQueuePresentation.upNextCount(1, 0))
    }

    @Test
    fun playlistSaveRequiresANonblankName() {
        assertEquals(false, PlaybackQueuePresentation.canSaveAsPlaylist(""))
        assertEquals(false, PlaybackQueuePresentation.canSaveAsPlaylist("   "))
        assertEquals(true, PlaybackQueuePresentation.canSaveAsPlaylist("Road trip"))
    }

    private fun track(id: String) = TrackItem(id, id, "Artist", "Album", "/$id", "", 1000)
}
