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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    var isSaved by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

    // Find first track's thumbnail as fallback/ambient artwork
    val firstTrackThumb = tracks.firstOrNull()?.thumb
    val thumbPath = if (!firstTrackThumb.isNullOrEmpty()) firstTrackThumb else null
    val imageUrl = if (thumbPath != null) "$normalizedBaseUrl$thumbPath" else null

    val gradientColors = remember(colors) {
        if (colors.isNotEmpty()) {
            colors.map { Color(it) }
        } else {
            listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
        }
    }

    val typeLabel = when (type) {
        "daily_mix" -> "Daily Mix"
        "radio" -> "Generated Radio"
        "ai_playlist" -> "AI Smart Playlist"
        else -> "Generated Mix"
    }

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
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .align(Alignment.TopStart)
                            .testTag("custom_playlist_back_button")
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
                            text = "$typeLabel • ${tracks.size} tracks",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = gradientColors.firstOrNull() ?: Color(0xFF818CF8),
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

            // Playback & Playlist Action Buttons
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Quick Play / Shuffle Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Play All Button
                        Button(
                            onClick = {
                                viewModel.playbackManager.playQueue(tracks, 0)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("custom_playlist_play_all"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = gradientColors.firstOrNull() ?: Color(0xFF818CF8),
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
                                viewModel.playbackManager.playQueue(tracks.shuffled(), 0)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("custom_playlist_shuffle"),
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

                    // Save & Download Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Save Playlist Button
                        Button(
                            onClick = {
                                viewModel.savePlaylistWithTracks(playlistName, tracks) {
                                    isSaved = true
                                    Toast.makeText(context, "Saved '$playlistName' to local playlists!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("custom_playlist_save"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSaved) Color(0xFF10B981) else Color.White.copy(alpha = 0.1f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSaved) "Saved" else "Save to Library",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        // Download Playlist Tracks Button
                        Button(
                            onClick = {
                                isDownloading = true
                                viewModel.downloadTracksList(tracks)
                                Toast.makeText(context, "Queued all ${tracks.size} tracks for offline download!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("custom_playlist_download"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDownloading) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.1f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (isDownloading) Icons.Rounded.Downloading else Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isDownloading) "Downloading..." else "Download All",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Divider
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
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
                            text = "This playlist is empty.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
                        )
                    }
                }
            } else {
                itemsIndexed(tracks) { index, track ->
                    CustomTrackRow(
                        index = index + 1,
                        track = track,
                        onClick = {
                            viewModel.playbackManager.playTrack(track, tracks)
                        }
                    )
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
fun CustomTrackRow(
    index: Int,
    track: TrackItem,
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

        // Duration text
        val minutes = (track.duration / 1000) / 60
        val seconds = (track.duration / 1000) % 60
        val formattedDuration = if (track.duration > 0) {
            String.format("%d:%02d", minutes, seconds)
        } else {
            "3:45"
        }

        Text(
            text = formattedDuration,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White.copy(alpha = 0.4f)
            ),
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}
