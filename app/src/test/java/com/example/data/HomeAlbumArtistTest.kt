package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAlbumArtistTest {
    @Test
    fun prefersAlbumArtistAndUsesParentArtistWhenAlbumArtistIsBlank() {
        assertEquals(
            "Compilation Artist",
            PlexMetadata(
                ratingKey = "album-1",
                title = "Compilation",
                type = "album",
                parentTitle = "Parent Artist",
                albumArtist = "Compilation Artist"
            ).homeAlbumArtist()
        )
        assertEquals(
            "Parent Artist",
            PlexMetadata(
                ratingKey = "album-2",
                title = "Album",
                type = "album",
                parentTitle = "Parent Artist",
                albumArtist = " "
            ).homeAlbumArtist()
        )
    }

    @Test
    fun missingAlbumArtistUsesVariousArtistsSentinel() {
        assertEquals(
            "Various Artists",
            PlexMetadata(
                ratingKey = "album-3",
                title = "Compilation",
                type = "album",
                parentTitle = ""
            ).homeAlbumArtist()
        )
    }
}
