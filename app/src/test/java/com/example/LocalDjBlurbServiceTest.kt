package com.example

import com.example.data.LocalDjBlurbService
import com.example.playback.TrackItem
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDjBlurbServiceTest {
    @Test
    fun `blurb is deterministic and uses only track and queue context`() {
        val track = TrackItem("1", "Song", "Artist", "Album", "/song", "", 1L, genres = listOf("Ambient"))

        assertEquals(
            "Track 2 of 5 / Artist from Album / Ambient",
            LocalDjBlurbService().describe(track, 1, 5)
        )
    }
}
