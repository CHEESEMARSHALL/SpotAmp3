package com.example.data

import android.content.Context
import android.util.Log
import com.example.playback.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.*

// ==========================================
// 1. Data Models for Home Screen Components
// ==========================================

data class HomeRecentPlay(
    val id: String,
    val title: String,
    val artist: String,
    val thumb: String,
    val relativeTime: String,
    val type: String, // "track", "album", "playlist"
    val tracks: List<TrackItem>
)

data class DailyMix(
    val id: String,
    val title: String,
    val reason: String,
    val tracks: List<TrackItem>,
    val colors: List<Long> // Hex colors for gradient background
) {
    val description: String
        get() = "Based on " + reason
}

data class JumpBackInItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumb: String,
    val type: String, // "album", "artist", "playlist", "station"
    val extraData: String? = null
)

data class RecommendedStation(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: RadioType,
    val gradientColors: List<Long>
)

enum class RadioType {
    LIBRARY_RADIO,
    ARTIST_RADIO,
    ALBUM_RADIO,
    DEEP_CUTS,
    TIME_TRAVEL,
    RANDOM_ALBUM,
    STYLE_RADIO,
    MOOD_RADIO,
    DECADE_RADIO,
    SOUNDTRACK_RADIO,
    FORGOTTEN_FAVORITES,
    GENRE_RADIO,
    COLLECTION_RADIO,
    RECENTLY_ADDED_RADIO,
    ON_THIS_DAY
}

data class MadeForYouItem(
    val id: String,
    val title: String,
    val description: String,
    val artists: List<String>,
    val tracks: List<TrackItem>,
    val thumb: String
)

data class MoreFromSection(
    val title: String,
    val type: String, // "genre" or "collection"
    val tracks: List<TrackItem>,
    val reason: String? = null
)

data class MostPlayedItem(
    val track: TrackItem,
    val playCount: Int
)

data class OnThisDayItem(
    val title: String,
    val artist: String,
    val year: Int,
    val timeAgo: String,
    val thumb: String,
    val tracks: List<TrackItem>,
    val matchReason: String
)

data class NewReleaseItem(
    val title: String,
    val artist: String,
    val year: Int,
    val thumb: String,
    val tracks: List<TrackItem>
)

// ==========================================
// 2. Home Screen State Container
// ==========================================

data class HomeFeedState(
    val recentPlays: List<HomeRecentPlay> = emptyList(),
    val recentlyAdded: List<PlexMetadata> = emptyList(),
    val dailyMixes: List<DailyMix> = emptyList(),
    val jumpBackIn: List<JumpBackInItem> = emptyList(),
    val stations: List<RecommendedStation> = emptyList(),
    val madeForYou: List<MadeForYouItem> = emptyList(),
    val moreFromSections: List<MoreFromSection> = emptyList(),
    val mostPlayedThisMonth: List<MostPlayedItem> = emptyList(),
    val onThisDay: OnThisDayItem? = null,
    val newReleases: List<NewReleaseItem> = emptyList(),
    val history: List<RecentTrack> = emptyList()
)

// ==========================================
// 3. Recommendation Engine Implementation
// ==========================================

class HomeRecommendationEngine(private val context: Context) {
    private val database = MusicDatabase.getDatabase(context)
    private val musicDao = database.musicDao()

    // -------------------------------------------------------------
    // Home shelves are generated locally from Room's cached Plex metadata.
    // -------------------------------------------------------------

    suspend fun generateHomeFeed(
        realRecentlyAdded: List<PlexMetadata>,
        realHistory: List<RecentTrack>
    ): HomeFeedState = withContext(Dispatchers.IO) {
        val cachedTracks = musicDao.getCachedTracksList()
        // Keep the stable discovery layout available before the first full index.
        // Personalized shelves below still require real cached metadata and remain
        // empty until that data exists.
        val stations = buildDefaultStations(cachedTracks)

        if (cachedTracks.isEmpty()) {
            return@withContext HomeFeedState(
                recentlyAdded = realRecentlyAdded,
                stations = stations,
                history = realHistory.take(10)
            )
        }

        try {
            // Real Plex Metadata Analyzer
            val recentPlays = buildRealRecentPlays(realHistory, cachedTracks)
            val dailyMixes = buildRealDailyMixes(cachedTracks)
            val jumpBackIn = buildRealJumpBackIn(realHistory, cachedTracks)
            val madeForYou = buildRealMadeForYou(cachedTracks)
            val moreFromSections = buildRealMoreFromSections(cachedTracks)
            val mostPlayed = buildRealMostPlayed(realHistory, cachedTracks)
            val onThisDay = buildRealOnThisDay(cachedTracks)
            val newReleases = buildRealNewReleases(realRecentlyAdded, cachedTracks)

            HomeFeedState(
                recentPlays = recentPlays,
                recentlyAdded = realRecentlyAdded,
                dailyMixes = dailyMixes,
                jumpBackIn = jumpBackIn,
                stations = stations,
                madeForYou = madeForYou,
                moreFromSections = moreFromSections,
                mostPlayedThisMonth = mostPlayed,
                onThisDay = onThisDay,
                newReleases = newReleases,
                history = realHistory.take(10)
            )
        } catch (e: Exception) {
            Log.e("HomeRecommendationEngine", "Error generating real recommendation feed", e)
            HomeFeedState(recentlyAdded = realRecentlyAdded, history = realHistory.take(10))
        }
    }

    // ==========================================
    // Real Analytics Helpers
    // ==========================================

    private fun buildRealRecentPlays(
        history: List<RecentTrack>,
        cachedTracks: List<CachedTrack>
    ): List<HomeRecentPlay> {
        if (history.isEmpty()) return emptyList()
        
        // Group by album or track to generate Recent Plays rows
        val now = System.currentTimeMillis()
        return history.take(6).map { item ->
            val cached = cachedTracks.firstOrNull { it.ratingKey == item.ratingKey }
            val trackItem = TrackItem(
                ratingKey = item.ratingKey,
                title = item.title,
                artist = item.artist,
                album = item.album,
                key = item.key,
                thumb = item.thumb,
                duration = cached?.duration ?: 0L
            )

            val elapsed = (now - item.timestamp).coerceAtLeast(0L)
            val timeString = when {
                elapsed < 60_000L -> "Just now"
                elapsed < 3_600_000L -> "${elapsed / 60_000L} min ago"
                elapsed < 86_400_000L -> "${elapsed / 3_600_000L} hr ago"
                elapsed < 172_800_000L -> "Yesterday"
                else -> "${elapsed / 86_400_000L} days ago"
            }

            HomeRecentPlay(
                id = "rp_${item.ratingKey}",
                title = item.title,
                artist = item.artist,
                thumb = item.thumb,
                relativeTime = timeString,
                type = "track",
                tracks = listOf(trackItem)
            )
        }
    }

    private fun buildRealDailyMixes(cachedTracks: List<CachedTrack>): List<DailyMix> {
        val mixes = mutableListOf<DailyMix>()
        
        // Group tracks by artist to make clusters
        val artistGroups = cachedTracks.groupBy { it.artist }
            .toList()
            .sortedByDescending { it.second.size }

        if (artistGroups.isEmpty()) return emptyList()

        // Daily Mix 01: Top Artists Cluster
        val topArtist1 = artistGroups.getOrNull(0)
        val topArtist2 = artistGroups.getOrNull(1)
        if (topArtist1 != null) {
            val tracks = (topArtist1.second + (topArtist2?.second ?: emptyList())).shuffled().take(30).map { it.toTrackItem() }
            val reasonText = topArtist1.first + (topArtist2?.let { ", " + it.first } ?: "")
            mixes.add(
                DailyMix(
                    id = "dm_1",
                    title = "Daily Mix 1",
                    reason = reasonText,
                    tracks = tracks,
                    colors = listOf(0xFF4F46E5, 0xFF06B6D4)
                )
            )
        }

        // Daily Mix 02: Ambient, sound tracks or game music if exists, otherwise other artists
        val secondaryArtist = artistGroups.getOrNull(2)
        val thirdArtist = artistGroups.getOrNull(3)
        if (secondaryArtist != null) {
            val tracks = (secondaryArtist.second + (thirdArtist?.second ?: emptyList())).shuffled().take(30).map { it.toTrackItem() }
            val reasonText = secondaryArtist.first + (thirdArtist?.let { ", " + it.first } ?: "")
            mixes.add(
                DailyMix(
                    id = "dm_2",
                    title = "Daily Mix 2",
                    reason = reasonText,
                    tracks = tracks,
                    colors = listOf(0xFF8B5CF6, 0xFFEC4899)
                )
            )
        }

        // Daily Mix 03: Diverse eclectic mix from remaining library
        val remainingTracks = cachedTracks.shuffled().take(40).map { it.toTrackItem() }
        if (remainingTracks.isNotEmpty()) {
            mixes.add(
                DailyMix(
                    id = "dm_3",
                    title = "Daily Mix 3",
                    reason = "Eclectic selection of local gems",
                    tracks = remainingTracks,
                    colors = listOf(0xFFEF4444, 0xFFF59E0B)
                )
            )
        }

        // Fill the remaining mix slots with deterministic, disjoint slices of
        // the indexed catalog. Never claim these are favorites unless history
        // actually supplied that signal, and never use fabricated tracks.
        while (mixes.size < 5) {
            val idx = mixes.size + 1
            val fallbackTracks = cachedTracks.shuffled()
                .take(30)
                .map { it.toTrackItem() }
            if (fallbackTracks.isEmpty()) break
            val artists = fallbackTracks.map { it.artist }.distinct().take(3)
            mixes.add(
                DailyMix(
                    id = "dm_$idx",
                    title = "Daily Mix $idx",
                    reason = artists.joinToString(", ").ifBlank { "Indexed library tracks" },
                    tracks = fallbackTracks,
                    colors = when (idx) {
                        4 -> listOf(0xFF10B981, 0xFF06B6D4)
                        else -> listOf(0xFF6366F1, 0xFF8B5CF6)
                    }
                )
            )
        }

        return mixes
    }

    private fun buildRealJumpBackIn(
        history: List<RecentTrack>,
        cachedTracks: List<CachedTrack>
    ): List<JumpBackInItem> {
        val items = mutableListOf<JumpBackInItem>()
        
        // Use historical albums
        val uniqueAlbums = history.map { it.album to it }.distinctBy { it.first }
        uniqueAlbums.take(4).forEach { (_, recent) ->
            items.add(
                JumpBackInItem(
                    id = "jbi_alb_${recent.ratingKey}",
                    title = recent.album,
                    subtitle = recent.artist,
                    thumb = recent.thumb,
                    type = "album",
                    extraData = recent.ratingKey
                )
            )
        }

        // Add deterministic artist shortcuts from the cached catalog.
        val uniqueArtists = cachedTracks.map { it.artist }.distinct().sorted().take(2)
        uniqueArtists.forEach { artistName ->
            val match = cachedTracks.firstOrNull { it.artist == artistName }
            if (match != null) {
                items.add(
                    JumpBackInItem(
                        id = "jbi_art_${match.ratingKey}",
                        title = artistName,
                        subtitle = "Artist",
                        thumb = match.thumb,
                        type = "artist",
                        extraData = artistName
                    )
                )
            }
        }

        return items.take(6)
    }

    private fun buildDefaultStations(cachedTracks: List<CachedTrack>): List<RecommendedStation> {
        val stations = mutableListOf(
            RecommendedStation("st_lib", "Library Radio", "Continuous mix of your self-hosted collection", RadioType.LIBRARY_RADIO, listOf(0xFF4F46E5, 0xFF06B6D4)),
            RecommendedStation("st_art", "Artist Radio", "Build a station around a real Plex artist", RadioType.ARTIST_RADIO, listOf(0xFF8B5CF6, 0xFFEC4899)),
            RecommendedStation("st_deep", "Deep Cuts Radio", "Unearth rare, lesser-played library files", RadioType.DEEP_CUTS, listOf(0xFF10B981, 0xFF3B82F6)),
            RecommendedStation("st_forgotten", "Forgotten Favorites", "Played before, not heard again recently", RadioType.FORGOTTEN_FAVORITES, listOf(0xFFF97316, 0xFFEF4444)),
            RecommendedStation("st_rand", "Random Album Radio", "Plays full albums entirely at random", RadioType.RANDOM_ALBUM, listOf(0xFF6366F1, 0xFF8B5CF6)),
            RecommendedStation("st_genre", "Genre Radio", "Play tracks with a real Plex genre tag", RadioType.GENRE_RADIO, listOf(0xFF06B6D4, 0xFF3B82F6)),
            RecommendedStation("st_mood", "Mood Radio", "Filter your library by real genre tags", RadioType.MOOD_RADIO, listOf(0xFF06B6D4, 0xFF3B82F6)),
            RecommendedStation("st_decade", "Decade Radio", "Songs from a selected release decade", RadioType.DECADE_RADIO, listOf(0xFFEC4899, 0xFFF59E0B)),
            RecommendedStation("st_collection", "Collection Radio", "Play music from a Plex collection", RadioType.COLLECTION_RADIO, listOf(0xFF8B5CF6, 0xFFEC4899)),
            RecommendedStation("st_sound", "Soundtrack Radio", "Soundtracks, games, and cinematic tracks", RadioType.SOUNDTRACK_RADIO, listOf(0xFF14B8A6, 0xFF6366F1)),
            RecommendedStation("st_added", "Recently Added Radio", "The newest music in your Plex library", RadioType.RECENTLY_ADDED_RADIO, listOf(0xFF0EA5E9, 0xFF14B8A6)),
            RecommendedStation("st_day", "On This Day Radio", "Music added to your library on this date", RadioType.ON_THIS_DAY, listOf(0xFFF97316, 0xFFEC4899))
        )
        val hasGenreMetadata = cachedTracks.any { it.genres.isNotBlank() }
        val hasCollectionMetadata = cachedTracks.any { it.collections.isNotBlank() }
        val hasYearMetadata = cachedTracks.any { it.year != null }
        val hasSoundtrackMetadata = cachedTracks.any {
            it.genres.contains("soundtrack", true) ||
                it.collections.contains("soundtrack", true) ||
                it.album.contains("OST", true)
        }
        val hasAddedMetadata = cachedTracks.any { it.addedAt != null }
        val today = Calendar.getInstance()
        val hasOnThisDayMetadata = cachedTracks.any { track ->
            val addedAt = track.addedAt ?: return@any false
            Calendar.getInstance().apply { timeInMillis = addedAt * 1000L }.let { added ->
                added.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                    added.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
            }
        }
        val hasPlayedTracks = cachedTracks.any { it.playCount > 0 }

        return stations.filter { station ->
            when (station.type) {
                // These are stable entry points in the Home layout. Their playback
                // action will surface the normal empty/offline state when indexing
                // has not produced candidates yet.
                RadioType.LIBRARY_RADIO,
                RadioType.ARTIST_RADIO,
                RadioType.RANDOM_ALBUM -> true
                RadioType.DEEP_CUTS,
                RadioType.FORGOTTEN_FAVORITES -> hasPlayedTracks
                RadioType.TIME_TRAVEL,
                RadioType.DECADE_RADIO -> hasYearMetadata
                RadioType.MOOD_RADIO,
                RadioType.STYLE_RADIO,
                RadioType.GENRE_RADIO -> hasGenreMetadata
                RadioType.COLLECTION_RADIO -> hasCollectionMetadata
                RadioType.SOUNDTRACK_RADIO -> hasSoundtrackMetadata
                RadioType.RECENTLY_ADDED_RADIO -> hasAddedMetadata
                RadioType.ON_THIS_DAY -> hasOnThisDayMetadata
                else -> true
            }
        }
    }

    private fun buildRealMadeForYou(cachedTracks: List<CachedTrack>): List<MadeForYouItem> {
        val list = mutableListOf<MadeForYouItem>()

        // Look for heavy/rock keywords
        val heavyTracks = cachedTracks.filter { 
            it.artist.contains("Metal", true) || it.title.contains("Rock", true) || it.artist.contains("Horizon", true) 
        }
        if (heavyTracks.size >= 5) {
            list.add(
                MadeForYouItem(
                    id = "mfy_heavy",
                    title = "Heavy & Energetic Mix",
                    description = "Bracing riffs and powerful beats from your library",
                    artists = heavyTracks.map { it.artist }.distinct().take(4),
                    tracks = heavyTracks.sortedBy { it.ratingKey }.take(30).map { it.toTrackItem() },
                    thumb = heavyTracks.first().thumb
                )
            )
        }

        // Look for chill/cinematic keywords
        val ambientTracks = cachedTracks.filter { 
            it.artist.contains("Zimmer", true) || it.title.contains("Theme", true) || it.album.contains("Soundtrack", true) || it.album.contains("OST", true)
        }
        if (ambientTracks.size >= 5) {
            list.add(
                MadeForYouItem(
                    id = "mfy_cinematic",
                    title = "Cinematic Soundtrack Mix",
                    description = "Epic orchestral themes and background scores",
                    artists = ambientTracks.map { it.artist }.distinct().take(4),
                    tracks = ambientTracks.sortedBy { it.ratingKey }.take(30).map { it.toTrackItem() },
                    thumb = ambientTracks.first().thumb
                )
            )
        }

        // Generic custom mix
        val favoriteRanking = cachedTracks.sortedWith(compareByDescending<CachedTrack> { it.playCount }.thenBy { it.ratingKey })
        if (favoriteRanking.size >= 5) {
            list.add(
                MadeForYouItem(
                    id = "mfy_fav",
                    title = "Personal Favorites Mix",
                    description = "Your absolute most-cherished home tracks combined",
                    artists = favoriteRanking.map { it.artist }.distinct().take(4),
                    tracks = favoriteRanking.take(30).map { it.toTrackItem() },
                    thumb = favoriteRanking.first().thumb
                )
            )
        }

        return list
    }

    private fun buildRealMoreFromSections(cachedTracks: List<CachedTrack>): List<MoreFromSection> {
        val sections = mutableListOf<MoreFromSection>()
        val explanationService = RecommendationExplanationService()
        val nowSeconds = System.currentTimeMillis() / 1000L
        
        // Group by artist
        val artistGroups = cachedTracks.groupBy { it.artist }
            .toList()
            .filter { it.second.size >= 3 }
            .sortedBy { it.first }

        artistGroups.take(2).forEach { (artistName, tracks) ->
            sections.add(
                MoreFromSection(
                    title = "More from $artistName",
                    type = "artist",
                    tracks = tracks.sortedBy { it.ratingKey }.take(8).map { it.toTrackItem() }
                )
            )
        }

        // Group by album
        val albumGroups = cachedTracks.groupBy { it.album }
            .toList()
            .filter { it.second.size >= 4 }
            .sortedBy { it.first }

        albumGroups.take(1).forEach { (albumName, tracks) ->
            sections.add(
                MoreFromSection(
                    title = "Full Album: $albumName",
                    type = "album",
                    tracks = tracks.sortedBy { it.ratingKey }.map { it.toTrackItem() }
                )
            )
        }

        val forgotten = cachedTracks
            .filter { it.playCount > 0 && (it.lastPlayedAt == null || it.lastPlayedAt < nowSeconds - 30L * 24L * 60L * 60L) }
            .sortedWith(compareByDescending<CachedTrack> { it.playCount }.thenBy { it.ratingKey })
            .take(12)
        if (forgotten.isNotEmpty()) {
            val title = "Forgotten Favorites"
            sections.add(MoreFromSection(title, "history", forgotten.map { it.toTrackItem() }, explanationService.explainShelf(title, forgotten).text))
        }

        val recentlyAddedBarelyPlayed = cachedTracks
            .filter { it.addedAt != null && it.addedAt > nowSeconds - 30L * 24L * 60L * 60L && it.playCount == 0 }
            .sortedWith(compareByDescending<CachedTrack> { it.addedAt }.thenBy { it.ratingKey })
            .take(12)
        if (recentlyAddedBarelyPlayed.isNotEmpty()) {
            val title = "Recently Added, Barely Played"
            sections.add(MoreFromSection(title, "recent", recentlyAddedBarelyPlayed.map { it.toTrackItem() }, explanationService.explainShelf(title, recentlyAddedBarelyPlayed).text))
        }

        val familiarArtists = cachedTracks.filter { it.playCount > 0 }.map { it.artist }.toSet()
        val deepCuts = cachedTracks
            .filter { it.artist in familiarArtists && it.playCount == 0 }
            .sortedWith(compareBy<CachedTrack> { it.artist }.thenBy { it.album }.thenBy { it.ratingKey })
            .take(12)
        if (deepCuts.isNotEmpty()) {
            val title = "Deep Cuts From Familiar Artists"
            sections.add(MoreFromSection(title, "discovery", deepCuts.map { it.toTrackItem() }, explanationService.explainShelf(title, deepCuts).text))
        }

        val soundtrack = cachedTracks
            .filter { it.genres.contains("soundtrack", true) || it.collections.contains("soundtrack", true) || it.album.contains("OST", true) }
            .sortedBy { it.ratingKey }
            .take(12)
        if (soundtrack.isNotEmpty()) {
            val title = "Soundtracks and Game Music"
            sections.add(MoreFromSection(title, "collection", soundtrack.map { it.toTrackItem() }, explanationService.explainShelf(title, soundtrack).text))
        }

        val albumsForDiscovery = cachedTracks.groupBy { it.album }.entries.sortedBy { it.key }
        val randomAlbum = if (albumsForDiscovery.isEmpty()) {
            emptyList()
        } else {
            val dayIndex = deterministicDailyIndex(albumsForDiscovery.size, System.currentTimeMillis())
            albumsForDiscovery[dayIndex].value
        }
        if (randomAlbum.isNotEmpty()) {
            val title = "Random Album Discovery"
            sections.add(MoreFromSection(title, "album", randomAlbum.sortedBy { it.ratingKey }.map { it.toTrackItem() }, explanationService.explainShelf(title, randomAlbum).text))
        }

        return sections
    }

    private fun buildRealMostPlayed(
        history: List<RecentTrack>,
        cachedTracks: List<CachedTrack>
    ): List<MostPlayedItem> {
        // Use actual Plex play counts first, then local history as a supplement.
        val counts = history.groupBy { it.ratingKey }.mapValues { it.value.size }
        val sortedKeys = cachedTracks
            .map { it.ratingKey to (it.playCount + (counts[it.ratingKey] ?: 0)) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        val list = mutableListOf<MostPlayedItem>()
        if (sortedKeys.isNotEmpty()) {
            sortedKeys.take(8).forEach { (key, count) ->
                val track = cachedTracks.firstOrNull { it.ratingKey == key }?.toTrackItem()
                if (track != null) {
                    list.add(MostPlayedItem(track, count + 1))
                }
            }
        }

        return list.sortedByDescending { it.playCount }
    }

    private fun buildRealOnThisDay(cachedTracks: List<CachedTrack>): OnThisDayItem? {
        if (cachedTracks.isEmpty()) return null

        val today = Calendar.getInstance()
        val dated = cachedTracks.filter { track ->
            val addedAt = track.addedAt ?: return@filter false
            val added = Calendar.getInstance().apply { timeInMillis = addedAt * 1000L }
            added.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                added.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH) &&
                track.year != null
        }
        val track = dated.sortedWith(compareByDescending<CachedTrack> { it.year }.thenBy { it.ratingKey }).firstOrNull() ?: return null
        val selectedYear = track.year ?: return null
        val yearsAgo = Calendar.getInstance().get(Calendar.YEAR) - selectedYear

        return OnThisDayItem(
            title = track.album,
            artist = track.artist,
            year = selectedYear,
            timeAgo = if (yearsAgo > 0) "$yearsAgo years ago" else "Added a year ago",
            thumb = track.thumb,
            tracks = cachedTracks.filter { it.album == track.album }.map { it.toTrackItem() },
            matchReason = "This classic was released back in $selectedYear. Relive the original self-hosted experience!"
        )
    }

    private fun buildRealNewReleases(
        recentlyAdded: List<PlexMetadata>,
        cachedTracks: List<CachedTrack>
    ): List<NewReleaseItem> {
        if (recentlyAdded.isEmpty()) return emptyList()

        return recentlyAdded.take(5).map { album ->
            val albumTracks = cachedTracks.filter { it.album == album.title }.sortedBy { it.ratingKey }.map { it.toTrackItem() }
            NewReleaseItem(
                title = album.title,
                artist = album.parentTitle ?: "Various Artists",
                year = album.year ?: Calendar.getInstance().get(Calendar.YEAR),
                thumb = album.thumb ?: "",
                tracks = albumTracks
            )
        }
    }

    /*
     * Legacy mock-feed fixture retained outside the production path for
     * migration reference only. It is deliberately disabled so fabricated
     * content cannot be compiled into or returned by the Home engine.
     *

    private fun createMockFeed(history: List<RecentTrack>): HomeFeedState {
        // Legacy fixture retained only for source-history context; never return fabricated content.
        return HomeFeedState(isMock = false)
        val metalcoreTracks = listOf(
            TrackItem("mc_1", "Teardrops", "Bring Me The Horizon", "POST HUMAN: SURVIVAL HORROR", "", "", 215000L),
            TrackItem("mc_2", "Happy Song", "Bring Me The Horizon", "That's The Spirit", "", "", 239000L),
            TrackItem("mc_3", "MANTRA", "Bring Me The Horizon", "amo", "", "", 233000L),
            TrackItem("mc_4", "DArkSide", "Bring Me The Horizon", "POST HUMAN: NeX GEn", "", "", 224000L),
            TrackItem("mc_5", "Kool-Aid", "Bring Me The Horizon", "POST HUMAN: NeX GEn", "", "", 228000L),
            TrackItem("mc_6", "Lost", "Bring Me The Horizon", "POST HUMAN: NeX GEn", "", "", 205000L),
            TrackItem("mc_7", "Bow Down", "I Prevail", "TRAUMA", "", "", 242000L),
            TrackItem("mc_8", "Hurricane", "I Prevail", "TRAUMA", "", "", 223000L)
        )

        val animeSoundtracks = listOf(
            TrackItem("an_1", "The World (Death Note)", "Nightmare", "Death Note Original Soundtrack", "", "", 233000L),
            TrackItem("an_2", "Alones (Bleach)", "Aqua Timez", "Bleach Best Tunes", "", "", 258000L),
            TrackItem("an_3", "Guren no Yumiya", "Linked Horizon", "Shingeki no Kyojin Theme", "", "", 315000L),
            TrackItem("an_4", "Again (Fullmetal Alchemist)", "YUI", "FMA Brotherhood OST", "", "", 254000L),
            TrackItem("an_5", "Unravel (Tokyo Ghoul)", "TK from Ling Tosite Sigure", "Tokyo Ghoul Theme", "", "", 237000L)
        )

        val gameSoundtracks = listOf(
            TrackItem("gs_1", "Tina's Theme", "Nobuo Uematsu", "Final Fantasy VI OST - Disc 1", "", "", 185000L),
            TrackItem("gs_2", "To Zanarkand", "Nobuo Uematsu", "Final Fantasy X OST", "", "", 184000L),
            TrackItem("gs_3", "One-Winged Angel", "Nobuo Uematsu", "Final Fantasy VII OST", "", "", 362000L),
            TrackItem("gs_4", "Liberi Fatali", "Nobuo Uematsu", "Final Fantasy VIII OST", "", "", 191000L),
            TrackItem("gs_5", "Battle Theme", "Masashi Hamauzu", "Final Fantasy XIII OST", "", "", 204000L),
            TrackItem("gs_6", "Route 201 (Day)", "Junichi Masuda", "Pokémon Diamond & Pearl Super Music", "", "", 164000L),
            TrackItem("gs_7", "Primal Dialga Battle", "Keisuke Ito", "Pokémon Mystery Dungeon OST", "", "", 223000L)
        )

        val rockMetalTracks = listOf(
            TrackItem("rm_1", "Sonic Brew", "Black Label Society", "Sonic Brew (20th Anniversary)", "", "", 251000L),
            TrackItem("rm_2", "Stillborn", "Black Label Society", "The Blessed Hellride", "", "", 164000L),
            TrackItem("rm_3", "In This River", "Black Label Society", "Mafia", "", "", 292000L),
            TrackItem("rm_4", "My December", "Linkin Park", "Hybrid Theory", "", "", 259000L),
            TrackItem("rm_5", "Numb", "Linkin Park", "Meteora", "", "", 187000L)
        )

        val cinematicAmbient = listOf(
            TrackItem("ca_1", "Time", "Hans Zimmer", "Inception OST", "", "", 275000L),
            TrackItem("ca_2", "Cornfield Chase", "Hans Zimmer", "Interstellar OST", "", "", 126000L),
            TrackItem("ca_3", "Stay", "Hans Zimmer", "Interstellar OST", "", "", 412000L),
            TrackItem("ca_4", "Gladiator Theme", "Hans Zimmer", "Gladiator OST", "", "", 314000L),
            TrackItem("ca_5", "The Dark Knight Suite", "Hans Zimmer", "The Dark Knight OST", "", "", 482000L)
        )

        val allMockTracks = metalcoreTracks + animeSoundtracks + gameSoundtracks + rockMetalTracks + cinematicAmbient

        val recentPlays = listOf(
            HomeRecentPlay(
                id = "rp_1",
                title = "L.I.V.E. In São Paulo (live)",
                artist = "Bring Me The Horizon",
                thumb = "",
                relativeTime = "a day ago",
                type = "album",
                tracks = metalcoreTracks.take(4)
            ),
            HomeRecentPlay(
                id = "rp_2",
                title = "Final Fantasy VI OST - Disc 1",
                artist = "Nobuo Uematsu",
                thumb = "",
                relativeTime = "3 days ago",
                type = "album",
                tracks = gameSoundtracks.take(3)
            ),
            HomeRecentPlay(
                id = "rp_3",
                title = "Inception OST",
                artist = "Hans Zimmer",
                thumb = "",
                relativeTime = "5 days ago",
                type = "album",
                tracks = cinematicAmbient
            )
        )

        val recentlyAddedMetadata = listOf(
            PlexMetadata("rec_1", "", "Count Your Blessings", "album", "", "Bring Me The Horizon", null, null, null, null, 2006),
            PlexMetadata("rec_2", "", "Nintendo 3DS Pokémon Omega Ruby & Alpha Sapphire OST", "album", "", "Various Artists", null, null, null, null, 2014),
            PlexMetadata("rec_3", "", "Pokémon Diamond & Pearl Super Music Collection", "album", "", "GAME FREAK", null, null, null, null, 2006),
            PlexMetadata("rec_4", "", "Hybrid Theory", "album", "", "Linkin Park", null, null, null, null, 2000)
        )

        val dailyMixes = listOf(
            DailyMix("dm_1", "Daily Mix 1", "Bring Me The Horizon, I Prevail, and Linkin Park", metalcoreTracks + rockMetalTracks, listOf(0xFF3F3F46, 0xFF71717A)),
            DailyMix("dm_2", "Daily Mix 2", "Nobuo Uematsu, Masashi Hamauzu, and Final Fantasy OSTs", gameSoundtracks, listOf(0xFF1E293B, 0xFF475569)),
            DailyMix("dm_3", "Daily Mix 3", "Hans Zimmer and Orchestral Soundtracks", cinematicAmbient, listOf(0xFF172554, 0xFF1E40AF)),
            DailyMix("dm_4", "Daily Mix 4", "Linked Horizon, Nightmare, and Anime Favorites", animeSoundtracks, listOf(0xFF3B0764, 0xFF581C87)),
            DailyMix("dm_5", "Daily Mix 5", "Black Label Society and heavy rock riffs", rockMetalTracks, listOf(0xFF451A03, 0xFF78350F))
        )

        val jumpBackIn = listOf(
            JumpBackInItem("jbi_1", "Japanese Metal Mix", "Playlist • Custom Mix", "", "playlist"),
            JumpBackInItem("jbi_2", "Silver Bleeds the Black Sun...", "AFI", "", "album"),
            JumpBackInItem("jbi_3", "Hans Zimmer", "Artist • 14 albums", "", "artist"),
            JumpBackInItem("jbi_4", "Pirates of the Caribbean OST", "Hans Zimmer", "", "album")
        )

        val stations = buildDefaultStations(emptyList())

        val madeForYou = listOf(
            MadeForYouItem(
                id = "mfy_1",
                title = "Metalcore Mix",
                description = "Bracing heavy tracks of I Prevail, Bring Me The Horizon, and Poppy.",
                artists = listOf("Bring Me The Horizon", "I Prevail", "Poppy"),
                tracks = metalcoreTracks,
                thumb = ""
            ),
            MadeForYouItem(
                id = "mfy_2",
                title = "Anime Soundtrack Mix",
                description = "Soaring J-Rock and cinematic scoring from your favorite series.",
                artists = listOf("Nightmare", "YUI", "Linked Horizon", "Aqua Timez"),
                tracks = animeSoundtracks,
                thumb = ""
            ),
            MadeForYouItem(
                id = "mfy_3",
                title = "Final Fantasy Mix",
                description = "Legendary RPG compositions by Nobuo Uematsu and Masashi Hamauzu.",
                artists = listOf("Nobuo Uematsu", "Masashi Hamauzu", "Square Enix"),
                tracks = gameSoundtracks,
                thumb = ""
            )
        )

        val moreFromSections = listOf(
            MoreFromSection("More in Anime Soundtrack", "genre", animeSoundtracks),
            MoreFromSection("More in Game Soundtracks", "genre", gameSoundtracks),
            MoreFromSection("More from Bring Me The Horizon", "artist", metalcoreTracks)
        )

        val mostPlayed = listOf(
            MostPlayedItem(metalcoreTracks[0], 28),
            MostPlayedItem(gameSoundtracks[1], 24),
            MostPlayedItem(cinematicAmbient[0], 19),
            MostPlayedItem(animeSoundtracks[3], 15),
            MostPlayedItem(rockMetalTracks[0], 12)
        )

        val onThisDay = OnThisDayItem(
            title = "The Silver Lining (Expanded Edition)",
            artist = "Soul Asylum",
            year = 2006,
            timeAgo = "20 YEARS AGO",
            thumb = "",
            tracks = listOf(TrackItem("sa_1", "Stand Up", "Soul Asylum", "The Silver Lining", "", "", 200000L)),
            matchReason = "The Silver Lining was released back in 2006 (20 years ago). Revisit this absolute alternative rock masterpiece."
        )

        val newReleases = listOf(
            NewReleaseItem(
                title = "Open to Him // Mirror Feathers",
                artist = "Chaospra",
                year = 2026,
                thumb = "",
                tracks = listOf(TrackItem("ch_1", "Open to Him", "Chaospra", "Mirror Feathers", "", "", 180000L))
            )
        )

        return HomeFeedState(
            isMock = false,
            recentPlays = recentPlays,
            recentlyAdded = recentlyAddedMetadata,
            dailyMixes = dailyMixes,
            jumpBackIn = jumpBackIn,
            stations = stations,
            madeForYou = madeForYou,
            moreFromSections = moreFromSections,
            mostPlayedThisMonth = mostPlayed,
            onThisDay = onThisDay,
            newReleases = newReleases,
            history = history.ifEmpty { 
                metalcoreTracks.map { track ->
                    RecentTrack(
                        ratingKey = track.ratingKey,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        key = track.key,
                        thumb = track.thumb
                    )
                }
            }
        )
    }
    */
}

fun CachedTrack.toTrackItem(): TrackItem {
    return TrackItem(
        ratingKey = this.ratingKey,
        title = this.title,
        artist = this.artist,
        album = this.album,
        key = this.key,
        thumb = this.thumb,
        duration = this.duration,
        genres = this.genres.split('|').map { it.trim() }.filter { it.isNotEmpty() }
    )
}
