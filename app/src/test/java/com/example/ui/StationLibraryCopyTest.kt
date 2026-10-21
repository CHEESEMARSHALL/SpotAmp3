package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.RadioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StationLibraryCopyTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun previewCopyDescribesTheSavedLibraryForEachState() {
        val checking = context.getString(stationLibrarySummaryString(StationLibrarySummary.Checking))
        val empty = context.getString(stationLibrarySummaryString(StationLibrarySummary.NoMatches))
        val populated = context.getString(
            stationLibrarySummaryString(StationLibrarySummary.Matches(12)),
            12
        )

        assertEquals("Checking your saved library…", checking)
        assertEquals(
            "No tracks in your saved library match these selections. Update library details from SpotCore.",
            empty
        )
        assertEquals("12 matching tracks in your saved library", populated)
        assertEquals(
            "Selected from your saved library",
            context.getString(R.string.station_selected_from_library)
        )
        listOf(checking, empty, populated).forEach { text ->
            assertFalse("Unexpected cache implementation language: $text", "cached" in text.lowercase())
        }
    }

    @Test
    fun artistCopyDistinguishesLoadingFromUnavailable() {
        assertEquals(
            "Loading artists from your saved library…",
            context.getString(stationArtistStatusString(isLoading = true))
        )
        assertEquals(
            "No artists are available in your saved library.",
            context.getString(stationArtistStatusString(isLoading = false))
        )
        assertEquals("Selected: Nina Simone", context.getString(R.string.station_selected_artist, "Nina Simone"))
    }

    @Test
    fun stationBuilderHintsNameWhatCanBeSearched() {
        assertEquals("Choose artists", context.getString(R.string.station_artist_mix_builder_title))
        assertEquals("Choose albums", context.getString(R.string.station_album_mix_builder_title))
        assertEquals("Search artists…", context.getString(R.string.station_search_artist_hint))
        assertEquals("Search albums or artists…", context.getString(R.string.station_search_album_hint))
        assertEquals("Number of tracks", context.getString(R.string.station_track_count_label))
        assertEquals("40 tracks", context.getString(R.string.station_track_count, 40))

        val expectedHints = mapOf(
            RadioType.GENRE_RADIO to R.string.station_search_genre_hint,
            RadioType.MOOD_RADIO to R.string.station_search_mood_hint,
            RadioType.STYLE_RADIO to R.string.station_search_style_hint,
            RadioType.DECADE_RADIO to R.string.station_search_decade_hint,
            RadioType.TIME_TRAVEL to R.string.station_search_time_period_hint
        )
        expectedHints.forEach { (type, expectedResource) ->
            assertEquals(expectedResource, stationSearchHintString(type))
        }
        assertEquals(R.string.station_search_options_hint, stationSearchHintString(RadioType.LIBRARY_RADIO))
        assertEquals("Search genres…", context.getString(stationSearchHintString(RadioType.GENRE_RADIO)))
        assertEquals("Search time periods…", context.getString(stationSearchHintString(RadioType.TIME_TRAVEL)))
    }

    @Test
    fun radioEmptyStatesUseSavedLibraryAndListeningHistoryLanguage() {
        val expectedByType = mapOf(
            RadioType.ARTIST_RADIO to R.string.radio_no_artist_matches,
            RadioType.ALBUM_RADIO to R.string.radio_no_album_matches,
            RadioType.GENRE_RADIO to R.string.radio_no_genre_matches,
            RadioType.MOOD_RADIO to R.string.radio_no_mood_matches,
            RadioType.STYLE_RADIO to R.string.radio_no_genre_matches,
            RadioType.COLLECTION_RADIO to R.string.radio_no_collection_matches,
            RadioType.DECADE_RADIO to R.string.radio_no_decade_matches,
            RadioType.TIME_TRAVEL to R.string.radio_no_decade_matches,
            RadioType.SOUNDTRACK_RADIO to R.string.radio_no_soundtrack_matches,
            RadioType.FORGOTTEN_FAVORITES to R.string.radio_no_forgotten_favorites
        )
        expectedByType.forEach { (type, resource) ->
            assertEquals(resource, radioNoMatchesString(type))
            assertFalse("Unexpected cached wording for $type", "cached" in context.getString(resource).lowercase())
        }
        val generalStationEmptyMessage = context.getString(radioNoMatchesString(RadioType.LIBRARY_RADIO))
        assertEquals(R.string.radio_no_library_matches, radioNoMatchesString(RadioType.LIBRARY_RADIO))
        assertEquals("No tracks in your saved library match this station.", generalStationEmptyMessage)
        assertFalse("Unexpected implementation detail: $generalStationEmptyMessage", "cache" in generalStationEmptyMessage.lowercase())
        assertEquals(
            "No tracks in your saved library match “Jazz” yet. Try another selection or update your library.",
            context.getString(radioSelectionNoMatchesString(), "Jazz")
        )
        assertEquals(
            "Couldn’t reach SpotCore to search. Try again, or browse your saved library on this phone.",
            context.getString(searchFailureString(hasSavedLibrary = true))
        )
        assertEquals(
            "Couldn’t reach SpotCore. Check the connection or try again.",
            context.getString(searchFailureString(hasSavedLibrary = false))
        )
    }

    @Test
    fun stationDescriptionsAvoidProviderAndImplementationLanguage() {
        val descriptions = listOf(
            R.string.station_description_library,
            R.string.station_description_artist,
            R.string.station_description_deep_cuts,
            R.string.station_description_forgotten,
            R.string.station_description_random_album,
            R.string.station_description_genre,
            R.string.station_description_style,
            R.string.station_description_mood,
            R.string.station_description_decade,
            R.string.station_description_collection,
            R.string.station_description_soundtrack,
            R.string.station_description_recently_added,
            R.string.station_description_on_this_day
        ).map(context::getString)

        descriptions.forEach { description ->
            val lower = description.lowercase()
            assertFalse("Provider name leaked into: $description", "plex" in lower)
            assertFalse("Implementation wording leaked into: $description", "indexed" in lower || "files" in lower)
        }
        assertEquals("Build a station around a selected artist", context.getString(R.string.station_description_artist))
        assertEquals("Play music from a selected collection", context.getString(R.string.station_description_collection))
    }

    @Test
    fun stationDetailsUseListenerFacingFilterAndSelectionCopy() {
        assertEquals("40 tracks", context.getString(R.string.station_track_count, 40))
        assertEquals("Include recently played tracks", context.getString(R.string.station_repeat_filter_none))
        assertEquals("Skip tracks played in the last 7 days", context.resources.getQuantityString(R.plurals.station_repeat_filter_days, 7, 7))
        assertEquals("Artist: Nina Simone", context.getString(R.string.station_selection_artist, "Nina Simone"))
        assertEquals("Genre: Jazz", context.getString(R.string.station_selection_genre, "Jazz"))
        assertEquals("Release decade: 1980–1989", context.getString(R.string.station_selection_decade, 1980, 1989))
        assertEquals("Play 1980s radio", context.getString(R.string.station_play_radio_action, "1980s"))

        val strategyCopy = listOf(
            R.string.station_selection_recently_added,
            R.string.station_selection_deep_cuts,
            R.string.station_selection_forgotten_favorites
        ).map(context::getString)
        strategyCopy.forEach { text ->
            assertFalse("Internal ranking terminology leaked: $text", "ranked" in text.lowercase() || "play count" in text.lowercase())
        }
    }
}
