package com.example.data

import com.example.playback.TrackItem
import java.util.Calendar
import java.util.Locale

data class RadioRequest(
    val type: RadioType,
    val seedArtist: String? = null,
    val seedAlbum: String? = null,
    val genre: String? = null,
    val collection: String? = null,
    val decadeStart: Int? = null,
    val trackCount: Int = 40,
    val discoveryLevel: DiscoveryLevel = DiscoveryLevel.BALANCED,
    val recentCooldownDays: Int = 7,
    val maxTracksPerArtist: Int = 3
)

enum class DiscoveryLevel { FAMILIAR, BALANCED, DEEP_CUTS }

internal fun deterministicDailyIndex(size: Int, epochMillis: Long): Int {
    if (size <= 0) return -1
    return ((epochMillis / 86_400_000L) % size).toInt()
}

class RadioService {
    private fun CachedTrack.hasGenre(tag: String): Boolean =
        genres.split('|')
            .asSequence()
            .map { it.trim() }
            .any { it.equals(tag, ignoreCase = true) }

    private fun CachedTrack.hasCollection(name: String): Boolean =
        collections.split('|')
            .asSequence()
            .map { it.trim() }
            .any { it.equals(name, ignoreCase = true) }

    fun generate(request: RadioRequest, tracks: List<CachedTrack>, history: List<RecentTrack> = emptyList()): List<TrackItem> {
        if (tracks.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val cooldownMillis = request.recentCooldownDays.coerceAtLeast(0).toLong() * 86_400_000L
        val recentKeys = history
            .filter { cooldownMillis == 0L || now - it.timestamp <= cooldownMillis }
            .map { it.ratingKey }
            .toSet()
        val candidates = when (request.type) {
            RadioType.ARTIST_RADIO -> {
                val seedArtist = request.seedArtist
                if (seedArtist == null) emptyList() else {
                    val artistTracks = tracks.filter { it.artist.equals(seedArtist, true) }
                    if (artistTracks.isEmpty()) emptyList() else {
                        val artistGenres = artistTracks.flatMap { it.genres.split('|').map { g -> g.trim().lowercase(Locale.ROOT) } }
                            .filter { it.isNotEmpty() }.toSet()
                        
                        val similarTracks = if (artistGenres.isNotEmpty()) {
                            tracks.filter { track ->
                                !track.artist.equals(seedArtist, true) && 
                                track.genres.split('|').any { g -> g.trim().lowercase(Locale.ROOT) in artistGenres }
                            }
                        } else emptyList()

                        val shuffledArtistTracks = artistTracks.shuffled()
                        val shuffledSimilarTracks = similarTracks.shuffled()
                        
                        val targetArtistCount = (request.trackCount * 0.6).toInt().coerceIn(1, request.trackCount)
                        val targetSimilarCount = request.trackCount - targetArtistCount
                        
                        val resultList = mutableListOf<CachedTrack>()
                        resultList.addAll(shuffledArtistTracks.take(targetArtistCount))
                        resultList.addAll(shuffledSimilarTracks.take(targetSimilarCount))
                        
                        if (resultList.size < request.trackCount) {
                            val remainingArtist = shuffledArtistTracks.drop(targetArtistCount)
                            val remainingSimilar = shuffledSimilarTracks.drop(targetSimilarCount)
                            resultList.addAll(remainingArtist)
                            if (resultList.size < request.trackCount) {
                                resultList.addAll(remainingSimilar)
                            }
                        }
                        resultList
                    }
                }
            }
            RadioType.ALBUM_RADIO -> {
                val seedAlbum = request.seedAlbum
                if (seedAlbum == null) emptyList() else {
                    val albumTracks = tracks.filter { it.album.equals(seedAlbum, true) }
                    if (albumTracks.isEmpty()) emptyList() else {
                        val artist = albumTracks.firstOrNull()?.artist
                        val albumGenres = albumTracks.flatMap { it.genres.split('|').map { g -> g.trim().lowercase(Locale.ROOT) } }
                            .filter { it.isNotEmpty() }.toSet()
                        
                        val otherTracksByArtist = if (artist != null) {
                            tracks.filter { it.artist.equals(artist, true) && !it.album.equals(seedAlbum, true) }
                        } else emptyList()
                        
                        val similarTracks = if (albumGenres.isNotEmpty()) {
                            tracks.filter { track ->
                                !track.album.equals(seedAlbum, true) && 
                                (artist == null || !track.artist.equals(artist, true)) &&
                                track.genres.split('|').any { g -> g.trim().lowercase(Locale.ROOT) in albumGenres }
                            }
                        } else emptyList()
                        
                        val resultList = mutableListOf<CachedTrack>()
                        resultList.addAll(albumTracks.shuffled())
                        resultList.addAll(otherTracksByArtist.shuffled().take(10))
                        resultList.addAll(similarTracks.shuffled().take(15))
                        resultList
                    }
                }
            }
            RadioType.GENRE_RADIO, RadioType.MOOD_RADIO, RadioType.STYLE_RADIO -> tracks.filter { request.genre != null && it.hasGenre(request.genre) }
            RadioType.COLLECTION_RADIO -> tracks.filter { request.collection != null && it.hasCollection(request.collection) }
            RadioType.DECADE_RADIO -> {
                val start = request.decadeStart ?: (tracks.mapNotNull { it.year }.minOrNull()?.let { (it / 10) * 10 } ?: 1990)
                tracks.filter { it.year?.let { year -> year in start..(start + 9) } == true }
            }
            RadioType.TIME_TRAVEL -> {
                tracks.filter { it.year != null && (request.decadeStart == null || it.year >= request.decadeStart) }
            }
            RadioType.SOUNDTRACK_RADIO -> tracks.filter { it.genres.contains("soundtrack", true) || it.collections.contains("soundtrack", true) || it.album.contains("OST", true) }
            RadioType.FORGOTTEN_FAVORITES -> tracks.filter { it.playCount > 0 && it.ratingKey !in recentKeys }
            RadioType.DEEP_CUTS -> tracks.sortedWith(compareBy<CachedTrack> { it.playCount }.thenBy { it.lastPlayedAt ?: Long.MIN_VALUE })
            RadioType.RANDOM_ALBUM -> {
                val albums = tracks.groupBy { it.album }.entries.sortedBy { it.key }
                if (albums.isEmpty()) emptyList() else {
                    val dayIndex = deterministicDailyIndex(albums.size, System.currentTimeMillis())
                    albums[dayIndex].value
                }
            }
            RadioType.RECENTLY_ADDED_RADIO -> tracks.sortedWith(
                compareByDescending<CachedTrack> { it.addedAt ?: Long.MIN_VALUE }
                    .thenBy { it.ratingKey }
            )
            RadioType.ON_THIS_DAY -> {
                val today = Calendar.getInstance()
                tracks.filter { track ->
                    val addedAt = track.addedAt ?: return@filter false
                    Calendar.getInstance().apply { timeInMillis = addedAt * 1000L }.let { added ->
                        added.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                            added.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
                    }
                }
            }
            RadioType.LIBRARY_RADIO -> tracks
        }
        val effectiveDiscovery = if (request.type == RadioType.DEEP_CUTS) {
            DiscoveryLevel.DEEP_CUTS
        } else {
            request.discoveryLevel
        }
        val sorted = when (effectiveDiscovery) {
            DiscoveryLevel.FAMILIAR -> candidates.sortedWith(compareByDescending<CachedTrack> { it.playCount }.thenBy { it.ratingKey })
            DiscoveryLevel.DEEP_CUTS -> candidates.sortedWith(compareBy<CachedTrack> { it.playCount }.thenBy { it.ratingKey })
            DiscoveryLevel.BALANCED -> candidates.sortedWith(compareByDescending<CachedTrack> { it.playCount }.thenBy { it.artist }.thenBy { it.ratingKey })
        }
        val result = mutableListOf<CachedTrack>()
        val artistCounts = mutableMapOf<String, Int>()
        for (track in sorted) {
            if (request.type != RadioType.ARTIST_RADIO && request.type != RadioType.ALBUM_RADIO && artistCounts.getOrDefault(track.artist.lowercase(Locale.ROOT), 0) >= request.maxTracksPerArtist) continue
            if (request.recentCooldownDays > 0 && track.ratingKey in recentKeys && request.discoveryLevel != DiscoveryLevel.FAMILIAR) continue
            result += track
            val key = track.artist.lowercase(Locale.ROOT)
            artistCounts[key] = artistCounts.getOrDefault(key, 0) + 1
            if (result.size >= request.trackCount.coerceIn(1, 200)) break
        }
        val shouldShuffleResult = when (request.type) {
            RadioType.DEEP_CUTS,
            RadioType.RECENTLY_ADDED_RADIO,
            RadioType.LIBRARY_RADIO,
            RadioType.RANDOM_ALBUM,
            RadioType.TIME_TRAVEL -> false
            else -> true
        }
        val finalTracks = if (request.type == RadioType.TIME_TRAVEL) {
            result.sortedWith(compareBy<CachedTrack> { it.year ?: 0 }.thenBy { it.artist }.thenBy { it.title })
        } else if (shouldShuffleResult) {
            result.shuffled()
        } else {
            result
        }
        return finalTracks.map { it.toTrackItem() }
    }
}
