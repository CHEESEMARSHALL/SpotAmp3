package com.example

import com.example.data.CachedTrack
import com.example.data.albumIdentity
import com.example.data.albumNavigationKey
import com.example.data.effectiveAlbumArtist
import com.example.data.findCachedAlbumTracks
import com.example.data.groupCachedAlbums
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

    @Test
    fun `album identity uses album artist when song artists differ`() {
        val tracks = listOf(
            CachedTrack("track-1", "Opening", "Song Artist", "Compilation", "/one", "", 1, albumArtist = "Album Artist"),
            CachedTrack("track-2", "Guest Track", "Featured Artist", "Compilation", "/two", "", 1, albumArtist = "Album Artist")
        )

        assertEquals("Album Artist", tracks.first().effectiveAlbumArtist())
        assertEquals(1, tracks.map { it.albumIdentity() }.distinct().size)
        assertEquals(albumNavigationKey("Album Artist", "Compilation"), tracks.first().toTrackItem().albumRatingKey)
    }

    @Test
    fun `legacy cache groups multi artist album despite track specific artwork urls`() {
        val tracks = listOf(
            CachedTrack("track-1", "Opening", "Song Artist One", "Soundtrack", "/one", "/cover/track-1", 1, year = 2024),
            CachedTrack("track-2", "Guest Track", "Song Artist Two", "Soundtrack", "/two", "/cover/track-2", 1, year = 2024)
        )

        val album = groupCachedAlbums(tracks).single()

        assertEquals("Various Artists", album.albumArtist)
        assertEquals(2, album.tracks.size)
        assertEquals(2, findCachedAlbumTracks(tracks, "Various Artists", "Soundtrack").size)
    }

    @Test
    fun `legacy server song artist substitution is treated as missing album artist`() {
        val tracks = listOf(
            CachedTrack("track-1", "Opening", "Song Artist One", "Soundtrack", "/one", "/cover/track-1", 1, year = 2024, albumArtist = "Song Artist One"),
            CachedTrack("track-2", "Guest Track", "Song Artist Two", "Soundtrack", "/two", "/cover/track-2", 1, year = 2024, albumArtist = "Song Artist Two")
        )

        val album = groupCachedAlbums(tracks).single()

        assertEquals("Various Artists", album.albumArtist)
        assertEquals(2, album.tracks.size)
    }

    @Test
    fun `legacy cache keeps same titled albums from different years separate`() {
        val tracks = listOf(
            CachedTrack("track-1", "Song One", "Artist One", "Greatest Hits", "/one", "", 1, year = 1999),
            CachedTrack("track-2", "Song Two", "Artist Two", "Greatest Hits", "/two", "", 1, year = 2005)
        )

        assertEquals(2, groupCachedAlbums(tracks).size)
    }
}
