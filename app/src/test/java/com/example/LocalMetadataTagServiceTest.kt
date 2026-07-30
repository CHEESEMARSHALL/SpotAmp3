package com.example

import com.example.data.CachedTrack
import com.example.data.LocalMetadataTagService
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMetadataTagServiceTest {
    @Test
    fun `local tags preserve exact Plex genres and do not invent moods`() {
        val track = CachedTrack("1", "Song", "Artist", "Album", "/1", "", 1, genres = "Rock|Rock|Alternative")

        val result = LocalMetadataTagService().tag(track)

        assertEquals(emptyList<String>(), result.moods)
        assertEquals("unknown", result.energy)
        assertEquals(listOf("Rock", "Alternative"), result.styleTags)
    }
}
