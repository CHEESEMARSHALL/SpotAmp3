package com.example.data

import com.example.playback.TrackItem

/**
 * Album identity is owned by the album artist, while a track can still have
 * its own song artist (for example, a compilation or a featured guest).
 * Older cache rows do not have this field. Individual tracks still fall back
 * to their song artist, but album lists need collection-level grouping so a
 * multi-artist album is not split into one tile per song artist.
 */
fun effectiveAlbumArtist(albumArtist: String?, trackArtist: String): String =
    albumArtist?.trim()?.takeIf { it.isNotEmpty() } ?: trackArtist

fun CachedTrack.effectiveAlbumArtist(): String = effectiveAlbumArtist(albumArtist, artist)

fun CachedTrack.albumIdentity(): Pair<String, String> = effectiveAlbumArtist() to album

data class CachedAlbumGroup(
    val albumArtist: String,
    val album: String,
    val tracks: List<CachedTrack>
)

private data class CachedAlbumCohortKey(
    val album: String,
    val year: Int?
)

/**
 * Groups current rows by the authoritative album artist. For cache rows that
 * predate albumArtist, album title plus release year is the strongest stable
 * album-level identity available: companion artwork URLs are track-specific
 * even when the returned image is shared by the whole album.
 */
fun groupCachedAlbums(tracks: List<CachedTrack>): List<CachedAlbumGroup> = tracks
    .groupBy { track ->
        CachedAlbumCohortKey(track.album.trim().lowercase(), track.year)
    }
    .values
    .flatMap { cohortTracks ->
        val storedAlbumArtists = cohortTracks
            .map { it.albumArtist.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        val songArtistSubstitution = storedAlbumArtists.size > 1 && cohortTracks.all {
            it.albumArtist.isBlank() || it.albumArtist.trim().equals(it.artist.trim(), ignoreCase = true)
        }
        val albumGroups = if (storedAlbumArtists.isEmpty() || songArtistSubstitution) {
            listOf(cohortTracks)
        } else {
            cohortTracks.groupBy { it.effectiveAlbumArtist().trim().lowercase() }.values
        }

        albumGroups.map { albumTracks ->
            val storedAlbumArtist = albumTracks
                .map { it.albumArtist.trim() }
                .firstOrNull { it.isNotEmpty() }
                ?.takeUnless { songArtistSubstitution }
            val songArtists = albumTracks
                .map { it.artist.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
            CachedAlbumGroup(
                albumArtist = storedAlbumArtist
                    ?: songArtists.singleOrNull()
                    ?: "Various Artists",
                album = albumTracks.first().album,
                tracks = sortCachedTracksInPlaybackOrder(albumTracks)
            )
        }
    }

fun sortCachedTracksInPlaybackOrder(tracks: List<CachedTrack>): List<CachedTrack> =
    tracks.sortedWith(
        compareBy<CachedTrack> { if (it.discNumber <= 0) 1 else it.discNumber }
            .thenBy { if (it.index <= 0) Int.MAX_VALUE else it.index }
    )

fun findCachedAlbumTracks(
    tracks: List<CachedTrack>,
    albumArtist: String,
    album: String
): List<CachedTrack> = groupCachedAlbums(tracks)
    .firstOrNull {
        it.albumArtist.equals(albumArtist, ignoreCase = true) &&
            it.album.equals(album, ignoreCase = true)
    }
    ?.tracks
    .orEmpty()

/** Orders album child responses by disc and track number, keeping source order for ties. */
fun sortAlbumTracksInPlaybackOrder(tracks: List<PlexMetadata>): List<PlexMetadata> =
    tracks.sortedWith(
        compareBy<PlexMetadata> { it.parentIndex?.takeIf { disc -> disc > 0 } ?: 1 }
            .thenBy { it.index?.takeIf { number -> number > 0 } ?: Int.MAX_VALUE }
    )

fun sortTrackItemsInPlaybackOrder(tracks: List<TrackItem>): List<TrackItem> =
    tracks.sortedWith(
        compareBy<TrackItem> { it.discNumber.takeIf { disc -> disc > 0 } ?: 1 }
            .thenBy { it.index.takeIf { number -> number > 0 } ?: Int.MAX_VALUE }
    )
