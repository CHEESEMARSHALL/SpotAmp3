package com.example

import com.example.data.companionArtworkProxyUrl
import com.example.data.normalizeCompanionArtistArtworkUrl
import com.example.data.normalizeCompanionArtworkUrl
import com.example.data.resolveArtworkReference
import com.example.data.resolveCompanionMediaReference
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkReferencesTest {
    @Test
    fun `relative and absolute references resolve without corrupting URLs`() {
        assertEquals(
            "https://images.example.test/cover art.jpg",
            resolveArtworkReference("http://media.example.test", "https://images.example.test/cover art.jpg")
        )
        assertEquals(
            "http://spotcore.example.test/images/cover.jpg",
            resolveArtworkReference("http://spotcore.example.test/", "/images/cover.jpg")
        )
        assertEquals(
            "https://cdn.example.test/art.jpg",
            resolveArtworkReference("https://spotcore.example.test", "//cdn.example.test/art.jpg")
        )
        assertEquals(
            "data:image/png;base64,abc",
            resolveArtworkReference("http://spotcore.example.test", "data:image/png;base64,abc")
        )
    }

    @Test
    fun `companion normalization uses the canonical companion artwork path`() {
        val companion = "http://spotcore.example.test:4182"
        val proxy = "$companion/api/v1/media/art/song%2F1"

        assertEquals(proxy, companionArtworkProxyUrl(companion, "song/1"))
        assertEquals(
            "",
            normalizeCompanionArtworkUrl(companion, "song-1", null)
        )
        assertEquals(
            "$companion/api/v1/media/art/song-1",
            normalizeCompanionArtworkUrl(companion, "song-1", "")
        )
        assertEquals(
            "$companion/api/v1/media/art/song-1?v=123-456",
            normalizeCompanionArtworkUrl(
                companion,
                "song-1",
                "/api/v1/media/art/song-1?v=123-456"
            )
        )
        assertEquals(
            "$companion/api/v1/media/art/song-1",
            normalizeCompanionArtworkUrl(
                companion,
                "song-1",
                "data:image/(null);base64,/9j/4AAQ"
            )
        )
        assertEquals(
            "$companion/api/v1/media/art/song-1",
            normalizeCompanionArtworkUrl(
                companion,
                "song-1",
                "spotcore-artwork://cache/0123456789abcdef"
            )
        )
        assertEquals(
            "$companion/api/v1/media/art/song-1",
            normalizeCompanionArtworkUrl(
                companion,
                "song-1",
                "/legacy/artwork/42"
            )
        )
        assertEquals(
            "https://images.example.test/cover.jpg",
            normalizeCompanionArtworkUrl(
                companion,
                "song-1",
                "https://images.example.test/cover.jpg"
            )
        )
    }

    @Test
    fun `companion artist artwork resolves profile paths and rejects desktop references`() {
        val companion = "http://spotcore.example.test:4182/"

        assertEquals(
            "http://spotcore.example.test:4182/api/v1/artists/artist-1.jpg",
            normalizeCompanionArtistArtworkUrl(companion, "/api/v1/artists/artist-1.jpg")
        )
        assertEquals(
            "https://images.example.test/artist.jpg",
            normalizeCompanionArtistArtworkUrl(companion, "https://images.example.test/artist.jpg")
        )
        assertEquals(
            null,
            normalizeCompanionArtistArtworkUrl(companion, "spotcore-artwork://cache/artist-1")
        )
    }

    @Test
    fun `relative SpotCore media routes resolve against paired origin`() {
        val companion = "http://spotcore.example.test:4182/"

        assertEquals(
            "http://spotcore.example.test:4182/api/v1/media/stream/song-1",
            resolveCompanionMediaReference(companion, "/api/v1/media/stream/song-1")
        )
        assertEquals(
            "https://cdn.example.test/audio/song.mp3",
            resolveCompanionMediaReference(companion, "https://cdn.example.test/audio/song.mp3")
        )
    }
}
