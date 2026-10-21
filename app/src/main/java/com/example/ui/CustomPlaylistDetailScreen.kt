package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.playback.TrackItem

@Composable
fun CustomPlaylistDetailScreen(
    type: String, // "daily_mix", "radio", "ai_playlist"
    playlistId: String,
    playlistName: String,
    description: String,
    tracks: List<TrackItem>,
    colors: List<Long>,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTrack by viewModel.playbackManager.currentTrack.collectAsStateWithLifecycle()
    var isSaved by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

    // Find first track's thumbnail as fallback/ambient artwork
    val firstTrackThumb = tracks.firstOrNull()?.thumb
    val thumbPath = if (!firstTrackThumb.isNullOrEmpty()) firstTrackThumb else null
    val imageUrl = if (thumbPath != null) resolveArtworkUrl(baseUrl, thumbPath) else null

    val gradientColors = remember(colors) {
        if (colors.isNotEmpty()) {
            colors.map { Color(it) }
        } else {
            listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
        }
    }

    val typeLabelRes = when (type) {
        "daily_mix" -> R.string.custom_playlist_type_daily_mix
        "radio" -> R.string.custom_playlist_type_radio
        "ai_playlist" -> R.string.custom_playlist_type_smart_playlist
        else -> R.string.custom_playlist_type_mix
    }
    val typeLabel = stringResource(typeLabelRes)

    val typeIcon = when (type) {
        "daily_mix" -> Icons.Rounded.QueueMusic
        "radio" -> Icons.Rounded.RssFeed
        "ai_playlist" -> Icons.Rounded.AutoAwesome
        else -> Icons.Rounded.MusicNote
    }

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
            // Header Graphic Area
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
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
                            .testTag("custom_playlist_back_button")
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
                                .background(Color.Black.copy(alpha = 0.65f))
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

                    // Centered Card Cover Art & Meta Info
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
                                .size(130.dp)
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(colors = gradientColors)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = typeIcon,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(56.dp)
                                )
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

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = playlistDetailHeaderLabel(typeLabel, tracks.size),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        if (description.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }
            }

            // No playback or queue action is useful until this playlist has tracks.
            item {
                CustomPlaylistDetailActionsRow(
                    playlistName = playlistName,
                    trackCount = tracks.size,
                    isSaved = isSaved,
                    isDownloading = isDownloading,
                    onPlayAll = { viewModel.playbackManager.playQueue(tracks, 0) },
                    onShuffle = { viewModel.playbackManager.playQueue(tracks.shuffled(), 0) },
                    onSave = {
                        viewModel.savePlaylistWithTracks(playlistName, tracks) {
                            isSaved = true
                            Toast.makeText(
                                context,
                                context.getString(R.string.custom_playlist_saved_to_library, playlistName),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onDownload = {
                        isDownloading = true
                        viewModel.downloadTracksList(tracks)
                    },
                    onAddToQueue = { viewModel.playbackManager.addTracksToQueue(tracks) }
                )
            }

            // Tracks List
            if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.playlist_detail_empty),
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
                        )
                    }
                }
            } else {
                itemsIndexed(tracks, key = { index, track -> "custom_track_${track.ratingKey}_$index" }) { index, track ->
                    Column {
                        CustomTrackRow(
                            index = index + 1,
                            track = track,
                            onClick = {
                                viewModel.playbackManager.playTrack(track, tracks)
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

            // Extra padding at the bottom for smooth scrolling
            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
internal fun CustomPlaylistDetailActionsRow(
    playlistName: String,
    trackCount: Int,
    isSaved: Boolean,
    isDownloading: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSave: () -> Unit,
    onDownload: () -> Unit,
    onAddToQueue: () -> Unit
) {
    if (trackCount <= 0) return

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
                .testTag("custom_playlist_play_all")
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
                .testTag("custom_playlist_shuffle")
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
            onClick = onSave,
            enabled = !isSaved,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("custom_playlist_save")
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkAdd,
                contentDescription = stringResource(
                    if (isSaved) R.string.custom_playlist_saved_status else R.string.custom_playlist_save_action,
                    playlistName
                ),
                tint = if (isSaved) Color(0xFF10B981) else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onDownload,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("custom_playlist_download")
        ) {
            Icon(
                imageVector = if (isDownloading) Icons.Rounded.Downloading else Icons.Rounded.Download,
                contentDescription = stringResource(R.string.custom_playlist_download_action, playlistName),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = onAddToQueue,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("custom_playlist_add_to_queue")
        ) {
            Icon(
                imageVector = Icons.Rounded.PlaylistAdd,
                contentDescription = stringResource(R.string.custom_playlist_queue_action, playlistName),
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = Color.White.copy(alpha = 0.1f)
    )
}

@Composable
fun CustomTrackRow(
    index: Int,
    track: TrackItem,
    onClick: () -> Unit,
    isPlaying: Boolean = false
) {
    val activeColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isPlaying) activeColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
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

        // Duration text
        val minutes = (track.duration / 1000) / 60
        val seconds = (track.duration / 1000) % 60
        val formattedDuration = if (track.duration > 0) {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        } else {
            "--:--"
        }

        Text(
            text = formattedDuration,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.White.copy(alpha = 0.35f)
            ),
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}
