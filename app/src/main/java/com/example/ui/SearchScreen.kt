package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.PlexMetadata

@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val downloadedKeys = remember(downloadedTracks) {
        downloadedTracks.filter { it.status == "completed" && !it.localPath.isNullOrBlank() }
            .map { it.ratingKey }
            .toSet()
    }
    var offlineOnly by remember { mutableStateOf(viewModel.repository.settings.offlineOnly) }

    var activeContextMenu by remember { mutableStateOf<ContextMenuItem?>(null) }

    // Real-time search trigger
    LaunchedEffect(searchQuery) {
        viewModel.search(searchQuery)
    }

    val artistsMatches = searchResults.filter { it.type == "artist" }
    val albumsMatches = searchResults.filter { it.type == "album" }
    val tracksMatches = searchResults.filter { it.type == "track" }
    val visibleTracksMatches = if (offlineOnly) tracksMatches.filter { it.ratingKey in downloadedKeys } else tracksMatches

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp)
    ) {
        Text(
            text = "Ask Your Library",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Search your indexed Plex music using natural language.",
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.55f)),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Clean Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search artists, albums, or tracks...", color = Color.White.copy(alpha = 0.4f)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = Color.White.copy(alpha = 0.4f)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Clear search",
                            tint = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF6366F1).copy(alpha = 0.5f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.05f),
                focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.4f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("search_text_input"),
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { searchQuery = "recently added" },
                label = { Text("Recently added") }
            )
            AssistChip(
                onClick = { searchQuery = "downloaded deep cuts" },
                label = { Text("Offline deep cuts") }
            )
            AssistChip(
                onClick = { searchQuery = "instrumental from the 2000s" },
                label = { Text("Instrumental 2000s") }
            )
        }

        if (searchQuery.isBlank()) {
            // Empty State
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isConfigured) "Find your music instantly on your server" else "Search your indexed music offline",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.4f))
                    )
                }
            }
        } else if (isLoading && searchResults.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF818CF8))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Searching your indexed Plex library…",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.55f))
                    )
                }
            }
        } else if (searchError != null && searchResults.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = searchError ?: "Search failed.",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFFBBF24))
                )
            }
        } else if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matches found for \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White.copy(alpha = 0.5f))
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.width(8.dp))
                Text("Offline only", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                Switch(checked = offlineOnly, onCheckedChange = {
                    offlineOnly = it
                    viewModel.repository.settings.offlineOnly = it
                })
            }
            // Search Results List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Matching Artists
                if (artistsMatches.isNotEmpty()) {
                    item {
                        SearchSectionHeader("Artists")
                    }
                    items(artistsMatches) { artist ->
                        SearchItemRow(
                            title = artist.title,
                            subtitle = "Artist",
                            thumb = artist.thumb,
                            isCircle = true,
                            baseUrl = viewModel.repository.settings.baseUrl,
                            token = viewModel.repository.settings.token,
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

                // 2. Matching Albums
                if (albumsMatches.isNotEmpty()) {
                    item {
                        SearchSectionHeader("Albums")
                    }
                    items(albumsMatches) { album ->
                        SearchItemRow(
                            title = album.title,
                            subtitle = album.parentTitle ?: "Unknown Artist",
                            thumb = album.thumb,
                            isCircle = false,
                            baseUrl = viewModel.repository.settings.baseUrl,
                            token = viewModel.repository.settings.token,
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

                // 3. Matching Tracks / Songs
                if (visibleTracksMatches.isNotEmpty()) {
                    item {
                        SearchSectionHeader("Songs")
                    }
                    items(visibleTracksMatches) { track ->
                        val key = track.media?.firstOrNull()?.part?.firstOrNull()?.key ?: ""
                        SearchItemRow(
                            title = track.title,
                            subtitle = "${track.grandparentTitle ?: "Unknown Artist"} • ${track.parentTitle ?: "Unknown Album"}",
                            thumb = track.thumb,
                            isCircle = false,
                            baseUrl = viewModel.repository.settings.baseUrl,
                            token = viewModel.repository.settings.token,
                            isDownloaded = track.ratingKey in downloadedKeys,
                            onMoreClick = {
                                activeContextMenu = ContextMenuItem.Track(
                                    ratingKey = track.ratingKey,
                                    title = track.title,
                                    artist = track.grandparentTitle ?: track.parentTitle ?: "Unknown Artist",
                                    album = track.parentTitle ?: "Unknown Album",
                                    key = key,
                                    thumb = track.thumb ?: "",
                                    duration = track.duration ?: 0L
                                )
                            }
                        ) {
                            viewModel.playTrackFromMetadata(track, tracksMatches)
                        }
                    }
                }
            }
        }
    }

    // Modal Context Menu Sheet Trigger
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
fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = Color(0xFF818CF8),
            letterSpacing = 0.5.sp
        ),
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
    )
}

@Composable
fun SearchItemRow(
    title: String,
    subtitle: String,
    thumb: String?,
    isCircle: Boolean,
    baseUrl: String,
    token: String,
    isDownloaded: Boolean = false,
    onMoreClick: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (!thumb.isNullOrEmpty()) "$normalizedBaseUrl$thumb" else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(if (isCircle) 26.dp else 12.dp)
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
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCircle) Icons.Rounded.Person else Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
                    Text(
                        text = if (isDownloaded) "$subtitle • Downloaded" else subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.6f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = { onMoreClick() },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More",
                tint = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}
