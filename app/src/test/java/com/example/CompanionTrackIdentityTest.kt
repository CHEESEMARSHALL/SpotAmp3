package com.example

import com.example.data.PlaylistTrackEntity
import com.example.data.toTrackItem
import com.example.playback.TrackItem
import com.example.sync.spotCoreTrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionTrackIdentityTest {
    @Test
    fun normalizesHomeAndLibraryCompanionTrackIds() {
        assertEquals(
            "song-1",
            TrackItem("companion:song-1", "Song", "Artist", "Album", "", "", 1000).spotCoreTrackId()
        )
        assertEquals(
            "song-2",
            TrackItem(
                "ignored",
                "Song",
                "Artist",
                "Album",
                "http://spotcore.local:4182/api/v1/media/stream/song-2",
                "",
                1000
            ).spotCoreTrackId()
        )
    }

    @Test
    fun doesNotTreatPlexTracksAsSpotCoreEvents() {
        assertNull(
            TrackItem(
                "plex-123",
                "Song",
                "Artist",
                "Album",
                "/library/parts/123/file.mp3",
                "",
                1000
            ).spotCoreTrackId()
        )
    }

    @Test
    fun playlistTracksKeepTheCompanionIdWhenConvertedForSync() {
        val playlistTrack = PlaylistTrackEntity(
            playlistId = 1,
            ratingKey = "ignored-local-key",
            title = "Song",
            artist = "Artist",
            album = "Album",
            key = "http://spotcore.local:4182/api/v1/media/stream/song-3",
            thumb = "",
            duration = 1000
        )

        assertEquals("song-3", playlistTrack.toTrackItem().spotCoreTrackId())
    }
}
