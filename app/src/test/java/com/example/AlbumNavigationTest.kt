package com.example

import com.example.data.CachedTrack
import com.example.data.albumNavigationKey
import com.example.data.parseAlbumNavigationKey
import com.example.data.toTrackItem
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumNavigationTest {
    @Test
    fun `local album key round trips artist and title`() {
        val key = albumNavigationKey("Artist", "Album")

        assertEquals("Artist" to "Album", parseAlbumNavigationKey(key))
    }

    @Test
    fun `cached track carries local album key into playback`() {
        val track = CachedTrack(
            ratingKey = "track-1",
            title = "Song",
            artist = "Artist",
            album = "Album",
            key = "/track-1",
            thumb = "",
            duration = 180_000
        )

        assertEquals(albumNavigationKey("Artist", "Album"), track.toTrackItem().albumRatingKey)
    }
}
