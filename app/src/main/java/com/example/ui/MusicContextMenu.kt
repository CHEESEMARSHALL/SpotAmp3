package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.PlaylistEntity
import com.example.data.RadioRequest
import com.example.data.RadioType
import com.example.playback.TrackItem
import kotlinx.coroutines.launch

sealed class ContextMenuItem {
    data class Track(
        val ratingKey: String,
        val title: String,
        val artist: String,
        val album: String,
        val key: String,
        val thumb: String,
        val duration: Long
    ) : ContextMenuItem() {
        fun toTrackItem() = TrackItem(
            ratingKey = ratingKey,
            title = title,
            artist = artist,
            album = album,
            key = key,
            thumb = thumb,
            duration = duration
        )
    }

    data class Album(
        val ratingKey: String,
        val title: String,
        val artist: String,
        val thumb: String
    ) : ContextMenuItem()

    data class Artist(
        val ratingKey: String,
        val name: String,
        val thumb: String
    ) : ContextMenuItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicContextMenu(
    item: ContextMenuItem,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val playlists by viewModel.playlists.collectAsState()
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val context = LocalContext.current
    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

    // Retrieve cover photo or thumb url
    val thumb = when (item) {
        is ContextMenuItem.Track -> item.thumb
        is ContextMenuItem.Album -> item.thumb
        is ContextMenuItem.Artist -> item.thumb
    }
    val imageUrl = if (thumb.isNotEmpty()) "$normalizedBaseUrl$thumb" else null

    val title = when (item) {
        is ContextMenuItem.Track -> item.title
        is ContextMenuItem.Album -> item.title
        is ContextMenuItem.Artist -> item.name
    }

    val subtitle = when (item) {
        is ContextMenuItem.Track -> "${item.artist} • ${item.album}"
        is ContextMenuItem.Album -> item.artist
        is ContextMenuItem.Artist -> "Artist"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F0F14),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header Info Card
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Card(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageUrl)
                                .addHeader("X-Plex-Token", token)
                                .crossfade(true)
                                .build(),
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF2E2E38)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (item) {
                                    is ContextMenuItem.Track -> Icons.Rounded.MusicNote
                                    is ContextMenuItem.Album -> Icons.Rounded.Album
                                    is ContextMenuItem.Artist -> Icons.Rounded.Person
                                },
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
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.6f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(bottom = 8.dp))

            // Options List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Play
                item {
                    ContextMenuOption(
                        icon = Icons.Rounded.PlayArrow,
                        label = "Play"
                    ) {
                        coroutineScope.launch {
                            when (item) {
                                is ContextMenuItem.Track -> {
                                    viewModel.playbackManager.playTrack(item.toTrackItem(), listOf(item.toTrackItem()))
                                }
                                is ContextMenuItem.Album -> {
                                    val tracks = viewModel.repository.getAlbumTracks(item.ratingKey)
                                    val mapped = tracks.mapNotNull { t ->
                                        val key = t.media?.firstOrNull()?.part?.firstOrNull()?.key ?: return@mapNotNull null
                                        TrackItem(t.ratingKey, t.title, t.grandparentTitle ?: t.parentTitle ?: "Unknown", t.parentTitle ?: "Unknown", key, t.thumb ?: "", t.duration ?: 0L)
                                    }
                                    if (mapped.isNotEmpty()) {
                                        viewModel.playbackManager.playQueue(mapped, 0)
                                    }
                                }
                                is ContextMenuItem.Artist -> {
                                    val albums = viewModel.repository.getArtistAlbums(item.ratingKey)
                                    val allTracks = mutableListOf<TrackItem>()
                                    albums.forEach { album ->
                                        val tracks = viewModel.repository.getAlbumTracks(album.ratingKey)
                                        tracks.forEach { t ->
                                            t.media?.firstOrNull()?.part?.firstOrNull()?.key?.let { key ->
                                                allTracks.add(TrackItem(t.ratingKey, t.title, t.grandparentTitle ?: t.parentTitle ?: "Unknown", t.parentTitle ?: "Unknown", key, t.thumb ?: "", t.duration ?: 0L))
                                            }
                                        }
                                    }
                                    if (allTracks.isNotEmpty()) {
                                        viewModel.playbackManager.playQueue(allTracks, 0)
                                    }
                                }
                            }
                            onDismiss()
                        }
                    }
                }

                // 2. Shuffle
                item {
                    ContextMenuOption(
                        icon = Icons.Rounded.Shuffle,
                        label = "Shuffle"
                    ) {
                        coroutineScope.launch {
                            val allTracks = when (item) {
                                is ContextMenuItem.Track -> listOf(item.toTrackItem())
                                is ContextMenuItem.Album -> {
                                    val tracks = viewModel.repository.getAlbumTracks(item.ratingKey)
                                    tracks.mapNotNull { t ->
                                        val key = t.media?.firstOrNull()?.part?.firstOrNull()?.key ?: return@mapNotNull null
                                        TrackItem(t.ratingKey, t.title, t.grandparentTitle ?: t.parentTitle ?: "Unknown", t.parentTitle ?: "Unknown", key, t.thumb ?: "", t.duration ?: 0L)
                                    }
                                }
                                is ContextMenuItem.Artist -> {
                                    val albums = viewModel.repository.getArtistAlbums(item.ratingKey)
                                    val list = mutableListOf<TrackItem>()
                                    albums.forEach { album ->
                                        val tracks = viewModel.repository.getAlbumTracks(album.ratingKey)
                                        tracks.forEach { t ->
                                            t.media?.firstOrNull()?.part?.firstOrNull()?.key?.let { key ->
                                                list.add(TrackItem(t.ratingKey, t.title, t.grandparentTitle ?: t.parentTitle ?: "Unknown", t.parentTitle ?: "Unknown", key, t.thumb ?: "", t.duration ?: 0L))
                                            }
                                        }
                                    }
                                    list
                                }
                            }
                            if (allTracks.isNotEmpty()) {
                                viewModel.playbackManager.playQueue(allTracks.shuffled(), 0)
                            }
                            onDismiss()
                        }
                    }
                }

                // 3. Radio
                item {
                    val label = when (item) {
                        is ContextMenuItem.Track -> "Play track radio"
                        is ContextMenuItem.Album -> "Play album radio"
                        is ContextMenuItem.Artist -> "Play artist radio"
                    }
                    ContextMenuOption(
                        icon = Icons.Rounded.Radio,
                        label = label
                    ) {
                        val request = when (item) {
                            is ContextMenuItem.Track -> RadioRequest(
                                type = RadioType.ARTIST_RADIO,
                                seedArtist = item.artist
                            )
                            is ContextMenuItem.Album -> RadioRequest(
                                type = RadioType.ALBUM_RADIO,
                                seedAlbum = item.title
                            )
                            is ContextMenuItem.Artist -> RadioRequest(
                                type = RadioType.ARTIST_RADIO,
                                seedArtist = item.name
                            )
                        }
                        viewModel.playSeededRadio(request)
                        onDismiss()
                    }
                }

                // 4. Play Next
                item {
                    ContextMenuOption(
                        icon = Icons.Rounded.QueuePlayNext,
                        label = "Play Next"
                    ) {
                        coroutineScope.launch {
                            when (item) {
                                is ContextMenuItem.Track -> {
                                    viewModel.playbackManager.playNext(item.toTrackItem())
                                }
                                is ContextMenuItem.Album -> {
                                    val tracks = viewModel.repository.getAlbumTracks(item.ratingKey)
                                    val mapped = tracks.mapNotNull { t ->
                                        val key = t.media?.firstOrNull()?.part?.firstOrNull()?.key ?: return@mapNotNull null
                                        TrackItem(t.ratingKey, t.title, t.grandparentTitle ?: t.parentTitle ?: "Unknown", t.parentTitle ?: "Unknown", key, t.thumb ?: "", t.duration ?: 0L)
                                    }
                                    viewModel.playbackManager.playTracksNext(mapped)
                                }
                                is ContextMenuItem.Artist -> {
                                    val albums = viewModel.repository.getArtistAlbums(item.ratingKey)
                                    val all = mutableListOf<TrackItem>()
                                    albums.forEach { album ->
                                        val tracks = viewModel.repository.getAlbumTracks(album.ratingKey)
                                        tracks.forEach { t ->
                                            t.media?.firstOrNull()?.part?.firstOrNull()?.key?.let { key ->
                                                all.add(TrackItem(t.ratingKey, t.title, t.grandparentTitle ?: t.parentTitle ?: "Unknown", t.parentTitle ?: "Unknown", key, t.thumb ?: "", t.duration ?: 0L))
                                            }
                                        }
                                    }
                                    viewModel.playbackManager.playTracksNext(all)
                                }
                            }
                            onDismiss()
                        }
                    }
                }

                // 5. Add to queue
                item {
                    ContextMenuOption(
                        icon = Icons.Rounded.PlaylistAdd,
                        label = "Add to queue"
                    ) {
                        coroutineScope.launch {
                            when (item) {
                                is ContextMenuItem.Track -> {
                                    viewModel.playbackManager.addToQueue(item.toTrackItem())
                                }
                                is ContextMenuItem.Album -> {
                                    val tracks = viewModel.repository.getAlbumTracks(item.ratingKey)
                                    val mapped = tracks.mapNotNull { t ->
                                        val key = t.media?.firstOrNull()?.part?.firstOrNull()?.key ?: return@mapNotNull null
                                        TrackItem(t.ratingKey, t.title, t.grandparentTitle ?: t.parentTitle ?: "Unknown", t.parentTitle ?: "Unknown", key, t.thumb ?: "", t.duration ?: 0L)
                                    }
                                    viewModel.playbackManager.addTracksToQueue(mapped)
                                }
                                is ContextMenuItem.Artist -> {
                                    val albums = viewModel.repository.getArtistAlbums(item.ratingKey)
                                    val all = mutableListOf<TrackItem>()
                                    albums.forEach { album ->
                                        val tracks = viewModel.repository.getAlbumTracks(album.ratingKey)
                                        tracks.forEach { t ->
                                            t.media?.firstOrNull()?.part?.firstOrNull()?.key?.let { key ->
                                                all.add(TrackItem(t.ratingKey, t.title, t.grandparentTitle ?: t.parentTitle ?: "Unknown", t.parentTitle ?: "Unknown", key, t.thumb ?: "", t.duration ?: 0L))
                                            }
                                        }
                                    }
                                    viewModel.playbackManager.addTracksToQueue(all)
                                }
                            }
                            onDismiss()
                        }
                    }
                }

                // 6. Add to playlist
                item {
                    ContextMenuOption(
                        icon = Icons.Rounded.Add,
                        label = "Add to playlist"
                    ) {
                        showPlaylistDialog = true
                    }
                }

                // 7. Go to Artist/Album
                when (item) {
                    is ContextMenuItem.Track -> {
                        item {
                            ContextMenuOption(
                                icon = Icons.Rounded.Album,
                                label = "Go to album"
                            ) {
                                coroutineScope.launch {
                                    // Plex track details usually have parents, but to be simple and accurate, we find the track metadata
                                    // Since Plex track lists have parentRatingKey, let's navigate to the album
                                    onNavigateToAlbum(item.ratingKey, item.album)
                                    onDismiss()
                                }
                            }
                        }
                    }
                    is ContextMenuItem.Album -> {
                        item {
                            ContextMenuOption(
                                icon = Icons.Rounded.Person,
                                label = "Go to artist"
                            ) {
                                coroutineScope.launch {
                                    onNavigateToArtist(item.ratingKey, item.artist)
                                    onDismiss()
                                }
                            }
                        }
                    }
                    is ContextMenuItem.Artist -> {
                        // Already on or can navigate to artist
                    }
                }

                // 8. Download
                item {
                    val label = when (item) {
                        is ContextMenuItem.Track -> "Download track"
                        is ContextMenuItem.Album -> "Download full album"
                        is ContextMenuItem.Artist -> "Download artist catalog"
                    }
                    ContextMenuOption(
                        icon = Icons.Rounded.Download,
                        label = label
                    ) {
                        when (item) {
                            is ContextMenuItem.Track -> viewModel.startDownload(item.toTrackItem())
                            is ContextMenuItem.Album -> viewModel.downloadAlbum(item.ratingKey)
                            is ContextMenuItem.Artist -> viewModel.downloadArtist(item.ratingKey)
                        }
                        onDismiss()
                    }
                }

                // 9. Build album/song/artist mix through the selected provider
                item {
                    val label = when (item) {
                        is ContextMenuItem.Track -> "Build song mix"
                        is ContextMenuItem.Album -> "Build album mix"
                        is ContextMenuItem.Artist -> "Build artist mix"
                    }
                    ContextMenuOption(
                        icon = Icons.Rounded.AutoAwesome,
                        label = label
                    ) {
                        val prompt = when (item) {
                            is ContextMenuItem.Track -> "Create an atmospheric acoustic or modern mix around '${item.title}' by ${item.artist}"
                            is ContextMenuItem.Album -> "Create a journey playlist inspired by the album '${item.title}'"
                            is ContextMenuItem.Artist -> "Generate a premium playlist of deep cuts and hits similar to ${item.name}"
                        }
                        viewModel.generateAiPlaylist(prompt)
                        onDismiss()
                    }
                }
            }
        }
    }

    // Dialog for choosing/creating playlist
    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Add to Playlist", color = Color.White) },
            containerColor = Color(0xFF1E1E24),
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Option to create a new playlist
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCreatePlaylistDialog = true
                                showPlaylistDialog = false
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Create New Playlist", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    // Existing playlists
                    if (playlists.isEmpty()) {
                        Text(
                            text = "No existing playlists. Tap above to create one.",
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 16.dp),
                            fontSize = 14.sp
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(playlists) { playlist ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            when (item) {
                                                is ContextMenuItem.Track -> {
                                                    viewModel.addTrackToPlaylist(playlist.id, item.toTrackItem())
                                                }
                                                is ContextMenuItem.Album -> {
                                                    viewModel.addAlbumToPlaylist(playlist.id, item.ratingKey)
                                                }
                                                is ContextMenuItem.Artist -> {
                                                    viewModel.addArtistToPlaylist(playlist.id, item.ratingKey)
                                                }
                                            }
                                            showPlaylistDialog = false
                                            onDismiss()
                                        }
                                        .padding(vertical = 12.dp)
                                ) {
                                    Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(playlist.name, color = Color.White)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }

    // Dialog for creating a new playlist
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist", color = Color.White) },
            containerColor = Color(0xFF1E1E24),
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name", color = Color.White.copy(alpha = 0.4f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName) { newId ->
                                when (item) {
                                    is ContextMenuItem.Track -> {
                                        viewModel.addTrackToPlaylist(newId.toInt(), item.toTrackItem())
                                    }
                                    is ContextMenuItem.Album -> {
                                        viewModel.addAlbumToPlaylist(newId.toInt(), item.ratingKey)
                                    }
                                    is ContextMenuItem.Artist -> {
                                        viewModel.addArtistToPlaylist(newId.toInt(), item.ratingKey)
                                    }
                                }
                            }
                            showCreatePlaylistDialog = false
                            onDismiss()
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }
}

@Composable
fun ContextMenuOption(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        )
    }
}
