package com.example.data

import com.example.playback.TrackItem

/**
 * Converts SpotCore's companion contract into the existing phone-side Home
 * model. Empty companion collections deliberately preserve their Plex-backed
 * equivalents so a partial server response cannot make Home less useful.
 */
object BackendHomeMapper {
    fun track(dto: BackendTrackDto): TrackItem = TrackItem(
        ratingKey = "companion:${dto.id}",
        title = dto.title,
        artist = dto.artist,
        album = dto.album,
        key = dto.streamUrl,
        thumb = dto.coverUrl,
        duration = dto.duration,
        genres = dto.genre?.takeIf(String::isNotBlank)?.let(::listOf).orEmpty(),
        albumRatingKey = albumNavigationKey(dto.artist, dto.album)
    )

    fun merge(response: BackendHomeFeedResponse, fallback: HomeFeedState): HomeFeedState {
        val recent = response.recentlyPlayed.orEmpty().map { dto ->
            val item = track(dto)
            HomeRecentPlay(
                id = "companion-recent:${dto.id}",
                title = item.title,
                artist = item.artist,
                thumb = item.thumb,
                relativeTime = "From SpotCore",
                type = "track",
                tracks = listOf(item)
            )
        }
        val mixes = response.dailyMixes.orEmpty().map { mix ->
            DailyMix(
                id = mix.id,
                title = mix.title,
                reason = mix.reason,
                tracks = mix.tracks.map(::track),
                colors = mix.colors.mapNotNull(::parseColor).ifEmpty {
                    listOf(0xFF4F46E5, 0xFF06B6D4)
                }
            )
        }.filter { it.tracks.isNotEmpty() }
        val albumShelves = buildList {
            response.dailyMixes.orEmpty().forEach { mix ->
                val albums = mix.albums.map(::album).filter { it.tracks.isNotEmpty() }
                if (albums.isNotEmpty()) add(AlbumRecommendationShelf(mix.id, mix.title, mix.reason, albums))
            }
            response.madeForYou.orEmpty().forEach { row ->
                val albums = row.albums.map(::album).filter { it.tracks.isNotEmpty() }
                if (albums.isNotEmpty()) add(AlbumRecommendationShelf(row.id, row.title, row.description, albums))
            }
        }
        val stations = response.stations.orEmpty().mapNotNull { station ->
            val type = radioType(station.type) ?: return@mapNotNull null
            RecommendedStation(
                id = station.id,
                title = station.title,
                subtitle = station.subtitle,
                type = type,
                gradientColors = station.colors.mapNotNull(::parseColor).ifEmpty {
                    listOf(0xFF6366F1, 0xFF8B5CF6)
                }
            )
        }
        val madeForYou = response.madeForYou.orEmpty().map { item ->
            MadeForYouItem(
                id = item.id,
                title = item.title,
                description = item.description,
                artists = item.artists,
                tracks = item.tracks.map(::track),
                thumb = item.coverUrl
            )
        }.filter { it.tracks.isNotEmpty() }
        val recentlyAdded = response.recentlyAdded.orEmpty().map { album ->
            PlexMetadata(
                ratingKey = albumNavigationKey(album.artist, album.title),
                title = album.title,
                type = "album",
                thumb = album.coverUrl,
                parentTitle = album.artist,
                year = album.year
            )
        }
        val onThisDay = response.onThisDay?.takeIf { it.tracks.isNotEmpty() }?.let { item ->
            OnThisDayItem(
                title = item.albumTitle,
                artist = item.artist,
                year = item.year,
                timeAgo = item.timeAgo,
                thumb = item.coverUrl,
                tracks = item.tracks.map(::track),
                matchReason = item.matchReason
            )
        }
        val hasCompanionContent = recent.isNotEmpty() || mixes.isNotEmpty() ||
            stations.isNotEmpty() || madeForYou.isNotEmpty() ||
            recentlyAdded.isNotEmpty() || onThisDay != null || albumShelves.isNotEmpty()

        if (!hasCompanionContent) return fallback
        return fallback.copy(
            recentPlays = recent.ifEmpty { fallback.recentPlays },
            recentlyAdded = recentlyAdded.ifEmpty { fallback.recentlyAdded },
            dailyMixes = mixes.ifEmpty { fallback.dailyMixes },
            stations = stations.ifEmpty { fallback.stations },
            madeForYou = madeForYou.ifEmpty { fallback.madeForYou },
            albumShelves = albumShelves.ifEmpty { fallback.albumShelves },
            onThisDay = onThisDay ?: fallback.onThisDay,
            source = HomeFeedSource.SPOTCORE
        )
    }

    private fun album(dto: BackendAlbumDto): AlbumRecommendation {
        val albumKey = albumNavigationKey(dto.artist, dto.title)
        return AlbumRecommendation(
            id = albumKey,
            title = dto.title,
            artist = dto.artist,
            thumb = dto.coverUrl,
            year = dto.year,
            tracks = dto.tracks.map(::track).map { it.copy(albumRatingKey = albumKey) }
        )
    }

    private fun parseColor(value: String): Long? {
        val normalized = value.trim().removePrefix("#")
        val rgb = normalized.toLongOrNull(16) ?: return null
        return when (normalized.length) {
            6 -> 0xFF000000 or rgb
            8 -> rgb
            else -> null
        }
    }

    private fun radioType(value: String): RadioType? {
        val normalized = value.trim()
            .replace('-', '_')
            .replace(' ', '_')
            .uppercase()
        return RadioType.entries.firstOrNull { it.name == normalized }
    }
}
