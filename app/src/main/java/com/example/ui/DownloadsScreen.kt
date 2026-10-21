package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.DownloadedAlbumGroup
import com.example.data.DownloadedTrackEntity
import com.example.data.isNonEmptyRegularFile
import com.example.data.locallyAvailableDownloadRatingKeys
import com.example.data.filterDownloadedPlaylistTracks
import com.example.data.formatDownloadDuration
import com.example.data.formatDownloadSize
import com.example.data.groupDownloadedAlbums
import com.example.data.ProductFailurePresentation
import com.example.data.toTrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DownloadsScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier,
    onNavigateToAlbum: (String, String) -> Unit = { _, _ -> },
    onNavigateToDownloadedAlbum: (String, String) -> Unit = { artist, album ->
        onNavigateToAlbum(com.example.data.albumNavigationKey(artist, album), album)
    },
    onNavigateToArtist: (String, String) -> Unit = { _, _ -> },
    onNavigateToLibrary: () -> Unit = {},
    onStartPairing: () -> Unit = {}
) {
    val downloadedTracksSnapshot by viewModel.downloadedTracksSnapshot.collectAsStateWithLifecycle()
    val downloadedTracksLoaded = downloadedTracksSnapshot != null
    val downloadedTracks = downloadedTracksSnapshot.orEmpty()
    val downloadProgresses by viewModel.downloadProgresses.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val cachedTracks by viewModel.cachedTracks.collectAsStateWithLifecycle()
    val companionConfigured by viewModel.companionConfigured.collectAsStateWithLifecycle()
    val companionConnectionStatus by viewModel.companionConnectionStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locallyAvailableKeys = remember(downloadedTracks) {
        locallyAvailableDownloadRatingKeys(downloadedTracks, ::isNonEmptyRegularFile)
    }
    val completedTracks = downloadedTracks.filter { it.ratingKey in locallyAvailableKeys }
    val pendingTracks = downloadedTracks.filter { it.ratingKey !in locallyAvailableKeys }
    val albumGroups = remember(downloadedTracks, cachedTracks) {
        groupDownloadedAlbums(downloadedTracks, cachedTracks)
    }
    var offlineOnly by remember { mutableStateOf(viewModel.repository.settings.offlineOnly) }
    var actionsExpanded by remember { mutableStateOf(false) }
    val currentTrack by viewModel.playbackManager.currentTrack.collectAsStateWithLifecycle()
    var confirmDeleteAllDownloads by remember { mutableStateOf(false) }
    var confirmClearTemporaryFiles by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var tracksToAdd by remember { mutableStateOf<List<com.example.playback.TrackItem>>(emptyList()) }
    var albumIdToAdd by remember { mutableStateOf<String?>(null) }

    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token
    val totalSizeBytes = completedTracks.sumOf { it.fileSize }
    val totalDurationMs = completedTracks.sumOf { it.duration }
    val connectionNoticeBodyResource = if (
        shouldShowCompanionConnectionNotice(companionConfigured, companionConnectionStatus)
    ) {
        connectionStatusNoticeBodyResource(
            status = companionConnectionStatus,
            offlineBodyResource = R.string.downloads_connection_offline_body,
            unavailableBodyResource = R.string.downloads_connection_unavailable_body
        )
    } else null

    LaunchedEffect(companionConfigured) {
        if (companionConfigured) viewModel.ensureCompanionReachabilityChecked()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.downloads_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.weight(1f)
            )
            Box {
                FeedbackIconButton(
                    onClick = { actionsExpanded = true },
                    modifier = Modifier.testTag("downloads_more_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.downloads_options_accessibility),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false }
                ) {
                    DropdownMenuItem(
                        modifier = Modifier.testTag("downloads_offline_mode_menu_item"),
                        text = {
                            Text(
                                stringResource(
                                    if (offlineOnly) R.string.downloads_allow_online_music
                                    else R.string.downloads_use_downloaded_only
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (offlineOnly) Icons.Rounded.CloudOff else Icons.Rounded.CloudDownload,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            offlineOnly = !offlineOnly
                            viewModel.repository.settings.offlineOnly = offlineOnly
                            actionsExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.downloads_clear_temporary_files)) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                        onClick = {
                            confirmClearTemporaryFiles = true
                            actionsExpanded = false
                        }
                    )
                    ClearFailedDownloadsMenuItem(
                        downloadedTracks = downloadedTracks,
                        onDelete = { viewModel.deleteDownload(it.ratingKey) },
                        onDismiss = { actionsExpanded = false }
                    )
                    if (downloadedTracks.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.downloads_delete_all)) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
                            onClick = {
                                confirmDeleteAllDownloads = true
                                actionsExpanded = false
                            }
                        )
                    }
                }
            }
            DownloadsAddToPlaylistButton(
                hasCompletedTracks = completedTracks.isNotEmpty(),
                onClick = {
                    tracksToAdd = completedTracks.map { it.toTrackItem() }
                    albumIdToAdd = null
                    showPlaylistDialog = true
                }
            )
        }

        if (connectionNoticeBodyResource != null) {
            CompanionConnectionNotice(
                status = companionConnectionStatus,
                bodyResource = connectionNoticeBodyResource,
                onCheckConnection = viewModel::retryCompanionConnection,
                modifier = Modifier.padding(bottom = 12.dp),
                testTag = "downloads_connection_status_notice"
            )
        }

        Text(
            text = if (downloadedTracksLoaded) {
                downloadSizeDurationLabel(totalSizeBytes, totalDurationMs)
            } else {
                stringResource(R.string.downloads_loading_saved_tracks)
            },
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )
        DownloadsPlaybackModeSummary(offlineOnly = offlineOnly)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .testTag("downloads_list"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
        ) {
            if (pendingTracks.any { it.status == "failed" }) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.downloads_queue_title),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        RetryFailedDownloadsAction(
                            pendingTracks = pendingTracks,
                            onRetry = viewModel::retryDownload
                        )
                    }
                }
            }
            if (!downloadedTracksLoaded) {
                item {
                    DownloadsLoadingState()
                }
            } else if (shouldShowDownloadsEmptyState(
                    hasCompletedTracks = completedTracks.isNotEmpty(),
                    hasPendingTracks = pendingTracks.isNotEmpty(),
                    hasActiveProgress = downloadProgresses.isNotEmpty()
                )
            ) {
                item {
                    DownloadsEmptyState(
                        onBrowseMusic = onNavigateToLibrary,
                        offlineOnly = offlineOnly,
                        companionConfigured = companionConfigured,
                        onStartPairing = onStartPairing,
                        onAllowOnlineMusic = {
                            offlineOnly = false
                            viewModel.repository.settings.offlineOnly = false
                        }
                    )
                }
            } else {
                albumGroups.forEach { albumGroup ->
                    item(key = "album:${albumGroup.artist.lowercase()}\u0000${albumGroup.album.lowercase()}") {
                        DownloadedAlbumRow(
                            album = albumGroup,
                            baseUrl = baseUrl,
                            token = token,
                            downloadProgresses = downloadProgresses,
                            locallyAvailableKeys = locallyAvailableKeys,
                            onNavigateToAlbum = onNavigateToDownloadedAlbum,
                            onDeleteAlbum = {
                                albumGroup.tracks.forEach { track ->
                                    viewModel.deleteDownload(track.ratingKey)
                                }
                            },
                            onNavigateToArtist = onNavigateToArtist,
                            viewModel = viewModel,
                            onAddToPlaylist = { tracks, albumId ->
                                tracksToAdd = tracks
                                albumIdToAdd = albumId
                                showPlaylistDialog = true
                            }
                        )
                    }
                }

                // Playlist downloads also remain visible as playlist
                // collections, alongside the album-based download rows.
                playlists.forEach { playlist ->
                    item(key = "playlist:${playlist.id}") {
                        DownloadedPlaylistRow(
                            playlist = playlist,
                            downloadedTracks = downloadedTracks,
                            baseUrl = baseUrl,
                            token = token,
                            onNavigateToAlbum = onNavigateToAlbum,
                            onNavigateToArtist = onNavigateToArtist,
                            viewModel = viewModel,
                            onAddToPlaylist = { tracks, albumId ->
                                tracksToAdd = tracks
                                albumIdToAdd = albumId
                                showPlaylistDialog = true
                            },
                            currentTrack = currentTrack
                        )
                    }
                }
            }
        }
    }

    if (showPlaylistDialog) {
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        PlaylistSelectionDialog(
            playlists = playlists,
            onDismiss = { showPlaylistDialog = false },
            onPlaylistSelected = { playlistId ->
                if (albumIdToAdd != null) {
                    viewModel.addAlbumToPlaylist(playlistId, albumIdToAdd!!)
                } else {
                    tracksToAdd.forEach { track ->
                        viewModel.addTrackToPlaylist(playlistId, track)
                    }
                }
                showPlaylistDialog = false
            },
            onCreateNewPlaylist = { name, onFailure, onCreated ->
                viewModel.createPlaylist(
                    name = name,
                    onFailure = onFailure,
                    onCreated = { newId ->
                        if (albumIdToAdd != null) {
                            viewModel.addAlbumToPlaylist(newId.toInt(), albumIdToAdd!!)
                        } else {
                            tracksToAdd.forEach { track ->
                                viewModel.addTrackToPlaylist(newId.toInt(), track)
                            }
                        }
                        onCreated()
                    }
                )
            }
        )
    }

    if (confirmDeleteAllDownloads) {
        DeleteAllDownloadsDialog(
            onConfirm = {
                confirmDeleteAllDownloads = false
                viewModel.deleteAllDownloads()
            },
            onDismiss = { confirmDeleteAllDownloads = false }
        )
    }

    if (confirmClearTemporaryFiles) {
        ClearTemporaryFilesDialog(
            onConfirm = {
                confirmClearTemporaryFiles = false
                coroutineScope.launch(Dispatchers.IO) { context.cacheDir.deleteRecursively() }
            },
            onDismiss = { confirmClearTemporaryFiles = false }
        )
    }
}

@Composable
fun DownloadedAlbumDetailScreen(
    albumArtist: String,
    albumName: String,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadedTracksSnapshot by viewModel.downloadedTracksSnapshot.collectAsStateWithLifecycle()
    val downloadedTracks = downloadedTracksSnapshot.orEmpty()
    val cachedTracks by viewModel.cachedTracks.collectAsStateWithLifecycle()
    val downloadProgresses by viewModel.downloadProgresses.collectAsStateWithLifecycle()
    val albumTracks = remember(downloadedTracks, cachedTracks, albumArtist, albumName) {
        groupDownloadedAlbums(downloadedTracks, cachedTracks)
            .firstOrNull {
                it.artist.equals(albumArtist, ignoreCase = true) &&
                    it.album.equals(albumName, ignoreCase = true)
            }
            ?.tracks
            .orEmpty()
    }
    val locallyAvailableKeys = remember(downloadedTracks) {
        locallyAvailableDownloadRatingKeys(downloadedTracks, ::isNonEmptyRegularFile)
    }
    val offlineTracks = remember(albumTracks, locallyAvailableKeys) {
        albumTracks.filter { it.ratingKey in locallyAvailableKeys }
    }
    val progress = remember(albumTracks, locallyAvailableKeys, downloadProgresses) {
        com.example.data.downloadedAlbumProgress(albumTracks, locallyAvailableKeys, downloadProgresses)
    }
    val currentTrack by viewModel.playbackManager.currentTrack.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token
    val firstTrack = albumTracks.firstOrNull()
    val imageUrl = firstTrack?.thumb?.takeIf(String::isNotBlank)?.let { resolveArtworkUrl(baseUrl, it) }
    var expandedFailureDetails by remember(albumTracks) { mutableStateOf<Map<String, String?>>(emptyMap()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
            .testTag("downloaded_album_detail")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FeedbackIconButton(onClick = onBack, modifier = Modifier.testTag("downloaded_album_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.details_back_accessibility),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = albumName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = localizedArtistPresentation(albumArtist),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (offlineTracks.isNotEmpty()) {
                FeedbackIconButton(
                    onClick = { viewModel.playbackManager.playQueue(offlineTracks.map { it.toTrackItem() }, 0) },
                    modifier = Modifier.testTag("downloaded_album_play")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.downloads_play_album_offline),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(modifier = Modifier.size(88.dp), shape = RoundedCornerShape(10.dp)) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = authenticatedArtworkRequest(context, imageUrl, token),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Album, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                if (downloadedTracksSnapshot == null) {
                    Text(
                        text = stringResource(R.string.downloads_loading_saved_tracks),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (progress != null) {
                    Text(
                        text = stringResource(
                            R.string.downloads_album_progress,
                            offlineTracks.size,
                            progress.totalTrackCount,
                            (progress.fraction * 100).toInt()
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (progress.isIndeterminate) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                } else if (offlineTracks.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.downloads_album_saved_count, offlineTracks.size, albumTracks.size),
                        color = Color(0xFF047857),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = downloadSizeDurationLabel(
                            offlineTracks.sumOf { it.fileSize },
                            offlineTracks.sumOf { it.duration }
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text = stringResource(R.string.downloads_album_waiting),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(
                    onClick = { onNavigateToArtist(com.example.data.artistNavigationKey(albumArtist), albumArtist) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Text(stringResource(R.string.downloads_go_to_artist))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (downloadedTracksSnapshot == null) {
                item { DownloadsLoadingState() }
            } else if (albumTracks.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.downloads_album_no_tracks),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                itemsIndexed(albumTracks, key = { _, track -> "download-track:${track.ratingKey}" }) { index, track ->
                    val isAvailable = track.ratingKey in locallyAvailableKeys
                    val fileMissing = track.status == "completed" && !isAvailable
                    val canRetry = track.status == "failed" || fileMissing
                    val activeProgress = downloadProgresses[track.ratingKey]
                    val isCurrent = currentTrack?.ratingKey == track.ratingKey
                    val isShowingFailure = expandedFailureDetails.containsKey(track.ratingKey)

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        DownloadsActionRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            onClick = {
                                if (isAvailable) {
                                    val queue = offlineTracks.map { it.toTrackItem() }
                                    viewModel.playbackManager.playTrack(track.toTrackItem(), queue)
                                }
                            }
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.width(26.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isAvailable) stringResource(R.string.downloads_available_offline)
                                        else DownloadsTrackStatusLabel(track.artist, track.status, fileMissing),
                                    color = if (canRetry) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (activeProgress != null && activeProgress >= 0f) {
                                    LinearProgressIndicator(
                                        progress = { activeProgress.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                } else if (track.status == "downloading") {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                            FeedbackIconButton(
                                onClick = {
                                    if (canRetry) viewModel.retryDownload(track)
                                    else viewModel.deleteDownload(track.ratingKey)
                                },
                                modifier = Modifier.testTag("downloaded_album_track_action_${track.ratingKey}")
                            ) {
                                Icon(
                                    imageVector = when {
                                        canRetry -> Icons.Rounded.Refresh
                                        isAvailable -> Icons.Rounded.Delete
                                        else -> Icons.Rounded.Close
                                    },
                                    contentDescription = when {
                                        canRetry -> stringResource(R.string.downloads_retry_track_accessibility, track.title, track.artist)
                                        isAvailable -> stringResource(R.string.downloads_delete_track)
                                        else -> stringResource(R.string.downloads_cancel_track_accessibility, track.title, track.artist)
                                    },
                                    tint = if (canRetry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (canRetry) {
                            DownloadFailureDetails(
                                errorMessage = track.errorMessage.takeUnless { fileMissing },
                                showingDetails = isShowingFailure,
                                detailsToggleTestTag = "download_failure_details_toggle_${track.ratingKey}",
                                fallbackResource = if (fileMissing) R.string.downloads_missing_file_recovery
                                    else R.string.downloads_failure_generic,
                                onToggleDetails = {
                                    expandedFailureDetails = if (isShowingFailure) {
                                        expandedFailureDetails - track.ratingKey
                                    } else {
                                        expandedFailureDetails + (track.ratingKey to track.errorMessage)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DownloadsPlaybackModeSummary(offlineOnly: Boolean) {
    Text(
        text = stringResource(
            if (offlineOnly) R.string.downloads_downloaded_only_on
            else R.string.downloads_downloaded_only_off
        ),
        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .testTag("downloads_playback_mode_summary"),
        textAlign = TextAlign.End
    )
}

@Composable
internal fun RetryFailedDownloadsAction(
    pendingTracks: List<DownloadedTrackEntity>,
    onRetry: (DownloadedTrackEntity) -> Unit
) {
    val failedTracks = pendingTracks.filter { it.status == "failed" }
    if (failedTracks.size > 1) {
        TextButton(
            onClick = { failedTracks.forEach(onRetry) },
            modifier = Modifier.testTag("downloads_retry_all_failed")
        ) {
            Text(stringResource(R.string.downloads_retry_all_failed))
        }
    }
}

@Composable
internal fun DownloadPendingTrackAction(
    trackTitle: String,
    artist: String,
    shouldRetry: Boolean,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val normalizedTitle = if (trackTitle.isBlank()) {
        stringResource(R.string.common_unknown_track)
    } else {
        trackTitle.trim()
    }
    val normalizedArtist = localizedArtistPresentation(artist)
    val actionLabel = stringResource(
        if (shouldRetry) R.string.downloads_retry_track_accessibility
        else R.string.downloads_cancel_track_accessibility,
        normalizedTitle,
        normalizedArtist
    )
    TextButton(
        onClick = if (shouldRetry) onRetry else onCancel,
        modifier = Modifier
            .semantics { contentDescription = actionLabel }
            .testTag("downloads_pending_action")
    ) {
        Text(stringResource(if (shouldRetry) R.string.downloads_retry else R.string.downloads_cancel))
    }
}

@Composable
internal fun ClearFailedDownloadsMenuItem(
    downloadedTracks: List<DownloadedTrackEntity>,
    onDelete: (DownloadedTrackEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val failedTracks = downloadedTracks.filter { it.status == "failed" }
    if (failedTracks.isNotEmpty()) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.downloads_clear_failed_items)) },
            leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
            onClick = {
                failedTracks.forEach(onDelete)
                onDismiss()
            }
        )
    }
}

@Composable
internal fun DownloadsEmptyState(
    onBrowseMusic: () -> Unit,
    offlineOnly: Boolean = false,
    onAllowOnlineMusic: () -> Unit = {},
    companionConfigured: Boolean = true,
    onStartPairing: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 250.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.DownloadDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.downloads_empty_title),
                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(
                    if (companionConfigured) R.string.downloads_empty_body
                    else R.string.downloads_pair_spotcore_body
                ),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 32.dp).padding(top = 8.dp)
            )
            Button(
                onClick = if (companionConfigured) onBrowseMusic else onStartPairing,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag(
                        if (companionConfigured) "downloads_browse_music"
                        else "downloads_pair_spotcore_action"
                    )
            ) {
                Icon(
                    if (companionConfigured) Icons.Rounded.LibraryMusic else Icons.Rounded.QrCodeScanner,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (companionConfigured) R.string.downloads_browse_music
                        else R.string.library_pair_spotcore_action
                    )
                )
            }
            if (offlineOnly) {
                TextButton(
                    onClick = onAllowOnlineMusic,
                    modifier = Modifier.testTag("downloads_allow_online_from_empty")
                ) {
                    Text(stringResource(R.string.downloads_allow_online_music))
                }
            }
        }
    }
}

internal fun shouldShowDownloadsEmptyState(
    hasCompletedTracks: Boolean,
    hasPendingTracks: Boolean,
    hasActiveProgress: Boolean
): Boolean = !hasCompletedTracks && !hasPendingTracks && !hasActiveProgress

@Composable
internal fun DownloadsLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp)
            .testTag("downloads_loading_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.downloads_loading_saved_tracks),
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}

@Composable
internal fun DownloadsPlaylistOfflineCount(trackCount: Int) {
    Text(
        text = pluralStringResource(R.plurals.downloads_playlist_offline_count, trackCount, trackCount),
        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    )
}

@Composable
internal fun DownloadFailureDetails(
    errorMessage: String?,
    showingDetails: Boolean,
    onToggleDetails: () -> Unit,
    detailsToggleTestTag: String = "download_failure_details_toggle",
    fallbackResource: Int = R.string.downloads_failure_generic
) {
    val context = LocalContext.current
    val failure = ProductFailurePresentation.from(
        context,
        errorMessage,
        fallback = stringResource(fallbackResource)
    )
    Text(
        failure.message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .testTag("download_failure_message")
            .semantics {
                liveRegion = LiveRegionMode.Polite
                error(failure.message)
            }
    )
    if (failure.technicalDetails != null) {
        val detailsStateDescription = stringResource(
            if (showingDetails) R.string.recovery_details_expanded else R.string.recovery_details_collapsed
        )
        TextButton(
            onClick = onToggleDetails,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
            modifier = Modifier
                .testTag(detailsToggleTestTag)
                .semantics {
                    stateDescription = detailsStateDescription
                }
        ) {
            Text(stringResource(if (showingDetails) R.string.downloads_hide_details else R.string.downloads_show_details))
        }
        if (showingDetails) {
            Text(
                failure.technicalDetails,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

internal fun retainCurrentDownloadFailureDetails(
    expanded: Map<String, String?>,
    currentFailures: Map<String, String?>
): Map<String, String?> = expanded.filter { (ratingKey, errorMessage) ->
    currentFailures.containsKey(ratingKey) && currentFailures[ratingKey] == errorMessage
}

@Composable
internal fun DeleteAllDownloadsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloads_delete_all_title)) },
        text = { Text(stringResource(R.string.downloads_delete_all_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.downloads_delete_all_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.downloads_cancel))
            }
        }
    )
}

@Composable
internal fun RemoveOfflineCollectionDialog(
    collectionName: String,
    trackCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloads_remove_offline_collection_title)) },
        text = {
            Text(
                pluralStringResource(
                    R.plurals.downloads_remove_offline_collection_message,
                    trackCount,
                    collectionName,
                    trackCount
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.downloads_remove_offline_collection_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.downloads_cancel))
            }
        }
    )
}

@Composable
internal fun ClearTemporaryFilesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloads_clear_temporary_files_title)) },
        text = { Text(stringResource(R.string.downloads_clear_temporary_files_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.downloads_clear_temporary_files_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.downloads_cancel))
            }
        }
    )
}

@Composable
internal fun DownloadsAddToPlaylistButton(
    hasCompletedTracks: Boolean,
    onClick: () -> Unit
) {
    if (!hasCompletedTracks) return
    FeedbackIconButton(
        onClick = onClick,
        modifier = Modifier.testTag("downloads_add_all_button")
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.downloads_add_saved_tracks_to_playlist),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun DownloadsDisclosureButton(
    expanded: Boolean,
    expandDescription: String,
    collapseDescription: String,
    onToggle: () -> Unit
) {
    val disclosureState = stringResource(
        if (expanded) R.string.downloads_expanded_state else R.string.downloads_collapsed_state
    )
    FeedbackIconButton(
        onClick = onToggle,
        modifier = Modifier.semantics {
            stateDescription = disclosureState
        }
    ) {
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) collapseDescription else expandDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
internal fun DownloadsActionRow(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
internal fun DownloadsExpandablePlaylistHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val disclosureState = stringResource(
        if (expanded) R.string.downloads_expanded_state else R.string.downloads_collapsed_state
    )
    Row(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = disclosureState }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
internal fun DownloadedAlbumOptionsAction(
    albumName: String,
    onClick: () -> Unit
) {
    FeedbackIconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.downloads_album_options_accessibility, albumName),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun DownloadedTrackOptionsAction(
    title: String,
    artist: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FeedbackIconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.downloads_track_options_accessibility, title, artist),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun DownloadedPlaylistOptionsAction(
    playlistName: String,
    onClick: () -> Unit
) {
    FeedbackIconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.downloads_playlist_options_accessibility, playlistName),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun DownloadedAlbumAddToPlaylistAction(
    albumName: String,
    onClick: () -> Unit
) {
    FeedbackIconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.downloads_add_album_to_playlist, albumName),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun DownloadedTrackAddToPlaylistAction(
    title: String,
    artist: String,
    onClick: () -> Unit
) {
    FeedbackIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.downloads_add_track_to_playlist, title, artist),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun DownloadedPlaylistAddToPlaylistAction(
    playlistName: String,
    onClick: () -> Unit
) {
    FeedbackIconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.downloads_add_playlist_tracks_to_playlist, playlistName),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun downloadStatusLabel(status: String): String = stringResource(when (status.lowercase()) {
    "queued" -> R.string.downloads_waiting_status
    "downloading" -> R.string.downloads_in_progress
    "retrying" -> R.string.downloads_retrying_status
    "failed" -> R.string.downloads_failed_status
    else -> R.string.downloads_status_unavailable
})

@Composable
internal fun DownloadsTrackStatusLabel(artist: String, status: String, fileMissing: Boolean = false): String {
    val artistLabel = localizedArtistPresentation(artist)
    val statusLabel = if (fileMissing) stringResource(R.string.downloads_missing_file_status) else downloadStatusLabel(status)
    return stringResource(R.string.downloads_track_status, artistLabel, statusLabel)
}

@Composable
internal fun downloadSizeDurationLabel(totalSizeBytes: Long, totalDurationMs: Long): String =
    stringResource(
        R.string.downloads_size_duration,
        formatDownloadSize(totalSizeBytes),
        formatDownloadDuration(totalDurationMs)
    )

@Composable
fun DownloadedAlbumRow(
    album: DownloadedAlbumGroup,
    baseUrl: String,
    token: String,
    downloadProgresses: Map<String, Float>,
    locallyAvailableKeys: Set<String>,
    onNavigateToAlbum: (String, String) -> Unit,
    onDeleteAlbum: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    viewModel: MusicViewModel,
    onAddToPlaylist: (List<com.example.playback.TrackItem>, String?) -> Unit
) {
    val albumName = album.album
    val tracks = album.tracks
    val offlineTracks = remember(tracks, locallyAvailableKeys) {
        tracks.filter { it.ratingKey in locallyAvailableKeys }
    }
    val progress = remember(tracks, locallyAvailableKeys, downloadProgresses) {
        com.example.data.downloadedAlbumProgress(tracks, locallyAvailableKeys, downloadProgresses)
    }
    var rowMenuExpanded by remember { mutableStateOf(false) }
    var confirmRemoveDownloads by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val firstTrack = tracks.firstOrNull()
    val imageUrl = firstTrack?.thumb?.takeIf(String::isNotBlank)?.let { resolveArtworkUrl(baseUrl, it) }
    val totalSizeBytes = offlineTracks.sumOf { it.fileSize }
    val totalDurationMs = offlineTracks.sumOf { it.duration }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_album_${albumName.lowercase()}")
    ) {
        DownloadsActionRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            onClick = { onNavigateToAlbum(album.artist, albumName) }
        ) {
            Card(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(8.dp)
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
                            imageVector = Icons.Rounded.Album,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = localizedArtistPresentation(album.artist),
                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                when {
                    progress != null -> {
                        Text(
                            text = stringResource(
                                R.string.downloads_album_progress,
                                offlineTracks.size,
                                progress.totalTrackCount,
                                (progress.fraction * 100).toInt()
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (progress.isIndeterminate) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { progress.fraction },
                                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                    offlineTracks.size == tracks.size -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = Color(0xFF047857),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.downloads_available_offline),
                                style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF047857))
                            )
                        }
                        Text(
                            text = stringResource(R.string.downloads_album_tracks_count, tracks.size),
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                    else -> Text(
                        text = stringResource(R.string.downloads_album_saved_count, offlineTracks.size, tracks.size),
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (offlineTracks.isNotEmpty()) {
                    Text(
                        text = downloadSizeDurationLabel(totalSizeBytes, totalDurationMs),
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1
                    )
                }
            }

            Box {
                DownloadedAlbumOptionsAction(albumName = albumName, onClick = { rowMenuExpanded = true })
                DropdownMenu(
                    expanded = rowMenuExpanded,
                    onDismissRequest = { rowMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.downloads_play_album_offline)) },
                        enabled = offlineTracks.isNotEmpty(),
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                        onClick = {
                            viewModel.playbackManager.playQueue(offlineTracks.map { it.toTrackItem() }, 0)
                            rowMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.downloads_go_to_artist)) },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        onClick = {
                            onNavigateToArtist(com.example.data.artistNavigationKey(album.artist), album.artist)
                            rowMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.downloads_remove_album_downloads)) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
                        onClick = {
                            rowMenuExpanded = false
                            confirmRemoveDownloads = true
                        }
                    )
                }
            }

            if (offlineTracks.isNotEmpty()) {
                DownloadedAlbumAddToPlaylistAction(
                    albumName = albumName,
                    onClick = {
                        onAddToPlaylist(
                            offlineTracks.map { it.toTrackItem() },
                            com.example.data.albumNavigationKey(album.artist, albumName)
                        )
                    }
                )
            }
        }

        if (confirmRemoveDownloads) {
            RemoveOfflineCollectionDialog(
                collectionName = albumName,
                trackCount = tracks.size,
                onConfirm = {
                    confirmRemoveDownloads = false
                    onDeleteAlbum()
                },
                onDismiss = { confirmRemoveDownloads = false }
            )
        }
    }
}
@Composable
fun DownloadedPlaylistRow(
    playlist: com.example.data.PlaylistEntity,
    downloadedTracks: List<DownloadedTrackEntity>,
    baseUrl: String,
    token: String,
    onNavigateToAlbum: (String, String) -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    viewModel: MusicViewModel,
    onAddToPlaylist: (List<com.example.playback.TrackItem>, String?) -> Unit,
    currentTrack: com.example.playback.TrackItem?
) {
    val tracks by viewModel.repository.getPlaylistTracks(playlist.id).collectAsStateWithLifecycle(emptyList())

    // Filter playlist tracks that are downloaded
    val offlineTracks = remember(tracks, downloadedTracks) {
        filterDownloadedPlaylistTracks(tracks, downloadedTracks, ::isNonEmptyRegularFile)
    }

    if (offlineTracks.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    var rowMenuExpanded by remember { mutableStateOf(false) }
    var confirmRemoveDownloads by remember { mutableStateOf(false) }
    var trackMenuKey by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val firstTrack = offlineTracks.firstOrNull()
    val imageUrl = if (!firstTrack?.thumb.isNullOrEmpty()) resolveArtworkUrl(baseUrl, firstTrack?.thumb.orEmpty()) else null
    val totalSizeBytes = offlineTracks.sumOf { t ->
        downloadedTracks.find { it.ratingKey == t.ratingKey }?.fileSize ?: 0L
    }
    val totalDurationMs = offlineTracks.sumOf { it.duration }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_playlist_${playlist.id}")
    ) {
        DownloadsExpandablePlaylistHeader(
            expanded = expanded,
            onToggle = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Card(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(8.dp)
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
                                    colors = listOf(Color(0xFF818CF8), Color(0xFFC084FC))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                DownloadsPlaylistOfflineCount(offlineTracks.size)
                Text(
                    text = downloadSizeDurationLabel(totalSizeBytes, totalDurationMs),
                        style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Box {
                DownloadedPlaylistOptionsAction(playlistName = playlist.name, onClick = { rowMenuExpanded = true })
                DropdownMenu(
                    expanded = rowMenuExpanded,
                    onDismissRequest = { rowMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.downloads_play_playlist_offline)) },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                        onClick = {
                            viewModel.playbackManager.playQueue(offlineTracks.map { it.toTrackItem() }, 0)
                            rowMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.downloads_remove_playlist_downloads)) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
                        onClick = {
                            rowMenuExpanded = false
                            confirmRemoveDownloads = true
                        }
                    )
                }
            }

            DownloadedPlaylistAddToPlaylistAction(
                playlistName = playlist.name,
                onClick = {
                    onAddToPlaylist(offlineTracks.map { it.toTrackItem() }, null)
                }
            )

            DownloadsDisclosureButton(
                expanded = expanded,
                expandDescription = stringResource(R.string.downloads_expand_playlist_tracks_accessibility, playlist.name),
                collapseDescription = stringResource(R.string.downloads_collapse_playlist_tracks_accessibility, playlist.name),
                onToggle = { expanded = !expanded }
            )
        }

        if (expanded) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            offlineTracks.forEachIndexed { index, track ->
                val isCurrent = currentTrack?.ratingKey == track.ratingKey
                DownloadsActionRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    onClick = {
                            val playbackTracks = offlineTracks.map { it.toTrackItem() }
                            viewModel.playbackManager.playTrack(track.toTrackItem(), playbackTracks)
                    }
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.width(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = localizedArtistPresentation(track.artist),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    Box {
                        DownloadedTrackOptionsAction(
                            title = track.title,
                            artist = track.artist,
                            onClick = { trackMenuKey = track.ratingKey },
                            modifier = Modifier.size(48.dp)
                        )
                        DropdownMenu(
                            expanded = trackMenuKey == track.ratingKey,
                            onDismissRequest = { trackMenuKey = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_go_to_artist)) },
                                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                onClick = {
                                    onNavigateToArtist(com.example.data.artistNavigationKey(track.artist), track.artist)
                                    trackMenuKey = null
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_go_to_album)) },
                                leadingIcon = { Icon(Icons.Rounded.Album, contentDescription = null) },
                                onClick = {
                                    onNavigateToAlbum(com.example.data.albumNavigationKey(track.artist, track.album), track.album)
                                    trackMenuKey = null
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_delete_track)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = {
                                    viewModel.deleteDownload(track.ratingKey)
                                    trackMenuKey = null
                                }
                            )
                        }
                    }
                    DownloadedTrackAddToPlaylistAction(
                        title = track.title,
                        artist = track.artist,
                        onClick = {
                            onAddToPlaylist(listOf(track.toTrackItem()), null)
                        }
                    )
                }
            }
        }

        if (confirmRemoveDownloads) {
            RemoveOfflineCollectionDialog(
                collectionName = playlist.name,
                trackCount = offlineTracks.size,
                onConfirm = {
                    confirmRemoveDownloads = false
                    offlineTracks.forEach { track -> viewModel.deleteDownload(track.ratingKey) }
                },
                onDismiss = { confirmRemoveDownloads = false }
            )
        }
    }
}
