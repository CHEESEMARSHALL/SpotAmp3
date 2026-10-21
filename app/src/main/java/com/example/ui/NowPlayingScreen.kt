package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage

import com.example.playback.PlaybackControlAvailability
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem
import com.example.R
import kotlin.math.abs

@Composable
fun LegacyNowPlayingScreen(
    viewModel: MusicViewModel,
    baseUrl: String,
    token: String,
    onCollapse: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackManager = viewModel.playbackManager
    val visualizerFrame by playbackManager.visualizerFrame.collectAsStateWithLifecycle()
    val currentTrack by playbackManager.currentTrack.collectAsStateWithLifecycle()
    val queue by playbackManager.queue.collectAsStateWithLifecycle()
    val currentIndex by playbackManager.currentIndex.collectAsStateWithLifecycle()
    val isPlaying by playbackManager.isPlaying.collectAsStateWithLifecycle()
    val isLoading by playbackManager.isLoading.collectAsStateWithLifecycle()
    val progress by playbackManager.progress.collectAsStateWithLifecycle()
    val duration by playbackManager.duration.collectAsStateWithLifecycle()
    val shuffleMode by playbackManager.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by playbackManager.repeatMode.collectAsStateWithLifecycle()
    val lyrics by playbackManager.currentLyrics.collectAsStateWithLifecycle()
    val lyricsLoading by playbackManager.lyricsLoading.collectAsStateWithLifecycle()

    var showQueue by remember { mutableStateOf(value = false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val imageUrl = currentTrack?.let {
        if (it.thumb.isNotEmpty()) resolveArtworkUrl(baseUrl, it.thumb) else null
    }

    var visualizerMode by remember { mutableStateOf(PlaybackArtworkMode.ARTWORK) }
    LaunchedEffect(visualizerMode, showLyrics, showQueue) {
        playbackManager.setVisualizerCaptureEnabled(
            enabled = !showQueue && !showLyrics && visualizerMode != PlaybackArtworkMode.ARTWORK
        )
    }
    DisposableEffect(playbackManager) {
        onDispose { playbackManager.setVisualizerCaptureEnabled(false) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 50) { // Simple threshold for swipe down
                        onCollapse()
                    }
                }
            }
    ) {

        // 1. Blurred Background Artwork
        if (imageUrl != null) {
            AsyncImage(
                model = authenticatedArtworkRequest(context, imageUrl, token),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(30.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }

        // Dark gradient overlay to ensure readability and dynamic contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color(0xFF080808).copy(alpha = 0.85f),
                            Color(0xFF0F0F15)
                        )
                    )
                )
        )

        // Main Layout Scrollable/Box Wrapper
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Collapse Button & Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.testTag("collapse_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.player_collapse_accessibility),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color(0xFF818CF8)
                    )
                )

                IconButton(
                    onClick = { showQueue = !showQueue },
                    modifier = Modifier.testTag("queue_toggle_button")
                ) {
                    Icon(
                        imageVector = if (showQueue) Icons.Rounded.MusicNote else Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = stringResource(
                            if (showQueue) R.string.player_back_to_player_accessibility
                            else R.string.player_view_queue_accessibility
                        ),
                        tint = if (showQueue) Color(0xFF818CF8) else Color.White
                    )
                }
            }

            if (!showQueue) {
                // Artwork Screen (Default)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Elevated Custom Styled Visual Surface or Lyrics Panel
                    if (showLyrics) {
                        LyricsPanel(
                            lyrics = lyrics,
                            loading = lyricsLoading,
                            positionMs = progress,
                            onSeek = playbackManager::seekTo,
                            onClose = { showLyrics = false },
                            onReload = { playbackManager.loadLyricsForTrack(currentTrack) },
                            modifier = Modifier
                                .aspectRatio(1f)
                                .fillMaxWidth(0.85f)
                                .padding(bottom = 32.dp)
                                .clip(RoundedCornerShape(32.dp))
                        )
                    } else {
                        PlaybackVisualSurface(
                            artworkUrl = imageUrl,
                            spectrum = visualizerFrame.spectrum,
                            waveform = visualizerFrame.waveform,
                            mode = visualizerMode,
                            token = token,
                            onModeChange = { visualizerMode = it },
                            onClick = { 
                                showLyrics = true
                                playbackManager.loadLyricsForTrack(currentTrack)
                            },
                            onClickLabel = stringResource(R.string.player_show_lyrics),
                            modifier = Modifier
                                .aspectRatio(1f)
                                .fillMaxWidth(0.85f)
                                .padding(bottom = 32.dp)
                                .clip(RoundedCornerShape(32.dp))
                        )
                    }

                    // Metadata Titles with Like & Context Menu
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrack?.title ?: "No Track Playing",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 24.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            currentTrack?.let { track ->
                                Text(
                                    text = localizedArtistPresentation(track.artist),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color(0xFF818CF8),
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        onCollapse()
                                        onNavigateToArtist(track.artistRatingKey ?: track.ratingKey, track.artist)
                                    }
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = localizedAlbumPresentation(track.album),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.6f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        onCollapse()
                                        onNavigateToAlbum(track.albumRatingKey ?: track.ratingKey, track.album)
                                    }
                                )

                                if (track.genres.isNotEmpty()) {
                                    Text(
                                        text = "Genres • ${track.genres.joinToString(" • ")}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF67E8F9)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            } ?: run {
                                Text(
                                    text = stringResource(R.string.common_unknown_artist),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color(0xFF818CF8),
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        currentTrack?.let { track ->
                            val isLiked by viewModel.isTrackLikedFlow(track.ratingKey).collectAsStateWithLifecycle(initialValue = false)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.toggleLikeTrack(track) },
                                    modifier = Modifier.testTag("like_button")
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = trackLikeActionDescription(isLiked),
                                        tint = if (isLiked) Color.Red else Color.White
                                    )
                                }

                                IconButton(
                                    onClick = { showContextMenu = true },
                                    modifier = Modifier.testTag("more_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = stringResource(R.string.player_track_options_accessibility, track.title),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Queue screen
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Play Queue (${queue.size} songs)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        if (queue.isNotEmpty()) {
                            TextButton(onClick = { playbackManager.clearQueue() }) { Text(stringResource(R.string.player_stop_clear_queue), color = Color(0xFFFCA5A5)) }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(queue) { idx, track ->
                            val isCurrent = idx == currentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isCurrent) Color.White.copy(alpha = 0.1f) 
                                        else Color.Transparent
                                    )
                                    .clickable { playbackManager.playQueue(queue, idx) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val trackImgUrl = if (track.thumb.isNotEmpty()) {
                                    resolveArtworkUrl(baseUrl, track.thumb)
                                } else null

                                Card(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (trackImgUrl != null) {
                                        AsyncImage(
                                            model = authenticatedArtworkRequest(context, trackImgUrl, token),
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
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) Color(0xFF818CF8) else Color.White
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = localizedArtistPresentation(track.artist),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White.copy(alpha = 0.6f)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isCurrent && isPlaying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF818CF8)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Controls Column
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 2. Playback Seek Slider (Custom Waveform)
                val currentPosition = progress
                val displayPosition = formatDuration(currentPosition)

                WaveformDurationBar(
                    progress = progress,
                    duration = duration,
                    trackId = currentTrack?.ratingKey ?: "default",
                    onSeek = { position ->
                        playbackManager.seekTo(position)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .testTag("playback_slider"),
                    activeColor = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = displayPosition,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                    )
                    RemainingDurationText(
                        progressMs = currentPosition,
                        durationMs = duration,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Audio Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(
                        onClick = { playbackManager.toggleShuffle() },
                        modifier = Modifier.testTag("shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = stringResource(R.string.player_shuffle),
                            tint = if (shuffleMode) Color(0xFF818CF8) else Color.White.copy(alpha = 0.5f)
                        )
                    }

                    // Previous
                    IconButton(
                        onClick = { playbackManager.prev() },
                        modifier = Modifier.testTag("prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(R.string.player_previous_track),
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play / Pause Elevated
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(36.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                                )
                            )
                            .clickable { playbackManager.togglePlayPause() }
                            .testTag("play_pause_fab"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.player_pause_action else R.string.player_play_action
                                ),
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    // Next
                    IconButton(
                        onClick = { playbackManager.next() },
                        enabled = PlaybackControlAvailability.canSkipToNext(
                            queueSize = queue.size,
                            currentIndex = currentIndex,
                            repeatAll = repeatMode == Player.REPEAT_MODE_ALL
                        ),
                        modifier = Modifier.testTag("next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = stringResource(R.string.player_next_track),
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Repeat
                    IconButton(
                        onClick = { playbackManager.toggleRepeat() },
                        modifier = Modifier.testTag("repeat_button")
                    ) {
                        Icon(
                            imageVector = when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                                Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat
                                else -> Icons.Rounded.Repeat
                            },
                            contentDescription = stringResource(
                                when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> R.string.player_repeat_one
                                    Player.REPEAT_MODE_ALL -> R.string.player_repeat_all
                                    else -> R.string.player_repeat_off
                                }
                            ),
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) Color(0xFF818CF8) else Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        if (showContextMenu && currentTrack != null) {
            val trackItem = currentTrack!!
            MusicContextMenu(
                item = ContextMenuItem.Track(
                    ratingKey = trackItem.ratingKey,
                    title = trackItem.title,
                    artist = trackItem.artist,
                    album = trackItem.album,
                    key = trackItem.key,
                    thumb = trackItem.thumb,
                    duration = trackItem.duration,
                    albumRatingKey = trackItem.albumRatingKey,
                    albumArtist = trackItem.albumArtist,
                    artistRatingKey = trackItem.artistRatingKey
                ),
                viewModel = viewModel,
                onDismiss = { showContextMenu = false },
                onNavigateToArtist = { id, name ->
                    showContextMenu = false
                    onCollapse()
                    onNavigateToArtist(id, name)
                },
                onNavigateToAlbum = { id, name ->
                    showContextMenu = false
                    onCollapse()
                    onNavigateToAlbum(id, name)
                }
            )
        }
    }
}

// Utility to format millisecond durations (e.g. 195000 -> "3:15")
fun formatDuration(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0L) / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.ROOT, "%d:%02d", min, sec)
}

@Composable
internal fun trackLikeActionDescription(isLiked: Boolean): String = stringResource(
    if (isLiked) R.string.player_remove_liked_track else R.string.player_like_track
)

fun formatRemainingDuration(progressMs: Long, durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val boundedProgress = progressMs.coerceIn(0L, durationMs)
    val remainingMs = durationMs - boundedProgress
    return if (remainingMs < 1_000L) "0:00" else "-${formatDuration(remainingMs)}"
}

@Composable
internal fun RemainingDurationText(
    progressMs: Long,
    durationMs: Long,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val displayText = formatRemainingDuration(progressMs, durationMs)
    val unknownDurationLabel = stringResource(R.string.player_duration_unknown)
    Text(
        text = displayText,
        style = style,
        modifier = if (durationMs <= 0L) {
            modifier.semantics { contentDescription = unknownDurationLabel }
        } else {
            modifier
        }
    )
}

@Composable
fun WaveformDurationBar(
    progress: Long,
    duration: Long,
    trackId: String,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val barsCount = 75
    // Generate deterministic heights based on trackId with some beautiful peaks and valleys
    val heights = remember(trackId) {
        val random = java.util.Random(trackId.hashCode().toLong())
        List(barsCount) { index ->
            val centerFactor = 1.0f - (abs(index - barsCount / 2f) / (barsCount / 2f))
            // Make the wave look like a real master track with some silent/dense sections
            val noise = random.nextFloat()
            val wave = if (index % 12 == 0) 0.15f else if (index % 7 == 0) 0.85f else 0.4f + noise * 0.5f
            (0.1f + wave * 0.9f) * (0.3f + centerFactor * 0.7f)
        }
    }

    var isDragging by remember { mutableStateOf(value = false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = if (isDragging) {
        dragFraction
    } else {
        if (duration > 0) progress.toFloat() / duration else 0f
    }.coerceIn(0f, 1f)
    val seekBarDescription = stringResource(R.string.player_seek_bar)
    val boundedProgress = if (duration > 0L) progress.coerceIn(0L, duration) else progress.coerceAtLeast(0L)
    val seekStateDescription = if (duration > 0L) {
        stringResource(
            R.string.player_seek_position_of_duration,
            formatDuration(boundedProgress),
            formatDuration(duration)
        )
    } else {
        stringResource(R.string.player_seek_duration_unknown, formatDuration(boundedProgress))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics {
                contentDescription = seekBarDescription
                stateDescription = seekStateDescription
                progressBarRangeInfo = ProgressBarRangeInfo(currentFraction, 0f..1f)
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        if (duration > 0) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((fraction * duration).toLong())
                        }
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            if (duration > 0L) onSeek((dragFraction * duration).toLong())
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { change, _ ->
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            dragFraction = fraction
                        }
                    )
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val gap = 2.dp.toPx()
            val totalGaps = (barsCount - 1) * gap
            val barWidth = (canvasWidth - totalGaps) / barsCount
            val middleY = canvasHeight / 2f

            for (i in 0 until barsCount) {
                val heightPercent = heights[i]
                val barHeight = canvasHeight * heightPercent
                val x = i * (barWidth + gap)
                val top = middleY - barHeight / 2f

                val barFraction = i.toFloat() / barsCount
                val isPlayed = barFraction <= currentFraction
                
                // Determine color based on active theme
                val color = if (isPlayed) {
                    activeColor
                } else {
                    Color.White.copy(alpha = 0.12f)
                }

                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(x, top),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }

            // Draw playhead glowing dot or vertical neon bar at the current played fraction
            val playheadX = currentFraction * canvasWidth
            if (playheadX > 0f && playheadX < canvasWidth) {
                // Neon glow line at playhead
                drawRoundRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(playheadX - 1.5.dp.toPx(), 4.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(3.dp.toPx(), canvasHeight - 8.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                )
                // Outer subtle glow circle
                drawCircle(
                    color = activeColor.copy(alpha = 0.4f),
                    radius = 8.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(playheadX, middleY)
                )
                // Inner solid circle
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(playheadX, middleY)
                )
            }
        }
    }
}
