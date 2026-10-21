package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.CompanionConnectionStatus
import com.example.data.PlexMetadata
import com.example.data.isNonEmptyRegularFile
import com.example.data.locallyAvailableDownloadRatingKeys

@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    onNavigateToResults: (String, List<PlexMetadata>) -> Unit,
    onNavigateToSettings: (String) -> Unit,
    onStartPairing: () -> Unit = {},
    initialQuery: String = "",
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
    val companionConfigured by viewModel.companionConfigured.collectAsStateWithLifecycle()
    val companionConnectionStatus by viewModel.companionConnectionStatus.collectAsStateWithLifecycle()
    val cachedCount by viewModel.cachedCount.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearchLoading.collectAsStateWithLifecycle()
    val currentTrack by viewModel.playbackManager.currentTrack.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val downloadedKeys = remember(downloadedTracks) {
        locallyAvailableDownloadRatingKeys(downloadedTracks, ::isNonEmptyRegularFile)
    }
    val baseUrl by viewModel.baseUrlFlow.collectAsStateWithLifecycle()
    val token by viewModel.tokenFlow.collectAsStateWithLifecycle()
    var offlineOnly by remember { mutableStateOf(viewModel.repository.settings.offlineOnly) }
    var activeContextMenu by remember { mutableStateOf<ContextMenuItem?>(null) }
    val focusManager = LocalFocusManager.current
    val navigateToResults: (String, List<PlexMetadata>) -> Unit = { category, items ->
        focusManager.clearFocus()
        onNavigateToResults(category, items)
    }
    val showFirstRunQueryGuidance = searchQuery.isNotBlank() &&
        !companionConfigured &&
        !isConfigured &&
        cachedCount == 0 &&
        searchResults.isEmpty() &&
        searchError == null
    val compactBlankSearchForIme = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // Real-time search trigger
    LaunchedEffect(searchQuery) {
        viewModel.search(searchQuery)
    }

    val artistsMatches = searchResults.filter { it.type == "artist" }.ifEmpty {
        searchResults.groupBy(::searchGroupingArtist).filterKeys { !it.isNullOrBlank() }.map { (name, songs) ->
            PlexMetadata("spotcore-artist:$name", title = name!!, type = "artist", thumb = songs.firstOrNull()?.thumb)
        }
    }
    val albumsMatches = searchResults.filter { it.type == "album" }.ifEmpty {
        searchResults.groupBy { searchGroupingArtist(it) to it.parentTitle }
            .filterKeys { it.first != null && it.second != null }
            .map { (keys, songs) ->
                val (artist, album) = keys
                PlexMetadata(com.example.data.albumNavigationKey(artist!!, album!!), title = album, type = "album", thumb = songs.first().thumb, parentTitle = artist)
            }
    }
    val tracksMatches = searchResults.filter { it.type == "track" }
    val visibleTracksMatches = if (offlineOnly) tracksMatches.filter { it.ratingKey in downloadedKeys } else tracksMatches

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SearchHeader(
            query = if (showFirstRunQueryGuidance) "" else searchQuery,
            downloadedOnly = offlineOnly,
            onDownloadedOnlyChange = {
                offlineOnly = it
                viewModel.repository.settings.offlineOnly = it
            }
        )

        SearchQueryField(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it }
        )

        if (searchQuery.isNotBlank() && !isSearching && searchError == null && searchResults.isNotEmpty()) {
            SearchResultQuickLinks(
                artists = artistsMatches,
                albums = albumsMatches,
                songs = visibleTracksMatches,
                onNavigateToResults = navigateToResults
            )
        }

        if (searchQuery.isBlank()) {
            SearchBlankQueryState(
                modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
                compactForIme = compactBlankSearchForIme,
                isConfigured = isConfigured,
                hasSavedLibrary = cachedCount > 0,
                companionConfigured = companionConfigured,
                connectionStatus = companionConnectionStatus,
                onOpenConnectionSettings = { onNavigateToSettings(searchQuery) },
                onStartPairing = onStartPairing
            )
        } else if (showFirstRunQueryGuidance) {
            Box(
                modifier = Modifier.weight(1f).imePadding(),
                contentAlignment = Alignment.Center
            ) {
                SearchEmptyStateHint(
                    isConfigured = isConfigured,
                    hasSavedLibrary = cachedCount > 0,
                    companionConfigured = companionConfigured,
                    connectionStatus = companionConnectionStatus,
                    onOpenConnectionSettings = { onNavigateToSettings(searchQuery) },
                    onStartPairing = onStartPairing
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f).imePadding()) {
                if (isSearching && searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.search_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (searchError != null && searchResults.isEmpty()) {
                    SearchUnavailableState(
                        message = searchError,
                        onRetry = { viewModel.search(searchQuery) },
                        onOpenConnectionSettings = { onNavigateToSettings(searchQuery) },
                        showPairingAction = !companionConfigured && !isConfigured && cachedCount == 0,
                        onStartPairing = onStartPairing
                    )
                } else if (searchResults.isEmpty()) {
                    SearchNoResultsState(
                        query = searchQuery,
                        onClearQuery = { searchQuery = "" }
                    )
                } else {
                    // Search Results List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("search_results_list"),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        if (shouldShowSearchOfflineNotice(companionConfigured, companionConnectionStatus)) {
                            item(key = "search_offline_connection_notice") {
                                SearchOfflineResultsNotice()
                            }
                        }

                        if (offlineOnly && tracksMatches.isNotEmpty() && visibleTracksMatches.isEmpty()) {
                            item {
                                SearchDownloadedOnlyEmptyState(
                                    query = searchQuery,
                                    onShowAllSongs = {
                                        offlineOnly = false
                                        viewModel.repository.settings.offlineOnly = false
                                    }
                                )
                            }
                        }

                        // 1. Matching Artists
                        if (artistsMatches.isNotEmpty()) {
                            item {
                                SearchSectionHeader(stringResource(R.string.search_section_artists), artistsMatches, navigateToResults)
                            }
                            items(artistsMatches.take(3)) { artist ->
                                SearchItemRow(
                                    title = artist.title,
                                    subtitle = stringResource(R.string.search_item_artist),
                                    thumb = artist.thumb,
                                    isCircle = true,
                                    baseUrl = baseUrl,
                                    token = token,
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
                                SearchSectionHeader(stringResource(R.string.search_section_albums), albumsMatches, navigateToResults)
                            }
                            items(albumsMatches.take(3)) { album ->
                                val unknownArtist = localizedArtistPresentation(album.parentTitle)
                                SearchItemRow(
                                    title = album.title,
                                    subtitle = album.parentTitle ?: unknownArtist,
                                    thumb = album.thumb,
                                    isCircle = false,
                                    baseUrl = baseUrl,
                                    token = token,
                                    onMoreClick = {
                                        activeContextMenu = ContextMenuItem.Album(
                                            ratingKey = album.ratingKey,
                                            title = album.title,
                                            artist = album.parentTitle ?: unknownArtist,
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
                                SearchSectionHeader(stringResource(R.string.search_section_songs), visibleTracksMatches, navigateToResults)
                            }
                            items(visibleTracksMatches.take(3)) { track ->
                                val key = track.media?.firstOrNull()?.part?.firstOrNull()?.key ?: ""
                                val unknownArtist = stringResource(R.string.common_unknown_artist)
                                val unknownAlbum = stringResource(R.string.common_unknown_album)
                                SearchItemRow(
                                    title = track.title,
                                    subtitle = localizedArtistAlbumSubtitle(
                                        track.grandparentTitle?.takeIf(String::isNotBlank) ?: track.albumArtist,
                                        track.parentTitle
                                    ),
                                    thumb = track.thumb,
                                    isCircle = false,
                                    baseUrl = baseUrl,
                                    token = token,
                                    isDownloaded = track.ratingKey in downloadedKeys,
                                    isPlaying = currentTrack?.ratingKey == track.ratingKey,
                                    onMoreClick = {
                                        activeContextMenu = ContextMenuItem.Track(
                                            ratingKey = track.ratingKey,
                                            title = track.title,
                                            artist = track.grandparentTitle?.takeIf(String::isNotBlank)
                                                ?: track.albumArtist?.takeIf(String::isNotBlank)
                                                ?: unknownArtist,
                                            album = track.parentTitle?.takeIf(String::isNotBlank) ?: unknownAlbum,
                                            key = key,
                                            thumb = track.thumb ?: "",
                                            duration = track.duration ?: 0L,
                                            albumRatingKey = track.parentRatingKey,
                                            albumArtist = track.albumArtist,
                                            artistRatingKey = track.grandparentRatingKey
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
@OptIn(ExperimentalLayoutApi::class)
internal fun SearchResultQuickLinks(
    artists: List<PlexMetadata>,
    albums: List<PlexMetadata>,
    songs: List<PlexMetadata>,
    onNavigateToResults: (String, List<PlexMetadata>) -> Unit
) {
    val artistsTitle = stringResource(R.string.search_section_artists)
    val albumsTitle = stringResource(R.string.search_section_albums)
    val songsTitle = stringResource(R.string.search_section_songs)

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .testTag("search_results_quick_links"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (artists.isNotEmpty()) {
            AssistChip(
                onClick = { onNavigateToResults(artistsTitle, artists) },
                label = {
                    Text(
                        stringResource(R.string.search_quick_link_count, artistsTitle, artists.size),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag("search_results_jump_artists")
            )
        }
        if (albums.isNotEmpty()) {
            AssistChip(
                onClick = { onNavigateToResults(albumsTitle, albums) },
                label = {
                    Text(
                        stringResource(R.string.search_quick_link_count, albumsTitle, albums.size),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag("search_results_jump_albums")
            )
        }
        if (songs.isNotEmpty()) {
            AssistChip(
                onClick = { onNavigateToResults(songsTitle, songs) },
                label = {
                    Text(
                        stringResource(R.string.search_quick_link_count, songsTitle, songs.size),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag("search_results_jump_songs")
            )
        }
    }
}

@Composable
internal fun SearchBlankQueryState(
    modifier: Modifier = Modifier,
    compactForIme: Boolean,
    isConfigured: Boolean,
    hasSavedLibrary: Boolean,
    companionConfigured: Boolean,
    connectionStatus: CompanionConnectionStatus,
    onOpenConnectionSettings: () -> Unit,
    onStartPairing: () -> Unit
) {
    Box(
        modifier = modifier.verticalScroll(rememberScrollState()),
        contentAlignment = if (compactForIme) Alignment.TopCenter else Alignment.Center
    ) {
        Column(
            modifier = if (compactForIme) Modifier.padding(top = 12.dp) else Modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!compactForIme) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            SearchEmptyStateHint(
                isConfigured = isConfigured,
                hasSavedLibrary = hasSavedLibrary,
                companionConfigured = companionConfigured,
                connectionStatus = connectionStatus,
                onOpenConnectionSettings = onOpenConnectionSettings,
                onStartPairing = onStartPairing
            )
        }
    }
}

@Composable
internal fun SearchHeader(
    query: String,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp, top = 8.dp)
            .testTag("search_header")
    ) {
        val largeText = LocalDensity.current.fontScale >= 1.15f
        if (largeText || maxWidth < 276.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SearchHeaderTitle(query)
                Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    SearchDownloadedOnlyControl(downloadedOnly, onDownloadedOnlyChange)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SearchHeaderTitle(query)
                SearchDownloadedOnlyControl(downloadedOnly, onDownloadedOnlyChange)
            }
        }
    }
}

@Composable
private fun SearchHeaderTitle(query: String) {
    Text(
        text = stringResource(if (query.isBlank()) R.string.search_title else R.string.search_results_title),
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun SearchDownloadedOnlyControl(
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        DownloadedOnlyToggle(
            checked = downloadedOnly,
            onCheckedChange = onDownloadedOnlyChange,
            modifier = Modifier.testTag("search_downloaded_only_toggle")
        )
        Text(
            stringResource(R.string.downloaded_only_filter),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

internal fun searchGroupingArtist(item: PlexMetadata): String? =
    item.albumArtist?.takeIf(String::isNotBlank)
        ?: item.grandparentTitle?.takeIf(String::isNotBlank)

@Composable
internal fun SearchQueryField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text(stringResource(R.string.search_field_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.search_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onSearchQueryChange("") },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.search_clear_accessibility),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .testTag("search_text_input"),
        singleLine = true,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun SearchResultsScreen(
    category: String,
    results: List<PlexMetadata>,
    viewModel: MusicViewModel,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit
) {
    val currentTrack by viewModel.playbackManager.currentTrack.collectAsStateWithLifecycle()
    var activeContextMenu by remember { mutableStateOf<ContextMenuItem?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(category, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.padding(bottom = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results) { item ->
                SearchResultsItemRow(
                    item = item,
                    category = category,
                    baseUrl = viewModel.repository.settings.baseUrl,
                    token = viewModel.repository.settings.token,
                    isPlaying = currentTrack?.ratingKey == item.ratingKey,
                    onMoreItemSelected = { activeContextMenu = it },
                    onClick = {
                        when (item.type) {
                            "artist" -> onNavigateToArtist(item.ratingKey, item.title)
                            "album" -> onNavigateToAlbum(item.ratingKey, item.title)
                            else -> viewModel.playTrackFromMetadata(item, results)
                        }
                    }
                )
            }
        }
    }

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
internal fun SearchResultsItemRow(
    item: PlexMetadata,
    category: String,
    baseUrl: String,
    token: String,
    isPlaying: Boolean,
    onMoreItemSelected: (ContextMenuItem) -> Unit,
    onClick: () -> Unit
) {
    val unknownArtist = stringResource(R.string.common_unknown_artist)
    val unknownAlbum = stringResource(R.string.common_unknown_album)
    val isTrack = category.equals(stringResource(R.string.search_section_songs), ignoreCase = true) || item.type == "track"
    val subtitle = when (item.type) {
        "artist" -> stringResource(R.string.search_item_artist)
        "album" -> localizedArtistPresentation(item.parentTitle)
        else -> localizedArtistAlbumSubtitle(
            item.grandparentTitle?.takeIf(String::isNotBlank) ?: item.albumArtist,
            item.parentTitle
        )
    }

    SearchItemRow(
        title = item.title,
        subtitle = subtitle,
        thumb = item.thumb,
        isCircle = item.type == "artist",
        baseUrl = baseUrl,
        token = token,
        isPlaying = isTrack && isPlaying,
        onMoreClick = {
            onMoreItemSelected(searchResultContextMenuItem(item, unknownArtist, unknownAlbum))
        },
        onClick = onClick
    )
}

internal fun searchResultContextMenuItem(
    item: PlexMetadata,
    unknownArtist: String,
    unknownAlbum: String
): ContextMenuItem = when (item.type) {
    "artist" -> ContextMenuItem.Artist(
        ratingKey = item.ratingKey,
        name = item.title,
        thumb = item.thumb.orEmpty()
    )
    "album" -> ContextMenuItem.Album(
        ratingKey = item.ratingKey,
        title = item.title,
        artist = item.parentTitle ?: unknownArtist,
        thumb = item.thumb.orEmpty()
    )
    else -> ContextMenuItem.Track(
        ratingKey = item.ratingKey,
        title = item.title,
        artist = item.grandparentTitle?.takeIf(String::isNotBlank)
            ?: item.albumArtist?.takeIf(String::isNotBlank)
            ?: unknownArtist,
        album = item.parentTitle ?: unknownAlbum,
        key = item.media?.firstOrNull()?.part?.firstOrNull()?.key.orEmpty(),
        thumb = item.thumb.orEmpty(),
        duration = item.duration ?: 0L,
        albumRatingKey = item.parentRatingKey,
        albumArtist = item.albumArtist,
        artistRatingKey = item.grandparentRatingKey
    )
}

@Composable
fun SearchSectionHeader(title: String, items: List<PlexMetadata>, onNavigateToResults: (String, List<PlexMetadata>) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.5.sp), modifier = Modifier.weight(1f))
        if (items.size > 3) TextButton(onClick = { onNavigateToResults(title, items) }) { Text(stringResource(R.string.search_see_all)) }
    }
}

@Composable
internal fun SearchEmptyStateHint(
    isConfigured: Boolean,
    hasSavedLibrary: Boolean,
    companionConfigured: Boolean = false,
    connectionStatus: CompanionConnectionStatus = CompanionConnectionStatus.NOT_PAIRED,
    onOpenConnectionSettings: () -> Unit = {},
    onStartPairing: () -> Unit = {}
) {
    val message = when {
        companionConfigured && connectionStatus == CompanionConnectionStatus.CHECKING ->
            R.string.connection_checking_body
        companionConfigured && connectionStatus == CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE ->
            R.string.connection_offline_body
        companionConfigured && connectionStatus == CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE ->
            R.string.connection_unavailable_body
        isConfigured -> R.string.search_empty_connected
        hasSavedLibrary -> R.string.search_empty_offline
        else -> R.string.search_empty_first_run
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        if (companionConfigured && connectionStatus == CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenConnectionSettings) {
                Text(stringResource(R.string.home_open_settings))
            }
        }
        if (!companionConfigured && !isConfigured && !hasSavedLibrary) {
            Spacer(Modifier.height(12.dp))
            SearchPairingAction(onStartPairing)
        }
    }
}

@Composable
internal fun SearchNoResultsState(
    query: String,
    onClearQuery: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.search_no_matches, query),
                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClearQuery) {
                    Text(stringResource(R.string.search_clear))
                }
                Text(
                    stringResource(R.string.search_try_another_query),
                    modifier = Modifier.align(Alignment.CenterVertically),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SearchPairingAction(onStartPairing: () -> Unit) {
    Button(
        onClick = onStartPairing,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag("search_pair_spotcore_action")
    ) {
        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.library_pair_spotcore_action))
    }
}

internal fun shouldShowSearchOfflineNotice(
    companionConfigured: Boolean,
    connectionStatus: CompanionConnectionStatus
): Boolean = companionConfigured && connectionStatus == CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE

@Composable
internal fun SearchOfflineResultsNotice() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2B2416))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("search_offline_results_notice")
            .semantics {
                liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.connection_offline_library),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFD180)
            )
        )
        Text(
            text = stringResource(R.string.connection_offline_body),
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.82f))
        )
    }
}

@Composable
internal fun SearchDownloadedOnlyEmptyState(
    query: String,
    onShowAllSongs: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.search_no_downloaded_matches, query),
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        TextButton(
            onClick = onShowAllSongs,
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            Text(stringResource(R.string.search_show_all_songs))
        }
    }
}

@Composable
internal fun SearchUnavailableBody(message: String?) {
    val recoveryMessage = message ?: stringResource(R.string.search_unavailable_body)
    Text(
        text = recoveryMessage,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics {
            liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
            error(recoveryMessage)
        }
    )
}

@Composable
internal fun SearchUnavailableState(
    message: String?,
    onRetry: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    showPairingAction: Boolean = false,
    onStartPairing: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize().testTag("search_unavailable_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.search_unavailable_title),
                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.error)
            )
            Spacer(Modifier.height(8.dp))
            SearchUnavailableBody(message)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.search_retry), maxLines = 2)
                }
                TextButton(
                    onClick = onOpenConnectionSettings,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.home_open_settings), maxLines = 2)
                }
            }
            if (showPairingAction) {
                Spacer(Modifier.height(4.dp))
                SearchPairingAction(onStartPairing)
            }
        }
    }
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
    isPlaying: Boolean = false,
    onMoreClick: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (!thumb.isNullOrEmpty()) resolveArtworkUrl(baseUrl, thumb) else null
    val activeColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPlaying) activeColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(if (isCircle) 26.dp else 12.dp)
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
                        imageVector = if (isCircle) Icons.Rounded.Person else Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isPlaying) activeColor else MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isDownloaded) {
                    stringResource(R.string.search_track_downloaded_subtitle, subtitle)
                } else {
                    subtitle
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = { onMoreClick() },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.search_more_accessibility, title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
