package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.playback.PlaybackManager

@Composable
fun BottomMiniPlayer(
    playbackManager: PlaybackManager,
    baseUrl: String,
    token: String,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTrack by playbackManager.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by playbackManager.isPlaying.collectAsStateWithLifecycle()
    val isLoading by playbackManager.isLoading.collectAsStateWithLifecycle()
    val progress by playbackManager.progress.collectAsStateWithLifecycle()
    val duration by playbackManager.duration.collectAsStateWithLifecycle()

    if (currentTrack == null) return

    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = currentTrack?.let {
        if (it.thumb.isNotEmpty()) "$normalizedBaseUrl${it.thumb}" else null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .clickable { onExpand() }
            .testTag("mini_player")
    ) {
        // Tiny Top Timeline Progress Line
        if (duration > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (progress.toFloat() / duration).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Color(0xFF818CF8))
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Little album cover art
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .addHeader("X-Plex-Token", token)
                            .crossfade(true)
                            .build(),
                        contentDescription = currentTrack?.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
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
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title and Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack?.title ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack?.artist ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls: Quick play/pause or buffer spinner
            IconButton(
                onClick = { playbackManager.togglePlayPause() },
                modifier = Modifier
                    .testTag("mini_player_play_pause")
                    .semantics {
                        role = Role.Button
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
