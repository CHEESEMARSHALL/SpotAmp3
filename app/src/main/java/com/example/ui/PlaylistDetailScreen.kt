package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.PlaylistTrackEntity
import com.example.data.toTrackItem
import com.example.playback.TrackItem
import kotlinx.coroutines.flow.map

@Composable
fun PlaylistDetailScreen(
    playlistId: Int,
    playlistName: String,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect tracks reactively from Room
    val tracksState = remember(playlistId) {
        viewModel.repository.getPlaylistTracks(playlistId).map<
            List<PlaylistTrackEntity>,
            List<PlaylistTrackEntity>?
            > { it }
    }
    val tracks by tracksState.collectAsStateWithLifecycle(initialValue = null)
    val loadedTracks = tracks.orEmpty()
    var orderedTracks by remember(playlistId) { mutableStateOf(emptyList<PlaylistTrackEntity>()) }
    LaunchedEffect(tracks) {
        tracks?.let { orderedTracks = it }
    }
    val visibleTracks = if (orderedTracks.isEmpty() && loadedTracks.isNotEmpty()) {
        loadedTracks
    } else {
        orderedTracks
    }
    val currentTrack by viewModel.playbackManager.currentTrack.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(playlistId) {
        viewModel.refreshSharedPlaylist(playlistId)
    }

    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

    // Find first track's thumbnail as playlist art, or use fallback gradient
    val firstTrackThumb = visibleTracks.firstOrNull()?.thumb
    val thumbPath = if (!firstTrackThumb.isNullOrEmpty()) firstTrackThumb else null
    val imageUrl = if (thumbPath != null) resolveArtworkUrl(baseUrl, thumbPath) else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // Playlist Header Graphic Area
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    // Back button overlay
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(48.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .align(Alignment.TopStart)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.playlist_detail_back),
                            tint = Color.White
                        )
                    }

                    // Background ambient blur if artwork exists
                    if (imageUrl != null) {
                        AsyncImage(
                            model = authenticatedArtworkRequest(context, imageUrl, token),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f))
                        )
                    }

                    // Bottom ambient dark overlay gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF0F0F15).copy(alpha = 0.6f),
                                        Color(0xFF080808)
                                    )
                                )
                            )
                    )

                    // Centered Card Cover Art
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(top = 16.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Card(
                            modifier = Modifier
                                .size(120.dp)
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            if (imageUrl != null) {
                                AsyncImage(
                                    model = authenticatedArtworkRequest(context, imageUrl, token),
                                    contentDescription = playlistName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF10B981), Color(0xFF059669))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.QueueMusic,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = playlistName,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        Text(
                            text = if (tracks == null) {
                                stringResource(R.string.playlist_detail_type)
                            } else {
                                playlistDetailHeaderLabel(
                                    stringResource(R.string.playlist_detail_type),
                                    loadedTracks.size
                                )
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // Quick Play Actions Header
            if (visibleTracks.isNotEmpty()) {
                item {
                    PlaylistDetailActionsRow(
                        playlistName = playlistName,
                        onPlayAll = {
                            viewModel.playbackManager.playQueue(visibleTracks.map { it.toTrackItem() }, 0)
                        },
                        onShuffle = {
                            viewModel.playbackManager.playQueue(visibleTracks.map { it.toTrackItem() }.shuffled(), 0)
                        },
                        onDownload = { viewModel.downloadTracksList(visibleTracks.map { it.toTrackItem() }) }
                    )
                }
            }

            // Tracks List
            if (tracks == null || visibleTracks.isEmpty()) {
                item {
                    PlaylistDetailTracksPlaceholder(isLoading = tracks == null)
                }
            } else {
                itemsIndexed(visibleTracks, key = { _, track -> track.id }) { index, track ->
                    Column {
                        PlaylistTrackRow(
                            index = index + 1,
                            track = track,
                            playlistName = playlistName,
                            onDelete = {
                                viewModel.removeTrackFromPlaylist(playlistId, track.ratingKey)
                            },
                            onClick = {
                                val playbackTracks = visibleTracks.map { it.toTrackItem() }
                                viewModel.playbackManager.playTrack(track.toTrackItem(), playbackTracks)
                            },
                            onMove = { delta ->
                                val currentIndex = visibleTracks.indexOfFirst { it.id == track.id }
                                val target = (currentIndex + delta).coerceIn(0, visibleTracks.lastIndex)
                                if (currentIndex >= 0 && target != currentIndex) {
                                    val reordered = visibleTracks.toMutableList().apply { add(target, removeAt(currentIndex)) }
                                    orderedTracks = reordered
                                    viewModel.reorderPlaylistTracks(reordered)
                                }
                            },
                            isPlaying = currentTrack?.ratingKey == track.ratingKey
                        )
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
}

@Composable
internal fun PlaylistDetailTracksPlaceholder(isLoading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .testTag(if (isLoading) "playlist_detail_loading_state" else "playlist_detail_empty_state"),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFF818CF8))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.playlist_detail_loading_tracks),
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.65f))
                )
            }
        } else {
            Text(
                text = stringResource(R.string.playlist_detail_empty),
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
internal fun playlistDetailHeaderLabel(typeLabel: String, trackCount: Int): String =
    stringResource(
        R.string.playlist_detail_header,
        typeLabel,
        pluralStringResource(R.plurals.playlist_detail_track_count, trackCount, trackCount)
    )

@Composable
internal fun PlaylistDetailActionsRow(
    playlistName: String,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPlayAll,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary)
                .testTag("playlist_detail_play_all")
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.playlist_detail_play_all, playlistName),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        IconButton(
            onClick = onShuffle,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("playlist_detail_shuffle")
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.playlist_detail_shuffle, playlistName),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onDownload,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("playlist_detail_download")
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = stringResource(R.string.playlist_detail_download, playlistName),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }

    HorizontalDivider(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = Color.White.copy(alpha = 0.1f)
    )
}

@Composable
fun PlaylistTrackRow(
    index: Int,
    track: PlaylistTrackEntity,
    playlistName: String,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    isPlaying: Boolean = false
) {
    val activeColor = MaterialTheme.colorScheme.primary
    var dragOffset by remember { mutableStateOf(0f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isPlaying) activeColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragOffset = 0f },
                    onDragCancel = { dragOffset = 0f },
                    onDragEnd = { dragOffset = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount.y
                        while (dragOffset > 52f) { onMove(1); dragOffset -= 52f }
                        while (dragOffset < -52f) { onMove(-1); dragOffset += 52f }
                    }
                )
            }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Index Number - Muted matching reference
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
                    color = if (isPlaying) activeColor else Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = localizedArtistAlbumSubtitle(track.artist, track.album),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.5f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Delete track option
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.RemoveCircleOutline,
                contentDescription = stringResource(
                    R.string.playlist_detail_remove_track,
                    track.title,
                    playlistName
                ),
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
