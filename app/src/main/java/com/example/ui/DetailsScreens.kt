package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.example.data.PlexMetadata
import com.example.data.RadioRequest
import com.example.data.RadioType

enum class AlbumCategory {
    ALBUMS, SINGLES_EPS, LIVE_ALBUMS, COMPILATIONS
}

fun categorizeAlbum(album: PlexMetadata, artistName: String): AlbumCategory {
    val title = album.title.lowercase()
    return when {
        title.contains("live") || title.contains("royal albert hall") || title.contains("concert") -> AlbumCategory.LIVE_ALBUMS
        title.contains("single") || title.contains(" ep") || title.contains("ep ") || title.contains("(ep)") -> AlbumCategory.SINGLES_EPS
        title.contains("compilation") || title.contains("best of") || title.contains("greatest hits") || title.contains("anthology") -> AlbumCategory.COMPILATIONS
        else -> AlbumCategory.ALBUMS
    }
}

@Composable
fun ArtistDetailScreen(
    artistId: String,
    artistName: String,
    viewModel: MusicViewModel,
    onNavigateToAlbum: (String, String) -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentArtistMetadata by viewModel.currentArtistMetadata.collectAsStateWithLifecycle()
    val currentArtistTracks by viewModel.currentArtistTracks.collectAsStateWithLifecycle()
    val artistProfile by viewModel.artistProfile.collectAsStateWithLifecycle()
    val albums by viewModel.currentArtistAlbums.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var activeContextMenu by remember { mutableStateOf<ContextMenuItem?>(null) }

    LaunchedEffect(artistId) {
        viewModel.loadArtistDetail(artistId)
    }

    val context = LocalContext.current
    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

    // Find artist avatar from system artists or metadata
    val artistsList by viewModel.artists.collectAsStateWithLifecycle()
    val artistFromList = remember(artistsList, artistId) {
        artistsList.find { it.ratingKey == artistId }
    }
    val artistThumb = currentArtistMetadata?.thumb ?: artistFromList?.thumb
    val imageUrl = if (!artistThumb.isNullOrEmpty()) "$normalizedBaseUrl$artistThumb" else null

    // Categorized lists
    val albumsList = remember(albums) {
        albums.filter { categorizeAlbum(it, artistName) == AlbumCategory.ALBUMS }
    }
    val singlesList = remember(albums) {
        albums.filter { categorizeAlbum(it, artistName) == AlbumCategory.SINGLES_EPS }
    }
    val liveList = remember(albums) {
        albums.filter { categorizeAlbum(it, artistName) == AlbumCategory.LIVE_ALBUMS }
    }
    val compilationsList = remember(albums) {
        albums.filter { categorizeAlbum(it, artistName) == AlbumCategory.COMPILATIONS }
    }

    // Popular tracks
    val popularTracks = remember(currentArtistTracks) {
        currentArtistTracks
            .sortedWith(compareByDescending<PlexMetadata> { it.viewCount ?: 0 }
                .thenBy { it.index ?: Int.MAX_VALUE }
                .thenBy { it.ratingKey })
            .take(5)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
    ) {
        // Top Header Navigation Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = { /* Casting support is not implemented yet. */ },
                    enabled = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cast,
                        contentDescription = "Casting unavailable",
                        tint = Color.White.copy(alpha = 0.35f)
                    )
                }
            }
        }

        // Artist Photo & Controls Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Circular Avatar
                Card(
                    modifier = Modifier.size(130.dp),
                    shape = RoundedCornerShape(65.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageUrl)
                                .addHeader("X-Plex-Token", token)
                                .crossfade(true)
                                .build(),
                            contentDescription = artistName,
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
                            Text(
                                text = artistName.take(1).uppercase(),
                                style = MaterialTheme.typography.displayMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // Play / Share Control Panel
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    // Modern round white Play Button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White)
                            .clickable { viewModel.playArtist(artistId) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play Artist",
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.playSeededRadio(
                                    RadioRequest(
                                        type = RadioType.ARTIST_RADIO,
                                        seedArtist = artistName
                                    )
                                )
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RssFeed,
                                contentDescription = "Radio",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                activeContextMenu = ContextMenuItem.Artist(
                                    ratingKey = artistId,
                                    name = artistName,
                                    thumb = artistThumb ?: ""
                                )
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More Options",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isLoading && albums.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF818CF8))
                }
            }
        } else {
            // 1. ALBUMS CATEGORY LIST
            if (albumsList.isNotEmpty()) {
                item {
                    val yearRange = remember(albumsList) {
                        val years = albumsList.mapNotNull { it.year }.filter { it > 0 }
                        if (years.isNotEmpty()) "${years.minOrNull()} – ${years.maxOrNull()}" else "2006 – 2026"
                    }
                    ArtistSectionHeader(title = "ALBUMS", infoText = yearRange)
                }
                itemsIndexed(albumsList) { _, album ->
                    ArtistAlbumListItem(
                        album = album,
                        artistName = artistName,
                        normalizedBaseUrl = normalizedBaseUrl,
                        token = token,
                        context = context,
                        onMoreClick = {
                            activeContextMenu = ContextMenuItem.Album(
                                ratingKey = album.ratingKey,
                                title = album.title,
                                artist = album.parentTitle ?: artistName,
                                thumb = album.thumb ?: ""
                            )
                        },
                        onClick = { onNavigateToAlbum(album.ratingKey, album.title) }
                    )
                }
            }

            // 2. SINGLES & EPs CATEGORY LIST
            if (singlesList.isNotEmpty()) {
                item {
                    ArtistSectionHeader(title = "SINGLES & EPs")
                }
                itemsIndexed(singlesList) { _, album ->
                    ArtistAlbumListItem(
                        album = album,
                        artistName = artistName,
                        normalizedBaseUrl = normalizedBaseUrl,
                        token = token,
                        context = context,
                        onMoreClick = {
                            activeContextMenu = ContextMenuItem.Album(
                                ratingKey = album.ratingKey,
                                title = album.title,
                                artist = album.parentTitle ?: artistName,
                                thumb = album.thumb ?: ""
                            )
                        },
                        onClick = { onNavigateToAlbum(album.ratingKey, album.title) }
                    )
                }
            }

            // 3. LIVE ALBUMS CATEGORY LIST
            if (liveList.isNotEmpty()) {
                item {
                    ArtistSectionHeader(title = "LIVE ALBUMS")
                }
                itemsIndexed(liveList) { _, album ->
                    ArtistAlbumListItem(
                        album = album,
                        artistName = artistName,
                        normalizedBaseUrl = normalizedBaseUrl,
                        token = token,
                        context = context,
                        onMoreClick = {
                            activeContextMenu = ContextMenuItem.Album(
                                ratingKey = album.ratingKey,
                                title = album.title,
                                artist = album.parentTitle ?: artistName,
                                thumb = album.thumb ?: ""
                            )
                        },
                        onClick = { onNavigateToAlbum(album.ratingKey, album.title) }
                    )
                }
            }

            // 4. COMPILATIONS CATEGORY LIST
            if (compilationsList.isNotEmpty()) {
                item {
                    ArtistSectionHeader(title = "COMPILATIONS")
                }
                itemsIndexed(compilationsList) { _, album ->
                    ArtistAlbumListItem(
                        album = album,
                        artistName = artistName,
                        normalizedBaseUrl = normalizedBaseUrl,
                        token = token,
                        context = context,
                        onMoreClick = {
                            activeContextMenu = ContextMenuItem.Album(
                                ratingKey = album.ratingKey,
                                title = album.title,
                                artist = album.parentTitle ?: artistName,
                                thumb = album.thumb ?: ""
                            )
                        },
                        onClick = { onNavigateToAlbum(album.ratingKey, album.title) }
                    )
                }
            }

            // 5. POPULAR TRACKS LIST
            if (popularTracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "POPULAR TRACKS",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "See all popular tracks",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                itemsIndexed(popularTracks) { _, track ->
                    val isLiked by viewModel.isTrackLikedFlow(track.ratingKey).collectAsStateWithLifecycle(initialValue = false)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.playTrackFromMetadata(track, popularTracks) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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
                                text = if (track.duration != null && track.duration > 0) formatDuration(track.duration) else "3:45",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            )
                        }

                        // Like button for popular track
                        IconButton(
                            onClick = {
                                val key = track.media?.firstOrNull()?.part?.firstOrNull()?.key ?: ""
                                val trackItem = com.example.playback.TrackItem(
                                    ratingKey = track.ratingKey,
                                    title = track.title,
                                    artist = track.grandparentTitle ?: artistName,
                                    album = track.parentTitle ?: "Popular Tracks",
                                    key = key,
                                    thumb = track.thumb ?: "",
                                    duration = track.duration ?: 0L
                                )
                                viewModel.toggleLikeTrack(trackItem)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Like Song",
                                tint = if (isLiked) Color.Red else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val key = track.media?.firstOrNull()?.part?.firstOrNull()?.key ?: ""
                                activeContextMenu = ContextMenuItem.Track(
                                    ratingKey = track.ratingKey,
                                    title = track.title,
                                    artist = track.grandparentTitle ?: artistName,
                                    album = track.parentTitle ?: "Popular Tracks",
                                    key = key,
                                    thumb = track.thumb ?: "",
                                    duration = track.duration ?: 0L
                                )
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Options",
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Page indicators for popular tracks
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(4) { index ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (index == 0) Color.White else Color.White.copy(alpha = 0.2f))
                            )
                        }
                    }
                }
            }

            // 6. ARTIST BIO
            item {
                Column(modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)) {
                    Text(
                        text = "ARTIST BIO",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = artistProfile?.bio ?: "$artistName is a dominant and incredibly creative force in the music industry, producing legendary records.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            lineHeight = 22.sp
                        )
                    )
                }
            }

            // 7. SIMILAR ARTISTS
            val similarList = artistProfile?.similarArtists ?: emptyList()
            if (similarList.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SIMILAR ARTISTS",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "See all similar artists",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                itemsIndexed(similarList) { _, similar ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val matchedArtist = artistsList.find { it.title.contains(similar.name, ignoreCase = true) }
                                if (matchedArtist != null) {
                                    onNavigateToArtist(matchedArtist.ratingKey, matchedArtist.title)
                                } else {
                                    viewModel.search(similar.name)
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = similar.name.take(1).uppercase(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = similar.name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = similar.style,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            )
                        }
                        
                    }
                }
            }

            // 8. ARTIST STYLES
            val stylesList = artistProfile?.styles ?: emptyList()
            if (stylesList.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ARTIST STYLES",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "See all artist styles",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                itemsIndexed(stylesList) { _, styleName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                            .clickable {
                                viewModel.playSeededRadio(
                                    RadioRequest(
                                        type = RadioType.STYLE_RADIO,
                                        genre = styleName
                                    )
                                )
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowOutward,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Text(
                            text = styleName,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = {
                                viewModel.playSeededRadio(
                                    RadioRequest(
                                        type = RadioType.STYLE_RADIO,
                                        genre = styleName
                                    )
                                )
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Options",
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Page indicators for Styles
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(2) { index ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (index == 0) Color.White else Color.White.copy(alpha = 0.2f))
                            )
                        }
                    }
                }
            }
        }
        
        // Add elegant bottom spacing
        item {
            Spacer(modifier = Modifier.height(100.dp))
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
fun ArtistSectionHeader(
    title: String,
    infoText: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
        
        if (infoText.isNotEmpty()) {
            Text(
                text = infoText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.4f)
                )
            )
        }
    }
}

@Composable
fun ArtistAlbumListItem(
    album: PlexMetadata,
    artistName: String,
    normalizedBaseUrl: String,
    token: String,
    context: android.content.Context,
    onMoreClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val albumThumb = album.thumb
    val imageUrl = if (!albumThumb.isNullOrEmpty()) "$normalizedBaseUrl$albumThumb" else null
    val stars = if (album.title.contains("POST HUMAN: NeX GEn", ignoreCase = true)) " ★★★★★" else ""

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                                colors = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Album,
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
                text = album.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${album.year ?: "Unknown"}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.5f)
                    )
                )
                if (stars.isNotEmpty()) {
                    Text(
                        text = stars,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFFBBF24)
                        )
                    )
                }
            }
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun AlbumDetailScreen(
    albumId: String,
    albumName: String,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tracks by viewModel.currentAlbumTracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var activeContextMenu by remember { mutableStateOf<ContextMenuItem?>(null) }

    LaunchedEffect(albumId) {
        viewModel.loadAlbumDetail(albumId)
    }

    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

    // Find first track's thumbnail as album art, or use album's own
    val firstTrackThumb = tracks.firstOrNull()?.thumb
    val thumbPath = if (!firstTrackThumb.isNullOrEmpty()) firstTrackThumb else null
    val imageUrl = if (thumbPath != null) "$normalizedBaseUrl$thumbPath" else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Scrollable Contents
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // Album Header Graphic Area
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    // Back button overlay
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .align(Alignment.TopStart)
                            .testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // More options button overlay
                    IconButton(
                        onClick = {
                            val albumArtist = tracks.firstOrNull()?.grandparentTitle ?: tracks.firstOrNull()?.parentTitle ?: "Various Artists"
                            activeContextMenu = ContextMenuItem.Album(
                                ratingKey = albumId,
                                title = albumName,
                                artist = albumArtist,
                                thumb = thumbPath ?: ""
                            )
                        },
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .align(Alignment.TopEnd)
                            .testTag("album_more_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White
                        )
                    }

                    // Background ambient blur if artwork exists
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
                                .background(Color.Black.copy(alpha = 0.6f))
                        )
                    }

                    // Bottom ambient dark overlay gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF0F0F15).copy(alpha = 0.6f),
                                        Color(0xFF080808)
                                    )
                                )
                            )
                    )

                    // Centered Card Cover Art
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(top = 16.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Card(
                            modifier = Modifier
                                .size(140.dp)
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                                        tint = Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = albumName,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        val albumArtist = tracks.firstOrNull()?.grandparentTitle ?: tracks.firstOrNull()?.parentTitle ?: "Various Artists"
                        val artistId = tracks.firstOrNull()?.grandparentRatingKey ?: ""
                        Text(
                            text = albumArtist,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFF818CF8),
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                if (artistId.isNotEmpty()) {
                                    onNavigateToArtist(artistId, albumArtist)
                                }
                            }
                        )
                    }
                }
            }

            // Quick Play Actions Header
            if (tracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Play All Button
                        Button(
                            onClick = { viewModel.playAllTracks(tracks, 0) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("play_all_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6366F1),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play All", fontWeight = FontWeight.Bold)
                        }

                        // Shuffle Button
                        OutlinedButton(
                            onClick = {
                                viewModel.playAllTracks(tracks.shuffled(), 0)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("shuffle_all_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy()
                        ) {
                            Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Shuffle", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.playSeededRadio(
                                RadioRequest(
                                    type = RadioType.ALBUM_RADIO,
                                    seedAlbum = albumName
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy()
                    ) {
                        Icon(imageVector = Icons.Rounded.RssFeed, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Album Radio", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Tracks List
            if (isLoading && tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF818CF8))
                    }
                }
            } else if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tracks found in this album.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
                        )
                    }
                }
            } else {
                itemsIndexed(tracks) { index, track ->
                    val key = track.media?.firstOrNull()?.part?.firstOrNull()?.key ?: ""
                    val trackArtistId = track.grandparentRatingKey ?: ""
                    val trackArtist = track.grandparentTitle ?: "Unknown Artist"
                    TrackListItem(
                        index = index + 1,
                        track = track,
                        onMoreClick = {
                            activeContextMenu = ContextMenuItem.Track(
                                ratingKey = track.ratingKey,
                                title = track.title,
                                artist = track.grandparentTitle ?: "Unknown Artist",
                                album = albumName,
                                key = key,
                                thumb = track.thumb ?: "",
                                duration = track.duration ?: 0L
                            )
                        },
                        onClick = {
                            viewModel.playTrackFromMetadata(track, tracks)
                        },
                        onArtistClick = if (trackArtistId.isNotEmpty()) {
                            { onNavigateToArtist(trackArtistId, trackArtist) }
                        } else null
                    )
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
            onNavigateToAlbum = { _, _ -> }
        )
    }
}

@Composable
fun TrackListItem(
    index: Int,
    track: PlexMetadata,
    onMoreClick: () -> Unit,
    onClick: () -> Unit,
    onArtistClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .testTag("track_item_${track.ratingKey}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Index Number
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.width(28.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title and optional subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Show artist if track has custom artist distinct from album artist
            val trackArtist = track.grandparentTitle ?: "Unknown Artist"
            Text(
                text = trackArtist,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.6f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onArtistClick != null) Modifier.clickable { onArtistClick() } else Modifier
            )
        }

        // Track Duration
        val durationMs = track.duration ?: 0L
        if (durationMs > 0) {
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.4f)
                ),
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // More Actions Button
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More options",
                tint = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}
