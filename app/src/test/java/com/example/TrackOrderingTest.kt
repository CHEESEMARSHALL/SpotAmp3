package com.example

import com.example.data.BackendTrackDto
import com.example.data.CachedTrack
import com.example.data.findCachedAlbumTracks
import com.example.data.resolvedDiscNumber
import com.example.data.resolvedTrackNumber
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackOrderingTest {
    @Test
    fun companionPositionsAcceptNumericAndFractionalAliases() {
        assertEquals(7, backend(trackNumber = "07/17").resolvedTrackNumber())
        assertEquals(3, backend(index = 3).resolvedTrackNumber())
        assertEquals(2, backend(discNumber = "2/2").resolvedDiscNumber())
    }

    @Test
    fun cachedAlbumTracksSortByDiscThenTrackInsteadOfTitle() {
        val tracks = listOf(
            cached("Bad Company Remix", disc = 1, track = 17),
            cached("Blue On Black", disc = 1, track = 1),
            cached("Broken World", disc = 1, track = 3),
            cached("Disc Two Opener", disc = 2, track = 1)
        )

        assertEquals(
            listOf("Blue On Black", "Broken World", "Bad Company Remix", "Disc Two Opener"),
            findCachedAlbumTracks(tracks, "Album Artist", "Album").map { it.title }
        )
    }

    private fun backend(
        index: Any? = null,
        trackNumber: Any? = null,
        discNumber: Any? = null
    ) = BackendTrackDto(
        id = "id",
        title = "Title",
        artist = "Artist",
        album = "Album",
        streamUrl = "http://localhost/stream/id",
        coverUrl = "",
        duration = 1.0,
        index = index,
        trackNumber = trackNumber,
        discNumber = discNumber
    )

    private fun cached(title: String, disc: Int, track: Int) = CachedTrack(
        ratingKey = "$disc-$track",
        title = title,
        artist = "Artist",
        album = "Album",
        key = "",
        thumb = "",
        duration = 1,
        albumArtist = "Album Artist",
        index = track,
        discNumber = disc
    )
}
