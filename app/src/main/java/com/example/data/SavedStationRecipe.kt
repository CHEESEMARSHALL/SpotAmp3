package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.playback.TrackItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** A phone-local recipe that can rebuild a station from the saved library. */
@Serializable
data class SavedStationRecipe(
    val id: String,
    val name: String,
    val radioType: String,
    val seedValues: List<String> = emptyList(),
    val genre: String? = null,
    val mood: String? = null,
    val style: String? = null,
    val collection: String? = null,
    val decadeStart: Int? = null,
    val trackCount: Int = 40,
    val discoveryLevel: String = DiscoveryLevel.BALANCED.name,
    val recentCooldownDays: Int = 7,
    val maxTracksPerArtist: Int = 3
) {
    fun normalized(): SavedStationRecipe = copy(
        name = name.trim().ifBlank { "Station" },
        radioType = runCatching { RadioType.valueOf(radioType).name }.getOrDefault(RadioType.LIBRARY_RADIO.name),
        seedValues = seedValues.map(String::trim).filter(String::isNotEmpty).distinct(),
        trackCount = trackCount.coerceIn(1, 200),
        discoveryLevel = runCatching { DiscoveryLevel.valueOf(discoveryLevel).name }
            .getOrDefault(DiscoveryLevel.BALANCED.name),
        recentCooldownDays = recentCooldownDays.coerceIn(0, 365),
        maxTracksPerArtist = maxTracksPerArtist.coerceIn(1, 200)
    )

    fun toRadioRequest(): RadioRequest {
        val recipe = normalized()
        return RadioRequest(
            type = RadioType.valueOf(recipe.radioType),
            seedArtist = recipe.seedValues.firstOrNull().takeIf { recipe.radioType == RadioType.ARTIST_RADIO.name },
            seedAlbum = recipe.seedValues.firstOrNull().takeIf { recipe.radioType == RadioType.ALBUM_RADIO.name },
            genre = recipe.genre,
            mood = recipe.mood,
            style = recipe.style,
            collection = recipe.collection,
            decadeStart = recipe.decadeStart,
            trackCount = recipe.trackCount,
            discoveryLevel = DiscoveryLevel.valueOf(recipe.discoveryLevel),
            recentCooldownDays = recipe.recentCooldownDays,
            maxTracksPerArtist = recipe.maxTracksPerArtist
        )
    }

    companion object {
        fun fromRequest(
            name: String,
            request: RadioRequest,
            seedValues: List<String> = listOfNotNull(
                request.seedArtist,
                request.seedAlbum,
                request.genre,
                request.mood,
                request.style,
                request.collection
            )
        ): SavedStationRecipe = SavedStationRecipe(
            id = UUID.randomUUID().toString(),
            name = name,
            radioType = request.type.name,
            seedValues = seedValues,
            genre = request.genre,
            mood = request.mood,
            style = request.style,
            collection = request.collection,
            decadeStart = request.decadeStart,
            trackCount = request.trackCount,
            discoveryLevel = request.discoveryLevel.name,
            recentCooldownDays = request.recentCooldownDays,
            maxTracksPerArtist = request.maxTracksPerArtist
        ).normalized()
    }
}

fun upsertSavedStationRecipe(
    recipes: List<SavedStationRecipe>,
    recipe: SavedStationRecipe
): List<SavedStationRecipe> =
    (recipes.filterNot { it.id == recipe.id } + recipe.normalized())
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

fun renameSavedStationRecipe(
    recipes: List<SavedStationRecipe>,
    id: String,
    name: String
): List<SavedStationRecipe> {
    val cleanedName = name.trim()
    if (cleanedName.isEmpty()) return recipes
    return recipes.map { recipe ->
        if (recipe.id == id) recipe.copy(name = cleanedName) else recipe
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

fun deleteSavedStationRecipe(
    recipes: List<SavedStationRecipe>,
    id: String
): List<SavedStationRecipe> = recipes.filterNot { it.id == id }

/**
 * Rebuilds the recipe using only the phone's saved library metadata. Multi
 * artist/album recipes combine their seeded mixes while removing duplicates.
 */
fun generateSavedStationTracks(
    recipe: SavedStationRecipe,
    cachedTracks: List<CachedTrack>,
    history: List<RecentTrack>,
    radioService: RadioService = RadioService(),
    tuning: DiscoveryTuning? = null
): List<TrackItem> {
    if (cachedTracks.isEmpty()) return emptyList()
    val normalized = recipe.normalized()
    val eligibleTracks = tuning?.let { filterDiscoveryTracks(cachedTracks, it) } ?: cachedTracks
    val request = normalized.toRadioRequest().let { base -> tuning?.let { applyDiscoveryTuning(base, it) } ?: base }
    val requests = when (request.type) {
        RadioType.ARTIST_RADIO -> normalized.seedValues.map { request.copy(seedArtist = it, trackCount = normalized.trackCount) }
        RadioType.ALBUM_RADIO -> normalized.seedValues.map { request.copy(seedAlbum = it, trackCount = normalized.trackCount) }
        else -> listOf(request)
    }
    if (requests.isEmpty()) return emptyList()
    return requests.asSequence()
        .flatMap { radioService.generate(it, eligibleTracks, history).asSequence() }
        .distinctBy(TrackItem::ratingKey)
        .take(normalized.trackCount)
        .toList()
}

class SavedStationRecipeStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<SavedStationRecipe> = runCatching {
        val stored = preferences.getString(KEY_RECIPES, null) ?: return emptyList()
        Json { ignoreUnknownKeys = true }.decodeFromString<List<SavedStationRecipe>>(stored)
            .map(SavedStationRecipe::normalized)
            .distinctBy(SavedStationRecipe::id)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(recipe: SavedStationRecipe): List<SavedStationRecipe> =
        persist(upsertSavedStationRecipe(load(), recipe))

    @Synchronized
    fun rename(id: String, name: String): List<SavedStationRecipe> =
        persist(renameSavedStationRecipe(load(), id, name))

    @Synchronized
    fun delete(id: String): List<SavedStationRecipe> =
        persist(deleteSavedStationRecipe(load(), id))

    private fun persist(recipes: List<SavedStationRecipe>): List<SavedStationRecipe> {
        preferences.edit().putString(KEY_RECIPES, Json.encodeToString(recipes)).apply()
        return recipes
    }

    private companion object {
        const val PREFERENCES_NAME = "spotamp_local_station_recipes"
        const val KEY_RECIPES = "recipes"
    }
}
