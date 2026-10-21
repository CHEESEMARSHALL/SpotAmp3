package com.example.ui

import com.example.data.PlexMedia
import com.example.data.PlexMetadata
import com.example.data.PlexPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MusicContextMenuMetadataTest {
    private val albumItem = ContextMenuItem.Album(
        ratingKey = "album-1",
        title = "Blue Train",
        artist = "Various Artists",
        thumb = "album-thumb"
    )

    @Test
    fun albumQueueUsesAlbumArtistFallbackInsteadOfTheAlbumTitleAsArtist() {
        val track = track(
            parentTitle = "Blue Train",
            grandparentTitle = "",
            albumArtist = ""
        )

        val queued = track.toAlbumContextTrackItem(
            album = albumItem,
            unknownArtist = "Unknown Artist",
            unknownAlbum = "Unknown Album"
        )

        assertNotNull(queued)
        assertEquals("Various Artists", queued?.artist)
        assertEquals("Blue Train", queued?.album)
    }

    @Test
    fun artistQueueUsesRouteArtistWhenTrackArtistMetadataIsBlank() {
        val track = track(
            parentTitle = "Blue Train",
            grandparentTitle = " ",
            albumArtist = null
        )

        val queued = track.toArtistQueueTrackItem(
            artistName = "John Coltrane",
            unknownArtist = "Unknown Artist",
            unknownAlbum = "Unknown Album"
        )

        assertNotNull(queued)
        assertEquals("John Coltrane", queued?.artist)
        assertEquals("Blue Train", queued?.album)
        assertEquals("album-1", queued?.albumRatingKey)
    }

    @Test
    fun artistQueueUsesSharedFallbacksAndRejectsTracksWithoutPlayableMedia() {
        val queued = track(parentTitle = "", grandparentTitle = "", albumArtist = "")
            .toArtistQueueTrackItem("", "Unknown Artist", "Unknown Album")
        val unplayable = PlexMetadata(ratingKey = "no-media", title = "No media", type = "track")
            .toArtistQueueTrackItem("Artist", "Unknown Artist", "Unknown Album")

        assertNotNull(queued)
        assertEquals("Unknown Artist", queued?.artist)
        assertEquals("Unknown Album", queued?.album)
        assertNull(unplayable)
    }

    private fun track(
        parentTitle: String?,
        grandparentTitle: String?,
        albumArtist: String?
    ) = PlexMetadata(
        ratingKey = "track-1",
        title = "Blue Train",
        type = "track",
        parentTitle = parentTitle,
        grandparentTitle = grandparentTitle,
        albumArtist = albumArtist,
        parentRatingKey = "album-1",
        media = listOf(PlexMedia(part = listOf(PlexPart(key = "/library/parts/track-1"))))
    )
}
