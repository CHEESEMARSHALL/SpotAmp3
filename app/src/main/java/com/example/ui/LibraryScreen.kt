package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.buildLibraryMetadataFacets
import com.example.data.CompanionConnectionStatus
import com.example.data.completedDownloadAlbumIdentities
import com.example.data.locallyAvailableDownloadRatingKeys
import com.example.data.PlexMetadata
import com.example.data.PlaylistEntity
import com.example.data.PlaylistSyncState
import com.example.data.albumNavigationKey
import com.example.data.effectiveAlbumArtist
import com.example.data.hasGenreTag
import com.example.data.hasMoodTag
import com.example.data.isNonEmptyRegularFile
import com.example.data.locallyAvailableDownloadArtistNames
import com.example.data.DownloadPresentationState
import com.example.data.downloadPresentationState
import com.example.data.sortArtistAlbumsChronologically
import com.example.data.toTrackItem
import com.example.playback.TrackItem

import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import java.text.NumberFormat
import kotlinx.coroutines.launch

enum class ViewMode { Grid, List }
enum class SortOption { TrackOrder, Name, RecentlyAdded }

@Composable
internal fun ArtistGridActions(
    onPlayFirstArtist: () -> Unit,
    onShuffleFirstArtist: () -> Unit,
    downloadedOnly: Boolean = false,
    onToggleDownloadedOnly: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconToggleButton(
            checked = downloadedOnly,
            onCheckedChange = { onToggleDownloadedOnly() },
            modifier = Modifier
                .size(48.dp)
                .testTag("library_downloaded_only_filter")
        ) {
            Icon(
                Icons.Rounded.CloudDownload,
                contentDescription = stringResource(R.string.downloaded_only_filter),
                tint = if (downloadedOnly) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onPlayFirstArtist,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("library_artist_play_first")
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.library_play_first_artist), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(
            onClick = onShuffleFirstArtist,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("library_artist_shuffle_first")
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.library_shuffle_first_artist), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

enum class LibraryCategory {
    MAIN_MENU,
    ARTISTS,
    ALBUMS,
    COMPILATIONS,
    SOUNDTRACKS,
    TRACKS,
    PLAYLISTS,
    ARTIST_GENRES,
    ALBUM_GENRES,
    STYLES,
    MOODS
}

internal fun librarySortActionResource(category: LibraryCategory): Int? = when (category) {
    LibraryCategory.ARTISTS -> R.string.library_sort_artists
    LibraryCategory.ALBUMS, LibraryCategory.COMPILATIONS, LibraryCategory.SOUNDTRACKS ->
        R.string.library_sort_albums
    LibraryCategory.TRACKS -> R.string.library_sort_tracks
    LibraryCategory.MAIN_MENU,
    LibraryCategory.PLAYLISTS,
    LibraryCategory.ARTIST_GENRES,
    LibraryCategory.ALBUM_GENRES,
    LibraryCategory.STYLES,
    LibraryCategory.MOODS -> null
}

@Composable
internal fun LibraryCategorySortButton(
    category: LibraryCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val labelResource = librarySortActionResource(category) ?: return
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(modifier)
            .testTag("library_category_sort")
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Sort,
            contentDescription = stringResource(labelResource),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun LibraryViewModeButton(
    isGrid: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).testTag("library_view_mode_toggle")
    ) {
        Icon(
            imageVector = if (isGrid) Icons.AutoMirrored.Rounded.List else Icons.Rounded.GridView,
            contentDescription = stringResource(
                if (isGrid) R.string.library_switch_to_list_view else R.string.library_switch_to_grid_view
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun LibraryBackButton(
    hasActiveFilter: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(
                if (hasActiveFilter) R.string.library_clear_filter_accessibility
                else R.string.library_back_accessibility
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun libraryCategoryTitle(category: LibraryCategory): String = stringResource(
    when (category) {
        LibraryCategory.ARTISTS -> R.string.library_category_artists
        LibraryCategory.ALBUMS -> R.string.library_category_albums
        LibraryCategory.COMPILATIONS -> R.string.library_category_compilations
        LibraryCategory.SOUNDTRACKS -> R.string.library_category_soundtracks
        LibraryCategory.TRACKS -> R.string.library_category_tracks
        LibraryCategory.PLAYLISTS -> R.string.library_category_playlists
        LibraryCategory.ARTIST_GENRES -> R.string.library_category_artist_genres
        LibraryCategory.ALBUM_GENRES -> R.string.library_category_album_genres
        LibraryCategory.STYLES -> R.string.library_category_styles
        LibraryCategory.MOODS -> R.string.library_category_moods
        LibraryCategory.MAIN_MENU -> R.string.library_category_items
    }
)

@Composable
internal fun libraryCategoryExploreCopy(item: String): String =
    stringResource(R.string.library_category_explore, item)

internal fun libraryCategoryCountString(category: LibraryCategory): Int = when (category) {
    LibraryCategory.ARTISTS -> R.plurals.library_category_count_artists
    LibraryCategory.ALBUMS -> R.plurals.library_category_count_albums
    LibraryCategory.COMPILATIONS -> R.plurals.library_category_count_compilations
    LibraryCategory.SOUNDTRACKS -> R.plurals.library_category_count_soundtracks
    LibraryCategory.TRACKS -> R.plurals.library_category_count_tracks
    LibraryCategory.PLAYLISTS -> R.plurals.library_category_count_playlists
    LibraryCategory.ARTIST_GENRES, LibraryCategory.ALBUM_GENRES -> R.plurals.library_category_count_genres
    LibraryCategory.STYLES -> R.plurals.library_category_count_styles
    LibraryCategory.MOODS -> R.plurals.library_category_count_moods
    LibraryCategory.MAIN_MENU -> R.plurals.library_category_count_items
}

@Composable
internal fun libraryCategoryCountLabel(category: LibraryCategory, count: Int): String =
    pluralStringResource(
        libraryCategoryCountString(category),
        count,
        NumberFormat.getInstance().format(count)
    )

@Composable
internal fun librarySavedPlaylistsHeading(count: Int): String =
    pluralStringResource(R.plurals.library_saved_playlists_heading, count, count)

@Composable
internal fun LibraryMainMenuTitle() {
    Text(
        text = stringResource(R.string.nav_library),
        style = MaterialTheme.typography.headlineLarge.copy(
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.testTag("library_title")
    )
}

@Composable
internal fun libraryEmptyPlaylistsCopy(): String = stringResource(R.string.library_empty_playlists)

@Composable
internal fun libraryPlaylistStatusLabel(playlist: PlaylistEntity): String = stringResource(
    when {
        playlist.remoteId != null && playlist.syncState == PlaylistSyncState.CONFLICT -> R.string.library_playlist_status_conflict
        playlist.remoteId != null && playlist.syncState == PlaylistSyncState.DIRTY -> R.string.library_playlist_status_pending
        playlist.remoteId != null -> R.string.library_playlist_status_shared
        else -> R.string.library_playlist_status_local
    }
)

@Composable
internal fun DownloadedArtistBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF047857)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                Icons.Rounded.CloudDownload,
                contentDescription = stringResource(R.string.library_saved_offline_badge_accessibility),
                modifier = Modifier.size(13.dp),
                tint = Color.White
            )
            Text(
                stringResource(R.string.library_saved_offline_badge),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    onNavigateToPlaylist: (Int, String) -> Unit,
    onStartPairing: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCustomPlaylist: (String, String, String, String, List<TrackItem>, List<Long>) -> Unit = { _, _, _, _, _, _ -> }
) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val companionConfigured by viewModel.companionConfigured.collectAsStateWithLifecycle()
    val companionConnectionStatus by viewModel.companionConnectionStatus.collectAsStateWithLifecycle()
    val selectedSectionId by viewModel.selectedSectionId.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrlFlow.collectAsStateWithLifecycle()
    val token by viewModel.tokenFlow.collectAsStateWithLifecycle()
    val isLoadingArtists by viewModel.isLoadingArtists.collectAsStateWithLifecycle()
    val isLoadingAlbums by viewModel.isLoadingAlbums.collectAsStateWithLifecycle()
    val artistLoadError by viewModel.artistLoadError.collectAsStateWithLifecycle()
    val albumLoadError by viewModel.albumLoadError.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val downloadsByRatingKey = remember(downloadedTracks) { downloadedTracks.associateBy { it.ratingKey } }
    val cachedTracks by viewModel.cachedTracks.collectAsStateWithLifecycle()
    val downloadedTrackKeys = remember(downloadedTracks) {
        locallyAvailableDownloadRatingKeys(downloadedTracks, ::isNonEmptyRegularFile)
    }
    val downloadedAlbums = remember(downloadedTracks, cachedTracks) {
        completedDownloadAlbumIdentities(downloadedTracks, cachedTracks, ::isNonEmptyRegularFile)
    }
    val downloadedArtists = remember(downloadedTracks) {
        locallyAvailableDownloadArtistNames(downloadedTracks, ::isNonEmptyRegularFile)
    }
    val currentTrack by viewModel.playbackManager.currentTrack.collectAsStateWithLifecycle()
    val unknownArtist = stringResource(R.string.common_unknown_artist)

    var selectedCategory by rememberSaveable { mutableStateOf(LibraryCategory.MAIN_MENU) }
    var selectedFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var offlineOnly by rememberSaveable { mutableStateOf(viewModel.repository.settings.offlineOnly) }
    val artistGridHasResults = remember(artists, selectedFilter, offlineOnly, downloadedArtists) {
        artists.any { artist ->
            (selectedFilter?.let { artist.hasGenreTag(it) } ?: true) &&
                (!offlineOnly || artist.title.lowercase() in downloadedArtists)
        }
    }

    // View modes
    var artistViewMode by rememberSaveable { mutableStateOf(ViewMode.Grid) }
    var albumViewMode by rememberSaveable { mutableStateOf(ViewMode.Grid) }
    
    // Sorting
    var artistSort by rememberSaveable { mutableStateOf(SortOption.Name) }
    var albumSort by rememberSaveable { mutableStateOf(SortOption.Name) }
    // Keep the library's indexed track sequence as the default. Alphabetical
    // sorting makes albums appear out of track order in the Songs view.
    var songSort by rememberSaveable { mutableStateOf(SortOption.TrackOrder) }

    var activeContextMenu by remember { mutableStateOf<ContextMenuItem?>(null) }
    var pendingDownloadRemoval by remember { mutableStateOf<com.example.data.CachedTrack?>(null) }

    val connectionNoticeBodyResource = if (
        shouldShowCompanionConnectionNotice(companionConfigured, companionConnectionStatus)
    ) {
        connectionStatusNoticeBodyResource(
            status = companionConnectionStatus,
            offlineBodyResource = R.string.connection_offline_body,
            unavailableBodyResource = R.string.connection_unavailable_body
        )
    } else null

    val coroutineScope = rememberCoroutineScope()
    val alphabet = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".map { it.toString() }
    val libraryCategoriesListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val artistGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    LaunchedEffect(companionConfigured) {
        if (companionConfigured) viewModel.ensureCompanionReachabilityChecked()
    }

    LaunchedEffect(selectedSectionId) {
        if (selectedSectionId.isNotEmpty()) {
            viewModel.loadArtists()
            viewModel.loadAlbums()
        }
    }

    // Derived counts
    val compilations = remember(albums) {
        albums.filter {
            it.title.contains("compilation", ignoreCase = true) ||
            it.parentTitle?.contains("Various Artists", ignoreCase = true) == true ||
            it.collections?.any { tag -> tag.tag.contains("compilation", ignoreCase = true) } == true
        }
    }

    val soundtracks = remember(albums) {
        albums.filter {
            it.title.contains("soundtrack", ignoreCase = true) ||
            it.title.contains("OST", ignoreCase = true) ||
            it.parentTitle?.contains("Soundtrack", ignoreCase = true) == true ||
            it.collections?.any { tag -> tag.tag.contains("soundtrack", ignoreCase = true) } == true
        }
    }

    val libraryFacets = remember(artists, albums, cachedTracks) {
        buildLibraryMetadataFacets(artists, albums, cachedTracks)
    }
    val albumGenresList = libraryFacets.albumGenres
    val artistGenresList = libraryFacets.artistGenres
    val stylesList = libraryFacets.styles
    val moodsList = libraryFacets.moods

    val activeCategoryLoading = when (selectedCategory) {
        LibraryCategory.ARTISTS, LibraryCategory.ARTIST_GENRES -> isLoadingArtists
        LibraryCategory.ALBUMS, LibraryCategory.COMPILATIONS, LibraryCategory.SOUNDTRACKS,
        LibraryCategory.ALBUM_GENRES -> isLoadingAlbums
        else -> false
    }
    val activeCategoryLoadError = when (selectedCategory) {
        LibraryCategory.ARTISTS, LibraryCategory.ARTIST_GENRES -> artistLoadError
        LibraryCategory.ALBUMS, LibraryCategory.COMPILATIONS, LibraryCategory.SOUNDTRACKS,
        LibraryCategory.ALBUM_GENRES -> albumLoadError
        else -> null
    }
    val activeCategoryHasData = when (selectedCategory) {
        LibraryCategory.ARTISTS -> artists.isNotEmpty()
        LibraryCategory.ALBUMS -> albums.isNotEmpty()
        LibraryCategory.COMPILATIONS -> compilations.isNotEmpty()
        LibraryCategory.SOUNDTRACKS -> soundtracks.isNotEmpty()
        LibraryCategory.ARTIST_GENRES -> artistGenresList.isNotEmpty()
        LibraryCategory.ALBUM_GENRES -> albumGenresList.isNotEmpty()
        else -> true
    }
    val retryActiveCategoryLoad = {
        when (selectedCategory) {
            LibraryCategory.ARTISTS, LibraryCategory.ARTIST_GENRES -> viewModel.loadArtists()
            LibraryCategory.ALBUMS, LibraryCategory.COMPILATIONS, LibraryCategory.SOUNDTRACKS,
            LibraryCategory.ALBUM_GENRES -> viewModel.loadAlbums()
            else -> Unit
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(
                horizontal = if (selectedCategory == LibraryCategory.ARTISTS && artistViewMode == ViewMode.Grid) 8.dp else 16.dp,
                vertical = 16.dp
            )
    ) {
        if (selectedCategory == LibraryCategory.MAIN_MENU) {
            // MAIN MENU HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LibraryMainMenuTitle()
            }

            if (connectionNoticeBodyResource != null) {
                CompanionConnectionNotice(
                    status = companionConnectionStatus,
                    bodyResource = connectionNoticeBodyResource,
                    onCheckConnection = viewModel::retryCompanionConnection,
                    modifier = Modifier.padding(bottom = 12.dp),
                    testTag = "library_connection_status_notice"
                )
            }

            if (selectedSectionId.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    LibraryNoSelectionState(
                        companionConfigured = companionConfigured,
                        onOpenSettings = onNavigateToSettings,
                        onStartPairing = onStartPairing
                    )
                }
            } else {
                LazyColumn(
                    state = libraryCategoriesListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("library_categories_list"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        LibraryCategoryRow(
                            title = libraryCategoryTitle(LibraryCategory.ARTISTS),
                            subtitle = libraryCategoryCountLabel(LibraryCategory.ARTISTS, artists.size)
                        ) { selectedCategory = LibraryCategory.ARTISTS }
                    }
                    item {
                        LibraryCategoryRow(
                            title = libraryCategoryTitle(LibraryCategory.ALBUMS),
                            subtitle = libraryCategoryCountLabel(LibraryCategory.ALBUMS, albums.size),
                            onClick = { selectedCategory = LibraryCategory.ALBUMS }
                        )
                    }
                    item {
                        LibraryCategoryRow(
                            title = libraryCategoryTitle(LibraryCategory.COMPILATIONS),
                            subtitle = libraryCategoryCountLabel(LibraryCategory.COMPILATIONS, compilations.size),
                            onClick = { selectedCategory = LibraryCategory.COMPILATIONS }
                        )
                    }
                    item {
                        LibraryCategoryRow(
                            title = libraryCategoryTitle(LibraryCategory.SOUNDTRACKS),
                            subtitle = libraryCategoryCountLabel(LibraryCategory.SOUNDTRACKS, soundtracks.size),
                            onClick = { selectedCategory = LibraryCategory.SOUNDTRACKS }
                        )
                    }
                    item {
                        LibraryCategoryRow(
                            title = libraryCategoryTitle(LibraryCategory.TRACKS),
                            subtitle = libraryCategoryCountLabel(LibraryCategory.TRACKS, cachedTracks.size),
                            onClick = { selectedCategory = LibraryCategory.TRACKS }
                        )
                    }
                    item {
                        LibraryCategoryRow(
                            title = libraryCategoryTitle(LibraryCategory.PLAYLISTS),
                            subtitle = libraryCategoryCountLabel(LibraryCategory.PLAYLISTS, playlists.size),
                            onClick = { selectedCategory = LibraryCategory.PLAYLISTS }
                        )
                    }
                    item {
                        LibraryCategoryRow(
                            title = libraryCategoryTitle(LibraryCategory.ARTIST_GENRES),
                            subtitle = libraryCategoryCountLabel(LibraryCategory.ARTIST_GENRES, artistGenresList.size),
                            onClick = { selectedCategory = LibraryCategory.ARTIST_GENRES }
                        )
                    }
                    item {
                        LibraryCategoryRow(
                            title = libraryCategoryTitle(LibraryCategory.ALBUM_GENRES),
                            subtitle = libraryCategoryCountLabel(LibraryCategory.ALBUM_GENRES, albumGenresList.size),
                            onClick = { selectedCategory = LibraryCategory.ALBUM_GENRES }
                        )
                    }
                    item {
                        LibraryCategoryRow(
                            title = libraryCategoryTitle(LibraryCategory.STYLES),
                            subtitle = libraryCategoryCountLabel(LibraryCategory.STYLES, stylesList.size),
                            onClick = { selectedCategory = LibraryCategory.STYLES }
                        )
                    }
                    item {
                        LibraryCategoryRow(
                            title = libraryCategoryTitle(LibraryCategory.MOODS),
                            subtitle = libraryCategoryCountLabel(LibraryCategory.MOODS, moodsList.size),
                            onClick = { selectedCategory = LibraryCategory.MOODS }
                        )
                    }
                }
            }
        } else {
            // CATEGORY SUB-VIEW
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LibraryBackButton(
                            hasActiveFilter = selectedFilter != null,
                            onClick = {
                                if (selectedFilter != null) {
                                    selectedFilter = null
                                } else {
                                    selectedCategory = LibraryCategory.MAIN_MENU
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedFilter ?: libraryCategoryTitle(selectedCategory),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedCategory == LibraryCategory.ARTISTS || selectedCategory == LibraryCategory.ALBUMS || selectedCategory == LibraryCategory.COMPILATIONS || selectedCategory == LibraryCategory.SOUNDTRACKS) {
                            LibraryViewModeButton(
                                isGrid = if (selectedCategory == LibraryCategory.ARTISTS) {
                                    artistViewMode == ViewMode.Grid
                                } else {
                                    albumViewMode == ViewMode.Grid
                                },
                                onClick = {
                                    if (selectedCategory == LibraryCategory.ARTISTS) artistViewMode = if (artistViewMode == ViewMode.Grid) ViewMode.List else ViewMode.Grid
                                    else albumViewMode = if (albumViewMode == ViewMode.Grid) ViewMode.List else ViewMode.Grid
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                        }

                        if (librarySortActionResource(selectedCategory) != null) {
                            LibraryCategorySortButton(
                                category = selectedCategory,
                                onClick = {
                                    when (selectedCategory) {
                                        LibraryCategory.ARTISTS -> artistSort = if (artistSort == SortOption.Name) SortOption.RecentlyAdded else SortOption.Name
                                        LibraryCategory.ALBUMS, LibraryCategory.COMPILATIONS, LibraryCategory.SOUNDTRACKS -> albumSort = if (albumSort == SortOption.Name) SortOption.RecentlyAdded else SortOption.Name
                                        LibraryCategory.TRACKS -> songSort = when (songSort) {
                                            SortOption.TrackOrder -> SortOption.RecentlyAdded
                                            SortOption.RecentlyAdded -> SortOption.Name
                                            SortOption.Name -> SortOption.TrackOrder
                                        }
                                        else -> Unit
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }

                if (selectedCategory != LibraryCategory.ARTISTS ||
                    artistViewMode != ViewMode.Grid ||
                    !artistGridHasResults
                ) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        LibraryDownloadedOnlyFilterChip(
                            selected = offlineOnly,
                            onClick = {
                                offlineOnly = !offlineOnly
                                viewModel.repository.settings.offlineOnly = offlineOnly
                            }
                        )
                    }
                }
            }

            if (selectedCategory == LibraryCategory.TRACKS) {
                Text(
                    text = stringResource(R.string.library_audio_storage_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                        .testTag("library_audio_storage_note")
                )
            }

            if (connectionNoticeBodyResource != null) {
                CompanionConnectionNotice(
                    status = companionConnectionStatus,
                    bodyResource = connectionNoticeBodyResource,
                    onCheckConnection = viewModel::retryCompanionConnection,
                    modifier = Modifier.padding(bottom = 12.dp),
                    testTag = "library_connection_status_notice"
                )
            }

            if (activeCategoryLoadError != null && activeCategoryHasData) {
                LibraryLoadWarning(
                    message = activeCategoryLoadError,
                    onRetry = retryActiveCategoryLoad
                )
            }

            when {
                activeCategoryLoading && !activeCategoryHasData -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                activeCategoryLoadError != null && !activeCategoryHasData -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LibraryLoadFailureState(
                            message = activeCategoryLoadError,
                            onRetry = retryActiveCategoryLoad
                        )
                    }
                }
                else -> {
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedCategory) {
                        LibraryCategory.ARTISTS -> {
                            val baseList = if (selectedFilter != null) {
                                artists.filter { artist -> artist.hasGenreTag(selectedFilter!!) }
                            } else {
                                artists
                            }
                            val visibleArtists = if (offlineOnly) baseList.filter { it.title.lowercase() in downloadedArtists } else baseList
                            val sortedArtists = remember(visibleArtists, artistSort) {
                                when (artistSort) {
                                    SortOption.TrackOrder -> visibleArtists.sortedBy { it.title.lowercase() }
                                    SortOption.Name -> visibleArtists.sortedBy { it.title.lowercase() }
                                    SortOption.RecentlyAdded -> visibleArtists.sortedByDescending { it.addedAt ?: 0L }
                                }
                            }
                            val availableAlphabetBuckets = remember(sortedArtists) {
                                sortedArtists.mapTo(mutableSetOf()) { artistAlphabetBucket(it.title) }
                            }

                            if (sortedArtists.isEmpty()) {
                                LibraryEmptyState(
                                    category = LibraryCategory.ARTISTS,
                                    downloadedOnly = offlineOnly,
                                    filter = selectedFilter
                                )
                            } else if (artistViewMode == ViewMode.Grid) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column {
                                        ArtistGridActions(
                                            onPlayFirstArtist = { viewModel.playArtist(sortedArtists.first().ratingKey) },
                                            onShuffleFirstArtist = { viewModel.playArtist(sortedArtists.shuffled().first().ratingKey) },
                                            downloadedOnly = offlineOnly,
                                            onToggleDownloadedOnly = {
                                                offlineOnly = !offlineOnly
                                                viewModel.repository.settings.offlineOnly = offlineOnly
                                            }
                                        )

                                        Row(modifier = Modifier.fillMaxSize()) {
                                            LazyVerticalGrid(
                                                columns = GridCells.Fixed(3),
                                                state = artistGridState,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .testTag("library_artists_grid")
                                            ) {
                                                items(
                                                    count = sortedArtists.size,
                                                    key = { index -> sortedArtists[index].ratingKey },
                                                    contentType = { "artist" }
                                                ) { index ->
                                                    val artist = sortedArtists[index]
                                                    val isDownloaded = remember(artist.title, downloadedArtists) {
                                                        artist.title.lowercase() in downloadedArtists
                                                    }
                                                    val onMore = remember(artist.ratingKey, artist.title, artist.thumb) {
                                                        {
                                                            activeContextMenu = ContextMenuItem.Artist(
                                                                ratingKey = artist.ratingKey,
                                                                name = artist.title,
                                                                thumb = artist.thumb ?: ""
                                                            )
                                                        }
                                                    }
                                                    val onArtistClick = remember(artist.ratingKey, artist.title) {
                                                        { onNavigateToArtist(artist.ratingKey, artist.title) }
                                                    }

                                                    ArtistGridItem(
                                                        artist = artist,
                                                        baseUrl = baseUrl,
                                                        token = token,
                                                        isDownloaded = isDownloaded,
                                                        onMoreClick = onMore,
                                                        onClick = onArtistClick
                                                    )
                                                }
                                            }

                                            // Alphabet Navigation
                                            Column(
                                                modifier = Modifier
                                                    .width(48.dp)
                                                    .fillMaxHeight()
                                                    .verticalScroll(rememberScrollState()),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.SpaceEvenly
                                            ) {
                                                alphabet.forEach { char ->
                                                    ArtistAlphabetLetterButton(
                                                        letter = char,
                                                        enabled = char in availableAlphabetBuckets
                                                    ) {
                                                        coroutineScope.launch {
                                                            val index = sortedArtists.indexOfFirst {
                                                                artistAlphabetBucket(it.title) == char
                                                            }
                                                            if (index != -1) {
                                                                artistGridState.scrollToItem(index)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().testTag("library_artists_list"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(sortedArtists) { artist ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(role = Role.Button) { onNavigateToArtist(artist.ratingKey, artist.title) }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(artist.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            }
                        }
                        LibraryCategory.ALBUMS, LibraryCategory.COMPILATIONS, LibraryCategory.SOUNDTRACKS -> {
                            val baseList = when (selectedCategory) {
                                LibraryCategory.COMPILATIONS -> compilations
                                LibraryCategory.SOUNDTRACKS -> soundtracks
                                else -> if (selectedFilter != null) albums.filter { album ->
                                    album.hasGenreTag(selectedFilter!!)
                                } else albums
                            }
                            val visibleAlbums = if (offlineOnly) baseList.filter { album ->
                                (album.parentTitle.orEmpty().trim().lowercase(java.util.Locale.ROOT) to
                                    album.title.trim().lowercase(java.util.Locale.ROOT)) in downloadedAlbums
                            } else baseList
                            val sortedAlbums = remember(visibleAlbums, albumSort) {
                                when (albumSort) {
                                    SortOption.TrackOrder -> sortArtistAlbumsChronologically(visibleAlbums)
                                    SortOption.Name -> visibleAlbums.sortedBy { it.title.lowercase() }
                                    SortOption.RecentlyAdded -> visibleAlbums.sortedByDescending { it.addedAt ?: 0L }
                                }
                            }
                            if (sortedAlbums.isEmpty()) {
                                LibraryEmptyState(
                                    category = selectedCategory,
                                    downloadedOnly = offlineOnly,
                                    filter = selectedFilter
                                )
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
                                            isDownloaded = (album.parentTitle.orEmpty().trim().lowercase(java.util.Locale.ROOT) to
                                                album.title.trim().lowercase(java.util.Locale.ROOT)) in downloadedAlbums,
                                            onMoreClick = {
                                                activeContextMenu = ContextMenuItem.Album(
                                                    ratingKey = album.ratingKey,
                                                    title = album.title,
                                                    artist = album.parentTitle?.takeIf { it.isNotBlank() } ?: unknownArtist,
                                                    thumb = album.thumb ?: ""
                                                )
                                            }
                                        ) {
                                            onNavigateToAlbum(album.ratingKey, album.title)
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().testTag("library_albums_list"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(sortedAlbums) { album ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(role = Role.Button) { onNavigateToAlbum(album.ratingKey, album.title) }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(album.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            }
                        }
                        LibraryCategory.TRACKS -> {
                            val filteredTracks = if (selectedFilter != null) {
                                cachedTracks.filter { it.hasMoodTag(selectedFilter!!) }
                            } else {
                                cachedTracks
                            }
                            val visibleTracks = if (offlineOnly) filteredTracks.filter { it.ratingKey in downloadedTrackKeys } else filteredTracks
                            val sortedTracks = remember(visibleTracks, songSort) {
                                when (songSort) {
                                    SortOption.TrackOrder -> visibleTracks
                                    SortOption.Name -> visibleTracks.sortedBy { it.title.lowercase() }
                                    SortOption.RecentlyAdded -> visibleTracks.sortedByDescending { it.addedAt ?: 0L }
                                }
                            }
                            if (sortedTracks.isEmpty()) {
                                LibraryEmptyState(
                                    category = LibraryCategory.TRACKS,
                                    downloadedOnly = offlineOnly,
                                    filter = selectedFilter
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().testTag("library_tracks_list"),
                                    contentPadding = PaddingValues(bottom = 16.dp)
                                ) {
                                    items(
                                        count = sortedTracks.size,
                                        key = { index -> sortedTracks[index].ratingKey },
                                        contentType = { "track" }
                                    ) { index ->
                                        val track = sortedTracks[index]
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
                                                    albumRatingKey = albumNavigationKey(effectiveAlbumArtist(track.albumArtist, track.artist), track.album),
                                                    albumArtist = effectiveAlbumArtist(track.albumArtist, track.artist),
                                                    artistRatingKey = com.example.data.artistNavigationKey(effectiveAlbumArtist(track.albumArtist, track.artist))
                                                )
                                            },
                                            onClick = { viewModel.playTrackFromCache(track) },
                                            isPlaying = currentTrack?.ratingKey == track.ratingKey,
                                            downloadState = downloadPresentationState(
                                                downloadsByRatingKey[track.ratingKey],
                                                ::isNonEmptyRegularFile
                                            ),
                                            onDownloadAction = {
                                                val download = downloadsByRatingKey[track.ratingKey]
                                                when (downloadPresentationState(download, ::isNonEmptyRegularFile)) {
                                                    DownloadPresentationState.AVAILABLE -> viewModel.startDownload(track.toTrackItem())
                                                    DownloadPresentationState.QUEUED,
                                                    DownloadPresentationState.DOWNLOADING -> viewModel.deleteDownload(track.ratingKey)
                                                    DownloadPresentationState.DOWNLOADED -> pendingDownloadRemoval = track
                                                    DownloadPresentationState.FAILED -> {
                                                        if (download != null) viewModel.retryDownload(download)
                                                        else viewModel.startDownload(track.toTrackItem())
                                                    }
                                                }
                                            },
                                            onCompanionFavoriteChange = if (viewModel.repository.settings.isCompanionConfigured) {
                                                { favorite -> viewModel.setCompanionFavorite(track.ratingKey, favorite) }
                                            } else null,
                                            onCompanionRatingChange = if (viewModel.repository.settings.isCompanionConfigured) {
                                                { rating -> viewModel.setCompanionRating(track.ratingKey, rating) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }
                        LibraryCategory.ARTIST_GENRES, LibraryCategory.ALBUM_GENRES, LibraryCategory.STYLES, LibraryCategory.MOODS -> {
                            val itemsList = when (selectedCategory) {
                                LibraryCategory.ARTIST_GENRES -> artistGenresList
                                LibraryCategory.ALBUM_GENRES -> albumGenresList
                                LibraryCategory.STYLES -> stylesList
                                else -> moodsList
                            }

                            if (itemsList.isEmpty()) {
                                val emptyMessage = when (selectedCategory) {
                                    LibraryCategory.MOODS -> stringResource(R.string.library_empty_moods)
                                    LibraryCategory.STYLES -> stringResource(R.string.library_empty_styles)
                                    else -> stringResource(R.string.library_empty_genres)
                                }
                                EmptyLibraryState(emptyMessage)
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(itemsList) { item ->
                                        LibraryCategoryRow(
                                            title = item,
                                            subtitle = libraryCategoryExploreCopy(item),
                                            onClick = {
                                                selectedFilter = item
                                                selectedCategory = when (selectedCategory) {
                                                    LibraryCategory.ARTIST_GENRES -> LibraryCategory.ARTISTS
                                                    LibraryCategory.MOODS -> LibraryCategory.TRACKS
                                                    else -> LibraryCategory.ALBUMS
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        LibraryCategory.PLAYLISTS -> {
                            val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()
                            val aiError by viewModel.aiError.collectAsStateWithLifecycle()

                            var playlistPrompt by remember { mutableStateOf("") }
                            var playlistTrackCount by remember { mutableFloatStateOf(30f) }
                            var showCreateDialog by remember { mutableStateOf(value = false) }
                            var manualPlaylistName by remember { mutableStateOf("") }
                            var isCreatingPlaylist by remember { mutableStateOf(false) }
                            var createPlaylistError by remember { mutableStateOf(false) }
                            var playlistPendingDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
                            var playlistPendingConflict by remember { mutableStateOf<PlaylistEntity?>(null) }
                            var isDeletingPlaylist by remember { mutableStateOf(false) }
                            var deletePlaylistError by remember { mutableStateOf(false) }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(bottom = 12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = Color(0xFF818CF8),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = stringResource(R.string.library_smart_playlist_heading),
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                )
                                            }

                                            Text(
                                                text = stringResource(R.string.library_smart_playlist_description),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier.padding(bottom = 12.dp)
                                            )

                                            OutlinedTextField(
                                                value = playlistPrompt,
                                                onValueChange = { playlistPrompt = it },
                                                placeholder = {
                                                    Text(
                                                        stringResource(R.string.library_smart_playlist_prompt_hint),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                                    )
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("ai_playlist_prompt_input"),
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                enabled = !aiLoading
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

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
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                ),
                                                enabled = playlistPrompt.isNotBlank()
                                            ) {
                                                Text(
                                                    stringResource(R.string.library_smart_playlist_generate),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            PlaylistGenerationError(error = aiError)
                                        }
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = librarySavedPlaylistsHeading(playlists.size),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        )

                                        TextButton(
                                            onClick = { showCreateDialog = true },
                                            modifier = Modifier.testTag("library_new_playlist_action"),
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.library_new_playlist))
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
                                                libraryEmptyPlaylistsCopy(),
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                                .clickable(role = Role.Button) { onNavigateToPlaylist(playlist.id, playlist.name) }
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
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
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                )
                                                Text(
                                                    text = libraryPlaylistStatusLabel(playlist),
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                )
                                            }

                                            LibraryPlaylistActions(
                                                playlist = playlist,
                                                showShareAction = companionConfigured &&
                                                    (playlist.remoteId == null || playlist.syncState == PlaylistSyncState.CONFLICT),
                                                onShare = {
                                                    if (playlist.syncState == PlaylistSyncState.CONFLICT) {
                                                        playlistPendingConflict = playlist
                                                    } else {
                                                        viewModel.sharePlaylist(playlist.id)
                                                    }
                                                },
                                                onDownload = { viewModel.downloadPlaylist(playlist.id) },
                                                onDelete = { playlistPendingDelete = playlist }
                                            )
                                        }
                                    }
                                }
                            }

                            if (showCreateDialog) {
                                LibraryCreatePlaylistDialog(
                                    name = manualPlaylistName,
                                    isCreating = isCreatingPlaylist,
                                    showError = createPlaylistError,
                                    onNameChange = {
                                        manualPlaylistName = it
                                        createPlaylistError = false
                                    },
                                    onDismiss = {
                                        if (isCreatingPlaylist) return@LibraryCreatePlaylistDialog
                                        showCreateDialog = false
                                        manualPlaylistName = ""
                                        createPlaylistError = false
                                    },
                                    onCreate = { name ->
                                        isCreatingPlaylist = true
                                        createPlaylistError = false
                                        viewModel.createPlaylist(
                                            name = name,
                                            onCreated = {
                                                showCreateDialog = false
                                                manualPlaylistName = ""
                                                isCreatingPlaylist = false
                                            },
                                            onFailure = {
                                                isCreatingPlaylist = false
                                                createPlaylistError = true
                                            }
                                        )
                                    }
                                )
                            }
                            playlistPendingConflict?.let { playlist ->
                                LibraryPlaylistConflictDialog(
                                    playlist = playlist,
                                    onDismiss = { playlistPendingConflict = null },
                                    onUseSpotCore = {
                                        viewModel.refreshSharedPlaylist(playlist.id)
                                        playlistPendingConflict = null
                                    },
                                    onKeepThisDevice = {
                                        viewModel.keepLocalPlaylistChangesAfterConflict(playlist.id)
                                        playlistPendingConflict = null
                                    }
                                )
                            }
                            playlistPendingDelete?.let { playlist ->
                                LibraryDeletePlaylistDialog(
                                    playlist = playlist,
                                    isDeleting = isDeletingPlaylist,
                                    showError = deletePlaylistError,
                                    onDismiss = {
                                        if (!isDeletingPlaylist) {
                                            playlistPendingDelete = null
                                            deletePlaylistError = false
                                        }
                                    },
                                    onConfirm = {
                                        isDeletingPlaylist = true
                                        deletePlaylistError = false
                                        viewModel.deletePlaylist(
                                            playlistId = playlist.id,
                                            onDeleted = {
                                                isDeletingPlaylist = false
                                                playlistPendingDelete = null
                                            },
                                            onFailure = {
                                                isDeletingPlaylist = false
                                                deletePlaylistError = true
                                            }
                                        )
                                    }
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
    }

    // Context Menu Trigger
    pendingDownloadRemoval?.let { track ->
        LibraryRemoveDownloadedAudioDialog(
            trackTitle = track.title,
            onDismiss = { pendingDownloadRemoval = null },
            onConfirm = {
                viewModel.deleteDownload(track.ratingKey)
                pendingDownloadRemoval = null
            }
        )
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
internal fun LibraryRemoveDownloadedAudioDialog(
    trackTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("library_remove_download_confirmation"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_remove_download_title)) },
        text = { Text(stringResource(R.string.library_remove_download_message, trackTitle)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("library_remove_download_confirm")
            ) { Text(stringResource(R.string.library_remove_download_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.downloads_cancel))
            }
        }
    )
}

@Composable
internal fun LibraryDownloadedOnlyFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.testTag("library_downloaded_only_filter"),
        label = {
            Text(
                text = stringResource(R.string.downloaded_only_filter),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null
    )
}

@Composable
internal fun LibraryPlaylistActions(
    playlist: PlaylistEntity,
    showShareAction: Boolean,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Row {
        if (showShareAction) {
            IconButton(
                onClick = onShare,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("library_playlist_share")
            ) {
                Icon(
                    Icons.Rounded.Sync,
                    contentDescription = stringResource(
                        when {
                            playlist.remoteId == null -> R.string.library_playlist_share_action
                            playlist.syncState == PlaylistSyncState.CONFLICT -> R.string.library_playlist_resolve_conflict_action
                            else -> R.string.library_playlist_keep_changes_action
                        },
                        playlist.name
                    ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        IconButton(
            onClick = onDownload,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("library_playlist_download")
        ) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = stringResource(R.string.library_playlist_download_action, playlist.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("library_playlist_delete")
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.library_playlist_delete_action, playlist.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun LibraryPlaylistConflictDialog(
    playlist: PlaylistEntity,
    onDismiss: () -> Unit,
    onUseSpotCore: () -> Unit,
    onKeepThisDevice: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("library_playlist_conflict_dialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_playlist_conflict_title)) },
        text = { Text(stringResource(R.string.library_playlist_conflict_message, playlist.name)) },
        confirmButton = {
            TextButton(
                onClick = onUseSpotCore,
                modifier = Modifier.testTag("library_playlist_conflict_use_remote")
            ) { Text(stringResource(R.string.library_playlist_conflict_use_remote)) }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onKeepThisDevice,
                    modifier = Modifier.testTag("library_playlist_conflict_keep_local")
                ) { Text(stringResource(R.string.library_playlist_conflict_keep_local)) }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("library_playlist_conflict_cancel")
                ) { Text(stringResource(R.string.library_playlist_conflict_cancel)) }
            }
        }
    )
}

@Composable
internal fun LibraryDeletePlaylistDialog(
    playlist: PlaylistEntity,
    isDeleting: Boolean = false,
    showError: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("library_delete_playlist_dialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_delete_playlist_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        if (playlist.remoteId != null) R.string.library_delete_shared_playlist_message
                        else R.string.library_delete_local_playlist_message,
                        playlist.name
                    )
                )
                if (showError) {
                    val failureMessage = stringResource(R.string.library_delete_playlist_error)
                    Text(
                        text = failureMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                error(failureMessage)
                            }
                            .testTag("library_delete_playlist_error")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDeleting,
                modifier = Modifier.testTag("library_delete_playlist_confirm")
            ) {
                Text(
                    stringResource(
                        if (isDeleting) R.string.library_deleting_playlist else R.string.library_delete_playlist_confirm
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting,
                modifier = Modifier.testTag("library_delete_playlist_cancel")
            ) {
                Text(stringResource(R.string.library_cancel_playlist_creation))
            }
        }
    )
}

@Composable
internal fun LibraryCreatePlaylistDialog(
    name: String,
    isCreating: Boolean = false,
    showError: Boolean = false,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("library_create_playlist_dialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_new_playlist)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.library_playlist_name)) },
                    singleLine = true,
                    enabled = !isCreating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_playlist_name_input")
                )
                if (showError) {
                    val failureMessage = stringResource(R.string.library_create_playlist_error)
                    Text(
                        text = failureMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                error(failureMessage)
                            }
                            .testTag("library_create_playlist_error")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim()) },
                enabled = name.isNotBlank() && !isCreating,
                modifier = Modifier.testTag("library_create_playlist_confirm")
            ) {
                Text(
                    stringResource(
                        if (isCreating) R.string.library_creating_playlist else R.string.library_create_playlist
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating,
                modifier = Modifier.testTag("library_create_playlist_cancel")
            ) {
                Text(stringResource(R.string.library_cancel_playlist_creation))
            }
        }
    )
}

@Composable
internal fun PlaylistGenerationError(error: String?) {
    val failureMessage = error
    if (!failureMessage.isNullOrBlank()) {
        Text(
            text = failureMessage,
            color = Color(0xFFFCA5A5),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    error(failureMessage)
                }
                .testTag("playlist_generation_error")
        )
    }
}

@Composable
fun LibraryCategoryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun ArtistAlphabetLetterButton(
    letter: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val actionLabel = if (letter == "#") {
        stringResource(R.string.library_jump_to_other_initials_accessibility)
    } else {
        stringResource(R.string.library_jump_to_letter_accessibility, letter)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                enabled = enabled,
                onClickLabel = actionLabel,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

internal fun artistAlphabetBucket(title: String): String =
    title.trimStart().firstOrNull()?.uppercaseChar()
        ?.takeIf { it in 'A'..'Z' }
        ?.toString()
        ?: "#"

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
    val imageUrl = remember(baseUrl, artist.thumb) {
        artist.thumb?.takeIf(String::isNotEmpty)?.let { resolveArtworkUrl(baseUrl, it) }
    }
    val imageRequest = remember(context, imageUrl, token) {
        imageUrl?.let { authenticatedArtworkRequest(context, it, token) }
    }
    val genreLabel = remember(artist.genres) {
        artist.genres.orEmpty()
            .map { it.tag.trim() }
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(" / ")
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .testTag("artist_item_${artist.ratingKey}"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
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
                if (isDownloaded) {
                    DownloadedArtistBadge(
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = artist.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (genreLabel.isNotEmpty()) {
                Text(
                    text = genreLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(48.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.library_artist_more_options_accessibility, artist.title),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun LibraryNoSelectionState(
    companionConfigured: Boolean,
    onOpenSettings: () -> Unit,
    onStartPairing: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!companionConfigured) {
            Text(
                text = stringResource(R.string.library_connect_spotcore_title),
                style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.library_connect_spotcore_body),
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onStartPairing,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("library_pair_spotcore_action")
            ) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.library_pair_spotcore_action))
            }
        } else {
            Text(
                text = stringResource(R.string.library_no_selection),
                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.library_open_settings_action))
            }
        }
    }
}

@Composable
internal fun LibraryLoadFailureState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().testTag("library_load_failure_state"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.library_load_failure_title),
                style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.error)
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("library_load_retry")
            ) {
                Text(stringResource(R.string.home_retry))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    error(message)
                }
            )
        }
    }
}

@Composable
internal fun LibraryLoadWarning(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF5B421A)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f).semantics {
                    liveRegion = LiveRegionMode.Polite
                    error(message)
                },
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("library_load_retry")
            ) {
                Text(stringResource(R.string.home_retry))
            }
        }
    }
}

@Composable
internal fun LibraryEmptyState(
    category: LibraryCategory,
    downloadedOnly: Boolean,
    filter: String?
) {
    val message = when {
        downloadedOnly && filter != null -> stringResource(R.string.library_empty_downloaded_filter, filter)
        downloadedOnly -> stringResource(R.string.library_empty_downloaded)
        filter != null -> stringResource(R.string.library_empty_filter, filter)
        category == LibraryCategory.ARTISTS -> stringResource(R.string.library_empty_artists)
        category == LibraryCategory.TRACKS -> stringResource(R.string.library_empty_tracks)
        else -> stringResource(R.string.library_empty_albums)
    }
    EmptyLibraryState(message)
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}
