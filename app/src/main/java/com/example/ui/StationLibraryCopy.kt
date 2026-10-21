package com.example.ui

import com.example.R
import com.example.data.RadioType
import com.example.data.DiscoveryLevel
import com.example.data.RadioRequest
import com.example.data.CachedTrack
import com.example.data.LocalMetadataTagService

internal sealed interface StationLibrarySummary {
    data object Checking : StationLibrarySummary
    data object NoMatches : StationLibrarySummary
    data class Matches(val count: Int) : StationLibrarySummary
}

internal fun stationLibrarySummaryString(summary: StationLibrarySummary): Int = when (summary) {
    StationLibrarySummary.Checking -> R.string.station_checking_library_tracks
    StationLibrarySummary.NoMatches -> R.string.station_no_matching_library_tracks
    is StationLibrarySummary.Matches -> R.string.station_matching_library_tracks
}

internal fun stationArtistStatusString(isLoading: Boolean): Int =
    if (isLoading) R.string.station_loading_artists else R.string.station_no_artists_available

internal fun stationSearchHintString(type: RadioType): Int = when (type) {
    RadioType.GENRE_RADIO -> R.string.station_search_genre_hint
    RadioType.MOOD_RADIO -> R.string.station_search_mood_hint
    RadioType.STYLE_RADIO -> R.string.station_search_style_hint
    RadioType.DECADE_RADIO -> R.string.station_search_decade_hint
    RadioType.TIME_TRAVEL -> R.string.station_search_time_period_hint
    else -> R.string.station_search_options_hint
}

internal fun searchFailureString(hasSavedLibrary: Boolean): Int =
    if (hasSavedLibrary) R.string.search_failure_with_saved_library
    else R.string.search_failure_without_saved_library

internal fun stationDiscoveryLabel(level: DiscoveryLevel): String = when (level) {
    DiscoveryLevel.FAMILIAR -> "Familiar"
    DiscoveryLevel.BALANCED -> "Balanced"
    DiscoveryLevel.DEEP_CUTS -> "Deep cuts"
}

internal data class StationTileCopy(val title: Int, val subtitle: Int)

internal fun stationTileCopy(type: RadioType): StationTileCopy? = when (type) {
    RadioType.ARTIST_RADIO -> StationTileCopy(R.string.station_tile_artist, R.string.station_tile_choose_artist)
    RadioType.ALBUM_RADIO -> StationTileCopy(R.string.station_tile_album, R.string.station_tile_explore_albums)
    RadioType.LIBRARY_RADIO -> StationTileCopy(R.string.station_tile_library, R.string.station_tile_from_library)
    RadioType.DEEP_CUTS -> StationTileCopy(R.string.station_tile_deep_cuts, R.string.station_tile_less_played)
    RadioType.TIME_TRAVEL -> StationTileCopy(R.string.station_tile_time_travel, R.string.station_tile_by_decade)
    RadioType.RANDOM_ALBUM -> StationTileCopy(R.string.station_tile_album_shuffle, R.string.station_tile_full_albums)
    RadioType.GENRE_RADIO -> StationTileCopy(R.string.station_tile_genre, R.string.station_tile_choose_genre)
    RadioType.STYLE_RADIO -> StationTileCopy(R.string.station_tile_style, R.string.station_tile_choose_style)
    RadioType.MOOD_RADIO -> StationTileCopy(R.string.station_tile_mood, R.string.station_tile_choose_mood)
    RadioType.DECADE_RADIO -> StationTileCopy(R.string.station_tile_decade, R.string.station_tile_choose_decade)
    else -> null
}

internal fun stationRadioFacetChoices(type: RadioType, tracks: List<CachedTrack>): List<String> {
    val tagService = LocalMetadataTagService()
    return when (type) {
        RadioType.GENRE_RADIO -> {
            val defaults = listOf(
                "Rock", "Pop", "Hip-Hop", "Electronic", "Jazz", "Classical", "Ambient",
                "Indie", "R&B", "Metal", "Soundtrack", "Folk", "Alternative", "Dance"
            )
            (tracks.flatMap { it.genres.split('|') } + defaults)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }
        RadioType.MOOD_RADIO -> tracks.flatMap { tagService.tag(it).moods }.distinct().sorted()
        RadioType.STYLE_RADIO -> tracks.flatMap { tagService.tag(it).styleTags }.distinct().sorted()
        else -> emptyList()
    }
}

internal fun stationRadioFacetLabel(type: RadioType): Int = when (type) {
    RadioType.MOOD_RADIO -> R.string.station_mood_label
    RadioType.STYLE_RADIO -> R.string.station_style_label
    else -> R.string.station_genre_label
}

internal fun stationRadioEmptyChoicesString(type: RadioType): Int = when (type) {
    RadioType.MOOD_RADIO -> R.string.library_picker_empty_moods
    RadioType.STYLE_RADIO -> R.string.library_picker_empty_styles
    else -> R.string.library_picker_empty_options
}
internal fun stationRadioRequest(
    type: RadioType,
    seedArtist: String?,
    selectedFacet: String?,
    collection: String?,
    decadeStart: Int?,
    trackCount: Int,
    discoveryLevel: DiscoveryLevel,
    recentCooldownDays: Int
): RadioRequest = RadioRequest(
    type = type,
    seedArtist = seedArtist,
    genre = selectedFacet.takeIf { type == RadioType.GENRE_RADIO },
    mood = selectedFacet.takeIf { type == RadioType.MOOD_RADIO },
    style = selectedFacet.takeIf { type == RadioType.STYLE_RADIO },
    collection = collection,
    decadeStart = decadeStart,
    trackCount = trackCount,
    discoveryLevel = discoveryLevel,
    recentCooldownDays = recentCooldownDays
)

internal fun radioNoMatchesString(type: RadioType): Int = when (type) {
    RadioType.ARTIST_RADIO -> R.string.radio_no_artist_matches
    RadioType.ALBUM_RADIO -> R.string.radio_no_album_matches
    RadioType.GENRE_RADIO, RadioType.STYLE_RADIO -> R.string.radio_no_genre_matches
    RadioType.MOOD_RADIO -> R.string.radio_no_mood_matches
    RadioType.COLLECTION_RADIO -> R.string.radio_no_collection_matches
    RadioType.DECADE_RADIO, RadioType.TIME_TRAVEL -> R.string.radio_no_decade_matches
    RadioType.SOUNDTRACK_RADIO -> R.string.radio_no_soundtrack_matches
    RadioType.FORGOTTEN_FAVORITES -> R.string.radio_no_forgotten_favorites
    else -> R.string.radio_no_library_matches
}

internal fun radioSelectionNoMatchesString(): Int = R.string.radio_selection_no_matches
