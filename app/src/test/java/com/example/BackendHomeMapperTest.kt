package com.example

import com.example.data.BackendDailyMixDto
import com.example.data.BackendAlbumDto
import com.example.data.BackendHomeFeedResponse
import com.example.data.BackendHomeMapper
import com.example.data.BackendStationDto
import com.example.data.BackendTrackDto
import com.example.data.HomeFeedSource
import com.example.data.HomeFeedState
import com.example.data.RecommendedStation
import com.example.data.RadioType
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
        duration = 123_000,
        genre = "Alternative"
    )

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
        assertEquals(0xFF112233, merged.dailyMixes.single().colors.single())
        assertEquals(RadioType.MOOD_RADIO, merged.stations.single().type)
        assertEquals("From SpotCore", merged.recentPlays.single().relativeTime)
    }

    @Test
    fun `partial or empty companion payload preserves Plex fallback`() {
        val fallbackStation = RecommendedStation(
            id = "plex",
            title = "Library Radio",
            subtitle = "Plex fallback",
            type = RadioType.LIBRARY_RADIO,
            gradientColors = listOf(0xFF000000)
        )
        val fallback = HomeFeedState(stations = listOf(fallbackStation))

        val merged = BackendHomeMapper.merge(BackendHomeFeedResponse(), fallback)

        assertEquals(HomeFeedSource.PLEX, merged.source)
        assertTrue(merged.stations.single() === fallbackStation)
    }

    @Test
    fun `companion shelves expose albums with their playable tracks`() {
        val response = BackendHomeFeedResponse(
            dailyMixes = listOf(
                BackendDailyMixDto(
                    id = "mix-albums",
                    title = "Similar albums",
                    reason = "CLAP and taxonomy matches",
                    colors = emptyList(),
                    tracks = listOf(track),
                    albums = listOf(
                        BackendAlbumDto(
                            id = "Artist\\u0000Album",
                            title = "Album",
                            artist = "Artist",
                            coverUrl = track.coverUrl,
                            tracks = listOf(track)
                        )
                    )
                )
            )
        )

        val merged = BackendHomeMapper.merge(response, HomeFeedState())

        assertEquals("Album", merged.albumShelves.single().albums.single().title)
        assertEquals("companion:song-1", merged.albumShelves.single().albums.single().tracks.single().ratingKey)
    }
}
