package com.example.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.PlexMedia
import com.example.data.PlexMetadata
import com.example.data.PlexPart
import com.example.data.calculateAlbumPlayStats
import com.example.data.normalizeCompanionArtistArtworkUrl
import com.example.data.rankArtistTracksByListens
import com.example.data.RadioRequest
import com.example.data.RadioType
import com.example.data.sortArtistAlbumsChronologically
import com.example.data.sortAlbumTracksInPlaybackOrder
import com.example.playback.TrackItem
import com.example.R
import androidx.annotation.StringRes
import java.util.Locale

enum class AlbumCategory {
    ALBUMS, SINGLES_EPS, LIVE_ALBUMS, COMPILATIONS
}

fun categorizeAlbum(album: PlexMetadata): AlbumCategory {
    val title = album.title.lowercase()
    return when {
        title.contains("live") || title.contains("royal albert hall") || title.contains("concert") -> AlbumCategory.LIVE_ALBUMS
        title.contains("single") || title.contains(" ep") || title.contains("ep ") || title.contains("(ep)") -> AlbumCategory.SINGLES_EPS
        title.contains("compilation") || title.contains("best of") || title.contains("greatest hits") || title.contains("anthology") -> AlbumCategory.COMPILATIONS
        else -> AlbumCategory.ALBUMS
    }
}

internal fun albumPlaytimeLabel(context: Context, totalPlaytimeMs: Long): String {
    val totalMinutes = totalPlaytimeMs.coerceAtLeast(0L) / 60_000L
    val hours = (totalMinutes / 60L).toInt()
    val minutes = (totalMinutes % 60L).toInt()
    val hourLabel = if (hours > 0) {
        context.resources.getQuantityString(R.plurals.album_detail_duration_hours, hours, hours)
    } else {
        null
    }
    val minuteLabel = if (minutes > 0 || hours == 0) {
        context.resources.getQuantityString(R.plurals.album_detail_duration_minutes, minutes, minutes)
    } else {
        null
    }
    val durationLabel = when {
        hourLabel != null && minuteLabel != null -> context.getString(
            R.string.album_detail_duration_hours_minutes,
            hourLabel,
            minuteLabel
        )
        hourLabel != null -> hourLabel
        else -> minuteLabel.orEmpty()
    }
    return context.getString(R.string.album_detail_listening_time, durationLabel)
}

internal fun artistPopularTrackDurationLabel(durationMs: Long?): String? =
    durationMs?.takeIf { it > 0L }?.let(::formatDuration)

internal fun artistAlbumYearRange(years: Iterable<Int?>): String? {
    val knownYears = years.filterNotNull().filter { it > 0 }
    val firstYear = knownYears.minOrNull() ?: return null
    val lastYear = knownYears.maxOrNull() ?: return null
    return if (firstYear == lastYear) firstYear.toString() else "$firstYear – $lastYear"
}

internal fun albumDetailArtistName(albumArtist: String?, trackArtist: String?): String =
    albumArtist?.takeIf(String::isNotBlank)
        ?: trackArtist?.takeIf(String::isNotBlank)
        ?: "Various Artists"

internal fun navigateToSimilarArtist(
    name: String,
    artists: List<PlexMetadata>,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToSearch: (String) -> Unit
) {
    val normalizedName = name.trim()
    if (normalizedName.isEmpty()) return

    val matchedArtist = artists.firstOrNull {
        it.title.trim().equals(normalizedName, ignoreCase = true)
    }
    if (matchedArtist != null) {
        onNavigateToArtist(matchedArtist.ratingKey, matchedArtist.title)
    } else {
        onNavigateToSearch(normalizedName)
    }
}

@Composable
internal fun ArtistDetailSectionHeading(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(titleRes).uppercase(Locale.ROOT),
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 1.sp
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
internal fun ArtistStyleRow(
    styleName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accessibilityLabel = stringResource(R.string.artist_detail_play_style_accessibility, styleName)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = accessibilityLabel }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowOutward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = styleName,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                color = Color.White
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun ArtistDetailPlayButton(
    artistName: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accessibilityLabel = stringResource(R.string.artist_detail_play_accessibility, artistName)
    val loadingLabel = if (isLoading) stringResource(R.string.player_loading_state) else null
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (isLoading) Color.White.copy(alpha = 0.3f) else Color.White)
            .clickable(enabled = !isLoading, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                if (loadingLabel != null) stateDescription = loadingLabel
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.Black,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun ArtistDetailScreen(
    artistId: String,
    artistName: String,
    viewModel: MusicViewModel,
    onNavigateToAlbum: (String, String) -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentArtistMetadata by viewModel.currentArtistMetadata.collectAsStateWithLifecycle()
    val currentArtistTracks by viewModel.currentArtistTracks.collectAsStateWithLifecycle()
    val artistProfile by viewModel.artistProfile.collectAsStateWithLifecycle()
    val albums by viewModel.currentArtistAlbums.collectAsStateWithLifecycle()
    val artistDetailLoadError by viewModel.artistDetailLoadError.collectAsStateWithLifecycle()
    val listeningHistory by viewModel.listeningHistory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val currentTrack by viewModel.playbackManager.currentTrack.collectAsStateWithLifecycle()
    val companionConfigured by viewModel.companionConfigured.collectAsStateWithLifecycle()
    val companionConnectionStatus by viewModel.companionConnectionStatus.collectAsStateWithLifecycle()

    var activeContextMenu by remember { mutableStateOf<ContextMenuItem?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var tracksToAdd by remember { mutableStateOf<List<TrackItem>>(emptyList()) }
    var albumIdToAdd by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(artistId) {
        viewModel.loadArtistDetail(artistId, artistName)
    }
    LaunchedEffect(companionConfigured) {
        if (companionConfigured) viewModel.ensureCompanionReachabilityChecked()
    }

    val context = LocalContext.current
    val baseUrl = viewModel.repository.settings.baseUrl
    val companionBaseUrl = viewModel.repository.settings.companionBackendUrl
    val isCompanionConfigured = viewModel.repository.settings.isCompanionConfigured
    val token = viewModel.repository.settings.token
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val unknownAlbumLabel = stringResource(R.string.common_unknown_album)

    // Find artist artwork from the authoritative profile/list metadata first,
    // then try the other catalog references if one source is missing or fails.
    val artistsList by viewModel.artists.collectAsStateWithLifecycle()
    val artistFromList = remember(artistsList, artistId) {
        artistsList.find { it.ratingKey == artistId }
    }
    val artistArtworkUrls = remember(
        artistId,
        artistProfile?.artworkUrl,
        artistFromList?.thumb,
        currentArtistMetadata?.thumb,
        baseUrl,
        companionBaseUrl,
        isCompanionConfigured
    ) {
        val references = if (isCompanionConfigured) {
            listOfNotNull(
                artistProfile?.artworkUrl,
                artistFromList?.thumb,
                currentArtistMetadata?.thumb
            )
        } else {
            listOfNotNull(currentArtistMetadata?.thumb, artistFromList?.thumb)
        }

        references.mapNotNull { reference ->
            val resolved = if (isCompanionConfigured) {
                normalizeCompanionArtistArtworkUrl(companionBaseUrl, reference)
            } else {
                resolveArtworkUrl(baseUrl, reference).takeIf {
                    it.startsWith("http://", ignoreCase = true) ||
                        it.startsWith("https://", ignoreCase = true)
                }
            }
            resolved?.takeIf { it.isNotBlank() }
        }.distinct()
    }
    var artistArtworkIndex by remember(artistArtworkUrls) { mutableStateOf(0) }
    val imageUrl = artistArtworkUrls.getOrNull(artistArtworkIndex)
    val artistThumb = imageUrl ?: artistArtworkUrls.firstOrNull() ?: ""

    // Categorized lists
    val sortedAlbums = remember(albums) {
        sortArtistAlbumsChronologically(albums)
    }
    val albumsList = remember(sortedAlbums) {
        sortedAlbums.filter { categorizeAlbum(it) == AlbumCategory.ALBUMS }
    }
    val singlesList = remember(sortedAlbums) {
        sortedAlbums.filter { categorizeAlbum(it) == AlbumCategory.SINGLES_EPS }
    }
    val liveList = remember(sortedAlbums) {
        sortedAlbums.filter { categorizeAlbum(it) == AlbumCategory.LIVE_ALBUMS }
    }
    val compilationsList = remember(sortedAlbums) {
        sortedAlbums.filter { categorizeAlbum(it) == AlbumCategory.COMPILATIONS }
    }

    // Popular tracks are based on actual local listening events, not the
    // optional viewCount field on a Plex child response.
    val localPlayCounts = remember(listeningHistory) {
        listeningHistory.associate { it.ratingKey to it.playCount }
    }
    val popularTracks = remember(currentArtistTracks, localPlayCounts) {
        rankArtistTracksByListens(currentArtistTracks, localPlayCounts)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
            .testTag("artist_detail_content")
    ) {
        // Top Header Navigation Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.details_back_accessibility),
                        tint = Color.White
                    )
                }
                
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    textAlign = TextAlign.Center
                )

            }
        }

        item {
            CompanionDetailConnectionNotice(
                companionConfigured = companionConfigured,
                status = companionConnectionStatus,
                onCheckConnection = viewModel::retryCompanionConnection,
                testTag = "artist_detail_connection_status_notice"
            )
        }

        artistDetailLoadError?.let { message ->
            item {
                DetailLoadFailureNotice(
                    message = message,
                    isRetrying = isLoading,
                    onRetry = { viewModel.loadArtistDetail(artistId, artistName) },
                    testTag = "artist_detail_retry_notice"
                )
            }
        }

        // Artist Photo & Controls Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Circular Avatar
                Card(
                    modifier = Modifier.size(130.dp),
                    shape = RoundedCornerShape(65.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = authenticatedArtworkRequest(context, imageUrl, token),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onError = {
                                if (artistArtworkIndex < artistArtworkUrls.lastIndex) {
                                    artistArtworkIndex++
                                }
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = artistName.take(1).uppercase(),
                                style = MaterialTheme.typography.displayMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // Play / Share Control Panel
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    ArtistDetailPlayButton(
                        artistName = artistName,
                        isLoading = isLoading,
                        onClick = { viewModel.playArtist(artistId) }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.playSeededRadio(
                                    RadioRequest(
                                        type = RadioType.ARTIST_RADIO,
                                        seedArtist = artistName
                                    )
                                )
                            },
                            enabled = !isLoading,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RssFeed,
                                contentDescription = stringResource(R.string.artist_detail_radio_accessibility, artistName),
                                tint = Color.White.copy(alpha = if (isLoading) 0.2f else 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                activeContextMenu = ContextMenuItem.Artist(
                                    ratingKey = artistId,
                                    name = artistName,
                                    thumb = artistThumb
                                )
                            },
                            enabled = !isLoading,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.details_more_options_accessibility, artistName),
                                tint = Color.White.copy(alpha = if (isLoading) 0.2f else 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.artist_detail_loading_music),
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.4f))
                    )
                }
            }
        } else {
            // 1. POPULAR TRACKS LIST (Moved to Top)
            if (popularTracks.isNotEmpty()) {
                item {
                    ArtistDetailSectionHeading(
                        titleRes = R.string.artist_detail_popular_tracks,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)
                    )
                }

                itemsIndexed(popularTracks) { _, track ->
                    val isLiked by viewModel.isTrackLikedFlow(track.ratingKey).collectAsStateWithLifecycle(initialValue = false)
                    val isCurrent = currentTrack?.ratingKey == track.ratingKey

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable(role = Role.Button) { viewModel.playTrackFromMetadata(track, popularTracks) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (isCurrent) Color(0xFF818CF8) else Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            artistPopularTrackDurationLabel(track.duration)?.let { durationLabel ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = durationLabel,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }

                        // Like button for popular track
                        IconButton(
                            onClick = {
                                val key = track.media?.firstOrNull()?.part?.firstOrNull()?.key ?: ""
                                val trackItem = TrackItem(
                                    ratingKey = track.ratingKey,
                                    title = track.title,
                                    artist = track.grandparentTitle?.takeIf(String::isNotBlank) ?: artistName,
                                    album = track.parentTitle?.takeIf(String::isNotBlank) ?: unknownAlbumLabel,
                                    key = key,
                                    thumb = track.thumb ?: "",
                                    duration = track.duration ?: 0L,
                                    albumRatingKey = track.parentRatingKey
                                )
                                viewModel.toggleLikeTrack(trackItem)
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = trackLikeActionDescription(isLiked),
                                tint = if (isLiked) Color.Red else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val key = track.media?.firstOrNull()?.part?.firstOrNull()?.key ?: ""
                                activeContextMenu = ContextMenuItem.Track(
                                    ratingKey = track.ratingKey,
                                    title = track.title,
                                    artist = track.grandparentTitle?.takeIf(String::isNotBlank) ?: artistName,
                                    album = track.parentTitle?.takeIf(String::isNotBlank) ?: unknownAlbumLabel,
                                    key = key,
                                    thumb = track.thumb ?: "",
                                    duration = track.duration ?: 0L,
                                    albumRatingKey = track.parentRatingKey,
                                    albumArtist = track.albumArtist,
                                    artistRatingKey = track.grandparentRatingKey
                                )
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.details_more_options_accessibility, track.title),
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

            }


            // 2. ALBUMS CATEGORY LIST
            if (albumsList.isNotEmpty()) {
                item {
                    val yearRange = remember(albumsList) {
                        artistAlbumYearRange(albumsList.map { it.year })
                    }
                    ArtistSectionHeader(
                        titleRes = R.string.library_category_albums,
                        infoText = yearRange.orEmpty()
                    )
                }
                items(
                    count = albumsList.size,
                    key = { idx -> albumsList[idx].ratingKey },
                    contentType = { "album" }
                ) { idx ->
                    val album = albumsList[idx]
                    ArtistAlbumListItem(
                        album = album,
                        artistName = artistName,
                        normalizedBaseUrl = normalizedBaseUrl,
                        token = token,
                        context = context,
                        onMoreClick = {
                            activeContextMenu = ContextMenuItem.Album(
                                ratingKey = album.ratingKey,
                                title = album.title,
                                artist = album.parentTitle?.takeIf(String::isNotBlank) ?: artistName,
                                thumb = album.thumb ?: ""
                            )
                        },
                        onClick = { onNavigateToAlbum(album.ratingKey, album.title) }
                    )
                }
            }

            // 3. SINGLES & EPs CATEGORY LIST
            if (singlesList.isNotEmpty()) {
                item {
                    ArtistSectionHeader(titleRes = R.string.artist_detail_singles_eps)
                }
                itemsIndexed(singlesList) { _, album ->
                    ArtistAlbumListItem(
                        album = album,
                        artistName = artistName,
                        normalizedBaseUrl = normalizedBaseUrl,
                        token = token,
                        context = context,
                        onMoreClick = {
                            activeContextMenu = ContextMenuItem.Album(
                                ratingKey = album.ratingKey,
                                title = album.title,
                                artist = album.parentTitle?.takeIf(String::isNotBlank) ?: artistName,
                                thumb = album.thumb ?: ""
                            )
                        },
                        onClick = { onNavigateToAlbum(album.ratingKey, album.title) }
                    )
                }
            }

            // 4. LIVE ALBUMS CATEGORY LIST
            if (liveList.isNotEmpty()) {
                item {
                    ArtistSectionHeader(titleRes = R.string.artist_detail_live_albums)
                }
                itemsIndexed(liveList) { _, album ->
                    ArtistAlbumListItem(
                        album = album,
                        artistName = artistName,
                        normalizedBaseUrl = normalizedBaseUrl,
                        token = token,
                        context = context,
                        onMoreClick = {
                            activeContextMenu = ContextMenuItem.Album(
                                ratingKey = album.ratingKey,
                                title = album.title,
                                artist = album.parentTitle?.takeIf(String::isNotBlank) ?: artistName,
                                thumb = album.thumb ?: ""
                            )
                        },
                        onClick = { onNavigateToAlbum(album.ratingKey, album.title) }
                    )
                }
            }

            // 5. COMPILATIONS CATEGORY LIST
            if (compilationsList.isNotEmpty()) {
                item {
                    ArtistSectionHeader(titleRes = R.string.library_category_compilations)
                }
                itemsIndexed(compilationsList) { _, album ->
                    ArtistAlbumListItem(
                        album = album,
                        artistName = artistName,
                        normalizedBaseUrl = normalizedBaseUrl,
                        token = token,
                        context = context,
                        onMoreClick = {
                            activeContextMenu = ContextMenuItem.Album(
                                ratingKey = album.ratingKey,
                                title = album.title,
                                artist = album.parentTitle?.takeIf(String::isNotBlank) ?: artistName,
                                thumb = album.thumb ?: ""
                            )
                        },
                        onClick = { onNavigateToAlbum(album.ratingKey, album.title) }
                    )
                }
            }

            // 6. ARTIST BIO
            item {
                Column(modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)) {
                    Text(
                        text = stringResource(R.string.artist_detail_bio_section).uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = artistProfile?.bio?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.artist_detail_bio_unavailable),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            lineHeight = 22.sp
                        )
                    )
                }
            }


            // 7. SIMILAR ARTISTS
            val similarList = artistProfile?.similarArtists ?: emptyList()
            if (similarList.isNotEmpty()) {
                item {
                    ArtistDetailSectionHeading(
                        titleRes = R.string.artist_detail_similar_artists,
                        modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp)
                    )
                }

                itemsIndexed(similarList) { _, similar ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                navigateToSimilarArtist(
                                    name = similar.name,
                                    artists = artistsList,
                                    onNavigateToArtist = onNavigateToArtist,
                                    onNavigateToSearch = onNavigateToSearch
                                )
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = similar.name.take(1).uppercase(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = similar.name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = similar.style,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            )
                        }
                        
                    }
                }
            }

            // 8. ARTIST STYLES
            val stylesList = artistProfile?.styles ?: emptyList()
            if (stylesList.isNotEmpty()) {
                item {
                    ArtistDetailSectionHeading(
                        titleRes = R.string.artist_detail_styles,
                        modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp)
                    )
                }

                itemsIndexed(stylesList) { _, styleName ->
                    ArtistStyleRow(
                        styleName = styleName,
                        onClick = {
                            viewModel.playSeededRadio(
                                RadioRequest(
                                    type = RadioType.STYLE_RADIO,
                                    style = styleName
                                )
                            )
                        }
                    )
                }

            }
        }
        
        // Add elegant bottom spacing
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // Context Menu Trigger
    activeContextMenu?.let { item ->
        MusicContextMenu(
            item = item,
            viewModel = viewModel,
            onDismiss = { activeContextMenu = null },
            onNavigateToArtist = onNavigateToArtist,
            onNavigateToAlbum = onNavigateToAlbum
        )
    }
}

@Composable
fun ArtistSectionHeader(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
    infoText: String = ""
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(titleRes).uppercase(Locale.ROOT),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            )
        }
        
        if (infoText.isNotEmpty()) {
            Text(
                text = infoText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.4f)
                )
            )
        }
    }
}

@Composable
fun ArtistAlbumListItem(
    album: PlexMetadata,
    artistName: String,
    normalizedBaseUrl: String,
    token: String,
    context: android.content.Context,
    onMoreClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val albumThumb = album.thumb
    val imageUrl = if (!albumThumb.isNullOrEmpty()) resolveArtworkUrl(normalizedBaseUrl, albumThumb) else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = authenticatedArtworkRequest(context, imageUrl, token),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val yearText = album.year?.takeIf { it > 0 }?.toString()
            if (yearText != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = yearText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.details_more_options_accessibility, album.title),
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun AlbumDetailScreen(
    albumId: String,
    albumName: String,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    seedTracks: List<TrackItem> = emptyList()
) {
    val loadedTracks by viewModel.currentAlbumTracks.collectAsStateWithLifecycle()
    val albumDetailLoadError by viewModel.albumDetailLoadError.collectAsStateWithLifecycle()
    val listeningHistory by viewModel.listeningHistory.collectAsStateWithLifecycle()
    val seededTrackMetadata = remember(albumId, seedTracks) {
        seedTracks.map { track ->
            PlexMetadata(
                ratingKey = track.ratingKey,
                key = track.key,
                title = track.title,
                type = "track",
                thumb = track.thumb.takeIf(String::isNotBlank),
                parentTitle = track.album,
                grandparentTitle = track.artist,
                albumArtist = track.albumArtist ?: track.artist,
                index = track.index,
                parentIndex = track.discNumber,
                duration = track.duration,
                media = listOf(PlexMedia(listOf(PlexPart(track.key)))),
                parentRatingKey = track.albumRatingKey
            )
        }
    }
    // The repository owns canonical disc/track ordering. Preserve it here so
    // multi-disc albums are not re-sorted as 1,1,2,2 across discs.
    val finalTracks = remember(loadedTracks, seededTrackMetadata) {
        sortAlbumTracksInPlaybackOrder(loadedTracks.ifEmpty { seededTrackMetadata })
    }
    val albumArtist = albumDetailArtistName(
        albumArtist = finalTracks.firstOrNull()?.albumArtist,
        trackArtist = finalTracks.firstOrNull()?.grandparentTitle
    )
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val currentTrack by viewModel.playbackManager.currentTrack.collectAsStateWithLifecycle()
    val companionConfigured by viewModel.companionConfigured.collectAsStateWithLifecycle()
    val companionConnectionStatus by viewModel.companionConnectionStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val localPlayCounts = remember(listeningHistory) {
        listeningHistory.associate { it.ratingKey to it.playCount }
    }
    val albumPlayStats = remember(finalTracks, localPlayCounts) {
        calculateAlbumPlayStats(finalTracks, localPlayCounts)
    }
    val totalPlays = albumPlayStats.totalPlays
    val totalPlaytimeMs = albumPlayStats.totalPlaytimeMs
    val playtimeLabel = remember(context, totalPlaytimeMs) {
        albumPlaytimeLabel(context, totalPlaytimeMs)
    }

    var activeContextMenu by remember { mutableStateOf<ContextMenuItem?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var tracksToAdd by remember { mutableStateOf<List<TrackItem>>(emptyList()) }
    var albumIdToAdd by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(albumId) {
        viewModel.loadAlbumDetail(albumId)
    }
    LaunchedEffect(companionConfigured) {
        if (companionConfigured) viewModel.ensureCompanionReachabilityChecked()
    }

    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token

    // Find first track's thumbnail as album art, or use album's own
    val firstTrackThumb = finalTracks.firstOrNull()?.thumb
    val thumbPath = firstTrackThumb?.takeIf { it.isNotEmpty() }
    val imageUrl = thumbPath?.let { resolveArtworkUrl(baseUrl, it) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Scrollable Contents
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .testTag("album_detail_content")
        ) {
            item {
                CompanionDetailConnectionNotice(
                    companionConfigured = companionConfigured,
                    status = companionConnectionStatus,
                    onCheckConnection = viewModel::retryCompanionConnection,
                    testTag = "album_detail_connection_status_notice"
                )
            }

            // Album Header Graphic Area
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp) // Slightly taller for mockup style
                ) {
                    // Background image
                    if (imageUrl != null) {
                        AsyncImage(
                            model = authenticatedArtworkRequest(context, imageUrl, token),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF1F2937), Color(0xFF111827))
                                    )
                                )
                        )
                    }

                    // Overlay for readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.8f)
                                    )
                                )
                            )
                    )

                    // Back button overlay
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(48.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .align(Alignment.TopStart)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.details_back_accessibility),
                            tint = Color.White
                        )
                    }

                    // More options button overlay
                    IconButton(
                        onClick = {
                            activeContextMenu = ContextMenuItem.Album(
                                ratingKey = albumId,
                                title = albumName,
                                artist = albumArtist,
                                thumb = thumbPath ?: ""
                            )
                        },
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(48.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.details_more_options_accessibility, albumName),
                            tint = Color.White
                        )
                    }

                    // Mockup Style Centered Content
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Album Title in Capsule
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = albumName,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Artist Name in Capsule
                        val artistId = finalTracks.firstOrNull()?.grandparentRatingKey ?: ""

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier
                                .padding(bottom = 24.dp)
                                .clickable(role = Role.Button) {
                                    if (artistId.isNotEmpty()) {
                                        onNavigateToArtist(artistId, albumArtist)
                                    }
                                }
                        ) {
                            Text(
                                text = localizedArtistPresentation(albumArtist),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color(0xFF818CF8),
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Play stats in Capsule
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                ) {
                                Text(
                                    text = localizedPlayCountLabel(totalPlays),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = playtimeLabel,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    // Action Bar at the bottom of the header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.8f)
                                    )
                                )
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Large Green Play Button
                            IconButton(
                                onClick = { viewModel.playAllTracks(finalTracks, 0) },
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(Color(0xFF22C55E))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.album_detail_play_all_accessibility, albumName),
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            FeedbackIconButton(onClick = { viewModel.playAllTracks(finalTracks.shuffled(), 0) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = stringResource(R.string.album_detail_shuffle_accessibility, albumName),
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            FeedbackIconButton(onClick = {
                                viewModel.playSeededRadio(
                                    RadioRequest(
                                        type = RadioType.ALBUM_RADIO,
                                        seedAlbum = albumName
                                    )
                                )
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.RssFeed,
                                    contentDescription = stringResource(R.string.album_detail_radio_accessibility, albumName),
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            FeedbackIconButton(onClick = { viewModel.downloadTracksList(finalTracks.mapNotNull { viewModel.mapMetadataToTrackItem(it) }) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = stringResource(R.string.album_detail_download_accessibility, albumName),
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            FeedbackIconButton(onClick = {
                                tracksToAdd = finalTracks.mapNotNull { viewModel.mapMetadataToTrackItem(it) }
                                albumIdToAdd = albumId
                                showPlaylistDialog = true
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.album_detail_add_to_playlist_accessibility, albumName),
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Small spacer before tracks
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Tracks List
            if (isLoading && finalTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF818CF8))
                    }
                }
            } else if (finalTracks.isEmpty()) {
                item {
                    if (albumDetailLoadError != null) {
                        DetailLoadFailureNotice(
                            message = albumDetailLoadError.orEmpty(),
                            isRetrying = isLoading,
                            onRetry = { viewModel.loadAlbumDetail(albumId) },
                            testTag = "album_detail_retry_notice"
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.album_detail_empty_tracks),
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(finalTracks, key = { _, track -> track.ratingKey }) { index, track ->
                    val key = track.media?.firstOrNull()?.part?.firstOrNull()?.key ?: ""
                    val trackArtistId = track.grandparentRatingKey ?: ""
                    val trackArtist = track.grandparentTitle?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.common_unknown_artist)
                    val isCurrent = currentTrack?.ratingKey == track.ratingKey
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                        ) {
                            TrackListItem(
                                index = track.index?.takeIf { it > 0 } ?: (index + 1),
                                track = track,
                                onMoreClick = {
                                    activeContextMenu = ContextMenuItem.Track(
                                        ratingKey = track.ratingKey,
                                        title = track.title,
                                        artist = trackArtist,
                                        album = albumName,
                                        key = key,
                                        thumb = track.thumb ?: "",
                                        duration = track.duration ?: 0L,
                                        albumRatingKey = albumId,
                                        albumArtist = track.albumArtist,
                                        artistRatingKey = track.grandparentRatingKey
                                    )
                                },
                                onClick = {
                                    viewModel.playTrackFromMetadata(track, finalTracks)
                                },
                                onArtistClick = if (trackArtistId.isNotEmpty()) {
                                    { onNavigateToArtist(trackArtistId, trackArtist) }
                                } else null
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.05f)
                        )
                    }
                }
            }
        }
    }

    // Context Menu Trigger
    activeContextMenu?.let { item ->
        MusicContextMenu(
            item = item,
            viewModel = viewModel,
            onDismiss = { activeContextMenu = null },
            onNavigateToArtist = onNavigateToArtist,
            onNavigateToAlbum = { _, _ -> }
        )
    }

    if (showPlaylistDialog) {
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        PlaylistSelectionDialog(
            playlists = playlists,
            onDismiss = { showPlaylistDialog = false },
            onPlaylistSelected = { playlistId ->
                if (albumIdToAdd != null) {
                    viewModel.addAlbumToPlaylist(playlistId, albumIdToAdd!!)
                } else {
                    tracksToAdd.forEach { track ->
                        viewModel.addTrackToPlaylist(playlistId, track)
                    }
                }
                showPlaylistDialog = false
            },
            onCreateNewPlaylist = { name, onFailure, onCreated ->
                viewModel.createPlaylist(
                    name = name,
                    onFailure = onFailure,
                    onCreated = { newId ->
                        if (albumIdToAdd != null) {
                            viewModel.addAlbumToPlaylist(newId.toInt(), albumIdToAdd!!)
                        } else {
                            tracksToAdd.forEach { track ->
                                viewModel.addTrackToPlaylist(newId.toInt(), track)
                            }
                        }
                        onCreated()
                    }
                )
            }
        )
    }
}

@Composable
fun TrackListItem(
    index: Int,
    track: PlexMetadata,
    onMoreClick: () -> Unit,
    onClick: () -> Unit,
    onArtistClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.details_track_play_accessibility, track.title),
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .testTag("track_item_${track.ratingKey}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Index Number - Muted and smaller matching reference
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.3f),
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.width(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Title and optional subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Show artist if track has custom artist distinct from album artist
            val trackArtist = track.grandparentTitle?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.common_unknown_artist)
            Text(
                text = trackArtist,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.5f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onArtistClick != null) {
                    Modifier.clickable(
                        onClickLabel = stringResource(R.string.details_artist_open_accessibility, trackArtist),
                        role = Role.Button,
                        onClick = onArtistClick
                    )
                } else Modifier
            )
        }

        // Track Duration
        val durationMs = track.duration ?: 0L
        if (durationMs > 0) {
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.35f)
                ),
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // More Actions Button
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.details_more_options_accessibility, track.title),
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
