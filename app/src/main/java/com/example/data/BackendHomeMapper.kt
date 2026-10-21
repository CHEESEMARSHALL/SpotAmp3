package com.example.data

import com.example.playback.TrackItem

/**
 * Converts SpotCore's companion contract into the existing phone-side Home
 * model. A successful companion response is authoritative: local Room data is
 * only used by the caller when the request itself fails. This prevents a
 * partial/empty server response from silently reintroducing alphabetical local
 * shelves beside the real server recommendations.
 */
object BackendHomeMapper {
    fun track(
        dto: BackendTrackDto,
        artworkBaseUrl: String = ""
    ): TrackItem {
        val albumArtist = effectiveAlbumArtist(dto.albumArtist, dto.artist)
        return TrackItem(
            ratingKey = "companion:${dto.id}",
            title = dto.title,
            artist = dto.artist,
            album = dto.album,
            key = resolveCompanionMediaReference(artworkBaseUrl, dto.streamUrl),
            thumb = normalizeCompanionArtworkUrl(
                companionBaseUrl = artworkBaseUrl,
                trackId = dto.id,
                coverUrl = dto.coverUrl
            ),
            duration = dto.duration.toLong(),
            index = dto.resolvedTrackNumber(),
            discNumber = dto.resolvedDiscNumber(),
            genres = dto.genre?.takeIf(String::isNotBlank)?.let(::listOf).orEmpty(),
            albumRatingKey = albumNavigationKey(albumArtist, dto.album),
            albumArtist = albumArtist
        )
    }

    fun merge(
        response: BackendHomeFeedResponse,
        fallback: HomeFeedState,
        artworkBaseUrl: String = ""
    ): HomeFeedState {
        val recent = response.recentlyPlayed.orEmpty().map { dto ->
            val item = track(dto, artworkBaseUrl)
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
                tracks = mix.tracks.map { track(it, artworkBaseUrl) },
                colors = mix.colors.mapNotNull(::parseColor).ifEmpty {
                    listOf(0xFF4F46E5, 0xFF06B6D4)
                }
            )
        }.filter { it.tracks.isNotEmpty() }
        val recentlyAdded = response.recentlyAdded.orEmpty().map { album ->
            PlexMetadata(
                ratingKey = albumNavigationKey(album.albumArtist ?: album.artist, album.title),
                title = album.title,
                type = "album",
                thumb = normalizeCompanionArtworkUrl(
                    companionBaseUrl = artworkBaseUrl,
                    trackId = album.tracks.firstOrNull()?.id.orEmpty(),
                    coverUrl = album.coverUrl
                ),
                parentTitle = album.albumArtist ?: album.artist,
                albumArtist = album.albumArtist ?: album.artist,
                year = album.year
            )
        }.distinctBy { it.ratingKey }
        val seenAlbumIds = recentlyAdded.map { it.ratingKey }.toMutableSet()
        val albumShelves = buildList {
            response.madeForYou.orEmpty().forEach { row ->
                val albums = row.albums
                    .map { album(it, artworkBaseUrl) }
                    .filter { it.tracks.isNotEmpty() }
                    .filter { seenAlbumIds.add(it.id) }
                if (albums.isNotEmpty()) add(AlbumRecommendationShelf(row.id, row.title, row.description, albums))
            }
        }
        val albumShelfRowIds = response.madeForYou.orEmpty()
            .filter { row -> row.albums.any { it.tracks.isNotEmpty() } }
            .map { it.id }
            .toSet()
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
                tracks = sortTrackItemsInPlaybackOrder(item.tracks.map { track(it, artworkBaseUrl) }),
                thumb = normalizeCompanionArtworkUrl(
                    companionBaseUrl = artworkBaseUrl,
                    trackId = item.tracks.firstOrNull()?.id.orEmpty(),
                    coverUrl = item.coverUrl
                )
            )
        }.filter { it.tracks.isNotEmpty() && it.id !in albumShelfRowIds }
        val onThisDay = response.onThisDay?.takeIf { it.tracks.isNotEmpty() }?.let { item ->
            OnThisDayItem(
                title = item.albumTitle,
                artist = item.artist,
                year = item.year,
                timeAgo = item.timeAgo,
                thumb = normalizeCompanionArtworkUrl(
                    companionBaseUrl = artworkBaseUrl,
                    trackId = item.tracks.firstOrNull()?.id.orEmpty(),
                    coverUrl = item.coverUrl
                ),
                tracks = item.tracks.map { track(it, artworkBaseUrl) },
                matchReason = item.matchReason
            )
        }
        return HomeFeedState(
            recentPlays = recent,
            recentlyAdded = recentlyAdded,
            dailyMixes = mixes,
            stations = stations,
            madeForYou = madeForYou,
            albumShelves = albumShelves,
            onThisDay = onThisDay,
            source = HomeFeedSource.SPOTCORE
        )
    }

    private fun album(
        dto: BackendAlbumDto,
        artworkBaseUrl: String
    ): AlbumRecommendation {
        val albumArtist = effectiveAlbumArtist(dto.albumArtist, dto.artist)
        val albumKey = albumNavigationKey(albumArtist, dto.title)
        return AlbumRecommendation(
            id = albumKey,
            title = dto.title,
            artist = albumArtist,
            thumb = normalizeCompanionArtworkUrl(
                companionBaseUrl = artworkBaseUrl,
                trackId = dto.tracks.firstOrNull()?.id.orEmpty(),
                coverUrl = dto.coverUrl
            ),
            year = dto.year,
            tracks = sortTrackItemsInPlaybackOrder(
                dto.tracks.map { track(it, artworkBaseUrl) }.map {
                    it.copy(albumRatingKey = albumKey, albumArtist = albumArtist)
                }
            )
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
