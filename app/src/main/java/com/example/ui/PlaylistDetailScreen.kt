package com.example.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.PlaylistTrackEntity
import com.example.data.toTrackItem
import com.example.playback.TrackItem

@Composable
fun PlaylistDetailScreen(
    playlistId: Int,
    playlistName: String,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect tracks reactively from Room
    val tracksState = remember(playlistId) { viewModel.repository.getPlaylistTracks(playlistId) }
    val tracks by tracksState.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current

    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

    // Find first track's thumbnail as playlist art, or use fallback gradient
    val firstTrackThumb = tracks.firstOrNull()?.thumb
    val thumbPath = if (!firstTrackThumb.isNullOrEmpty()) firstTrackThumb else null
    val imageUrl = if (thumbPath != null) "$normalizedBaseUrl$thumbPath" else null

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
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .align(Alignment.TopStart)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // Background ambient blur if artwork exists
                    if (imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageUrl)
                                .addHeader("X-Plex-Token", token)
                                .crossfade(true)
                                .build(),
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
                                    model = ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .addHeader("X-Plex-Token", token)
                                        .crossfade(true)
                                        .build(),
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
                            text = "Playlist • ${tracks.size} tracks",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // Quick Play Actions Header
            if (tracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Play All Button
                        Button(
                            onClick = {
                                val playbackTracks = tracks.map { it.toTrackItem() }
                                viewModel.playbackManager.playQueue(playbackTracks, 0)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play All", fontWeight = FontWeight.Bold)
                        }

                        // Shuffle Button
                        OutlinedButton(
                            onClick = {
                                val playbackTracks = tracks.map { it.toTrackItem() }.shuffled()
                                viewModel.playbackManager.playQueue(playbackTracks, 0)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy()
                        ) {
                            Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Shuffle", fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
                            text = "This playlist is empty.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
                        )
                    }
                }
            } else {
                itemsIndexed(tracks) { index, track ->
                    PlaylistTrackRow(
                        index = index + 1,
                        track = track,
                        onDelete = {
                            viewModel.removeTrackFromPlaylist(playlistId, track.ratingKey)
                        },
                        onClick = {
                            val playbackTracks = tracks.map { it.toTrackItem() }
                            viewModel.playbackManager.playTrack(track.toTrackItem(), playbackTracks)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistTrackRow(
    index: Int,
    track: PlaylistTrackEntity,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Index Number
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.width(28.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

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
            Text(
                text = "${track.artist} • ${track.album}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.6f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Delete track option
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.RemoveCircleOutline,
                contentDescription = "Remove from playlist",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
