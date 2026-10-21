package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CachedTrack
import com.example.data.DownloadPresentationState

@Composable
fun TrackRow(
    track: CachedTrack,
    baseUrl: String,
    token: String,
    onMoreClick: () -> Unit,
    onClick: () -> Unit,
    isPlaying: Boolean = false,
    onCompanionFavoriteChange: ((Boolean) -> Unit)? = null,
    onCompanionRatingChange: ((Int) -> Unit)? = null,
    downloadState: DownloadPresentationState? = null,
    onDownloadAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageUrl = if (track.thumb.isNotEmpty()) resolveArtworkUrl(baseUrl, track.thumb) else null
    val artworkRequest = remember(imageUrl, token) {
        imageUrl?.let { authenticatedArtworkRequest(context, it, token) }
    }
    val activeColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isPlaying) activeColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.library_currently_playing_accessibility),
                    tint = activeColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Card(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(7.dp)
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = artworkRequest,
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

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isPlaying) activeColor else Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = localizedArtistAlbumSubtitle(track.artist, track.album),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.6f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (downloadState != null) {
                Text(
                    text = stringResource(downloadState.labelResource()),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (downloadState) {
                        DownloadPresentationState.DOWNLOADED -> MaterialTheme.colorScheme.primary
                        DownloadPresentationState.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("library_track_download_state")
                )
            }
        }

        if (downloadState != null && onDownloadAction != null) {
            IconButton(
                onClick = onDownloadAction,
                modifier = Modifier.size(48.dp).testTag("library_track_download_action")
            ) {
                Icon(
                    imageVector = downloadState.actionIcon(),
                    contentDescription = stringResource(downloadState.actionLabelResource(), track.title),
                    tint = when (downloadState) {
                        DownloadPresentationState.DOWNLOADED -> MaterialTheme.colorScheme.primary
                        DownloadPresentationState.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        onCompanionFavoriteChange?.let { onChange ->
            IconButton(
                onClick = { onChange(!track.companionFavorite) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (track.companionFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = stringResource(
                        if (track.companionFavorite) R.string.library_spotcore_favorite_remove
                        else R.string.library_spotcore_favorite_add
                    ),
                    tint = if (track.companionFavorite) Color(0xFFFFD166) else Color.White.copy(alpha = 0.48f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        onCompanionRatingChange?.let { onChange ->
            val ratingMenuExpanded = remember(track.ratingKey) { mutableStateOf(false) }
            Box {
                TextButton(
                    onClick = { ratingMenuExpanded.value = true },
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(
                        text = if (track.companionRating == 0) {
                            stringResource(R.string.library_spotcore_rating_rate_track)
                        } else {
                            "★${track.companionRating}"
                        },
                        color = if (track.companionRating > 0) Color(0xFFFFD166) else Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                }
                DropdownMenu(
                    expanded = ratingMenuExpanded.value,
                    onDismissRequest = { ratingMenuExpanded.value = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_spotcore_rating_unrated)) },
                        onClick = {
                            ratingMenuExpanded.value = false
                            onChange(0)
                        }
                    )
                    (1..5).forEach { rating ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_spotcore_rating_option, rating)) },
                            onClick = {
                                ratingMenuExpanded.value = false
                                onChange(rating)
                            }
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.library_track_more_options_accessibility, track.title),
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@androidx.annotation.StringRes
private fun DownloadPresentationState.labelResource(): Int = when (this) {
    DownloadPresentationState.AVAILABLE -> R.string.download_state_available
    DownloadPresentationState.QUEUED -> R.string.download_state_queued
    DownloadPresentationState.DOWNLOADING -> R.string.download_state_downloading
    DownloadPresentationState.DOWNLOADED -> R.string.download_state_downloaded
    DownloadPresentationState.FAILED -> R.string.download_state_failed
}

@androidx.annotation.StringRes
private fun DownloadPresentationState.actionLabelResource(): Int = when (this) {
    DownloadPresentationState.AVAILABLE -> R.string.download_action_download
    DownloadPresentationState.QUEUED -> R.string.download_action_cancel
    DownloadPresentationState.DOWNLOADING -> R.string.download_action_cancel
    DownloadPresentationState.DOWNLOADED -> R.string.download_action_remove
    DownloadPresentationState.FAILED -> R.string.download_action_retry
}

private fun DownloadPresentationState.actionIcon() = when (this) {
    DownloadPresentationState.AVAILABLE -> Icons.Rounded.Download
    DownloadPresentationState.QUEUED, DownloadPresentationState.DOWNLOADING -> Icons.Rounded.Close
    DownloadPresentationState.DOWNLOADED -> Icons.Rounded.DeleteOutline
    DownloadPresentationState.FAILED -> Icons.Rounded.Refresh
}
