package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.PlexMetadata
import com.example.data.RadioRequest
import com.example.data.RadioType
import com.example.data.albumNavigationKey
import com.example.data.effectiveAlbumArtist
import com.example.data.sortTrackItemsInPlaybackOrder
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
        val duration: Long,
        val albumRatingKey: String? = null,
        val albumArtist: String? = null,
        val artistRatingKey: String? = null,
    ) : ContextMenuItem() {
        fun toTrackItem() = TrackItem(
            ratingKey = ratingKey,
            title = title,
            artist = artist,
            album = album,
            key = key,
            thumb = thumb,
            duration = duration,
            albumRatingKey = albumRatingKey,
            albumArtist = albumArtist,
            artistRatingKey = artistRatingKey,
        )
    }

    data class Album(
        val ratingKey: String,
        val title: String,
        val artist: String,
        val thumb: String,
        val tracks: List<TrackItem> = emptyList()
    ) : ContextMenuItem()

    data class Artist(
        val ratingKey: String,
        val name: String,
        val thumb: String
    ) : ContextMenuItem()
}

internal fun PlexMetadata.toAlbumContextTrackItem(
    album: ContextMenuItem.Album,
    unknownArtist: String,
    unknownAlbum: String
): TrackItem? {
    val key = media?.firstOrNull()?.part?.firstOrNull()?.key ?: return null
    return TrackItem(
        ratingKey = ratingKey,
        title = title,
        artist = grandparentTitle?.takeIf(String::isNotBlank)
            ?: albumArtist?.takeIf(String::isNotBlank)
            ?: album.artist.takeIf(String::isNotBlank)
            ?: unknownArtist,
        album = parentTitle?.takeIf(String::isNotBlank)
            ?: album.title.takeIf(String::isNotBlank)
            ?: unknownAlbum,
        key = key,
        thumb = thumb ?: album.thumb,
        duration = duration ?: 0L,
        albumRatingKey = parentRatingKey ?: album.ratingKey,
        albumArtist = albumArtist,
        artistRatingKey = grandparentRatingKey,
        index = index ?: 0,
        discNumber = parentIndex?.takeIf { it > 0 } ?: 1
    )
}

internal fun PlexMetadata.toArtistQueueTrackItem(
    artistName: String,
    unknownArtist: String,
    unknownAlbum: String
): TrackItem? {
    val key = media?.firstOrNull()?.part?.firstOrNull()?.key ?: return null
    return TrackItem(
        ratingKey = ratingKey,
        title = title,
        artist = grandparentTitle?.takeIf(String::isNotBlank)
            ?: albumArtist?.takeIf(String::isNotBlank)
            ?: artistName.takeIf(String::isNotBlank)
            ?: unknownArtist,
        album = parentTitle?.takeIf(String::isNotBlank) ?: unknownAlbum,
        key = key,
        thumb = thumb.orEmpty(),
        duration = duration ?: 0L,
        albumRatingKey = parentRatingKey,
        albumArtist = albumArtist,
        artistRatingKey = grandparentRatingKey,
        index = index ?: 0,
        discNumber = parentIndex?.takeIf { it > 0 } ?: 1
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicContextMenu(
    item: ContextMenuItem,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    onNavigateToAlbumWithTracks: (String, String, List<TrackItem>) -> Unit = { id, name, _ ->
        onNavigateToAlbum(id, name)
    },
    onRemoveFromQueue: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val playlists by viewModel.playlists.collectAsState()
    var showPlaylistDialog by remember { mutableStateOf(value = false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var isCreatingPlaylist by remember { mutableStateOf(false) }
    var createPlaylistError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token
    val unknownArtistLabel = stringResource(R.string.common_unknown_artist)
    val unknownAlbumLabel = stringResource(R.string.common_unknown_album)

    // Retrieve cover photo or thumb url
    val thumb = when (item) {
        is ContextMenuItem.Track -> item.thumb
        is ContextMenuItem.Album -> item.thumb
        is ContextMenuItem.Artist -> item.thumb
    }
    val imageUrl = if (thumb.isNotEmpty()) resolveArtworkUrl(baseUrl, thumb) else null

    suspend fun albumTrackItems(album: ContextMenuItem.Album): List<TrackItem> {
        val tracks = if (album.tracks.isNotEmpty()) {
            album.tracks
        } else {
            viewModel.repository.getAlbumTracks(album.ratingKey).mapNotNull { track ->
                track.toAlbumContextTrackItem(album, unknownArtistLabel, unknownAlbumLabel)
            }
        }
        return sortTrackItemsInPlaybackOrder(tracks)
    }

    val title = when (item) {
        is ContextMenuItem.Track -> item.title
        is ContextMenuItem.Album -> item.title
        is ContextMenuItem.Artist -> item.name
    }

    val subtitle = when (item) {
        is ContextMenuItem.Track -> localizedArtistAlbumSubtitle(item.artist, item.album)
        is ContextMenuItem.Album -> localizedArtistPresentation(item.artist)
        is ContextMenuItem.Artist -> stringResource(R.string.music_context_artist)
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
                            model = authenticatedArtworkRequest(context, imageUrl, token),
                            contentDescription = null,
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
                        label = stringResource(R.string.common_action_play)
                    ) {
                        coroutineScope.launch {
                            when (item) {
                                is ContextMenuItem.Track -> {
                                    viewModel.playbackManager.playTrack(item.toTrackItem(), listOf(item.toTrackItem()))
                                }
                                is ContextMenuItem.Album -> {
                                    val mapped = albumTrackItems(item)
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
                                            t.toArtistQueueTrackItem(item.name, unknownArtistLabel, unknownAlbumLabel)?.let(allTracks::add)
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
                        label = stringResource(R.string.music_context_shuffle)
                    ) {
                        coroutineScope.launch {
                            val allTracks = when (item) {
                                is ContextMenuItem.Track -> listOf(item.toTrackItem())
                                is ContextMenuItem.Album -> albumTrackItems(item)
                                is ContextMenuItem.Artist -> {
                                    val albums = viewModel.repository.getArtistAlbums(item.ratingKey)
                                    val list = mutableListOf<TrackItem>()
                                    albums.forEach { album ->
                                        val tracks = viewModel.repository.getAlbumTracks(album.ratingKey)
                                        tracks.forEach { t ->
                                            t.toArtistQueueTrackItem(item.name, unknownArtistLabel, unknownAlbumLabel)?.let(list::add)
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
                        is ContextMenuItem.Track -> stringResource(R.string.music_context_track_radio)
                        is ContextMenuItem.Album -> stringResource(R.string.music_context_album_radio)
                        is ContextMenuItem.Artist -> stringResource(R.string.music_context_artist_radio)
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
                        label = stringResource(R.string.music_context_play_next)
                    ) {
                        coroutineScope.launch {
                            when (item) {
                                is ContextMenuItem.Track -> {
                                    viewModel.playbackManager.playNext(item.toTrackItem())
                                }
                                is ContextMenuItem.Album -> {
                                    val mapped = albumTrackItems(item)
                                    viewModel.playbackManager.playTracksNext(mapped)
                                }
                                is ContextMenuItem.Artist -> {
                                    val albums = viewModel.repository.getArtistAlbums(item.ratingKey)
                                    val all = mutableListOf<TrackItem>()
                                    albums.forEach { album ->
                                        val tracks = viewModel.repository.getAlbumTracks(album.ratingKey)
                                        tracks.forEach { t ->
                                            t.toArtistQueueTrackItem(item.name, unknownArtistLabel, unknownAlbumLabel)?.let(all::add)
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
                        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        label = stringResource(R.string.music_context_add_to_queue)
                    ) {
                        coroutineScope.launch {
                            when (item) {
                                is ContextMenuItem.Track -> {
                                    viewModel.playbackManager.addToQueue(item.toTrackItem())
                                }
                                is ContextMenuItem.Album -> {
                                    val mapped = albumTrackItems(item)
                                    viewModel.playbackManager.addTracksToQueue(mapped)
                                }
                                is ContextMenuItem.Artist -> {
                                    val albums = viewModel.repository.getArtistAlbums(item.ratingKey)
                                    val all = mutableListOf<TrackItem>()
                                    albums.forEach { album ->
                                        val tracks = viewModel.repository.getAlbumTracks(album.ratingKey)
                                        tracks.forEach { t ->
                                            t.toArtistQueueTrackItem(item.name, unknownArtistLabel, unknownAlbumLabel)?.let(all::add)
                                        }
                                    }
                                    viewModel.playbackManager.addTracksToQueue(all)
                                }
                            }
                            onDismiss()
                        }
                    }
                }

                if (onRemoveFromQueue != null) {
                    item {
                        ContextMenuOption(
                            icon = Icons.Rounded.DeleteOutline,
                            label = stringResource(R.string.player_remove_from_queue)
                        ) {
                            onRemoveFromQueue()
                            onDismiss()
                        }
                    }
                }

                // 6. Add to playlist
                item {
                    ContextMenuOption(
                        icon = Icons.Rounded.Add,
                        label = stringResource(R.string.library_add_to_playlist)
                    ) {
                        showPlaylistDialog = true
                    }
                }

                // 7. Go to Artist/Album
                when (item) {
                    is ContextMenuItem.Track -> {
                        item {
                            ContextMenuOption(
                                icon = Icons.Rounded.Person,
                                label = stringResource(R.string.music_context_go_to_artist)
                            ) {
                                coroutineScope.launch {
                                    onNavigateToArtist(
                                        item.artistRatingKey ?: com.example.data.artistNavigationKey(item.artist),
                                        item.artist
                                    )
                                    onDismiss()
                                }
                            }
                        }
                        item {
                            ContextMenuOption(
                                icon = Icons.Rounded.Album,
                                label = stringResource(R.string.music_context_go_to_album)
                            ) {
                                coroutineScope.launch {
                                    // Plex track details usually have parents, but to be simple and accurate, we find the track metadata
                                    // Since Plex track lists have parentRatingKey, let's navigate to the album
                                    onNavigateToAlbumWithTracks(
                                        item.albumRatingKey ?: albumNavigationKey(effectiveAlbumArtist(item.albumArtist, item.artist), item.album),
                                        item.album,
                                        listOf(item.toTrackItem())
                                    )
                                    onDismiss()
                                }
                            }
                        }
                    }
                    is ContextMenuItem.Album -> {
                        item {
                            ContextMenuOption(
                                icon = Icons.Rounded.Person,
                                label = stringResource(R.string.music_context_go_to_artist)
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
                        is ContextMenuItem.Track -> stringResource(R.string.music_context_download_track)
                        is ContextMenuItem.Album -> stringResource(R.string.music_context_download_album)
                        is ContextMenuItem.Artist -> stringResource(R.string.music_context_download_artist)
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
                        is ContextMenuItem.Track -> stringResource(R.string.music_context_build_song_mix)
                        is ContextMenuItem.Album -> stringResource(R.string.music_context_build_album_mix)
                        is ContextMenuItem.Artist -> stringResource(R.string.music_context_build_artist_mix)
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
            title = { Text(stringResource(R.string.library_add_to_playlist), color = Color.White) },
            containerColor = Color(0xFF1E1E24),
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Option to create a new playlist
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                showCreatePlaylistDialog = true
                                showPlaylistDialog = false
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            stringResource(R.string.library_create_new_playlist),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    // Existing playlists
                    if (playlists.isEmpty()) {
                        Text(
                            text = stringResource(R.string.library_context_empty_playlists),
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
                                        .clickable(role = Role.Button) {
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
                                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
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
                    Text(stringResource(R.string.library_cancel_playlist_creation), color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }

    // Dialog for creating a new playlist
    if (showCreatePlaylistDialog) {
        LibraryCreatePlaylistDialog(
            name = newPlaylistName,
            isCreating = isCreatingPlaylist,
            showError = createPlaylistError,
            onNameChange = {
                newPlaylistName = it
                createPlaylistError = false
            },
            onDismiss = {
                if (isCreatingPlaylist) return@LibraryCreatePlaylistDialog
                showCreatePlaylistDialog = false
                newPlaylistName = ""
                createPlaylistError = false
            },
            onCreate = { name ->
                isCreatingPlaylist = true
                createPlaylistError = false
                viewModel.createPlaylist(
                    name = name,
                    onFailure = {
                        isCreatingPlaylist = false
                        createPlaylistError = true
                    },
                    onCreated = { newId ->
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
                        showCreatePlaylistDialog = false
                        newPlaylistName = ""
                        isCreatingPlaylist = false
                        onDismiss()
                    }
                )
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
            .clickable(role = Role.Button, onClick = onClick)
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
