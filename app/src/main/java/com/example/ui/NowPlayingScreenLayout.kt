package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.example.R
import coil.compose.AsyncImage
import com.example.playback.TrackItem
import com.example.playback.PlaybackControlAvailability
import com.example.playback.PlaybackQueuePresentation
import com.example.playback.QueueItemPosition
import kotlinx.coroutines.launch

internal fun nextTrackButtonTint(enabled: Boolean): Color =
    Color.White.copy(alpha = if (enabled) 1f else 0.38f)

@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    baseUrl: String,
    token: String,
    onCollapse: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToDownloads: () -> Unit
) {
    val playbackManager = viewModel.playbackManager
    val companionConfigured by viewModel.companionConfigured.collectAsStateWithLifecycle()
    val companionConnectionStatus by viewModel.companionConnectionStatus.collectAsStateWithLifecycle()
    LaunchedEffect(companionConfigured) {
        if (companionConfigured) viewModel.ensureCompanionReachabilityChecked()
    }
    val visualizerFrame by playbackManager.visualizerFrame.collectAsStateWithLifecycle()
    val currentTrack by playbackManager.currentTrack.collectAsStateWithLifecycle()
    val queue by playbackManager.queue.collectAsStateWithLifecycle()
    val currentIndex by playbackManager.currentIndex.collectAsStateWithLifecycle()
    val currentQueueIndex = currentIndex.takeIf { it in queue.indices }
        ?: queue.indexOfFirst { it.ratingKey == currentTrack?.ratingKey }
    val upNextCount = PlaybackQueuePresentation.upNextCount(queue.size, currentQueueIndex)
    val isPlaying by playbackManager.isPlaying.collectAsStateWithLifecycle()
    val isLoading by playbackManager.isLoading.collectAsStateWithLifecycle()
    val playbackError by playbackManager.playbackError.collectAsStateWithLifecycle()
    val playbackErrorRetryable by playbackManager.playbackErrorRetryable.collectAsStateWithLifecycle()
    val playbackErrorOfflineTrackMissing by playbackManager.playbackErrorOfflineTrackMissing.collectAsStateWithLifecycle()
    val playbackErrorDiagnostic by playbackManager.playbackErrorDiagnostic.collectAsStateWithLifecycle()
    val retryPlaybackLabel = stringResource(R.string.playback_retry)
    val progress by playbackManager.progress.collectAsStateWithLifecycle()
    val duration by playbackManager.duration.collectAsStateWithLifecycle()
    val shuffleMode by playbackManager.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by playbackManager.repeatMode.collectAsStateWithLifecycle()
    val canSkipToNext = PlaybackControlAvailability.canSkipToNext(
        queueSize = queue.size,
        currentIndex = currentIndex,
        repeatAll = repeatMode == Player.REPEAT_MODE_ALL
    )
    val shuffleLabel = stringResource(R.string.player_shuffle)
    val shuffleState = stringResource(if (shuffleMode) R.string.player_on else R.string.player_off)
    val previousTrackLabel = stringResource(R.string.player_previous_track)
    val nextTrackLabel = stringResource(R.string.player_next_track)
    val nextTrackState = stringResource(if (canSkipToNext) R.string.player_next_available else R.string.player_no_next_track)
    val repeatLabel = stringResource(
        when (repeatMode) {
            Player.REPEAT_MODE_ONE -> R.string.player_repeat_one
            Player.REPEAT_MODE_ALL -> R.string.player_repeat_all
            else -> R.string.player_repeat_off
        }
    )
    val repeatState = stringResource(
        when (repeatMode) {
            Player.REPEAT_MODE_ONE -> R.string.player_repeat_one_state
            Player.REPEAT_MODE_ALL -> R.string.player_repeat_all_state
            else -> R.string.player_off
        }
    )
    val playPauseLabel = stringResource(
        when {
            playbackError != null && playbackErrorRetryable -> R.string.playback_retry
            playbackError != null -> R.string.player_unavailable_action
            isLoading -> R.string.player_loading_action
            isPlaying -> R.string.player_pause_action
            else -> R.string.player_play_action
        }
    )
    val playPauseState = stringResource(
        when {
            playbackError != null -> R.string.player_failed_state
            isLoading -> R.string.player_loading_state
            isPlaying -> R.string.player_playing_state
            else -> R.string.player_paused_state
        }
    )
    val playPauseEnabled = playbackError == null || playbackErrorRetryable
    val lyrics by playbackManager.currentLyrics.collectAsStateWithLifecycle()
    val lyricsLoading by playbackManager.lyricsLoading.collectAsStateWithLifecycle()

    var showLyrics by remember { mutableStateOf(value = false) }
    var contextMenuTrack by remember { mutableStateOf<TrackItem?>(null) }
    var contextMenuQueueIndex by remember { mutableIntStateOf(-1) }
    var showClearUpcomingDialog by remember { mutableStateOf(false) }
    var showSaveQueueDialog by remember { mutableStateOf(false) }
    var queuePlaylistName by remember { mutableStateOf("") }
    var queueSaveError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val imageUrl = currentTrack?.let { track ->
        if (track.thumb.isNotEmpty()) resolveArtworkUrl(baseUrl, track.thumb) else null
    }

    var visualizerMode by remember { mutableStateOf(PlaybackArtworkMode.ARTWORK) }
    LaunchedEffect(visualizerMode, showLyrics) {
        playbackManager.setVisualizerCaptureEnabled(
            enabled = !showLyrics && visualizerMode != PlaybackArtworkMode.ARTWORK
        )
    }
    DisposableEffect(playbackManager) {
        onDispose { playbackManager.setVisualizerCaptureEnabled(false) }
    }
    val queueItemKeys = remember(queue) {
        val occurrenceByRatingKey = mutableMapOf<String, Int>()
        queue.map { track ->
            val occurrence = occurrenceByRatingKey.getOrDefault(track.ratingKey, 0)
            occurrenceByRatingKey[track.ratingKey] = occurrence + 1
            "queue_${track.ratingKey}_$occurrence"
        }
    }

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
                .navigationBarsPadding()
                .testTag("now_playing_scroll_container"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "main_player") {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onCollapse,
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("collapse_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.player_collapse_accessibility),
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Text(
                            text = stringResource(R.string.player_now_playing_label),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.6.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )

                        val isAtQueue by remember {
                            derivedStateOf { listState.firstVisibleItemIndex > 0 }
                        }

                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (isAtQueue) {
                                        listState.animateScrollToItem(0)
                                    } else {
                                        listState.animateScrollToItem(1)
                                    }
                                }
                            },
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("queue_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = stringResource(
                                    if (isAtQueue) R.string.player_back_to_player_accessibility
                                    else R.string.player_view_queue_accessibility
                                ),
                                tint = if (isAtQueue) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                    }

                    // 2. Middle Section (Artwork / Lyrics + Track Metadata)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                            .padding(vertical = 8.dp)
                            .testTag("player_visual_surface_container"),
                            contentAlignment = Alignment.Center
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
                                        .fillMaxHeight()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(24.dp))
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
                                        .fillMaxHeight()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(24.dp))
                                )
                            }
                        }

                        currentTrack?.let { track ->
                            val isLiked by viewModel.isTrackLikedFlow(track.ratingKey)
                                .collectAsStateWithLifecycle(initialValue = false)
                            val artistLabel = localizedArtistPresentation(track.artist)
                            val albumLabel = localizedAlbumPresentation(track.album)
                            val artistMetadataModifier = Modifier
                                .padding(top = 2.dp)
                                .testTag("player_artist_metadata")
                            val albumMetadataModifier = Modifier
                                .padding(top = 1.dp)
                                .testTag("player_album_metadata")

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
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
                                        text = artistLabel,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = if (track.artist.isNotBlank()) {
                                            artistMetadataModifier.clickable(
                                                onClickLabel = stringResource(R.string.player_open_artist),
                                                role = Role.Button
                                            ) {
                                                onCollapse()
                                                onNavigateToArtist(track.artistRatingKey ?: track.ratingKey, track.artist)
                                            }
                                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                                .wrapContentHeight(Alignment.CenterVertically)
                                        } else artistMetadataModifier
                                    )
                                    Text(
                                        text = albumLabel,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White.copy(alpha = 0.62f)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = if (track.album.isNotBlank()) {
                                            albumMetadataModifier.clickable(
                                                onClickLabel = stringResource(R.string.player_open_album),
                                                role = Role.Button
                                            ) {
                                                onCollapse()
                                                onNavigateToAlbum(track.albumRatingKey ?: track.ratingKey, track.album)
                                            }
                                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                                .wrapContentHeight(Alignment.CenterVertically)
                                        } else albumMetadataModifier
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.toggleLikeTrack(track) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("like_button")
                                    ) {
                                        Icon(
                                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = trackLikeActionDescription(isLiked),
                                            tint = if (isLiked) Color.Red else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { contextMenuTrack = track },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("more_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = stringResource(R.string.player_track_options_accessibility, track.title),
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        } ?: Text(
                            text = stringResource(R.string.player_no_track_playing),
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White.copy(alpha = 0.7f))
                        )
                    }

                    // 3. Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
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

                        playbackError?.let { message ->
                            if (shouldShowNowPlayingConnectionRecovery(
                                    playbackFailed = true,
                                    companionConfigured = companionConfigured,
                                    status = companionConnectionStatus
                                )
                            ) {
                                CompanionDetailConnectionNotice(
                                    companionConfigured = companionConfigured,
                                    status = companionConnectionStatus,
                                    onCheckConnection = viewModel::retryCompanionConnection,
                                    testTag = "now_playing_connection_notice"
                                )
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = message,
                                    color = Color(0xFFFCA5A5),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.semantics {
                                        liveRegion = LiveRegionMode.Polite
                                        error(message)
                                    }
                                )
                                TextButton(
                                    onClick = if (playbackErrorRetryable) playbackManager::play else playbackManager::clearPlaybackError,
                                    modifier = Modifier.testTag(if (playbackErrorRetryable) "playback_retry_button" else "playback_dismiss_error")
                                ) {
                                    Text(stringResource(if (playbackErrorRetryable) R.string.playback_retry else R.string.playback_dismiss_error))
                                }
                                if (playbackErrorOfflineTrackMissing) {
                                    TextButton(
                                        onClick = onNavigateToDownloads,
                                        modifier = Modifier.testTag("playback_open_downloads")
                                    ) {
                                        Text(stringResource(R.string.playback_open_downloads))
                                    }
                                }
                                playbackErrorDiagnostic?.let { diagnostic ->
                                    var showDetails by remember(playbackError) { mutableStateOf(false) }
                                    val detailsStateDescription = stringResource(
                                        if (showDetails) R.string.recovery_details_expanded
                                        else R.string.recovery_details_collapsed
                                    )
                                    TextButton(
                                        onClick = { showDetails = !showDetails },
                                        modifier = Modifier
                                            .testTag("playback_error_details_toggle")
                                            .semantics { stateDescription = detailsStateDescription }
                                    ) {
                                        Text(stringResource(if (showDetails) R.string.recovery_hide_details else R.string.recovery_show_details))
                                    }
                                    if (showDetails) {
                                        Text(
                                            text = stringResource(R.string.playback_error_code, diagnostic),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.testTag("playback_error_details")
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(progress),
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f))
                            )
                            RemainingDurationText(
                                progressMs = progress,
                                durationMs = duration,
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f))
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { playbackManager.toggleShuffle() },
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("shuffle_button")
                                    .semantics {
                                        contentDescription = shuffleLabel
                                        stateDescription = shuffleState
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = null,
                                    tint = if (shuffleMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f)
                                )
                            }
                            IconButton(
                                onClick = { playbackManager.prev() },
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("prev_button")
                                    .semantics { contentDescription = previousTrackLabel }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = null,
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
                                    .alpha(if (playPauseEnabled) 1f else 0.55f)
                                    .clickable(enabled = playPauseEnabled) { playbackManager.togglePlayPause() }
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = playPauseLabel
                                        stateDescription = playPauseState
                                    }
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
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { playbackManager.next() },
                                enabled = canSkipToNext,
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("next_button")
                                    .semantics {
                                        contentDescription = nextTrackLabel
                                        stateDescription = nextTrackState
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = null,
                                    tint = nextTrackButtonTint(canSkipToNext),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            IconButton(
                                onClick = { playbackManager.toggleRepeat() },
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("repeat_button")
                                    .semantics {
                                        contentDescription = repeatLabel
                                        stateDescription = repeatState
                                    }
                            ) {
                                Icon(
                                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    contentDescription = null,
                                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "up_next_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 6.dp)
                ) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.player_queue_heading),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            TextButton(
                                onClick = {
                                    coroutineScope.launch { listState.animateScrollToItem(0) }
                                },
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("queue_return_to_player_button"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(stringResource(R.string.player_back_to_player_accessibility))
                            }
                        }
                        if (upNextCount > 0) {
                            Text(
                                text = pluralStringResource(R.plurals.player_queue_up_next_count, upNextCount, upNextCount),
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.player_queue_nothing_up_next),
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                            )
                        }
                        if (queue.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = {
                                        queuePlaylistName = ""
                                        queueSaveError = null
                                        showSaveQueueDialog = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("queue_save_as_playlist_button"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.player_queue_save_as_playlist))
                                }
                                TextButton(
                                    onClick = { showClearUpcomingDialog = true },
                                    enabled = upNextCount > 0,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("queue_clear_upcoming_button"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Rounded.ClearAll, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.player_queue_clear_upcoming))
                                }
                            }
                        }
                    }
                }
            }

            itemsIndexed(
                items = queue,
                // ratingKey is not unique when a track occurs more than once.
                // Occurrence keys remain stable while distinct tracks move around the queue.
                // LazyList may briefly ask for a key from an older interval while the
                // observed queue is being restored/cleared. Keep key lookup safe across
                // that transient state; the item itself still supplies a stable fallback.
                key = { index, track -> queueItemKeys.getOrNull(index) ?: "queue_${track.ratingKey}_$index" }
            ) { index, track ->
                val isCurrent = index == currentQueueIndex
                val positionLabel = when (PlaybackQueuePresentation.itemPosition(index, currentQueueIndex)) {
                    QueueItemPosition.PLAYED_EARLIER -> stringResource(R.string.player_queue_played_earlier)
                    QueueItemPosition.NOW_PLAYING -> stringResource(R.string.player_queue_now_playing)
                    QueueItemPosition.UP_NEXT -> stringResource(R.string.player_queue_up_next)
                }
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    NowPlayingQueueRow(
                        track = track,
                        index = index,
                        isCurrent = isCurrent,
                        positionLabel = positionLabel,
                        isPlaying = isPlaying,
                        queueSize = queue.size,
                        baseUrl = baseUrl,
                        token = token,
                        context = context,
                        onClick = { playbackManager.playQueue(queue, index) },
                        onMove = playbackManager::moveQueueItem,
                        canMoveDown = index < queue.lastIndex,
                        canReorder = queue.size > 1,
                        onMoreClick = {
                            contextMenuTrack = track
                            contextMenuQueueIndex = index
                        }
                    )
                }
            }

            item(key = "up_next_footer") {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        contextMenuTrack?.let { track ->
            MusicContextMenu(
                item = track.toContextMenuItem(),
                viewModel = viewModel,
                onDismiss = {
                    contextMenuTrack = null
                    contextMenuQueueIndex = -1
                },
                onRemoveFromQueue = contextMenuQueueIndex
                    .takeIf { it in queue.indices && it != currentQueueIndex }
                    ?.let { queueIndex ->
                        {
                            playbackManager.removeFromQueue(queueIndex)
                            contextMenuTrack = null
                            contextMenuQueueIndex = -1
                        }
                    },
                onNavigateToArtist = { id, name ->
                    contextMenuTrack = null
                    contextMenuQueueIndex = -1
                    onCollapse()
                    onNavigateToArtist(id, name)
                },
                onNavigateToAlbum = { id, name ->
                    contextMenuTrack = null
                    contextMenuQueueIndex = -1
                    onCollapse()
                    onNavigateToAlbum(id, name)
                }
            )
        }

        if (showClearUpcomingDialog) {
            ClearUpcomingQueueDialog(
                trackCount = upNextCount,
                onDismiss = { showClearUpcomingDialog = false },
                onConfirm = {
                    playbackManager.clearUpcoming()
                    showClearUpcomingDialog = false
                }
            )
        }

        if (showSaveQueueDialog) {
            SaveQueueAsPlaylistDialog(
                name = queuePlaylistName,
                errorMessage = queueSaveError,
                onNameChange = {
                    queuePlaylistName = it
                    queueSaveError = null
                },
                onDismiss = { showSaveQueueDialog = false },
                onConfirm = {
                    val name = queuePlaylistName.trim()
                    if (name.isNotEmpty() && queue.isNotEmpty()) {
                        viewModel.savePlaylistWithTracks(
                            name = name,
                            tracks = queue,
                            onSaved = {
                                showSaveQueueDialog = false
                                queuePlaylistName = ""
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.player_queue_saved_as_playlist, name),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onFailure = {
                                queueSaveError = context.getString(R.string.player_queue_save_failed)
                            }
                        )
                    }
                }
            )
        }
    }
}

@Composable
internal fun ClearUpcomingQueueDialog(
    trackCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_queue_clear_upcoming_title)) },
        text = { Text(pluralStringResource(R.plurals.player_queue_clear_upcoming_message, trackCount, trackCount)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("queue_clear_upcoming_confirm")) {
                Text(stringResource(R.string.player_queue_clear_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("queue_clear_upcoming_cancel")) {
                Text(stringResource(R.string.downloads_cancel))
            }
        }
    )
}

@Composable
internal fun SaveQueueAsPlaylistDialog(
    name: String,
    errorMessage: String?,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_queue_save_as_playlist_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.player_queue_save_as_playlist_message))
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.player_queue_playlist_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("queue_playlist_name_field")
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = PlaybackQueuePresentation.canSaveAsPlaylist(name),
                modifier = Modifier.testTag("queue_save_playlist_confirm")
            ) {
                Text(stringResource(R.string.player_queue_save_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("queue_save_playlist_cancel")) {
                Text(stringResource(R.string.downloads_cancel))
            }
        }
    )
}

@Composable
internal fun NowPlayingQueueRow(
    track: TrackItem,
    index: Int,
    isCurrent: Boolean,
    positionLabel: String?,
    isPlaying: Boolean,
    queueSize: Int,
    baseUrl: String,
    token: String,
    context: android.content.Context,
    onClick: () -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    canMoveDown: Boolean,
    canReorder: Boolean,
    onMoreClick: () -> Unit
) {
    val imageUrl = if (track.thumb.isNotEmpty()) resolveArtworkUrl(baseUrl, track.thumb) else null
    val activeColor = MaterialTheme.colorScheme.primary
    val reorderThresholdPx = with(LocalDensity.current) { 52.dp.toPx() }
    val latestIndex = rememberUpdatedState(index)
    val latestQueueSize = rememberUpdatedState(queueSize)
    val latestReorderThresholdPx = rememberUpdatedState(reorderThresholdPx)

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragCurrentIndex by remember { mutableIntStateOf(index) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) activeColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.025f))
            .clickable(role = Role.Button, onClick = onClick)
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
                    contentDescription = stringResource(R.string.player_currently_selected_accessibility),
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
                text = localizedArtistPresentation(track.artist),
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.58f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            positionLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(color = activeColor.copy(alpha = 0.9f)),
                    maxLines = 1
                )
            }
        }

        if (canReorder) {
            // Keep the long-press gesture discoverable; move buttons are a
            // deterministic alternative for users who do not drag.
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = stringResource(R.string.player_drag_track_accessibility, track.title),
                tint = Color.White.copy(alpha = 0.48f),
                modifier = Modifier
                    .size(24.dp)
                    .pointerInput(track.ratingKey) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragOffset = 0f
                                dragCurrentIndex = latestIndex.value
                            },
                            onDragCancel = {
                                dragOffset = 0f
                                dragCurrentIndex = latestIndex.value
                            },
                            onDragEnd = {
                                dragOffset = 0f
                                dragCurrentIndex = latestIndex.value
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val result = QueueReorderPolicy.consumeDrag(
                                    startIndex = dragCurrentIndex,
                                    accumulatedOffsetPx = dragOffset + amount.y,
                                    thresholdPx = latestReorderThresholdPx.value,
                                    queueSize = latestQueueSize.value
                                )
                                dragOffset = result.remainingOffsetPx
                                result.moves.forEach { move ->
                                    onMove(move.fromIndex, move.toIndex)
                                }
                                result.moves.lastOrNull()?.let { dragCurrentIndex = it.toIndex }
                            }
                        )
                    }
                    .semantics { }
            )

            IconButton(
                onClick = { onMove(index, index - 1) },
                enabled = index > 0,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("queue_move_up_$index")
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.player_move_track_up_accessibility, track.title),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (index > 0) 0.76f else 0.38f)
                )
            }
            IconButton(
                onClick = { onMove(index, index + 1) },
                enabled = canMoveDown,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("queue_move_down_$index")
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.player_move_track_down_accessibility, track.title),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canMoveDown) 0.76f else 0.38f)
                )
            }
        }
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("queue_track_more_$index")
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.player_queue_options_accessibility, track.title),
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
    albumRatingKey = albumRatingKey,
    albumArtist = albumArtist,
    artistRatingKey = artistRatingKey
)
