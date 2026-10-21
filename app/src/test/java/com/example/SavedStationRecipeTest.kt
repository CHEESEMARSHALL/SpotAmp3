package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CachedTrack
import com.example.data.DiscoveryLevel
import com.example.data.RadioType
import com.example.data.SavedStationRecipe
import com.example.data.SavedStationRecipeStore
import com.example.data.deleteSavedStationRecipe
import com.example.data.generateSavedStationTracks
import com.example.data.renameSavedStationRecipe
import com.example.data.upsertSavedStationRecipe
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
class SavedStationRecipeTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val preferences get() = context.getSharedPreferences("spotamp_local_station_recipes", Context.MODE_PRIVATE)

    @Before
    @After
    fun clearDisposableRecipeFixture() {
        preferences.edit().clear().commit()
    }

    @Test
    fun recipeStorePersistsOnlyPhoneLocalRecipesAndSupportsRenameDelete() {
        val store = SavedStationRecipeStore(context)
        val recipe = SavedStationRecipe.fromRequest(
            name = "Warm Synth Radio",
            request = com.example.data.RadioRequest(
                type = RadioType.GENRE_RADIO,
                genre = "Synthpop",
                trackCount = 25,
                discoveryLevel = DiscoveryLevel.DEEP_CUTS,
                recentCooldownDays = 0
            )
        )

        assertEquals(listOf(recipe), store.save(recipe))
        val restored = SavedStationRecipeStore(context).load().single()
        assertEquals("Synthpop", restored.genre)
        assertEquals(25, restored.trackCount)
        assertEquals(DiscoveryLevel.DEEP_CUTS.name, restored.discoveryLevel)
        assertEquals(0, restored.recentCooldownDays)

        assertEquals("Night Drive Radio", store.rename(recipe.id, "Night Drive Radio").single().name)
        assertTrue(store.delete(recipe.id).isEmpty())
    }

    @Test
    fun recipeCollectionOperationsKeepOtherRecipesAndRejectBlankRename() {
        val first = SavedStationRecipe(id = "a", name = "A", radioType = RadioType.LIBRARY_RADIO.name)
        val second = SavedStationRecipe(id = "b", name = "B", radioType = RadioType.DEEP_CUTS.name)
        val saved = upsertSavedStationRecipe(listOf(first), second)

        assertEquals(listOf("A", "B"), saved.map { it.name })
        assertEquals(saved, renameSavedStationRecipe(saved, "a", "   "))
        assertEquals(listOf("A"), deleteSavedStationRecipe(saved, "b").map { it.name })
    }

    @Test
    fun multiArtistRecipeRestartsFromTheSavedLibraryAndHonorsTrackCount() {
        val recipe = SavedStationRecipe(
            id = "multi-artist",
            name = "Two Artists",
            radioType = RadioType.ARTIST_RADIO.name,
            seedValues = listOf("M83", "CHVRCHES"),
            trackCount = 2
        )
        val library = listOf(
            cachedTrack("m83-1", "M83", "Hurry Up", "Dream pop"),
            cachedTrack("churches-1", "CHVRCHES", "Every Open Eye", "Synthpop")
        )

        val tracks = generateSavedStationTracks(recipe, library, emptyList())

        assertEquals(2, tracks.size)
        assertEquals(setOf("m83-1", "churches-1"), tracks.map { it.ratingKey }.toSet())
    }

    private fun cachedTrack(key: String, artist: String, album: String, genre: String) = CachedTrack(
        ratingKey = key,
        title = key,
        artist = artist,
        album = album,
        key = "/media/$key",
        thumb = "",
        duration = 180_000,
        genres = genre
    )
}
