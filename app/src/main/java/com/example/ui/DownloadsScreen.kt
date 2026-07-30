package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.data.DownloadedTrackEntity
import com.example.data.toTrackItem
import java.text.DecimalFormat
import android.os.StatFs

@Composable
fun DownloadsScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val downloadProgresses by viewModel.downloadProgresses.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val completedTracks = downloadedTracks.filter { it.status == "completed" && !it.localPath.isNullOrBlank() }
    val pendingTracks = downloadedTracks.filter { it.status != "completed" }
    var offlineOnly by remember { mutableStateOf(viewModel.repository.settings.offlineOnly) }

    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

    // Calculate total size of downloads
    val totalSizeBytes = downloadedTracks.sumOf { it.fileSize }
    val totalSizeMb = totalSizeBytes / (1024.0 * 1024.0)
    val sizeFormat = DecimalFormat("#.##")

    var selectedTab by remember { mutableStateOf("Songs") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp)
    ) {
        // Downloads Title
        Text(
            text = "Downloads",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Downloads Stats Bar
        Text(
            text = "${completedTracks.size} songs • ${sizeFormat.format(totalSizeMb)} MB downloaded",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val storage = StatFs(context.filesDir.absolutePath)
        val availableMb = (storage.availableBytes / (1024.0 * 1024.0))
        Text(
            text = "${sizeFormat.format(availableMb)} MB available on device • ${sizeFormat.format(context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024.0 * 1024.0))} MB cache",
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.45f)),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Offline-only mode", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            Switch(checked = offlineOnly, onCheckedChange = { offlineOnly = it; viewModel.repository.settings.offlineOnly = it })
            TextButton(onClick = { context.cacheDir.deleteRecursively() }) { Text("Clear cache") }
        }

        if (downloadedTracks.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewModel.deleteAllDownloads() }) { Text("Delete all", color = Color(0xFFFCA5A5)) }
            }
        }

        // Custom Tab Switcher Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Songs", "Albums", "Playlists").forEach { tab ->
                val isSelected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f))
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("download_tab_$tab"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                        )
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Active Download Progress Indicators Section
            if (downloadProgresses.isNotEmpty()) {
                item {
                    Text(
                        text = "Downloading Tracks...",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                itemsIndexed(downloadProgresses.toList()) { _, (ratingKey, progress) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Fetching media stream...",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                                    )
                                }
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
            }

            if (pendingTracks.isNotEmpty()) {
                item {
                    Text("Download queue", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f)))
                }
                itemsIndexed(pendingTracks) { _, track ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(10.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${track.artist} • ${track.status}", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            track.errorMessage?.let { Text(it, color = Color(0xFFFCA5A5), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        if (track.status == "failed") {
                            TextButton(onClick = { viewModel.retryDownload(track) }) { Text("Retry") }
                        } else {
                            TextButton(onClick = { viewModel.deleteDownload(track.ratingKey) }) { Text("Cancel") }
                        }
                    }
                }
            }

            // Quick Play Actions Header (Only for general Songs view or if songs exist)
            if (selectedTab == "Songs" && completedTracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Play All Button
                        Button(
                            onClick = {
                                val playbackTracks = completedTracks.map { it.toTrackItem() }
                                viewModel.playbackManager.playQueue(playbackTracks, 0)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play All Offline", fontWeight = FontWeight.Bold)
                        }

                        // Shuffle Button
                        OutlinedButton(
                            onClick = {
                                val playbackTracks = completedTracks.map { it.toTrackItem() }.shuffled()
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
                            Text("Shuffle Offline", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Tab-Specific List Renderers
            if (completedTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.DownloadDone,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.1f),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Your offline vault is empty.",
                                style = MaterialTheme.typography.bodyLarge.copy(color = Color.White.copy(alpha = 0.4f)),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Click options (three dots) on any song or album to download for offline listening.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.3f),
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.padding(horizontal = 32.dp).padding(top = 8.dp)
                            )
                        }
                    }
                }
            } else {
                when (selectedTab) {
                    "Songs" -> {
                        itemsIndexed(completedTracks) { index, track ->
                            DownloadedTrackRow(
                                index = index + 1,
                                track = track,
                                baseUrl = baseUrl,
                                token = token,
                                onDelete = {
                                    viewModel.deleteDownload(track.ratingKey)
                                },
                                onClick = {
                                    val playbackTracks = completedTracks.map { it.toTrackItem() }
                                    viewModel.playbackManager.playTrack(track.toTrackItem(), playbackTracks)
                                }
                            )
                        }
                    }
                    "Albums" -> {
                        val groupedByAlbum = completedTracks.groupBy { it.album }
                        if (groupedByAlbum.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No offline albums found.",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        } else {
                            groupedByAlbum.forEach { (albumName, albumTracks) ->
                                item(key = albumName) {
                                    DownloadedAlbumRow(
                                        albumName = albumName,
                                        tracks = albumTracks,
                                        baseUrl = baseUrl,
                                        token = token,
                                        onDeleteAlbum = {
                                            albumTracks.forEach { track ->
                                                viewModel.deleteDownload(track.ratingKey)
                                            }
                                        },
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }
                    "Playlists" -> {
                        if (playlists.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No playlists found. Create one first and add downloaded tracks.",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f)),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                    )
                                }
                            }
                        } else {
                            playlists.forEach { playlist ->
                                item(key = playlist.id) {
                                    DownloadedPlaylistRow(
                                        playlist = playlist,
                                        downloadedTracks = downloadedTracks,
                                        baseUrl = baseUrl,
                                        token = token,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadedAlbumRow(
    albumName: String,
    tracks: List<DownloadedTrackEntity>,
    baseUrl: String,
    token: String,
    onDeleteAlbum: () -> Unit,
    viewModel: MusicViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val firstTrack = tracks.firstOrNull()
    val imageUrl = if (!firstTrack?.thumb.isNullOrEmpty()) "$normalizedBaseUrl${firstTrack?.thumb}" else null
    val totalSizeMb = tracks.sumOf { it.fileSize } / (1024.0 * 1024.0)
    val sizeFormat = DecimalFormat("#.##")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .addHeader("X-Plex-Token", token)
                            .crossfade(true)
                            .build(),
                        contentDescription = albumName,
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

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = firstTrack?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.6f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${tracks.size} tracks • ${sizeFormat.format(totalSizeMb)} MB",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            IconButton(
                onClick = {
                    val playbackTracks = tracks.map { it.toTrackItem() }
                    viewModel.playbackManager.playQueue(playbackTracks, 0)
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play album offline",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(onClick = onDeleteAlbum) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = "Delete offline album",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }

        if (expanded) {
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.05f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            tracks.forEachIndexed { index, track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val playbackTracks = tracks.map { it.toTrackItem() }
                            viewModel.playbackManager.playTrack(track.toTrackItem(), playbackTracks)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.width(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    IconButton(
                        onClick = { viewModel.deleteDownload(track.ratingKey) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete song",
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadedPlaylistRow(
    playlist: com.example.data.PlaylistEntity,
    downloadedTracks: List<DownloadedTrackEntity>,
    baseUrl: String,
    token: String,
    viewModel: MusicViewModel
) {
    val tracks by viewModel.repository.getPlaylistTracks(playlist.id).collectAsStateWithLifecycle(emptyList())

    // Filter playlist tracks that are downloaded
    val offlineTracks = remember(tracks, downloadedTracks) {
        tracks.filter { track -> downloadedTracks.any { it.ratingKey == track.ratingKey } }
    }

    if (offlineTracks.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val firstTrack = offlineTracks.firstOrNull()
    val imageUrl = if (!firstTrack?.thumb.isNullOrEmpty()) "$normalizedBaseUrl${firstTrack?.thumb}" else null
    val totalSizeMb = offlineTracks.sumOf { t ->
        downloadedTracks.find { it.ratingKey == t.ratingKey }?.fileSize ?: 0L
    } / (1024.0 * 1024.0)
    val sizeFormat = DecimalFormat("#.##")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .addHeader("X-Plex-Token", token)
                            .crossfade(true)
                            .build(),
                        contentDescription = playlist.name,
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
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${offlineTracks.size} songs offline",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.6f)
                    )
                )
                Text(
                    text = "${sizeFormat.format(totalSizeMb)} MB downloaded",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            IconButton(
                onClick = {
                    val playbackTracks = offlineTracks.map { it.toTrackItem() }
                    viewModel.playbackManager.playQueue(playbackTracks, 0)
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play playlist offline",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = {
                    offlineTracks.forEach { track ->
                        viewModel.deleteDownload(track.ratingKey)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = "Delete offline playlist",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }

        if (expanded) {
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.05f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            offlineTracks.forEachIndexed { index, track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val playbackTracks = offlineTracks.map { it.toTrackItem() }
                            viewModel.playbackManager.playTrack(track.toTrackItem(), playbackTracks)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.width(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }
                    IconButton(
                        onClick = { viewModel.deleteDownload(track.ratingKey) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete song",
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadedTrackRow(
    index: Int,
    track: DownloadedTrackEntity,
    baseUrl: String,
    token: String,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (track.thumb.isNotEmpty()) "$normalizedBaseUrl${track.thumb}" else null
    val sizeMb = track.fileSize / (1024.0 * 1024.0)
    val sizeFormat = DecimalFormat("#.##")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(Color.White.copy(alpha = 0.02f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Index Number
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center
        )

        // Track Cover Artwork
        Card(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .addHeader("X-Plex-Token", token)
                        .crossfade(true)
                        .build(),
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
                                colors = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
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

        // Track Text Titles Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${track.artist} • ${track.album}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.6f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // File Size indicator
        Text(
            text = "${sizeFormat.format(sizeMb)} MB",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White.copy(alpha = 0.4f)
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Delete offline copy Action Button
        IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("delete_download_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete downloaded song",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
