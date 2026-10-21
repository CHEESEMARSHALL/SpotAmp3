package com.example

import com.example.data.BackendDailyMixDto
import com.example.data.BackendAlbumDto
import com.example.data.BackendArtistProfileDto
import com.example.data.BackendSimilarArtistDto
import com.example.data.BackendHomeFeedResponse
import com.example.data.BackendHomeMapper
import com.example.data.BackendStationDto
import com.example.data.BackendTrackDto
import com.example.data.HomeFeedSource
import com.example.data.HomeFeedState
import com.example.data.RecommendedStation
import com.example.data.RadioType
import com.example.data.albumNavigationKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendHomeMapperTest {
    private val track = BackendTrackDto(
        id = "song-1",
        title = "Signal",
        artist = "Artist",
        album = "Album",
        streamUrl = "http://spotcore:4182/api/v1/media/stream/song-1",
        coverUrl = "http://spotcore:4182/api/v1/media/art/song-1",
        duration = 123_000.0,
        genre = "Alternative",
        albumArtist = "Album Artist"
    )

    @Test
    fun `companion artist profile carries bio and per-artist metadata`() {
        val profile = BackendArtistProfileDto(
            artist = "Artist",
            bio = "A synced biography.",
            similarArtists = listOf(BackendSimilarArtistDto("Neighbor", "Rock")),
            styles = listOf("Rock", "Alternative"),
            source = "Last.fm"
        )

        assertEquals("Artist", profile.name ?: profile.artist)
        assertEquals("A synced biography.", profile.bio)
        assertEquals("Neighbor", profile.similarArtists.single().name)
        assertEquals(listOf("Rock", "Alternative"), profile.styles)
        assertEquals("Last.fm", profile.source)
    }

    @Test
    fun `companion content replaces matching shelves and marks SpotCore source`() {
        val response = BackendHomeFeedResponse(
            recentlyPlayed = listOf(track),
            dailyMixes = listOf(
                BackendDailyMixDto(
                    id = "mix-1",
                    title = "Similar Sound",
                    reason = "CLAP neighbors",
                    colors = listOf("#112233", "invalid"),
                    tracks = listOf(track)
                )
            ),
            stations = listOf(
                BackendStationDto(
                    id = "station-1",
                    title = "Mood Radio",
                    subtitle = "Taxonomy: Brooding",
                    type = "mood-radio",
                    colors = listOf("#445566")
                )
            )
        )

        val merged = BackendHomeMapper.merge(response, HomeFeedState())

        assertEquals(HomeFeedSource.SPOTCORE, merged.source)
        assertEquals("companion:song-1", merged.dailyMixes.single().tracks.single().ratingKey)
        assertEquals(
            albumNavigationKey("Album Artist", "Album"),
            merged.dailyMixes.single().tracks.single().albumRatingKey
        )
        assertEquals(0xFF112233, merged.dailyMixes.single().colors.single())
        assertEquals(RadioType.MOOD_RADIO, merged.stations.single().type)
        assertEquals("From SpotCore", merged.recentPlays.single().relativeTime)
    }

    @Test
    fun `blank companion cover falls back to the authenticated track artwork endpoint`() {
        val merged = BackendHomeMapper.merge(
            BackendHomeFeedResponse(recentlyPlayed = listOf(track.copy(coverUrl = ""))),
            HomeFeedState(),
            artworkBaseUrl = "http://spotcore:4182"
        )

        assertEquals(
            "http://spotcore:4182/api/v1/media/art/song-1",
            merged.recentPlays.single().thumb
        )

        val embedded = BackendHomeMapper.merge(
            BackendHomeFeedResponse(
                recentlyPlayed = listOf(track.copy(coverUrl = "data:image/png;base64,AQID"))
            ),
            HomeFeedState(),
            artworkBaseUrl = "http://spotcore:4182"
        )

        assertEquals(
            "http://spotcore:4182/api/v1/media/art/song-1",
            embedded.recentPlays.single().thumb
        )
    }

    @Test
    fun `valid empty companion payload does not resurrect local shelves`() {
        val fallbackStation = RecommendedStation(
            id = "plex",
            title = "Library Radio",
            subtitle = "Plex fallback",
            type = RadioType.LIBRARY_RADIO,
            gradientColors = listOf(0xFF000000)
        )
        val fallback = HomeFeedState(stations = listOf(fallbackStation))

        val merged = BackendHomeMapper.merge(BackendHomeFeedResponse(), fallback)

        assertEquals(HomeFeedSource.SPOTCORE, merged.source)
        assertTrue(merged.stations.isEmpty())
        assertTrue(merged.recentPlays.isEmpty())
        assertTrue(merged.albumShelves.isEmpty())
    }

    @Test
    fun `companion shelves expose albums with their playable tracks`() {
        val response = BackendHomeFeedResponse(
            madeForYou = listOf(
                com.example.data.BackendMadeForYouDto(
                    id = "mfy-albums",
                    title = "Similar albums",
                    description = "CLAP and taxonomy matches",
                    artists = listOf("Artist"),
                    tracks = listOf(track),
                    coverUrl = track.coverUrl.orEmpty(),
                    albums = listOf(
                        BackendAlbumDto(
                            id = "Artist\\u0000Album",
                            title = "Album",
                            artist = "Artist",
                            coverUrl = track.coverUrl.orEmpty(),
                            tracks = listOf(track)
                        )
                    )
                )
            )
        )

        val merged = BackendHomeMapper.merge(response, HomeFeedState())

        assertEquals("Album", merged.albumShelves.single().albums.single().title)
        assertEquals("companion:song-1", merged.albumShelves.single().albums.single().tracks.single().ratingKey)
        assertTrue(merged.madeForYou.isEmpty())
    }

    @Test
    fun `recently added albums are not repeated in recommendation shelves`() {
        val response = BackendHomeFeedResponse(
            recentlyAdded = listOf(
                BackendAlbumDto(
                    id = "recent-album",
                    title = "Album",
                    artist = "Artist",
                    coverUrl = track.coverUrl
                )
            ),
            madeForYou = listOf(
                com.example.data.BackendMadeForYouDto(
                    id = "mfy-albums",
                    title = "Similar albums",
                    description = "CLAP and taxonomy matches",
                    artists = listOf("Artist"),
                    tracks = listOf(track),
                    coverUrl = track.coverUrl.orEmpty(),
                    albums = listOf(
                        BackendAlbumDto(
                            id = "recommended-album",
                            title = "Album",
                            artist = "Artist",
                            coverUrl = track.coverUrl.orEmpty(),
                            tracks = listOf(track)
                        )
                    )
                )
            )
        )

        val merged = BackendHomeMapper.merge(response, HomeFeedState())

        assertEquals(1, merged.recentlyAdded.size)
        assertTrue(merged.albumShelves.isEmpty())
    }
}
