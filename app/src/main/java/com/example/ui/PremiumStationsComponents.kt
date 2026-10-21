package com.example.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.*
import com.example.playback.TrackItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun StationMixBuilderInstructions(isAlbum: Boolean) {
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
                imageVector = if (isAlbum) Icons.Rounded.LibraryMusic else Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = stringResource(
                        if (isAlbum) R.string.station_album_mix_instructions_title
                        else R.string.station_artist_mix_instructions_title
                    ),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
                Text(
                    text = stringResource(
                        if (isAlbum) R.string.station_album_mix_instructions_body
                        else R.string.station_artist_mix_instructions_body
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                )
            }
        }
    }
}

@Composable
internal fun StationMixTrackLimitLabel(count: Int) {
    Text(
        text = pluralStringResource(R.plurals.station_mix_playlist_length, count, count),
        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
    )
}

@Composable
internal fun StationMixCreateActionLabel(isAlbum: Boolean, selectedCount: Int) {
    Text(
        text = pluralStringResource(
            if (isAlbum) R.plurals.station_album_mix_create_action else R.plurals.station_artist_mix_create_action,
            selectedCount,
            selectedCount
        ),
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
    )
}

@Composable
internal fun StationMixLibraryTrackCountLabel(count: Int) {
    Text(
        text = pluralStringResource(R.plurals.station_mix_library_track_count, count, count),
        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.4f))
    )
}

@Composable
internal fun PremiumStationsBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .testTag("premium_stations_back")
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.details_back_accessibility),
            tint = Color.White
        )
    }
}

internal fun stationRadioRequestForOption(type: RadioType, name: String, trackCount: Int): RadioRequest = when (type) {
    RadioType.DECADE_RADIO -> RadioRequest(
        type = type,
        decadeStart = name.replace("s", "").toIntOrNull() ?: 1990,
        trackCount = trackCount
    )
    RadioType.TIME_TRAVEL -> RadioRequest(
        type = type,
        decadeStart = if (name.contains("All Time", ignoreCase = true)) null else name.replace("s", "").toIntOrNull(),
        trackCount = trackCount
    )
    RadioType.MOOD_RADIO -> RadioRequest(type = type, mood = name, trackCount = trackCount)
    RadioType.STYLE_RADIO -> RadioRequest(type = type, style = name, trackCount = trackCount)
    RadioType.GENRE_RADIO -> RadioRequest(type = type, genre = name, trackCount = trackCount)
    else -> RadioRequest(type = type, trackCount = trackCount)
}

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
    var isLibraryLoading by remember { mutableStateOf(true) }
    var libraryLoadFailed by remember { mutableStateOf(false) }
    var libraryLoadAttempt by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedArtists = remember { mutableStateListOf<String>() }
    var trackCount by remember { mutableFloatStateOf(50f) }
    var stationRecipeSaved by remember { mutableStateOf(false) }

    LaunchedEffect(libraryLoadAttempt) {
        isLibraryLoading = true
        libraryLoadFailed = false
        try {
            cachedTracks = viewModel.repository.getCachedTracksList()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            libraryLoadFailed = true
        } finally {
            isLibraryLoading = false
        }
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
            PremiumStationsBackButton(onBack = onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.station_artist_mix_builder_title),
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
            placeholder = { Text(stringResource(R.string.station_search_artist_hint), color = Color.White.copy(alpha = 0.4f)) },
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

        StationMixBuilderInstructions(isAlbum = false)

        // Artist Grid
        Box(modifier = Modifier.weight(1f)) {
            if (isLibraryLoading || libraryLoadFailed || filteredArtists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LibraryPickerStatusView(
                        status = when {
                            isLibraryLoading -> LibraryPickerStatus.LOADING
                            libraryLoadFailed -> LibraryPickerStatus.LOAD_FAILED
                            artistsList.isEmpty() -> LibraryPickerStatus.LIBRARY_EMPTY
                            else -> LibraryPickerStatus.NO_FILTER_RESULTS
                        },
                        content = LibraryPickerContent.ARTISTS,
                        onRetry = { libraryLoadAttempt++ }
                    )
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
                                stationRecipeSaved = false
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
                            StationMixTrackLimitLabel(trackCount.toInt())
                        }
                        Slider(
                            value = trackCount,
                            onValueChange = { trackCount = it; stationRecipeSaved = false },
                            valueRange = 10f..100f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                val selectedArtistNames = selectedArtists.toList()
                                val request = RadioRequest(type = RadioType.ARTIST_RADIO, trackCount = trackCount.toInt())
                                viewModel.saveStationRecipe(
                                    SavedStationRecipe.fromRequest(
                                        name = context.getString(
                                            R.string.station_recipe_artist_mix_default,
                                            selectedArtistNames.joinToString(", ")
                                        ),
                                        request = request,
                                        seedValues = selectedArtistNames
                                    )
                                )
                                stationRecipeSaved = true
                            },
                            modifier = Modifier.fillMaxWidth().testTag("station_save_artist_recipe")
                        ) {
                            Text(
                                stringResource(
                                    if (stationRecipeSaved) R.string.discover_station_recipe_saved
                                    else R.string.discover_save_station_recipe
                                )
                            )
                        }
                        Button(
                            onClick = {
                                val selectedArtistNames = selectedArtists.toList()
                                coroutineScope.launch {
                                    val matched = cachedTracks
                                        .filter { it.artist in selectedArtistNames }
                                        .shuffled()
                                        .take(trackCount.toInt())
                                        .map { it.toTrackItem() }
                                    if (matched.isNotEmpty()) {
                                        onBack()
                                        onNavigateToCustomPlaylist(
                                            "artist_mix",
                                            "artist_mix_${selectedArtistNames.joinToString("_").hashCode()}",
                                            context.getString(R.string.station_artist_mix_playlist_title),
                                            context.getString(
                                                R.string.station_mix_playlist_description,
                                                selectedArtistNames.joinToString(", ")
                                            ),
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
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            StationMixCreateActionLabel(isAlbum = false, selectedCount = selectedArtists.size)
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
            .selectable(selected = isSelected, onClick = onClick, role = Role.Checkbox)
            .padding(4.dp)
            .testTag("station_artist_selection_${artist.name}"),
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
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
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

        StationMixLibraryTrackCountLabel(artist.tracksCount)
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
    var isLibraryLoading by remember { mutableStateOf(true) }
    var libraryLoadFailed by remember { mutableStateOf(false) }
    var libraryLoadAttempt by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedAlbums = remember { mutableStateListOf<String>() }
    var trackCount by remember { mutableFloatStateOf(50f) }
    var stationRecipeSaved by remember { mutableStateOf(false) }

    LaunchedEffect(libraryLoadAttempt) {
        isLibraryLoading = true
        libraryLoadFailed = false
        try {
            cachedTracks = viewModel.repository.getCachedTracksList()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            libraryLoadFailed = true
        } finally {
            isLibraryLoading = false
        }
    }

    // Extract unique albums and their representative cover
    val unknownArtist = stringResource(R.string.common_unknown_artist)
    val albumsList = remember(cachedTracks, unknownArtist) {
        cachedTracks
            .groupBy { it.album }
            .map { (albumName, tracks) ->
                val firstTrack = tracks.firstOrNull()
                val representativeThumb = firstTrack?.thumb ?: ""
                val artistName = firstTrack?.artist?.takeIf(String::isNotBlank) ?: unknownArtist
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
    val context = LocalContext.current

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
            PremiumStationsBackButton(onBack = onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.station_album_mix_builder_title),
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
            placeholder = { Text(stringResource(R.string.station_search_album_hint), color = Color.White.copy(alpha = 0.4f)) },
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

        StationMixBuilderInstructions(isAlbum = true)

        // Album Grid
        Box(modifier = Modifier.weight(1f)) {
            if (isLibraryLoading || libraryLoadFailed || filteredAlbums.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LibraryPickerStatusView(
                        status = when {
                            isLibraryLoading -> LibraryPickerStatus.LOADING
                            libraryLoadFailed -> LibraryPickerStatus.LOAD_FAILED
                            albumsList.isEmpty() -> LibraryPickerStatus.LIBRARY_EMPTY
                            else -> LibraryPickerStatus.NO_FILTER_RESULTS
                        },
                        content = LibraryPickerContent.ALBUMS,
                        onRetry = { libraryLoadAttempt++ }
                    )
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
                                stationRecipeSaved = false
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
                            StationMixTrackLimitLabel(trackCount.toInt())
                        }
                        Slider(
                            value = trackCount,
                            onValueChange = { trackCount = it; stationRecipeSaved = false },
                            valueRange = 10f..100f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                val selectedAlbumNames = selectedAlbums.toList()
                                val request = RadioRequest(type = RadioType.ALBUM_RADIO, trackCount = trackCount.toInt())
                                viewModel.saveStationRecipe(
                                    SavedStationRecipe.fromRequest(
                                        name = context.getString(
                                            R.string.station_recipe_album_mix_default,
                                            selectedAlbumNames.joinToString(", ")
                                        ),
                                        request = request,
                                        seedValues = selectedAlbumNames
                                    )
                                )
                                stationRecipeSaved = true
                            },
                            modifier = Modifier.fillMaxWidth().testTag("station_save_album_recipe")
                        ) {
                            Text(
                                stringResource(
                                    if (stationRecipeSaved) R.string.discover_station_recipe_saved
                                    else R.string.discover_save_station_recipe
                                )
                            )
                        }
                        Button(
                            onClick = {
                                val selectedAlbumNames = selectedAlbums.toList()
                                coroutineScope.launch {
                                    val matched = cachedTracks
                                        .filter { it.album in selectedAlbumNames }
                                        .shuffled()
                                        .take(trackCount.toInt())
                                        .map { it.toTrackItem() }
                                    if (matched.isNotEmpty()) {
                                        onBack()
                                        onNavigateToCustomPlaylist(
                                            "album_mix",
                                            "album_mix_${selectedAlbumNames.joinToString("_").hashCode()}",
                                            context.getString(R.string.station_album_mix_playlist_title),
                                            context.getString(
                                                R.string.station_mix_playlist_description,
                                                selectedAlbumNames.joinToString(", ")
                                            ),
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
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            StationMixCreateActionLabel(isAlbum = true, selectedCount = selectedAlbums.size)
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
            .selectable(selected = isSelected, onClick = onClick, role = Role.Checkbox)
            .padding(4.dp)
            .testTag("station_album_selection_${album.title}"),
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
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
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
            text = localizedArtistPresentation(album.artist),
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
    val context = LocalContext.current
    var cachedTracks by remember { mutableStateOf<List<CachedTrack>>(emptyList()) }
    var itemsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLibraryLoading by remember { mutableStateOf(true) }
    var libraryLoadFailed by remember { mutableStateOf(false) }
    var libraryLoadAttempt by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var trackCount by remember { mutableFloatStateOf(40f) }
    var stationRecipeSavedName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(station.type, libraryLoadAttempt) {
        isLibraryLoading = true
        libraryLoadFailed = false
        try {
            cachedTracks = viewModel.repository.getCachedTracksList()
            itemsList = when (station.type) {
            RadioType.STYLE_RADIO, RadioType.GENRE_RADIO, RadioType.MOOD_RADIO ->
                stationRadioFacetChoices(station.type, cachedTracks)
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
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            cachedTracks = emptyList()
            itemsList = emptyList()
            libraryLoadFailed = true
        } finally {
            isLibraryLoading = false
        }
    }

    val filteredItems = remember(itemsList, searchQuery) {
        if (searchQuery.isBlank()) {
            itemsList
        } else {
            itemsList.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }
    val searchHint = stringResource(stationSearchHintString(station.type))

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
            PremiumStationsBackButton(onBack = onBack)
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
            placeholder = { Text(searchHint, color = Color.White.copy(alpha = 0.4f)) },
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
                        text = stringResource(R.string.station_track_count_label),
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                    )
                    Text(
                        text = stringResource(R.string.station_track_count, trackCount.toInt()),
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                }
                Slider(
                    value = trackCount,
                    onValueChange = { trackCount = it; stationRecipeSavedName = null },
                    valueRange = 10f..100f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )
                stationRecipeSavedName?.let { savedName ->
                    Text(
                        text = stringResource(R.string.discover_station_recipe_named_saved, savedName),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp).testTag("station_recipe_saved_confirmation")
                    )
                }
            }
        }

        // List
        if (isLibraryLoading || libraryLoadFailed || filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LibraryPickerStatusView(
                    status = when {
                        isLibraryLoading -> LibraryPickerStatus.LOADING
                        libraryLoadFailed -> LibraryPickerStatus.LOAD_FAILED
                        cachedTracks.isEmpty() -> LibraryPickerStatus.LIBRARY_EMPTY
                        itemsList.isEmpty() -> LibraryPickerStatus.LIBRARY_EMPTY
                        else -> LibraryPickerStatus.NO_FILTER_RESULTS
                    },
                    content = LibraryPickerContent.STATION_OPTIONS,
                    onRetry = { libraryLoadAttempt++ },
                    messageOverride = if (itemsList.isEmpty() && cachedTracks.isNotEmpty()) {
                        stationRadioEmptyChoicesString(station.type)
                    } else null
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredItems) { name ->
                    val request = stationRadioRequestForOption(station.type, name, trackCount.toInt())
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClickLabel = stringResource(R.string.station_play_radio_action, name),
                                role = Role.Button
                            ) {
                                coroutineScope.launch {
                                    val tracks = viewModel.generateRadioTracks(request, useDiscoveryTuning = true)
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
                                        viewModel.postErrorMessage(
                                            context.getString(radioSelectionNoMatchesString(), name)
                                        )
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

                            IconButton(
                                onClick = {
                                    val stationName = "$name Radio"
                                    viewModel.saveStationRecipe(SavedStationRecipe.fromRequest(stationName, request))
                                    stationRecipeSavedName = stationName
                                },
                                modifier = Modifier.size(48.dp).testTag("station_save_recipe_${name.hashCode()}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.discover_save_station_named, name),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
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
