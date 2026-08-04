package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import com.example.data.PlexMetadata
import com.example.data.albumNavigationKey
import com.example.playback.TrackItem

enum class ViewMode { Grid, List }
enum class SortOption { Name, RecentlyAdded }

@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    onNavigateToPlaylist: (Int, String) -> Unit,
    onNavigateToCustomPlaylist: (String, String, String, String, List<TrackItem>, List<Long>) -> Unit = { _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val selectedSectionId by viewModel.selectedSectionId.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val downloadedAlbums = remember(downloadedTracks) {
        downloadedTracks.filter { it.status == "completed" && !it.localPath.isNullOrBlank() }
            .map { it.album.lowercase() }
            .toSet()
    }
    val downloadedArtists = remember(downloadedTracks) {
        downloadedTracks
            .filter { it.status == "completed" && !it.localPath.isNullOrBlank() }
            .map { it.artist.lowercase() }
            .toSet()
    }
    val cachedTracks by viewModel.cachedTracks.collectAsStateWithLifecycle()
    val currentTrack by viewModel.playbackManager.currentTrack.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var offlineOnly by remember { mutableStateOf(viewModel.repository.settings.offlineOnly) }
    
    // View modes
    var artistViewMode by remember { mutableStateOf(ViewMode.Grid) }
    var albumViewMode by remember { mutableStateOf(ViewMode.Grid) }
    
    // Sorting
    var artistSort by remember { mutableStateOf(SortOption.Name) }
    var albumSort by remember { mutableStateOf(SortOption.Name) }
    var songSort by remember { mutableStateOf(SortOption.Name) }
    var playlistSort by remember { mutableStateOf(SortOption.Name) }
    
    val tabs = listOf("Artists", "Albums", "Songs", "Playlists")

    var activeContextMenu by remember { mutableStateOf<ContextMenuItem?>(null) }

    LaunchedEffect(selectedSectionId) {
        if (selectedSectionId.isNotEmpty()) {
            viewModel.loadArtists()
            viewModel.loadAlbums()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp)
    ) {
        Text(
            text = "Your Library",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (selectedSectionId.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No music library selected. Choose a library in Settings.",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White.copy(alpha = 0.5f))
                )
            }
            return
        }

        // Custom M3 Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            divider = {
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            },
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { 
                        Text(
                            text = title, 
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp
                        ) 
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (offlineOnly) "Showing downloaded ${tabs[selectedTab].lowercase()}" else "Showing Plex ${tabs[selectedTab].lowercase()}",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // View Mode Toggle (Grid/List)
                if (selectedTab == 0 || selectedTab == 1) {
                    IconButton(
                        onClick = {
                            if (selectedTab == 0) artistViewMode = if (artistViewMode == ViewMode.Grid) ViewMode.List else ViewMode.Grid
                            else albumViewMode = if (albumViewMode == ViewMode.Grid) ViewMode.List else ViewMode.Grid
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if ((selectedTab == 0 && artistViewMode == ViewMode.Grid) || (selectedTab == 1 && albumViewMode == ViewMode.Grid)) Icons.Rounded.List else Icons.Rounded.GridView,
                            contentDescription = "Toggle view mode",
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }

                // Sorting (Simplified toggle for now: Name <-> Recent)
                IconButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> artistSort = if (artistSort == SortOption.Name) SortOption.RecentlyAdded else SortOption.Name
                            1 -> albumSort = if (albumSort == SortOption.Name) SortOption.RecentlyAdded else SortOption.Name
                            2 -> songSort = if (songSort == SortOption.Name) SortOption.RecentlyAdded else SortOption.Name
                            3 -> playlistSort = if (playlistSort == SortOption.Name) SortOption.RecentlyAdded else SortOption.Name
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sort,
                        contentDescription = "Sort",
                        tint = Color.White
                    )
                }

                Spacer(Modifier.width(8.dp))

                FilterChip(
                    selected = offlineOnly,
                    onClick = {
                        offlineOnly = !offlineOnly
                        viewModel.repository.settings.offlineOnly = offlineOnly
                    },
                    label = { Text("Offline") },
                    leadingIcon = if (offlineOnly) {
                        { Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        if (isLoading && artists.isEmpty() && albums.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> { // Artists
                        val visibleArtists = if (offlineOnly) artists.filter { it.title.lowercase() in downloadedArtists } else artists
                        val sortedArtists = remember(visibleArtists, artistSort) {
                            when (artistSort) {
                                SortOption.Name -> visibleArtists.sortedBy { it.title.lowercase() }
                                SortOption.RecentlyAdded -> visibleArtists.sortedByDescending { it.addedAt ?: 0L }
                            }
                        }
                        
                        if (sortedArtists.isEmpty()) {
                            EmptyLibraryState("No artists found in this library.")
                        } else if (artistViewMode == ViewMode.Grid) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(sortedArtists) { artist ->
                                    ArtistGridItem(
                                        artist = artist, 
                                        baseUrl = viewModel.repository.settings.baseUrl, 
                                        token = viewModel.repository.settings.token,
                                        isDownloaded = artist.title.lowercase() in downloadedArtists,
                                        onMoreClick = {
                                            activeContextMenu = ContextMenuItem.Artist(
                                                ratingKey = artist.ratingKey,
                                                name = artist.title,
                                                thumb = artist.thumb ?: ""
                                            )
                                        }
                                    ) {
                                        onNavigateToArtist(artist.ratingKey, artist.title)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(sortedArtists) { artist ->
                                    // Simple List Item for Artist
                                    Row(modifier = Modifier.fillMaxWidth().clickable { onNavigateToArtist(artist.ratingKey, artist.title) }.padding(8.dp)) {
                                        Text(artist.title, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                    1 -> { // Albums
                        val visibleAlbums = if (offlineOnly) albums.filter { it.title.lowercase() in downloadedAlbums } else albums
                        val sortedAlbums = remember(visibleAlbums, albumSort) {
                            when (albumSort) {
                                SortOption.Name -> visibleAlbums.sortedBy { it.title.lowercase() }
                                SortOption.RecentlyAdded -> visibleAlbums.sortedByDescending { it.addedAt ?: 0L }
                            }
                        }
                        if (sortedAlbums.isEmpty()) {
                            EmptyLibraryState("No albums found in this library.")
                        } else if (albumViewMode == ViewMode.Grid) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(sortedAlbums) { album ->
                                    AlbumGridItem(
                                        album = album, 
                                        baseUrl = viewModel.repository.settings.baseUrl, 
                                        token = viewModel.repository.settings.token,
                                        isDownloaded = album.title.lowercase() in downloadedAlbums,
                                        onMoreClick = {
                                            activeContextMenu = ContextMenuItem.Album(
                                                ratingKey = album.ratingKey,
                                                title = album.title,
                                                artist = album.parentTitle ?: "Unknown Artist",
                                                thumb = album.thumb ?: ""
                                            )
                                        }
                                    ) {
                                        onNavigateToAlbum(album.ratingKey, album.title)
                                    }
                                }
                            }
                        } else {
                             LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(sortedAlbums) { album ->
                                    // Simple List Item for Album
                                    Row(modifier = Modifier.fillMaxWidth().clickable { onNavigateToAlbum(album.ratingKey, album.title) }.padding(8.dp)) {
                                        Text(album.title, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                    2 -> { // Songs
                        val visibleTracks = if (offlineOnly) cachedTracks.filter { it.key.isNotEmpty() } else cachedTracks
                        val sortedTracks = remember(visibleTracks, songSort) {
                            when (songSort) {
                                SortOption.Name -> visibleTracks.sortedBy { it.title.lowercase() }
                                SortOption.RecentlyAdded -> visibleTracks.sortedByDescending { it.addedAt ?: 0L }
                            }
                        }
                        if (sortedTracks.isEmpty()) {
                            EmptyLibraryState("No songs found in this library.")
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(sortedTracks) { track ->
                                    TrackRow(
                                        track = track,
                                        baseUrl = viewModel.repository.settings.baseUrl,
                                        token = viewModel.repository.settings.token,
                                        onMoreClick = {
                                            activeContextMenu = ContextMenuItem.Track(
                                                ratingKey = track.ratingKey,
                                                title = track.title,
                                                artist = track.artist,
                                                album = track.album,
                                                key = track.key,
                                                thumb = track.thumb,
                                                duration = track.duration,
                                                albumRatingKey = albumNavigationKey(track.artist, track.album)
                                            )
                                        },
                                        onClick = { viewModel.playTrackFromCache(track) },
                                        isPlaying = currentTrack?.ratingKey == track.ratingKey
                                    )
                                }
                            }
                        }
                    }
                    3 -> { // Playlists Dashboard (controlled AI/local intent + lists)
                        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
                        val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()
                        val aiError by viewModel.aiError.collectAsStateWithLifecycle()
                        
                        var playlistPrompt by remember { mutableStateOf("") }
                        var playlistTrackCount by remember { mutableFloatStateOf(30f) }
                        var showCreateDialog by remember { mutableStateOf(false) }
                        var manualPlaylistName by remember { mutableStateOf("") }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Smart playlist generator; the selected provider owns interpretation.
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = "Smart playlist assistant",
                                                tint = Color(0xFF818CF8),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Smart Playlist Generator",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            )
                                        }

                                        Text(
                                            text = "Describe a vibe, genre, or mood. The selected provider will interpret it, then the local library engine chooses matching Plex tracks.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color.White.copy(alpha = 0.6f)
                                            ),
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )

                                        OutlinedTextField(
                                            value = playlistPrompt,
                                            onValueChange = { playlistPrompt = it },
                                            placeholder = { Text("e.g. Chill synthwave vibes for night driving", color = Color.White.copy(alpha = 0.3f)) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF6366F1).copy(alpha = 0.5f),
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.05f),
                                                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                                unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("ai_playlist_prompt_input"),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            enabled = !aiLoading
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Track Count: ${playlistTrackCount.toInt()}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }

                                        Slider(
                                            value = playlistTrackCount,
                                            onValueChange = { playlistTrackCount = it },
                                            valueRange = 10f..100f,
                                            steps = 8,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color(0xFF818CF8),
                                                activeTrackColor = Color(0xFF6366F1),
                                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        if (aiLoading) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = Color(0xFF6366F1),
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    "Interpreting your request and ranking local Plex tracks...",
                                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f))
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    if (playlistPrompt.isNotBlank()) {
                                                        viewModel.generateAiPlaylist(playlistPrompt, playlistTrackCount.toInt())
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("ai_playlist_generate_button"),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF6366F1)
                                                ),
                                                enabled = playlistPrompt.isNotBlank()
                                            ) {
                                                Text("Generate Smart Playlist", fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        aiError?.let { err ->
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = err,
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            // Saved Playlists List Header
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Saved Playlists (${playlists.size})",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )

                                    TextButton(
                                        onClick = { showCreateDialog = true },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF818CF8))
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("New Playlist")
                                    }
                                }
                            }

                            if (playlists.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No playlists created yet. Tap '+' to create one manually or generate a smart playlist.",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color.White.copy(alpha = 0.4f),
                                                textAlign = TextAlign.Center
                                            )
                                        )
                                    }
                                }
                            } else {
                                items(playlists) { playlist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onNavigateToPlaylist(playlist.id, playlist.name) }
                                            .background(Color.White.copy(alpha = 0.02f))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(Color(0xFF3B82F6), Color(0xFF6366F1))
                                                    ),
                                                    RoundedCornerShape(10.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.QueueMusic,
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = playlist.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            )
                                            Text(
                                                text = "Local Playlist",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color.White.copy(alpha = 0.5f)
                                                )
                                            )
                                        }

                                        IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Delete Playlist",
                                                tint = Color.White.copy(alpha = 0.3f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Create Manual Playlist Dialog
                        if (showCreateDialog) {
                            AlertDialog(
                                onDismissRequest = { showCreateDialog = false },
                                title = { Text("New Playlist", color = Color.White) },
                                containerColor = Color(0xFF1E1E24),
                                text = {
                                    OutlinedTextField(
                                        value = manualPlaylistName,
                                        onValueChange = { manualPlaylistName = it },
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
                                            if (manualPlaylistName.isNotBlank()) {
                                                viewModel.createPlaylist(manualPlaylistName)
                                                manualPlaylistName = ""
                                                showCreateDialog = false
                                            }
                                        },
                                        enabled = manualPlaylistName.isNotBlank()
                                    ) {
                                        Text("Create")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCreateDialog = false }) {
                                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Context Menu Trigger
    activeContextMenu?.let { item ->
        MusicContextMenu(
            item = item,
            viewModel = viewModel,
            onDismiss = { activeContextMenu = null },
            onNavigateToArtist = onNavigateToArtist,
            onNavigateToAlbum = onNavigateToAlbum
        )
    }
}

@Composable
fun ArtistGridItem(
    artist: PlexMetadata,
    baseUrl: String,
    token: String,
    isDownloaded: Boolean = false,
    onMoreClick: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (!artist.thumb.isNullOrEmpty()) resolveArtworkUrl(baseUrl, artist.thumb) else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("artist_item_${artist.ratingKey}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                // Circular portrait for Artists
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(60.dp) // Circular
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = authenticatedArtworkRequest(context, imageUrl, token),
                            contentDescription = artist.title,
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
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                }

                // More Overlay
                IconButton(
                    onClick = { onMoreClick() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More options",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (isDownloaded) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = "Downloaded artist", modifier = Modifier.size(13.dp), tint = Color.White)
                            Text("Local", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }
            }

            Text(
                text = artist.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun EmptyLibraryState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(color = Color.White.copy(alpha = 0.5f))
            )
        }
    }
}
