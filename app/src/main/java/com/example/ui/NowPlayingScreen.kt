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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage

import coil.request.ImageRequest
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem
import com.example.data.LocalDjBlurbService

@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    baseUrl: String,
    token: String,
    onCollapse: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackManager = viewModel.playbackManager
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

    var showQueue by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = currentTrack?.let {
        if (it.thumb.isNotEmpty()) "$normalizedBaseUrl${it.thumb}" else null
    }

    val spectrum = remember { FloatArray(48) }
    val waveform = remember { FloatArray(128) }
    var visualizerMode by remember { mutableStateOf(PlaybackArtworkMode.ARTWORK) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (dragAmount > 50) { // Simple threshold for swipe down
                        onCollapse()
                    }
                }
            }
    ) {

        // 1. Blurred Background Artwork
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
                        contentDescription = "Collapse Player",
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
                        imageVector = if (showQueue) Icons.Rounded.MusicNote else Icons.Rounded.QueueMusic,
                        contentDescription = "Show Queue",
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
                            spectrum = spectrum,
                            waveform = waveform,
                            mode = visualizerMode,
                            onModeChange = { visualizerMode = it },
                            onClick = { 
                                showLyrics = true
                                playbackManager.loadLyricsForTrack(currentTrack)
                            },
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
                                    text = track.artist,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color(0xFF818CF8),
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        onCollapse()
                                        onNavigateToArtist(track.ratingKey, track.artist)
                                    }
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = track.album,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.6f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        onCollapse()
                                        onNavigateToAlbum(track.ratingKey, track.album)
                                    }
                                )

                                Text(
                                    text = "Local DJ • ${LocalDjBlurbService().describe(track, currentIndex, queue.size)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.48f),
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                                if (track.genres.isNotEmpty()) {
                                    Text(
                                        text = "Plex style • ${track.genres.joinToString(" • ")}",
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
                                    text = "Unknown Artist",
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
                                        contentDescription = "Like Song",
                                        tint = if (isLiked) Color.Red else Color.White
                                    )
                                }

                                IconButton(
                                    onClick = { showContextMenu = true },
                                    modifier = Modifier.testTag("more_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "Track Context Menu",
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
                            TextButton(onClick = { playbackManager.clearQueue() }) { Text("Clear queue", color = Color(0xFFFCA5A5)) }
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
                                    "$normalizedBaseUrl${track.thumb}"
                                } else null

                                Card(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (trackImgUrl != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(trackImgUrl)
                                                .addHeader("X-Plex-Token", token)
                                                .crossfade(true)
                                                .build(),
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
                                        text = track.artist,
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
                    Text(
                        text = "-${formatDuration(maxOf(0L, duration - currentPosition))}",
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
                            contentDescription = "Shuffle",
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
                            contentDescription = "Previous Track",
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
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    // Next
                    IconButton(
                        onClick = { playbackManager.next() },
                        modifier = Modifier.testTag("next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next Track",
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
                            contentDescription = "Repeat",
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
                    duration = trackItem.duration
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
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%d:%02d", min, sec)
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
            val centerFactor = 1.0f - (Math.abs(index - barsCount / 2f) / (barsCount / 2f))
            // Make the wave look like a real master track with some silent/dense sections
            val noise = random.nextFloat()
            val wave = if (index % 12 == 0) 0.15f else if (index % 7 == 0) 0.85f else 0.4f + noise * 0.5f
            (0.1f + wave * 0.9f) * (0.3f + centerFactor * 0.7f)
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }

    val currentFraction = if (isDragging) {
        dragFraction
    } else {
        if (duration > 0) progress.toFloat() / duration else 0f
    }.coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
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
                            onSeek((dragFraction * duration).toLong())
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
