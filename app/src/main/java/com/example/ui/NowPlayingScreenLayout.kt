package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.data.LocalDjBlurbService
import com.example.playback.TrackItem
import kotlinx.coroutines.launch

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

    var showLyrics by remember { mutableStateOf(false) }
    var contextMenuTrack by remember { mutableStateOf<TrackItem?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val imageUrl = currentTrack?.let { track ->
        if (track.thumb.isNotEmpty()) resolveArtworkUrl(baseUrl, track.thumb) else null
    }

    val spectrum = remember { FloatArray(48) }
    val waveform = remember { FloatArray(128) }
    var visualizerMode by remember { mutableStateOf(PlaybackArtworkMode.ARTWORK) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
    ) {
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color(0xFF080808).copy(alpha = 0.9f),
                            Color(0xFF0F0F15)
                        )
                    )
                )
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "now_playing_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
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
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(3)
                            }
                        },
                        modifier = Modifier.testTag("queue_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = "Jump to Up Next",
                            tint = Color.White
                        )
                    }
                }
            }

            item(key = "now_playing_track") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                                .fillMaxWidth(0.88f)
                                .padding(bottom = 16.dp)
                                .clip(RoundedCornerShape(22.dp))
                        )
                    } else {
                        PlaybackVisualSurface(
                            artworkUrl = imageUrl,
                            spectrum = spectrum,
                            waveform = waveform,
                            mode = visualizerMode,
                            token = token,
                            onModeChange = { visualizerMode = it },
                            onClick = {
                                showLyrics = true
                                playbackManager.loadLyricsForTrack(currentTrack)
                            },
                            modifier = Modifier
                                .aspectRatio(1f)
                                .fillMaxWidth(0.88f)
                                .padding(bottom = 16.dp)
                                .clip(RoundedCornerShape(22.dp))
                        )
                    }

                    currentTrack?.let { track ->
                        val isLiked by viewModel.isTrackLikedFlow(track.ratingKey)
                            .collectAsStateWithLifecycle(initialValue = false)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clickable {
                                            onCollapse()
                                            onNavigateToArtist(track.ratingKey, track.artist)
                                        }
                                )
                                Text(
                                    text = track.album,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.62f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(top = 1.dp)
                                        .clickable {
                                            onCollapse()
                                            onNavigateToAlbum(track.albumRatingKey ?: track.ratingKey, track.album)
                                        }
                                )
                                Text(
                                    text = "Local DJ • ${LocalDjBlurbService().describe(track, currentIndex, queue.size)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White.copy(alpha = 0.46f),
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.toggleLikeTrack(track) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("like_button")
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Like Song",
                                        tint = if (isLiked) Color.Red else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { contextMenuTrack = track },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("more_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "Track Context Menu",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } ?: Text(
                        text = "No Track Playing",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White.copy(alpha = 0.7f))
                    )
                }
            }

            item(key = "now_playing_controls") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    WaveformDurationBar(
                        progress = progress,
                        duration = duration,
                        trackId = currentTrack?.ratingKey ?: "default",
                        onSeek = playbackManager::seekTo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("playback_slider"),
                        activeColor = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(progress),
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f))
                        )
                        Text(
                            text = "-${formatDuration(maxOf(0L, duration - progress))}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { playbackManager.toggleShuffle() },
                            modifier = Modifier.testTag("shuffle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (shuffleMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f)
                            )
                        }
                        IconButton(
                            onClick = { playbackManager.prev() },
                            modifier = Modifier.testTag("prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(34.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                    )
                                )
                                .clickable { playbackManager.togglePlayPause() }
                                .testTag("play_pause_fab"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { playbackManager.next() },
                            modifier = Modifier.testTag("next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next Track",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(
                            onClick = { playbackManager.toggleRepeat() },
                            modifier = Modifier.testTag("repeat_button")
                        ) {
                            Icon(
                                imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                contentDescription = "Repeat",
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }

            item(key = "up_next_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "UP NEXT",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "${queue.size} ${if (queue.size == 1) "track" else "tracks"}",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                            )
                        }
                        if (queue.isNotEmpty()) {
                            TextButton(
                                onClick = playbackManager::clearQueue,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Clear", color = Color(0xFFFCA5A5), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            itemsIndexed(
                items = queue,
                key = { index, track -> "queue_${track.ratingKey}_$index" }
            ) { index, track ->
                val isCurrent = index == currentIndex ||
                    (currentIndex !in queue.indices && track.ratingKey == currentTrack?.ratingKey)
                NowPlayingQueueRow(
                    track = track,
                    index = index,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    baseUrl = baseUrl,
                    token = token,
                    context = context,
                    onClick = { playbackManager.playQueue(queue, index) },
                    onMoreClick = { contextMenuTrack = track }
                )
            }

            item(key = "up_next_footer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        contextMenuTrack?.let { track ->
            MusicContextMenu(
                item = track.toContextMenuItem(),
                viewModel = viewModel,
                onDismiss = { contextMenuTrack = null },
                onNavigateToArtist = { id, name ->
                    contextMenuTrack = null
                    onCollapse()
                    onNavigateToArtist(id, name)
                },
                onNavigateToAlbum = { id, name ->
                    contextMenuTrack = null
                    onCollapse()
                    onNavigateToAlbum(id, name)
                }
            )
        }
    }
}

@Composable
private fun NowPlayingQueueRow(
    track: TrackItem,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    baseUrl: String,
    token: String,
    context: android.content.Context,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val imageUrl = if (track.thumb.isNotEmpty()) resolveArtworkUrl(baseUrl, track.thumb) else null
    val activeColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) activeColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.025f))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrent) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Equalizer else Icons.Rounded.PlayArrow,
                    contentDescription = "Currently selected",
                    tint = activeColor,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.42f))
                )
            }
        }

        Card(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(7.dp)
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = authenticatedArtworkRequest(context, imageUrl, token),
                    contentDescription = track.title,
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
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(11.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) activeColor else Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.58f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier
                .size(34.dp)
                .testTag("queue_track_more_$index")
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Options for ${track.title}",
                tint = Color.White.copy(alpha = 0.62f),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

private fun TrackItem.toContextMenuItem(): ContextMenuItem.Track = ContextMenuItem.Track(
    ratingKey = ratingKey,
    title = title,
    artist = artist,
    album = album,
    key = key,
    thumb = thumb,
    duration = duration,
    albumRatingKey = albumRatingKey
)
