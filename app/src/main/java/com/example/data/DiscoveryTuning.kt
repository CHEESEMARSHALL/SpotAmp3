package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.playback.TrackItem
import java.util.Locale

/** Discovery preferences belong to this phone and never sync to SpotCore. */
data class DiscoveryTuning(
    val familiarity: Int = 55,
    val depth: Int = 50,
    val excludedArtists: List<String> = emptyList(),
    val excludedGenres: List<String> = emptyList()
) {
    fun normalized(): DiscoveryTuning = copy(
        familiarity = familiarity.coerceIn(0, 100),
        depth = depth.coerceIn(0, 100),
        excludedArtists = normalizeExclusions(excludedArtists),
        excludedGenres = normalizeExclusions(excludedGenres)
    )

    fun discoveryLevel(): DiscoveryLevel = when (normalized().familiarity) {
        in 0..33 -> DiscoveryLevel.DEEP_CUTS
        in 67..100 -> DiscoveryLevel.FAMILIAR
        else -> DiscoveryLevel.BALANCED
    }

    fun recentCooldownDays(): Int = (normalized().depth * 30 / 100).coerceIn(0, 30)

    fun maxTracksPerArtist(): Int = (5 - normalized().depth * 4 / 100).coerceAtLeast(1)
}

fun applyDiscoveryTuning(request: RadioRequest, tuning: DiscoveryTuning): RadioRequest {
    val normalized = tuning.normalized()
    return request.copy(
        discoveryLevel = normalized.discoveryLevel(),
        recentCooldownDays = normalized.recentCooldownDays(),
        maxTracksPerArtist = normalized.maxTracksPerArtist()
    )
}

fun filterDiscoveryTracks(
    tracks: List<CachedTrack>,
    tuning: DiscoveryTuning
): List<CachedTrack> {
    val normalized = tuning.normalized()
    val excludedArtists = normalized.excludedArtists.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
    val excludedGenres = normalized.excludedGenres.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
    return tracks.filter { track ->
        track.artist.trim().lowercase(Locale.ROOT) !in excludedArtists &&
            track.genres.split('|').none { it.trim().lowercase(Locale.ROOT) in excludedGenres }
    }
}

fun filterDiscoveryTrackItems(
    tracks: List<TrackItem>,
    cachedByRatingKey: Map<String, CachedTrack>,
    tuning: DiscoveryTuning
): List<TrackItem> {
    val normalized = tuning.normalized()
    val excludedArtists = normalized.excludedArtists.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
    val excludedGenres = normalized.excludedGenres.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
    return tracks.filter { track ->
        val cached = cachedByRatingKey[track.ratingKey]
        track.artist.trim().lowercase(Locale.ROOT) !in excludedArtists &&
            cached?.genres.orEmpty().split('|').none { it.trim().lowercase(Locale.ROOT) in excludedGenres }
    }
}

private fun normalizeExclusions(values: List<String>): List<String> = values
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy { it.lowercase(Locale.ROOT) }
    .sortedBy { it.lowercase(Locale.ROOT) }

class DiscoveryTuningStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): DiscoveryTuning = DiscoveryTuning(
        familiarity = preferences.getInt(KEY_FAMILIARITY, 55),
        depth = preferences.getInt(KEY_DEPTH, 50),
        excludedArtists = preferences.getStringSet(KEY_EXCLUDED_ARTISTS, emptySet()).orEmpty().toList(),
        excludedGenres = preferences.getStringSet(KEY_EXCLUDED_GENRES, emptySet()).orEmpty().toList()
    ).normalized()

    @Synchronized
    fun save(tuning: DiscoveryTuning): DiscoveryTuning {
        val normalized = tuning.normalized()
        preferences.edit()
            .putInt(KEY_FAMILIARITY, normalized.familiarity)
            .putInt(KEY_DEPTH, normalized.depth)
            .putStringSet(KEY_EXCLUDED_ARTISTS, normalized.excludedArtists.toSet())
            .putStringSet(KEY_EXCLUDED_GENRES, normalized.excludedGenres.toSet())
            .apply()
        return normalized
    }

    private companion object {
        const val PREFERENCES_NAME = "spotamp_discovery_tuning"
        const val KEY_FAMILIARITY = "familiarity"
        const val KEY_DEPTH = "depth"
        const val KEY_EXCLUDED_ARTISTS = "excluded_artists"
        const val KEY_EXCLUDED_GENRES = "excluded_genres"
    }
}
