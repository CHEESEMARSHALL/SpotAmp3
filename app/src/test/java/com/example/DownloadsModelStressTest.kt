package com.example

import com.example.data.DownloadedTrackEntity
import com.example.data.PlaylistTrackEntity
import com.example.data.filterDownloadedPlaylistTracks
import com.example.data.groupDownloadedAlbums
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsModelStressTest {
    @Test
    fun deterministicDownloadCollectionsPreserveIndependentModelInvariants() {
        val seed = (System.getProperty("spotamp.stress.seed") ?: System.getenv("SPOTAMP_STRESS_SEED"))
            ?.toIntOrNull() ?: 847229173
        val actions = (System.getProperty("spotamp.stress.actions") ?: System.getenv("SPOTAMP_STRESS_ACTIONS"))
            ?.toIntOrNull()?.coerceIn(1, 10_000) ?: 10_000
        val random = Random(seed)

        var downloads = (0 until 80).map { index ->
            download(index, status = if (index % 3 == 0) "completed" else "downloading")
        }
        val playlist = downloads.map { track ->
            PlaylistTrackEntity(
                id = track.ratingKey.hashCode(),
                playlistId = 7,
                ratingKey = track.ratingKey,
                title = track.title,
                artist = track.artist,
                album = track.album,
                key = track.key,
                thumb = track.thumb,
                duration = track.duration,
                addedAt = 0L
            )
        }

        repeat(actions) {
            val index = random.nextInt(downloads.size)
            val current = downloads[index]
            downloads = downloads.toMutableList().also {
                it[index] = current.copy(
                    status = if (random.nextBoolean()) "completed" else "failed",
                    localPath = if (random.nextBoolean()) "/qa/" + current.ratingKey + ".mp3" else null,
                    fileSize = random.nextLong(0L, 4096L)
                )
            }

            val expectedPlayableKeys = downloads
                .filter { it.status == "completed" && !it.localPath.isNullOrBlank() }
                .map { it.ratingKey }
                .toSet()
            val actualOffline = filterDownloadedPlaylistTracks(playlist, downloads) { true }

            assertEquals(
                expectedPlayableKeys,
                actualOffline.map { it.ratingKey }.toSet()
            )
            assertEquals(
                actualOffline.size,
                actualOffline.map { it.ratingKey }.distinct().size
            )

            val groups = groupDownloadedAlbums(downloads.filter { it.status == "completed" })
            assertEquals(
                downloads.filter { it.status == "completed" }.size,
                groups.sumOf { it.tracks.size }
            )
            assertTrue(groups.all { group -> group.tracks.all { it.album.trim().equals(group.album.trim(), ignoreCase = true) } })
        }

        println("DOWNLOAD_STRESS seed=" + seed + " actions=" + actions + " finalPlayable=" + filterDownloadedPlaylistTracks(playlist, downloads) { true }.size)
    }

    private fun download(index: Int, status: String) = DownloadedTrackEntity(
        ratingKey = "download-" + index,
        title = "Track " + index,
        artist = if (index % 5 == 0) "Artist A" else "Artist B",
        album = if (index % 2 == 0) "Album " + (index % 4) else "album " + (index % 4),
        key = "/track/" + index,
        thumb = "",
        duration = 180_000L,
        fileSize = 2048L,
        localPath = if (status == "completed") "/qa/download-" + index + ".mp3" else null,
        status = status
    )
}
