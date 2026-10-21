package com.example

import com.example.data.DownloadedTrackEntity
import com.example.data.PlaylistTrackEntity
import com.example.data.filterDownloadedPlaylistTracks
import com.example.data.completedDownloadRatingKeys
import com.example.data.completedDownloadAlbumIdentities
import com.example.data.CachedTrack
import com.example.data.locallyAvailableDownloadRatingKeys
import com.example.data.locallyAvailableDownloadArtistNames
import com.example.data.formatDownloadDuration
import com.example.data.formatDownloadSize
import com.example.data.groupDownloadedAlbums
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsModelTest {
    @Test
    fun `tracks with the same album become one collection`() {
        val tracks = listOf(
            downloadedTrack("one", "First song", "Artist", "Album"),
            downloadedTrack("two", "Second song", "Artist", "Album")
        )

        val groups = groupDownloadedAlbums(tracks)

        assertEquals(1, groups.size)
        assertEquals("Album", groups.single().album)
        assertEquals("Artist", groups.single().artist)
        assertEquals(2, groups.single().tracks.size)
    }

    @Test
    fun `mixed artist album remains one album collection`() {
        val tracks = listOf(
            downloadedTrack("one", "First song", "Artist One", "Soundtrack"),
            downloadedTrack("two", "Second song", "Artist Two", "Soundtrack")
        )

        val group = groupDownloadedAlbums(tracks).single()

        assertEquals("Various Artists", group.artist)
        assertEquals(2, group.tracks.size)
    }

    @Test
    fun `playlist collection keeps only completed local tracks`() {
        val playlistTracks = listOf(
            playlistTrack("one", "Downloaded"),
            playlistTrack("two", "Queued")
        )
        val downloadedTracks = listOf(
            downloadedTrack("one", "Downloaded", "Artist", "Album"),
            downloadedTrack("two", "Queued", "Artist", "Album").copy(
                status = "queued",
                localPath = null
            )
        )

        val offlineTracks = filterDownloadedPlaylistTracks(playlistTracks, downloadedTracks) { true }

        assertEquals(listOf("one"), offlineTracks.map { it.ratingKey })
    }

    @Test
    fun `offline library keys include only completed downloads with a local file`() {
        val downloads = listOf(
            downloadedTrack("ready", "Ready", "Artist", "Album"),
            downloadedTrack("queued", "Queued", "Artist", "Album").copy(status = "queued", localPath = null),
            downloadedTrack("missing-file", "Missing", "Artist", "Album").copy(localPath = null),
            downloadedTrack("failed", "Failed", "Artist", "Album").copy(status = "failed")
        )

        assertEquals(setOf("ready"), completedDownloadRatingKeys(downloads))
    }

    @Test
    fun `offline album identities include artist and use cached album artist`() {
        val downloads = listOf(
            downloadedTrack("compilation-track", "Guest song", "Guest Artist", "Shared title"),
            downloadedTrack("legacy-track", "Old song", "Legacy Artist", "Legacy album")
        )
        val cachedTracks = listOf(
            CachedTrack(
                ratingKey = "compilation-track",
                title = "Guest song",
                artist = "Guest Artist",
                album = "Shared title",
                key = "/compilation-track",
                thumb = "",
                duration = 180_000L,
                albumArtist = "Compilation Artist"
            )
        )

        assertEquals(
            setOf("compilation artist" to "shared title", "legacy artist" to "legacy album"),
            completedDownloadAlbumIdentities(downloads, cachedTracks) { true }
        )
        assertEquals(
            false,
            ("different artist" to "shared title") in completedDownloadAlbumIdentities(downloads, cachedTracks) { true }
        )
    }

    @Test
    fun `missing or empty local files are not counted as available offline`() {
        val downloads = listOf(
            downloadedTrack("present", "Present", "Artist", "Album"),
            downloadedTrack("missing", "Missing", "Artist", "Album"),
            downloadedTrack("empty", "Empty", "Artist", "Album").copy(fileSize = 0L)
        )

        assertEquals(
            setOf("present"),
            locallyAvailableDownloadRatingKeys(downloads) { path -> path.endsWith("present.mp3") }
        )
        assertEquals(
            listOf("present"),
            filterDownloadedPlaylistTracks(
                listOf(playlistTrack("present", "Present"), playlistTrack("missing", "Missing")),
                downloads
            ) { path -> path.endsWith("present.mp3") }
                .map { it.ratingKey }
        )
    }

    @Test
    fun `downloaded artist names include only playable local files`() {
        val downloads = listOf(
            downloadedTrack("ready", "Ready", " Nina Simone ", "Album"),
            downloadedTrack("missing", "Missing", "Missing Artist", "Album"),
            downloadedTrack("empty", "Empty", "Empty Artist", "Album").copy(fileSize = 0L),
            downloadedTrack("queued", "Queued", "Queued Artist", "Album").copy(status = "queued")
        )

        assertEquals(
            setOf("nina simone"),
            locallyAvailableDownloadArtistNames(downloads) { path -> path.endsWith("ready.mp3") }
        )
    }

    @Test
    fun `download summary formatting matches compact media metadata`() {
        assertEquals("70.2 GB", formatDownloadSize(70_200_000_000L))
        assertEquals("1 minute", formatDownloadDuration(60_000L))
        assertEquals("1 day", formatDownloadDuration(24 * 60 * 60_000L))
        assertEquals("1 hr, 47 min", formatDownloadDuration(107 * 60_000L))
        assertEquals("8 days", formatDownloadDuration(8 * 24 * 60 * 60_000L))
    }

    private fun downloadedTrack(
        ratingKey: String,
        title: String,
        artist: String,
        album: String
    ) = DownloadedTrackEntity(
        ratingKey = ratingKey,
        title = title,
        artist = artist,
        album = album,
        key = "/$ratingKey",
        thumb = "",
        duration = 180_000L,
        fileSize = 1_000L,
        localPath = "/music/$ratingKey.mp3"
    )

    private fun playlistTrack(ratingKey: String, title: String) = PlaylistTrackEntity(
        playlistId = 1,
        ratingKey = ratingKey,
        title = title,
        artist = "Artist",
        album = "Album",
        key = "/$ratingKey",
        thumb = "",
        duration = 180_000L
    )
}
