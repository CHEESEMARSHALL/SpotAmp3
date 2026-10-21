package com.example

import com.example.data.CachedTrack
import com.example.data.PlexMetadata
import com.example.data.PlexTag
import com.example.data.buildLibraryMetadataFacets
import com.example.data.hasGenreTag
import com.example.data.hasStyleTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFacetsTest {
    @Test
    fun `library facets come from persisted metadata rather than placeholder values`() {
        val artist = PlexMetadata(
            ratingKey = "artist-1",
            title = "Artist",
            genres = listOf(PlexTag("Rock"))
        )
        val album = PlexMetadata(
            ratingKey = "album-1",
            title = "Album",
            genres = listOf(PlexTag("Alternative"))
        )
        val cachedTrack = CachedTrack(
            ratingKey = "track-1",
            title = "Track",
            artist = "Artist",
            album = "Album",
            key = "/track-1",
            thumb = "",
            duration = 1,
            genres = "Ambient|Ambient"
        )

        val facets = buildLibraryMetadataFacets(listOf(artist), listOf(album), listOf(cachedTrack))

        assertEquals(listOf("Ambient", "Rock"), facets.artistGenres)
        assertEquals(listOf("Alternative", "Ambient"), facets.albumGenres)
        assertEquals(listOf("Alternative", "Ambient"), facets.styles)
        assertTrue(facets.moods.isEmpty())
    }

    @Test
    fun `library facet matching is exact and case insensitive`() {
        val artist = PlexMetadata(
            ratingKey = "artist-1",
            title = "Artist",
            genres = listOf(PlexTag("Rock"))
        )
        val track = CachedTrack("1", "Song", "Artist", "Album", "/1", "", 1, genres = "Rock")

        assertTrue(artist.hasGenreTag("rock"))
        assertTrue(track.hasStyleTag("ROCK"))
        assertFalse(artist.hasGenreTag("Hard Rock"))
        assertFalse(track.hasStyleTag("Hard Rock"))
    }
}
