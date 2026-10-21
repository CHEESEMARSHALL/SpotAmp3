package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem

@Composable
internal fun MiniPlayerArtwork(
    imageRequest: ImageRequest?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    onImageError: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { onImageError?.invoke() }
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_artwork_placeholder),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

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

    val track = currentTrack ?: return

    BottomMiniPlayerContent(
        currentTrack = track,
        isPlaying = isPlaying,
        isLoading = isLoading,
        progress = progress,
        duration = duration,
        baseUrl = baseUrl,
        token = token,
        onTogglePlayPause = playbackManager::togglePlayPause,
        onExpand = onExpand,
        modifier = modifier
    )
}

@Composable
internal fun BottomMiniPlayerContent(
    currentTrack: TrackItem,
    isPlaying: Boolean,
    isLoading: Boolean,
    progress: Long,
    duration: Long,
    baseUrl: String,
    token: String,
    onTogglePlayPause: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val imageUrl = currentTrack.thumb.takeIf { it.isNotEmpty() }
        ?.let { resolveArtworkUrl(baseUrl, it) }
    val imageRequest = remember(imageUrl, token) {
        if (imageUrl != null) authenticatedArtworkRequest(context, imageUrl, token) else null
    }
    val currentTrackTitle = currentTrack.title
    val currentTrackArtist = currentTrack.artist.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.common_unknown_artist)
    val playPauseLabel = stringResource(
        if (isPlaying) R.string.player_pause_action else R.string.player_play_action
    )
    val openNowPlayingLabel = stringResource(
        R.string.player_open_now_playing_track_accessibility,
        currentTrackTitle,
        currentTrackArtist
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .clickable { onExpand() }
            .semantics {
                role = Role.Button
                contentDescription = openNowPlayingLabel
            }
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
            MiniPlayerArtwork(
                imageRequest = imageRequest
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Title and Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = localizedArtistPresentation(currentTrack.artist),
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
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("mini_player_play_pause")
                    .semantics {
                        role = Role.Button
                        contentDescription = playPauseLabel
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
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
