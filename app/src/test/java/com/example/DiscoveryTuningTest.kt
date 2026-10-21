package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CachedTrack
import com.example.data.DiscoveryLevel
import com.example.data.DiscoveryTuning
import com.example.data.DiscoveryTuningStore
import com.example.data.RadioRequest
import com.example.data.RadioType
import com.example.data.applyDiscoveryTuning
import com.example.data.filterDiscoveryTracks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiscoveryTuningTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val preferences get() = context.getSharedPreferences("spotamp_discovery_tuning", Context.MODE_PRIVATE)

    @Before
    @After
    fun clearDisposableTuningFixture() {
        preferences.edit().clear().commit()
    }

    @Test
    fun familiarityAndDepthProduceStableRadioControls() {
        val deep = DiscoveryTuning(familiarity = 0, depth = 100)
        val familiar = DiscoveryTuning(familiarity = 100, depth = 0)
        val request = RadioRequest(type = RadioType.LIBRARY_RADIO, trackCount = 40)

        assertEquals(DiscoveryLevel.DEEP_CUTS, deep.discoveryLevel())
        assertEquals(30, deep.recentCooldownDays())
        assertEquals(1, deep.maxTracksPerArtist())
        assertEquals(DiscoveryLevel.FAMILIAR, familiar.discoveryLevel())
        assertEquals(0, familiar.recentCooldownDays())
        assertEquals(5, familiar.maxTracksPerArtist())
        assertEquals(DiscoveryLevel.DEEP_CUTS, applyDiscoveryTuning(request, deep).discoveryLevel)
    }

    @Test
    fun exclusionsAreCaseInsensitiveAndPersistOnThisPhone() {
        val tuning = DiscoveryTuning(
            familiarity = 42,
            depth = 73,
            excludedArtists = listOf("M83", " m83 ", ""),
            excludedGenres = listOf("Ambient")
        )
        val store = DiscoveryTuningStore(context)
        val saved = store.save(tuning)
        val restored = DiscoveryTuningStore(context).load()
        assertEquals(listOf("M83"), restored.excludedArtists)
        assertEquals(listOf("Ambient"), restored.excludedGenres)
        assertEquals(saved, restored)

        val library = listOf(
            track("m83", "M83", "Synthpop"),
            track("ambient", "Other Artist", "ambient|Electronic"),
            track("eligible", "New Artist", "Electronic")
        )
        assertEquals(listOf("eligible"), filterDiscoveryTracks(library, restored).map { it.ratingKey })
    }

    @Test
    fun savedValuesAreClampedToSupportedRanges() {
        val normalized = DiscoveryTuning(
            familiarity = 200,
            depth = -10,
            excludedArtists = listOf("", "Artist")
        ).normalized()

        assertEquals(100, normalized.familiarity)
        assertEquals(0, normalized.depth)
        assertTrue(normalized.excludedArtists.contains("Artist"))
    }

    private fun track(key: String, artist: String, genres: String) = CachedTrack(
        ratingKey = key,
        title = key,
        artist = artist,
        album = "Album",
        key = "/media/$key",
        thumb = "",
        duration = 180_000,
        genres = genres
    )
}
