package com.example.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.*
import com.example.playback.TrackItem
import kotlinx.coroutines.launch

// -------------------------------------------------------------
// ARTIST MIX BUILDER SCREEN
// -------------------------------------------------------------
@Composable
fun ArtistMixBuilderScreen(
    viewModel: MusicViewModel,
    baseUrl: String,
    token: String,
    onBack: () -> Unit,
    onNavigateToCustomPlaylist: (String, String, String, String, List<TrackItem>, List<Long>) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var cachedTracks by remember { mutableStateOf<List<CachedTrack>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedArtists = remember { mutableStateListOf<String>() }
    var trackCount by remember { mutableFloatStateOf(50f) }

    LaunchedEffect(Unit) {
        cachedTracks = runCatching { viewModel.repository.getCachedTracksList() }.getOrDefault(emptyList())
    }

    // Extract unique artists and their representative thumbnail
    val artistsList = remember(cachedTracks) {
        cachedTracks
            .groupBy { it.artist }
            .map { (artistName, tracks) ->
                val representativeThumb = tracks.firstOrNull { it.thumb.isNotEmpty() }?.thumb ?: ""
                ArtistItem(name = artistName, thumb = representativeThumb, tracksCount = tracks.size)
            }
            .filter { it.name.isNotBlank() }
            .sortedByDescending { it.tracksCount }
    }

    val filteredArtists = remember(artistsList, searchQuery) {
        if (searchQuery.isBlank()) {
            artistsList
        } else {
            artistsList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .statusBarsPadding()
    ) {
        // Custom App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ARTIST MIX BUILDER",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = Color.White
                )
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search for artists...", color = Color.White.copy(alpha = 0.4f)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedContainerColor = Color.White.copy(alpha = 0.04f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.02f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true
        )

        // Banner Instructions
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Build your mix!",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Text(
                        text = "Select one or more artists below to build an offline-first premium playlist mix.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                    )
                }
            }
        }

        // Artist Grid
        Box(modifier = Modifier.weight(1f)) {
            if (filteredArtists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No artists found in your offline cache.", color = Color.White.copy(alpha = 0.4f))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredArtists) { artist ->
                        val isSelected = selectedArtists.contains(artist.name)
                        ArtistGridItem(
                            artist = artist,
                            normalizedBaseUrl = normalizedBaseUrl,
                            token = token,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    selectedArtists.remove(artist.name)
                                } else {
                                    selectedArtists.add(artist.name)
                                }
                            }
                        )
                    }
                }
            }

            // Floating Play Action Bar
            if (selectedArtists.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF151515),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Track Count: ${trackCount.toInt()}",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                        }
                        Slider(
                            value = trackCount,
                            onValueChange = { trackCount = it },
                            valueRange = 10f..100f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val matched = cachedTracks
                                        .filter { it.artist in selectedArtists }
                                        .shuffled()
                                        .take(trackCount.toInt())
                                        .map { it.toTrackItem() }
                                    if (matched.isNotEmpty()) {
                                        onBack()
                                        onNavigateToCustomPlaylist(
                                            "artist_mix",
                                            "artist_mix_${selectedArtists.joinToString("_").hashCode()}",
                                            "Artist Mix",
                                            "A custom blend of ${selectedArtists.joinToString(", ")}",
                                            matched,
                                            listOf(0xFF10B981, 0xFF059669)
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generate Artist Mix (${selectedArtists.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ArtistItem(val name: String, val thumb: String, val tracksCount: Int)

@Composable
fun ArtistGridItem(
    artist: ArtistItem,
    normalizedBaseUrl: String,
    token: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(artist.thumb, normalizedBaseUrl, token) {
        if (artist.thumb.isNotEmpty()) {
            authenticatedArtworkRequest(context, resolveArtworkUrl(normalizedBaseUrl, artist.thumb), token)
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF4F46E5), Color(0xFF8B5CF6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "${artist.tracksCount} tracks",
            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.4f)),
            textAlign = TextAlign.Center
        )
    }
}


// -------------------------------------------------------------
// ALBUM MIX BUILDER SCREEN
// -------------------------------------------------------------
@Composable
fun AlbumMixBuilderScreen(
    viewModel: MusicViewModel,
    baseUrl: String,
    token: String,
    onBack: () -> Unit,
    onNavigateToCustomPlaylist: (String, String, String, String, List<TrackItem>, List<Long>) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var cachedTracks by remember { mutableStateOf<List<CachedTrack>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedAlbums = remember { mutableStateListOf<String>() }
    var trackCount by remember { mutableFloatStateOf(50f) }

    LaunchedEffect(Unit) {
        cachedTracks = runCatching { viewModel.repository.getCachedTracksList() }.getOrDefault(emptyList())
    }

    // Extract unique albums and their representative cover
    val albumsList = remember(cachedTracks) {
        cachedTracks
            .groupBy { it.album }
            .map { (albumName, tracks) ->
                val firstTrack = tracks.firstOrNull()
                val representativeThumb = firstTrack?.thumb ?: ""
                val artistName = firstTrack?.artist ?: "Unknown Artist"
                AlbumItem(title = albumName, artist = artistName, thumb = representativeThumb, tracksCount = tracks.size)
            }
            .filter { it.title.isNotBlank() }
            .sortedBy { it.title }
    }

    val filteredAlbums = remember(albumsList, searchQuery) {
        if (searchQuery.isBlank()) {
            albumsList
        } else {
            albumsList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val tokenHeader = token

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .statusBarsPadding()
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ALBUM MIX BUILDER",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = Color.White
                )
            )
        }

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search for albums...", color = Color.White.copy(alpha = 0.4f)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedContainerColor = Color.White.copy(alpha = 0.04f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.02f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true
        )

        // Instruction Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Build album mixes!",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Text(
                        text = "Select albums below to generate a tailored playlist from those specific tracks.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                    )
                }
            }
        }

        // Album Grid
        Box(modifier = Modifier.weight(1f)) {
            if (filteredAlbums.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No cached albums found.", color = Color.White.copy(alpha = 0.4f))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredAlbums) { album ->
                        val isSelected = selectedAlbums.contains(album.title)
                        AlbumGridItem(
                            album = album,
                            normalizedBaseUrl = normalizedBaseUrl,
                            token = tokenHeader,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    selectedAlbums.remove(album.title)
                                } else {
                                    selectedAlbums.add(album.title)
                                }
                            }
                        )
                    }
                }
            }

            // Play Floating Action Bar
            if (selectedAlbums.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF151515),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Track Count: ${trackCount.toInt()}",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                        }
                        Slider(
                            value = trackCount,
                            onValueChange = { trackCount = it },
                            valueRange = 10f..100f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val matched = cachedTracks
                                        .filter { it.album in selectedAlbums }
                                        .shuffled()
                                        .take(trackCount.toInt())
                                        .map { it.toTrackItem() }
                                    if (matched.isNotEmpty()) {
                                        onBack()
                                        onNavigateToCustomPlaylist(
                                            "album_mix",
                                            "album_mix_${selectedAlbums.joinToString("_").hashCode()}",
                                            "Album Mix",
                                            "A custom blend of ${selectedAlbums.joinToString(", ")}",
                                            matched,
                                            listOf(0xFF4C0519, 0xFFF43F5E)
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generate Album Mix (${selectedAlbums.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class AlbumItem(val title: String, val artist: String, val thumb: String, val tracksCount: Int)

@Composable
fun AlbumGridItem(
    album: AlbumItem,
    normalizedBaseUrl: String,
    token: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(album.thumb, normalizedBaseUrl, token) {
        if (album.thumb.isNotEmpty()) {
            authenticatedArtworkRequest(context, resolveArtworkUrl(normalizedBaseUrl, album.thumb), token)
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF4F46E5), Color(0xFF06B6D4))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Album, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = album.artist,
            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


// -------------------------------------------------------------
// STYLE / MOOD / DECADE SELECTOR SCREEN
// -------------------------------------------------------------
@Composable
fun GenericRadioSelectorScreen(
    station: RecommendedStation,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onNavigateToCustomPlaylist: (String, String, String, String, List<TrackItem>, List<Long>) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var cachedTracks by remember { mutableStateOf<List<CachedTrack>>(emptyList()) }
    var itemsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var trackCount by remember { mutableFloatStateOf(40f) }

    LaunchedEffect(station.type) {
        cachedTracks = runCatching { viewModel.repository.getCachedTracksList() }.getOrDefault(emptyList())
        itemsList = when (station.type) {
            RadioType.STYLE_RADIO, RadioType.GENRE_RADIO, RadioType.MOOD_RADIO -> {
                val extracted = cachedTracks.flatMap { it.genres.split('|') }.map { it.trim() }.filter { it.isNotEmpty() }
                val defaults = listOf("Rock", "Pop", "Hip-Hop", "Electronic", "Jazz", "Classical", "Ambient", "Indie", "R&B", "Metal", "Soundtrack", "Folk", "Alternative", "Dance", "Soul", "Blues", "Punk", "Country", "Reggae", "Funk", "Disco")
                (extracted + defaults).distinct().sorted()
            }
            RadioType.DECADE_RADIO -> {
                val extracted = cachedTracks.mapNotNull { it.year }.map { ((it / 10) * 10).toString() + "s" }
                val defaults = listOf("2020s", "2010s", "2000s", "1990s", "1980s", "1970s", "1960s", "1950s")
                (extracted + defaults).distinct().sortedDescending()
            }
            RadioType.TIME_TRAVEL -> {
                val extracted = cachedTracks.mapNotNull { it.year }.map { ((it / 10) * 10).toString() + "s" }
                val defaults = listOf("2020s", "2010s", "2000s", "1990s", "1980s", "1970s", "1960s", "1950s")
                val decades = (extracted + defaults).distinct().sortedDescending()
                listOf("All Time (Earliest to Present)") + decades
            }
            else -> emptyList()
        }
    }

    val filteredItems = remember(itemsList, searchQuery) {
        if (searchQuery.isBlank()) {
            itemsList
        } else {
            itemsList.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    val gradientBrush = remember(station.gradientColors) {
        Brush.verticalGradient(station.gradientColors.map { Color(it) })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .statusBarsPadding()
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = station.title.uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = Color.White
                )
            )
        }

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search list...", color = Color.White.copy(alpha = 0.4f)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedContainerColor = Color.White.copy(alpha = 0.04f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.02f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true
        )

        // Track count slider card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Station Track Count: ${trackCount.toInt()}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                }
                Slider(
                    value = trackCount,
                    onValueChange = { trackCount = it },
                    valueRange = 10f..100f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )
            }
        }

        // List
        if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No items found matching filter.", color = Color.White.copy(alpha = 0.4f))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredItems) { name ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val req = when (station.type) {
                                    RadioType.DECADE_RADIO -> {
                                        val decadeVal = name.replace("s", "").toIntOrNull() ?: 1990
                                        RadioRequest(type = station.type, decadeStart = decadeVal, trackCount = trackCount.toInt())
                                    }
                                    RadioType.TIME_TRAVEL -> {
                                        val decadeVal = if (name.contains("All Time", ignoreCase = true)) null else name.replace("s", "").toIntOrNull()
                                        RadioRequest(type = station.type, decadeStart = decadeVal, trackCount = trackCount.toInt())
                                    }
                                    else -> {
                                        RadioRequest(type = station.type, genre = name, trackCount = trackCount.toInt())
                                    }
                                }
                                coroutineScope.launch {
                                    val tracks = viewModel.generateRadioTracks(req)
                                    if (tracks.isNotEmpty()) {
                                        onBack()
                                        onNavigateToCustomPlaylist(
                                            "radio",
                                            "radio_${station.type.name}_${name.hashCode()}_${System.currentTimeMillis()}",
                                            "$name Radio",
                                            "Continuous mix inspired by $name",
                                            tracks,
                                            station.gradientColors
                                        )
                                    } else {
                                        viewModel.postErrorMessage("No tracks found matching $name.")
                                    }
                                }
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(gradientBrush),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sensors,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                                Text(
                                    text = when (station.type) {
                                        RadioType.TIME_TRAVEL -> if (name.contains("All Time", ignoreCase = true)) "Chronological journey across all eras" else "Chronological journey starting from $name"
                                        RadioType.DECADE_RADIO -> "Continuous radio mix from $name"
                                        RadioType.GENRE_RADIO -> "Curated continuous mix of $name"
                                        RadioType.MOOD_RADIO -> "Atmospheric continuous vibe: $name"
                                        else -> "Start continuous radio session"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.5f))
                                )
                            }

                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
