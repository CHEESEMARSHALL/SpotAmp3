package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.*
import com.example.R
import com.example.playback.TrackItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.homeTileGesture(
    onClickLabel: String,
    onLongClickLabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = combinedClickable(
    onClickLabel = onClickLabel,
    role = Role.Button,
    onClick = onClick,
    onLongClickLabel = onLongClickLabel,
    onLongClick = onLongClick
)

@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onNavigateToSettings: () -> Unit,
    onStartPairing: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    onNavigateToCustomPlaylist: (String, String, String, String, List<TrackItem>, List<Long>) -> Unit,
    onNavigateToAlbumWithTracks: (String, String, List<TrackItem>) -> Unit = { id, name, _ ->
        onNavigateToAlbum(id, name)
    },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
    val selectedLibraryName by viewModel.selectedLibraryName.collectAsStateWithLifecycle()
    val homeFeedState by viewModel.homeFeedState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingRecentlyAdded by viewModel.isLoadingRecentlyAdded.collectAsStateWithLifecycle()
    val recentlyAddedLoadError by viewModel.recentlyAddedLoadError.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrlFlow.collectAsStateWithLifecycle()
    val token by viewModel.tokenFlow.collectAsStateWithLifecycle()
    val cachedCount by viewModel.cachedCount.collectAsStateWithLifecycle()
    val generalErrorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val homeFeedFailure by viewModel.homeFeedFailure.collectAsStateWithLifecycle()
    val companionConfigured by viewModel.companionConfigured.collectAsStateWithLifecycle()
    val companionConnectionStatus by viewModel.companionConnectionStatus.collectAsStateWithLifecycle()
    val showFirstRunPairing = !isConfigured && cachedCount == 0

    var activeContextMenu by remember { mutableStateOf<ContextMenuItem?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_feed"),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // 1. Personalized Header Row
            item(key = "header") {
                HomeHeader(
                    libraryName = selectedLibraryName,
                    isConfigured = isConfigured,
                    hasConnectionError = homeFeedFailure != null,
                    hasCachedMusic = cachedCount > 0,
                    source = homeFeedState.source,
                    onNavigateToSettings = onNavigateToSettings,
                    companionConfigured = companionConfigured,
                    connectionStatus = companionConnectionStatus,
                    showPairingGuidance = !showFirstRunPairing
                )
            }

            if (showFirstRunPairing) {
                item(key = "first_run_pairing") {
                    HomeEmptyState(
                        isConfigured = false,
                        hasCachedMusic = false,
                        onNavigateToSettings = onNavigateToSettings,
                        onStartPairing = onStartPairing
                    )
                }
            }

            homeFeedState.dailyMixes.firstOrNull()?.let { spotlight ->
                item(key = "spotlight") {
                    HomeSpotlight(
                        mix = spotlight,
                        source = homeFeedState.source,
                        baseUrl = baseUrl,
                        token = token,
                        onPlay = { viewModel.playDailyMix(spotlight) },
                        onOpen = {
                            onNavigateToCustomPlaylist(
                                "daily_mix",
                                spotlight.id,
                                spotlight.title,
                                context.getString(R.string.home_mix_based_on_reason, spotlight.reason),
                                spotlight.tracks,
                                spotlight.colors
                            )
                        }
                    )
                }
            }

            if (homeFeedFailure != null || generalErrorMessage != null) {
                item(key = "home_error_banners") {
                    HomeRecoveryBanners(
                        homeFeedFailure = homeFeedFailure,
                        actionErrorMessage = generalErrorMessage,
                        onRetryHomeFeed = viewModel::retryHomeFeed,
                        onNavigateToSettings = onNavigateToSettings,
                        onDismissActionError = viewModel::clearActionErrorMessage
                    )
                }
            }

            // Skeleton Loading State
            if (isLoading && homeFeedState.recentPlays.isEmpty()) {
                item(key = "skeleton") {
                    HomeSkeletonLoader()
                }
            } else {
                // SECTION 1: RECENT PLAYS (Dynamic Horizontal Cards)
                if (homeFeedState.recentPlays.isNotEmpty()) {
                    item(key = "recent_plays") {
                        RecentPlaysSection(
                            recentPlays = homeFeedState.recentPlays,
                            baseUrl = baseUrl,
                            token = token,
                            onPlayClick = { recent ->
                                if (recent.tracks.isNotEmpty()) {
                                    viewModel.playTrackItem(recent.tracks.first(), recent.tracks)
                                }
                            },
                            onMoreClick = { recent ->
                                if (recent.tracks.isNotEmpty()) {
                                    val track = recent.tracks.first()
                                    activeContextMenu = ContextMenuItem.Track(
                                        ratingKey = track.ratingKey,
                                        title = track.title,
                                        artist = track.artist,
                                        album = track.album,
                                        key = track.key,
                                        thumb = track.thumb,
                                        duration = track.duration,
                                        albumRatingKey = track.albumRatingKey,
                                        albumArtist = track.albumArtist,
                                        artistRatingKey = track.artistRatingKey
                                    )
                                }
                            }
                        )
                    }
                }

                // SECTION 2: RECENTLY ADDED ALBUMS (Horizontally Scrolling Cards)
                if (recentlyAddedLoadError != null) {
                    item(key = "recently_added_error") {
                        HomeErrorBanner(
                            message = requireNotNull(recentlyAddedLoadError),
                            onNavigateToSettings = onNavigateToSettings,
                            onRetry = viewModel::loadRecentlyAdded,
                            isRetrying = isLoadingRecentlyAdded
                        )
                    }
                } else if (isLoadingRecentlyAdded && !companionConfigured && homeFeedState.recentlyAdded.isEmpty()) {
                    item(key = "recently_added_loading") {
                        HomeRecentlyAddedLoading()
                    }
                }

                if (homeFeedState.recentlyAdded.isNotEmpty()) {
                    item(key = "recently_added") {
                        RecentlyAddedSection(
                            albums = homeFeedState.recentlyAdded,
                            baseUrl = baseUrl,
                            token = token,
                            onAlbumClick = { album ->
                                val attachedAlbum = homeFeedState.albumShelves
                                    .asSequence()
                                    .flatMap { it.albums.asSequence() }
                                    .firstOrNull {
                                        it.title == album.title &&
                                            it.artist == (album.parentTitle?.takeIf(String::isNotBlank) ?: "Various Artists")
                                    }
                                if (attachedAlbum != null) {
                                    onNavigateToAlbumWithTracks(
                                        attachedAlbum.id,
                                        attachedAlbum.title,
                                        attachedAlbum.tracks
                                    )
                                } else {
                                    onNavigateToAlbum(album.ratingKey, album.title)
                                }
                            },
                            onMoreClick = { album ->
                                activeContextMenu = ContextMenuItem.Album(
                                    ratingKey = album.ratingKey,
                                    title = album.title,
                                    artist = album.parentTitle?.takeIf(String::isNotBlank) ?: "Various Artists",
                                    thumb = album.thumb ?: ""
                                )
                            }
                        )
                    }
                }

                // Album-first discovery shelves. A tap opens the album detail;
                // playback stays behind the detail screen's explicit controls.
                if (homeFeedState.albumShelves.isNotEmpty()) {
                    homeFeedState.albumShelves
                        .filterNot { it.title.startsWith("Daily Mix", ignoreCase = true) }
                        .forEach { shelf ->
                            item(key = "shelf_${shelf.id}") {
                                AlbumRecommendationShelfSection(
                                    shelf = shelf,
                                    baseUrl = baseUrl,
                                    token = token,
                                    onOpenAlbum = { album ->
                                        onNavigateToAlbumWithTracks(album.id, album.title, album.tracks)
                                    },
                                    onMoreClick = { album ->
                                        activeContextMenu = ContextMenuItem.Album(
                                            ratingKey = album.id,
                                            title = album.title,
                                            artist = album.artist,
                                            thumb = album.thumb,
                                            tracks = album.tracks
                                        )
                                    }
                                )
                            }
                        }
                }

                // The first mix is already rendered as the spotlight above.
                // Keep it out of the list so the same server recommendation is
                // not presented twice.
                val additionalMixes = homeFeedState.dailyMixes.drop(1)
                if (additionalMixes.isNotEmpty()) {
                    item(key = "daily_mixes") {
                        DailyMixesSection(
                            mixes = additionalMixes,
                            baseUrl = baseUrl,
                            token = token,
                            onMixClick = { mix ->
                                onNavigateToCustomPlaylist(
                                    "daily_mix",
                                    mix.id,
                                    mix.title,
                                    context.getString(R.string.home_mix_based_on_reason, mix.reason),
                                    mix.tracks,
                                    mix.colors
                                )
                            }
                        )
                    }
                }

                // SECTION 4: MADE FOR YOU (Server rows without album payloads)
                if (homeFeedState.madeForYou.isNotEmpty()) {
                    item(key = "made_for_you") {
                        MadeForYouSection(
                            madeForYou = homeFeedState.madeForYou,
                            source = homeFeedState.source,
                            baseUrl = baseUrl,
                            token = token,
                            onPlayClick = { mfyItem ->
                                if (mfyItem.tracks.isNotEmpty()) {
                                    viewModel.playbackManager.playQueue(mfyItem.tracks, 0)
                                }
                            },
                            onAddQueueClick = { mfyItem ->
                                viewModel.playbackManager.addTracksToQueue(mfyItem.tracks)
                            }
                        )
                    }
                }

                // SECTION 6: MORE FROM [GENRE / COLLECTION]
                if (homeFeedState.moreFromSections.isNotEmpty()) {
                    homeFeedState.moreFromSections.forEach { section ->
                        item(key = "more_from_${section.title}") {
                            MoreFromSectionLayout(
                                section = section,
                                baseUrl = baseUrl,
                                token = token,
                                onTrackClick = { track ->
                                    viewModel.playTrackItem(track, section.tracks)
                                },
                                onMoreClick = { track ->
                                    activeContextMenu = ContextMenuItem.Track(
                                        ratingKey = track.ratingKey,
                                        title = track.title,
                                        artist = track.artist,
                                        album = track.album,
                                        key = track.key,
                                        thumb = track.thumb,
                                        duration = track.duration,
                                        albumRatingKey = track.albumRatingKey,
                                        albumArtist = track.albumArtist,
                                        artistRatingKey = track.artistRatingKey
                                    )
                                }
                            )
                        }
                    }
                }


                // SECTION 7: MOST PLAYED THIS MONTH
                if (homeFeedState.mostPlayedThisMonth.isNotEmpty()) {
                    item(key = "most_played") {
                        MostPlayedSection(
                            items = homeFeedState.mostPlayedThisMonth,
                            baseUrl = baseUrl,
                            token = token,
                            onTrackClick = { trackItem ->
                                viewModel.playTrackItem(trackItem, homeFeedState.mostPlayedThisMonth.map { it.track })
                            },
                            onMoreClick = { trackItem ->
                                activeContextMenu = ContextMenuItem.Track(
                                    ratingKey = trackItem.ratingKey,
                                    title = trackItem.title,
                                    artist = trackItem.artist,
                                    album = trackItem.album,
                                    key = trackItem.key,
                                    thumb = trackItem.thumb,
                                    duration = trackItem.duration,
                                    albumRatingKey = trackItem.albumRatingKey,
                                    albumArtist = trackItem.albumArtist,
                                    artistRatingKey = trackItem.artistRatingKey
                                )
                            }
                        )
                    }
                }

                // SECTION 8: ON THIS DAY (Anniversaries / Classics throwback)
                homeFeedState.onThisDay?.let { item ->
                    item(key = "on_this_day") {
                        OnThisDaySection(
                            onThisDay = item,
                            baseUrl = baseUrl,
                            token = token,
                            onPlayClick = {
                                if (item.tracks.isNotEmpty()) {
                                    viewModel.playbackManager.playQueue(item.tracks, 0)
                                }
                            }
                        )
                    }
                }

                if (!showFirstRunPairing &&
                    homeFeedState.recentPlays.isEmpty() &&
                    homeFeedState.recentlyAdded.isEmpty() &&
                    homeFeedState.albumShelves.isEmpty() &&
                    homeFeedState.dailyMixes.isEmpty() &&
                    homeFeedState.madeForYou.isEmpty() &&
                    homeFeedState.mostPlayedThisMonth.isEmpty() &&
                    homeFeedState.onThisDay == null
                ) {
                    item(key = "empty_state") {
                        HomeEmptyState(
                            isConfigured = isConfigured,
                            hasCachedMusic = cachedCount > 0,
                            onNavigateToSettings = onNavigateToSettings,
                            onStartPairing = onStartPairing
                        )
                    }
                }
            }
        }

        // Context Menu Overlay Sheet
        activeContextMenu?.let { item ->
            MusicContextMenu(
                item = item,
                viewModel = viewModel,
                onDismiss = { activeContextMenu = null },
                onNavigateToArtist = onNavigateToArtist,
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToAlbumWithTracks = onNavigateToAlbumWithTracks
            )
        }

    }
}

@Composable
internal fun HomeEmptyState(
    isConfigured: Boolean,
    hasCachedMusic: Boolean,
    onNavigateToSettings: () -> Unit,
    onStartPairing: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("home_empty_state"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (hasCachedMusic) Icons.Rounded.CloudOff else Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = when {
                    !isConfigured && hasCachedMusic -> stringResource(R.string.home_offline_ready_title)
                    !isConfigured -> stringResource(R.string.home_connect_spotcore_title)
                    !hasCachedMusic -> stringResource(R.string.home_setup_library_title)
                    else -> stringResource(R.string.home_library_ready_title)
                },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = when {
                    !isConfigured && hasCachedMusic -> stringResource(R.string.home_offline_ready_body)
                    !isConfigured -> stringResource(R.string.home_pairing_body)
                    !hasCachedMusic -> stringResource(R.string.home_setup_library_body)
                    else -> stringResource(R.string.home_library_ready_body)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (!isConfigured) {
                Button(onClick = onStartPairing, modifier = Modifier.testTag("home_pairing_cta")) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_pairing_cta))
                }
                TextButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("home_connection_settings_button")
                ) {
                    Text(stringResource(R.string.home_connection_settings))
                }
            } else if (!hasCachedMusic) {
                Button(onClick = onNavigateToSettings, modifier = Modifier.testTag("home_setup_library_cta")) {
                    Icon(Icons.Rounded.Sync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_setup_library_cta))
                }
            }
        }
    }
}

@Composable
internal fun HomeErrorBanner(
    message: String,
    technicalDetails: String? = null,
    onNavigateToSettings: () -> Unit,
    onRetry: () -> Unit,
    isRetrying: Boolean = false
) {
    var showingTechnicalDetails by remember(message, technicalDetails) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.errorContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.error.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.error)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_error_title),
                    color = colors.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    message,
                    color = colors.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        error(message)
                    }
                )
                if (!technicalDetails.isNullOrBlank()) {
                    val detailsStateDescription = stringResource(
                        if (showingTechnicalDetails) R.string.recovery_details_expanded
                        else R.string.recovery_details_collapsed
                    )
                    TextButton(
                        onClick = { showingTechnicalDetails = !showingTechnicalDetails },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("home_error_details_toggle")
                            .semantics { stateDescription = detailsStateDescription }
                    ) {
                        Text(stringResource(if (showingTechnicalDetails) R.string.recovery_hide_details else R.string.recovery_show_details))
                    }
                    if (showingTechnicalDetails) {
                        Text(
                            technicalDetails,
                            color = colors.onErrorContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.testTag("home_error_technical_details")
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onRetry,
                        enabled = !isRetrying,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("home_retry_button")
                    ) {
                        Text(stringResource(if (isRetrying) R.string.home_retrying else R.string.home_retry))
                    }
                    TextButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("home_settings_button")
                    ) { Text(stringResource(R.string.home_open_settings)) }
                }
            }
        }
    }
}

@Composable
internal fun HomeRecentlyAddedLoading() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.home_loading_recently_added),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
internal fun HomeRecoveryBanners(
    homeFeedFailure: FailurePresentation?,
    actionErrorMessage: String?,
    onRetryHomeFeed: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onDismissActionError: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        homeFeedFailure?.let { failure ->
            HomeErrorBanner(
                message = failure.message,
                technicalDetails = failure.technicalDetails,
                onNavigateToSettings = onNavigateToSettings,
                onRetry = onRetryHomeFeed
            )
        }

        actionErrorMessage
            ?.takeIf { it != homeFeedFailure?.message }
            ?.let { message ->
                HomeActionErrorBanner(
                    message = message,
                    onDismiss = onDismissActionError
                )
            }
    }
}

@Composable
internal fun HomeActionErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.errorContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.error.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.error)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_action_error_title),
                    color = colors.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    message,
                    color = colors.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        error(message)
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("home_action_error_dismiss_button")
                    ) {
                        Text(stringResource(R.string.home_dismiss_error))
                    }
                }
            }
        }
    }
}

@Composable
internal fun StationDetailsDialog(
    station: RecommendedStation,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onPlay: (RadioRequest) -> Unit
) {
    var stationRecipeSaved by remember(station.id) { mutableStateOf(false) }
    val cachedTracks by produceState<List<CachedTrack>?>(null, station.id) {
        value = runCatching { viewModel.repository.getCachedTracksList() }.getOrDefault(emptyList())
    }
    val recentHistory by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val cachedTrackList = cachedTracks.orEmpty()
    var trackCount by remember { mutableFloatStateOf(40f) }
    var discovery by remember { mutableStateOf(DiscoveryLevel.BALANCED) }
    var cooldown by remember { mutableFloatStateOf(7f) }
    var selectedFacet by remember(station.id) { mutableStateOf<String?>(null) }
    var selectedDecade by remember(station.id) { mutableStateOf<Int?>(null) }
    var selectedCollection by remember(station.id) { mutableStateOf<String?>(null) }
    var selectedArtist by remember(station.id) { mutableStateOf<String?>(null) }
    val facets = remember(cachedTracks, station.type) {
        stationRadioFacetChoices(station.type, cachedTrackList)
    }
    val decades = remember(cachedTracks) {
        val extracted = cachedTrackList.mapNotNull { it.year }.map { (it / 10) * 10 }
        val defaults = listOf(2020, 2010, 2000, 1990, 1980, 1970, 1960, 1950)
        (extracted + defaults).distinct().sortedDescending().take(12)
    }
    val collections = remember(cachedTracks) {
        cachedTrackList.flatMap { it.collections.split('|') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted().take(12)
    }
    val artists = remember(cachedTracks) {
        cachedTrackList.map { it.artist }.filter { it.isNotBlank() }.distinct().sorted().take(12)
    }
    LaunchedEffect(facets, decades, collections, artists, station.type) {
        if (facets.none { it == selectedFacet }) selectedFacet = facets.firstOrNull()
        if (selectedDecade == null) selectedDecade = decades.firstOrNull()
        if (selectedCollection == null) selectedCollection = collections.firstOrNull()
        if (selectedArtist == null) selectedArtist = artists.firstOrNull()
    }
    val previewRequest = remember(
        station.type,
        selectedArtist,
        selectedFacet,
        selectedCollection,
        selectedDecade,
        trackCount,
        discovery,
        cooldown
    ) {
        stationRadioRequest(
            type = station.type,
            seedArtist = selectedArtist,
            selectedFacet = selectedFacet,
            collection = selectedCollection,
            decadeStart = selectedDecade,
            trackCount = trackCount.toInt(),
            discoveryLevel = discovery,
            recentCooldownDays = cooldown.toInt()
        )
    }
    val matchingTrackCount = remember(cachedTrackList, previewRequest, recentHistory) {
        if (cachedTracks == null) null else RadioService().generate(previewRequest, cachedTrackList, recentHistory).size
    }
    val selectionExplanation = when {
        selectedArtist != null && station.type == RadioType.ARTIST_RADIO -> stringResource(R.string.station_selection_artist, selectedArtist!!)
        selectedFacet != null && station.type == RadioType.GENRE_RADIO -> stringResource(R.string.station_selection_genre, selectedFacet!!)
        selectedFacet != null && station.type == RadioType.MOOD_RADIO -> stringResource(R.string.station_selection_mood, selectedFacet!!)
        selectedFacet != null && station.type == RadioType.STYLE_RADIO -> stringResource(R.string.station_selection_style, selectedFacet!!)
        selectedCollection != null && station.type == RadioType.COLLECTION_RADIO -> stringResource(R.string.station_selection_collection, selectedCollection!!)
        selectedDecade != null && station.type in setOf(RadioType.DECADE_RADIO, RadioType.TIME_TRAVEL) -> stringResource(R.string.station_selection_decade, selectedDecade!!, selectedDecade!! + 9)
        station.type == RadioType.RECENTLY_ADDED_RADIO -> stringResource(R.string.station_selection_recently_added)
        station.type == RadioType.DEEP_CUTS -> stringResource(R.string.station_selection_deep_cuts)
        station.type == RadioType.FORGOTTEN_FAVORITES -> stringResource(R.string.station_selection_forgotten_favorites)
        else -> stringResource(R.string.station_selected_from_library)
    }
    val trackCountLabel = stringResource(R.string.station_track_count_label)
    val repeatFilterLabel = stringResource(R.string.station_repeat_filter_label)
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(station.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(station.subtitle, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = when (matchingTrackCount) {
                        null -> stringResource(stationLibrarySummaryString(StationLibrarySummary.Checking))
                        0 -> stringResource(stationLibrarySummaryString(StationLibrarySummary.NoMatches))
                        else -> stringResource(
                            stationLibrarySummaryString(StationLibrarySummary.Matches(matchingTrackCount)),
                            matchingTrackCount
                        )
                    },
                    color = if (matchingTrackCount == 0) colors.error else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = selectionExplanation,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(stringResource(R.string.station_track_count, trackCount.toInt()))
                Slider(
                    value = trackCount,
                    onValueChange = { trackCount = it },
                    valueRange = 10f..100f,
                    steps = 8,
                    modifier = Modifier.semantics {
                        contentDescription = trackCountLabel
                    }
                )
                Text(stringResource(R.string.station_discovery_label))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DiscoveryLevel.values().forEach { level ->
                        FilterChip(selected = discovery == level, onClick = { discovery = level }, label = { Text(stationDiscoveryLabel(level), fontSize = 11.sp) })
                    }
                }
                Text(
                    if (cooldown.toInt() == 0) stringResource(R.string.station_repeat_filter_none)
                    else pluralStringResource(R.plurals.station_repeat_filter_days, cooldown.toInt(), cooldown.toInt())
                )
                Slider(
                    value = cooldown,
                    onValueChange = { cooldown = it },
                    valueRange = 0f..30f,
                    steps = 5,
                    modifier = Modifier.semantics {
                        contentDescription = repeatFilterLabel
                    }
                )
                if (station.type == RadioType.GENRE_RADIO || station.type == RadioType.MOOD_RADIO || station.type == RadioType.STYLE_RADIO) {
                    Text(stringResource(stationRadioFacetLabel(station.type)))
                    if (facets.isEmpty()) {
                        Text(
                            text = stringResource(stationRadioEmptyChoicesString(station.type)),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(facets) { facet ->
                                FilterChip(selected = selectedFacet == facet, onClick = { selectedFacet = facet }, label = { Text(facet, fontSize = 11.sp) })
                            }
                        }
                    }
                }
                if (station.type == RadioType.DECADE_RADIO || station.type == RadioType.TIME_TRAVEL) {
                    Text(stringResource(R.string.station_decade_label))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(decades) { decade ->
                            FilterChip(selected = selectedDecade == decade, onClick = { selectedDecade = decade }, label = { Text("$decade", fontSize = 11.sp) })
                        }
                    }
                }
                if (station.type == RadioType.COLLECTION_RADIO) {
                    Text(stringResource(R.string.station_collection_label))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(collections) { collection ->
                            FilterChip(selected = selectedCollection == collection, onClick = { selectedCollection = collection }, label = { Text(collection, fontSize = 11.sp) })
                        }
                    }
                }
                if (station.type == RadioType.ARTIST_RADIO) {
                    Text(stringResource(R.string.station_artist_label))
                    Text(
                        text = selectedArtist?.let { stringResource(R.string.station_selected_artist, it) }
                            ?: stringResource(stationArtistStatusString(isLoading = cachedTracks == null)),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(artists) { artist ->
                            FilterChip(selected = selectedArtist == artist, onClick = { selectedArtist = artist }, label = { Text(artist, fontSize = 11.sp) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPlay(previewRequest) }, enabled = when (station.type) {
                RadioType.ARTIST_RADIO -> selectedArtist != null
                RadioType.MOOD_RADIO, RadioType.STYLE_RADIO, RadioType.GENRE_RADIO -> selectedFacet != null
                RadioType.DECADE_RADIO -> selectedDecade != null
                RadioType.COLLECTION_RADIO -> selectedCollection != null
                else -> true
            }) { Text(stringResource(R.string.station_play_action)) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.station_cancel_action)) }
                TextButton(
                    onClick = {
                        val recipeName = listOfNotNull(
                            previewRequest.seedArtist,
                            previewRequest.seedAlbum,
                            previewRequest.genre,
                            previewRequest.mood,
                            previewRequest.style,
                            previewRequest.collection
                        ).firstOrNull()?.let { "$it Radio" } ?: station.title
                        viewModel.saveStationRecipe(SavedStationRecipe.fromRequest(recipeName, previewRequest))
                        stationRecipeSaved = true
                    },
                    modifier = Modifier.testTag("station_save_recipe_details")
                ) {
                    Text(
                        stringResource(
                            if (stationRecipeSaved) R.string.discover_station_recipe_saved
                            else R.string.discover_save_station_recipe
                        )
                    )
                }
            }
        }
    )
}

// ==========================================
// Subcomponents & UI Block Layouts
// ==========================================

@Composable
fun HomeHeader(
    libraryName: String,
    isConfigured: Boolean,
    hasConnectionError: Boolean,
    hasCachedMusic: Boolean = false,
    source: HomeFeedSource,
    onNavigateToSettings: () -> Unit,
    companionConfigured: Boolean = false,
    connectionStatus: CompanionConnectionStatus = CompanionConnectionStatus.CHECKING,
    showPairingGuidance: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Dns,
                    contentDescription = null,
                    tint = when {
                        companionConfigured && CompanionConnectionPresentation.isHealthy(connectionStatus) -> Color(0xFF047857)
                        companionConfigured && connectionStatus == CompanionConnectionStatus.CHECKING -> colorScheme.primary
                        companionConfigured && connectionStatus == CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE -> Color(0xFF92400E)
                        !companionConfigured && isConfigured && !hasConnectionError -> Color(0xFF047857)
                        else -> colorScheme.error
                    },
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = when {
                        companionConfigured -> stringResource(connectionStatusLabelResource(connectionStatus))
                        hasConnectionError -> if (companionConfigured) stringResource(R.string.connection_unavailable) else stringResource(R.string.connection_unavailable_generic)
                        source == HomeFeedSource.SPOTCORE -> stringResource(
                            connectionStatusLabelResource(CompanionConnectionStatus.CONNECTED)
                        )
                        isConfigured -> stringResource(R.string.connection_library_ready)
                        hasCachedMusic -> stringResource(R.string.connection_offline_mode)
                        else -> stringResource(R.string.connection_connect_spotcore)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (libraryName.isNotEmpty()) libraryName else stringResource(R.string.home_default_library_name),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = colorScheme.onSurface,
                    letterSpacing = (-0.5).sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showPairingGuidance) {
                Text(
                    text = when {
                        companionConfigured && connectionStatus == CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE -> stringResource(R.string.connection_offline_body)
                        companionConfigured && connectionStatus == CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE -> stringResource(R.string.connection_unavailable_body)
                        companionConfigured && connectionStatus == CompanionConnectionStatus.CHECKING -> stringResource(R.string.connection_checking_body)
                        hasConnectionError && hasCachedMusic -> stringResource(R.string.connection_cached_refresh)
                        hasConnectionError -> stringResource(R.string.connection_unavailable_body)
                        source == HomeFeedSource.SPOTCORE -> stringResource(R.string.connection_connected_body)
                        isConfigured -> stringResource(R.string.connection_local_library_body)
                        hasCachedMusic -> stringResource(R.string.connection_cached_body)
                        else -> stringResource(R.string.home_pairing_body)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colorScheme.onSurfaceVariant,
                        letterSpacing = 0.2.sp
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (
                companionConfigured &&
                !hasConnectionError &&
                (connectionStatus == CompanionConnectionStatus.TEMPORARILY_UNAVAILABLE ||
                    connectionStatus == CompanionConnectionStatus.OFFLINE_LIBRARY_AVAILABLE)
            ) {
                TextButton(
                    onClick = onNavigateToSettings,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    modifier = Modifier.testTag("home_reconnect_settings")
                ) {
                    Text(stringResource(R.string.home_connection_settings))
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.size(48.dp).testTag("home_settings_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.nav_settings),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeSpotlight(
    mix: DailyMix,
    source: HomeFeedSource,
    baseUrl: String,
    token: String,
    onPlay: () -> Unit,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val cover = mix.tracks.firstOrNull()?.thumb.orEmpty()
    val imageRequest = remember(cover, baseUrl, token) {
        cover.takeIf(String::isNotBlank)?.let {
            authenticatedArtworkRequest(context, resolveArtworkUrl(baseUrl, it), token)
        }
    }
    val colors = mix.colors.map(::Color).ifEmpty {
        listOf(Color(0xFF312E81), Color(0xFF0E7490))
    }
    val openMixLabel = stringResource(R.string.home_open_spotlight_accessibility, mix.title)
    val playMixLabel = stringResource(R.string.home_play_mix_accessibility, mix.title)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(190.dp * fontScale)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = openMixLabel },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(colors))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (source == HomeFeedSource.SPOTCORE) {
                            stringResource(R.string.home_spotlight_from_library)
                        } else {
                            stringResource(R.string.home_spotlight_made_for_you)
                        },
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = mix.title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mix.reason,
                        color = Color.White.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Button(
                        onClick = onPlay,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .semantics { contentDescription = playMixLabel },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF111827)
                        )
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.common_action_play), fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageRequest != null) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                letterSpacing = (-0.2).sp
            )
        )
        if (onSeeAllClick != null) {
            TextButton(
                onClick = onSeeAllClick,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.testTag("home_see_all_button")
            ) {
                Text(
                    text = stringResource(R.string.home_action_see_all),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 1: RECENT PLAYS
// -------------------------------------------------------------
@Composable
fun RecentPlaysSection(
    recentPlays: List<HomeRecentPlay>,
    baseUrl: String,
    token: String,
    onPlayClick: (HomeRecentPlay) -> Unit,
    onMoreClick: (HomeRecentPlay) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = stringResource(R.string.home_section_recent_plays))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(recentPlays, key = { index, play -> "rp_${index}_${play.id}" }) { _, play ->
                RecentPlayCard(
                    play = play,
                    baseUrl = baseUrl,
                    token = token,
                    onPlayClick = { onPlayClick(play) },
                    onMoreClick = { onMoreClick(play) }
                )
            }
        }
    }
}

@Composable
fun RecentPlayCard(
    play: HomeRecentPlay,
    baseUrl: String,
    token: String,
    onPlayClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    val imageUrl = if (play.thumb.isNotEmpty()) resolveArtworkUrl(baseUrl, play.thumb) else null

    Card(
        modifier = Modifier
            .width(140.dp)
            .homeTileGesture(
                onClickLabel = stringResource(R.string.home_tile_play_accessibility, play.title),
                onLongClickLabel = stringResource(R.string.details_more_options_accessibility, play.title),
                onClick = onPlayClick,
                onLongClick = onMoreClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(10.dp)
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
                                        colors = listOf(Color(0xFF312E81), Color(0xFF4C1D95))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Small relative time tag overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = play.relativeTime,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Text(
                text = play.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = localizedArtistPresentation(play.artist),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// SECTION 2: RECENTLY ADDED ALBUMS
// -------------------------------------------------------------
@Composable
fun RecentlyAddedSection(
    albums: List<PlexMetadata>,
    baseUrl: String,
    token: String,
    onAlbumClick: (PlexMetadata) -> Unit,
    onMoreClick: (PlexMetadata) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = stringResource(R.string.home_section_recently_added))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(albums, key = { it.ratingKey }) { album ->
                AlbumCard(
                    album = album,
                    baseUrl = baseUrl,
                    token = token,
                    onClick = { onAlbumClick(album) },
                    onMoreClick = { onMoreClick(album) }
                )
            }
        }
    }
}

@Composable
fun AlbumCard(
    album: PlexMetadata,
    baseUrl: String,
    token: String,
    isDownloaded: Boolean = false,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (!album.thumb.isNullOrEmpty()) resolveArtworkUrl(baseUrl, album.thumb) else null

    Column(
        modifier = Modifier
            .width(130.dp)
            .homeTileGesture(
                onClickLabel = stringResource(R.string.home_tile_open_album_accessibility, album.title),
                onLongClickLabel = stringResource(R.string.details_more_options_accessibility, album.title),
                onClick = onClick,
                onLongClick = onMoreClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .padding(bottom = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                                    colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // Small year label if available
            album.year?.let { yr ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = yr.toString(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (isDownloaded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color(0xFF16A34A).copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Icon(
                        Icons.Rounded.CloudDownload,
                        contentDescription = stringResource(R.string.downloads_available_offline),
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = localizedArtistPresentation(album.parentTitle?.takeIf(String::isNotBlank) ?: "Various Artists"),
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AlbumRecommendationShelfSection(
    shelf: AlbumRecommendationShelf,
    baseUrl: String,
    token: String,
    onOpenAlbum: (AlbumRecommendation) -> Unit,
    onMoreClick: (AlbumRecommendation) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = shelf.title)
        if (shelf.reason.isNotBlank()) {
            Text(
                text = shelf.reason,
                color = Color.White.copy(alpha = 0.52f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(shelf.albums, key = { it.id }) { album ->
                AlbumCard(
                    album = PlexMetadata(
                        ratingKey = album.id,
                        title = album.title,
                        type = "album",
                        thumb = album.thumb,
                        parentTitle = album.artist,
                        year = album.year
                    ),
                    baseUrl = baseUrl,
                    token = token,
                    onClick = { onOpenAlbum(album) },
                    onMoreClick = { onMoreClick(album) }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 3: DAILY MIXES
// -------------------------------------------------------------
@Composable
fun DailyMixesSection(
    mixes: List<DailyMix>,
    baseUrl: String,
    token: String,
    onMixClick: (DailyMix) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = stringResource(R.string.home_section_daily_mixes))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(mixes, key = { it.id }) { mix ->
                MixCard(mix = mix, baseUrl = baseUrl, token = token, onClick = { onMixClick(mix) })
            }
        }
    }
}

@Composable
fun MixCard(
    mix: DailyMix,
    baseUrl: String,
    token: String,
    onClick: () -> Unit
) {
    val gradientColors = mix.colors.map { Color(it) }
    val track = mix.tracks.firstOrNull()
    val context = LocalContext.current
    val openMixLabel = stringResource(R.string.home_open_mix_accessibility, mix.title)
    val imageRequest = remember(track?.thumb, baseUrl, token) {
        if (track != null && track.thumb.isNotEmpty()) {
            authenticatedArtworkRequest(
                context,
                resolveArtworkUrl(baseUrl, track.thumb),
                token
            )
        } else null
    }

    Card(
        modifier = Modifier
            .width(160.dp)
            .height(240.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = openMixLabel }
            .testTag("daily_mix_card_${mix.id}"),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = gradientColors))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top portion: Prominent artist thumbnail
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageRequest != null) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Title and description section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.home_mix_badge),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = mix.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.home_mix_based_on_reason, mix.reason),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            lineHeight = 12.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 4: JUMP BACK IN
// -------------------------------------------------------------
@Composable
fun JumpBackInSection(
    items: List<JumpBackInItem>,
    baseUrl: String,
    token: String,
    onItemClick: (JumpBackInItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = stringResource(R.string.home_section_jump_back_in))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.id }) { jbi ->
                JumpBackInCard(jbi = jbi, baseUrl = baseUrl, token = token, onClick = { onItemClick(jbi) })
            }
        }
    }
}

@Composable
fun JumpBackInCard(
    jbi: JumpBackInItem,
    baseUrl: String,
    token: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (jbi.thumb.isNotEmpty()) resolveArtworkUrl(baseUrl, jbi.thumb) else null

    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .padding(bottom = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = if (jbi.type == "artist") CircleShape else RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = authenticatedArtworkRequest(context, imageUrl, token),
                        contentDescription = jbi.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF3F3F46), Color(0xFF18181B))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (jbi.type) {
                                "artist" -> Icons.Rounded.Person
                                "playlist" -> Icons.Rounded.PlaylistPlay
                                else -> Icons.Rounded.Album
                            },
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Quick play icon overlay on hover/center
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f), if (jbi.type == "artist") CircleShape else RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Text(
            text = jbi.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp
            ),
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = jbi.subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            ),
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------
// SECTION 5: RECOMMENDED STATIONS
// -------------------------------------------------------------
@Composable
fun RecommendedStationsSection(
    stations: List<RecommendedStation>,
    onStationClick: (RecommendedStation) -> Unit
) {
    // resolve the 9 main options
    val libraryRadio = stations.firstOrNull { it.type == RadioType.LIBRARY_RADIO } ?: RecommendedStation("st_lib", "Library Radio", stringResource(R.string.station_description_library), RadioType.LIBRARY_RADIO, listOf(0xFF1E3A8A, 0xFF0D9488))
    val artistMixBuilder = stations.firstOrNull { it.type == RadioType.ARTIST_RADIO } ?: RecommendedStation("st_art", "Artist Mix Builder", stringResource(R.string.station_description_artist), RadioType.ARTIST_RADIO, listOf(0xFF065F46, 0xFF10B981))
    val albumMixBuilder = RecommendedStation("st_alb", "Album Mix Builder", "Build a station around selected albums", RadioType.ALBUM_RADIO, listOf(0xFF4C0519, 0xFFF43F5E))
    val deepCuts = stations.firstOrNull { it.type == RadioType.DEEP_CUTS } ?: RecommendedStation("st_deep", "Deep Cuts Radio", stringResource(R.string.station_description_deep_cuts), RadioType.DEEP_CUTS, listOf(0xFF130026, 0xFF8B5CF6))
    val timeTravel = stations.firstOrNull { it.type == RadioType.TIME_TRAVEL } ?: RecommendedStation("st_time", "Time Travel Radio", stringResource(R.string.station_description_decade), RadioType.TIME_TRAVEL, listOf(0xFF451A03, 0xFFD97706))
    val randomAlbum = stations.firstOrNull { it.type == RadioType.RANDOM_ALBUM } ?: RecommendedStation("st_rand", "Random Album Radio", stringResource(R.string.station_description_random_album), RadioType.RANDOM_ALBUM, listOf(0xFF172554, 0xFF3B82F6))
    val genreRadio = stations.firstOrNull { it.type == RadioType.GENRE_RADIO } ?: RecommendedStation("st_genre", "Genre Radio", stringResource(R.string.station_description_genre), RadioType.GENRE_RADIO, listOf(0xFF0EA5E9, 0xFF2563EB))
    val styleRadio = stations.firstOrNull { it.type == RadioType.STYLE_RADIO } ?: RecommendedStation("st_style", "Style Radio", stringResource(R.string.station_description_style), RadioType.STYLE_RADIO, listOf(0xFF3B0764, 0xFFEC4899))
    val moodRadio = stations.firstOrNull { it.type == RadioType.MOOD_RADIO } ?: RecommendedStation("st_mood", "Mood Radio", stringResource(R.string.station_description_mood), RadioType.MOOD_RADIO, listOf(0xFF022C22, 0xFF059669))
    val decadeRadio = stations.firstOrNull { it.type == RadioType.DECADE_RADIO } ?: RecommendedStation("st_decade", "Decade Radio", stringResource(R.string.station_description_decade), RadioType.DECADE_RADIO, listOf(0xFF431407, 0xFFF97316))

    val gridItems = listOf(
        artistMixBuilder, albumMixBuilder, libraryRadio,
        genreRadio, moodRadio, styleRadio,
        timeTravel, decadeRadio, deepCuts,
        randomAlbum
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        // Updated Header matching reference: small caps, letter spacing, thin line
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.home_section_stations),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        
        // Compact 2-column grid with horizontal tiles
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            gridItems.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { station ->
                        PremiumStationTile(
                            station = station,
                            onClick = { onStationClick(station) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun PremiumStationTile(
    station: RecommendedStation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(station.gradientColors.firstOrNull() ?: 0xFF6366F1)

    val tileCopy = stationTileCopy(station.type)
    val title = tileCopy?.let { stringResource(it.title) } ?: station.title
    val subtitle = stringResource(tileCopy?.subtitle ?: R.string.station_tile_generic_subtitle)

    val icon = when (station.type) {
        RadioType.ARTIST_RADIO -> Icons.Rounded.NorthEast
        RadioType.ALBUM_RADIO -> Icons.Rounded.PlaylistAdd
        RadioType.LIBRARY_RADIO -> Icons.Rounded.Radio
        RadioType.DEEP_CUTS -> Icons.Rounded.Sensors
        RadioType.TIME_TRAVEL -> Icons.Rounded.History
        RadioType.RANDOM_ALBUM -> Icons.Rounded.Shuffle
        RadioType.STYLE_RADIO -> Icons.Rounded.Tune
        RadioType.MOOD_RADIO -> Icons.Rounded.AutoAwesome
        RadioType.DECADE_RADIO -> Icons.Rounded.CalendarToday
        else -> Icons.Rounded.Sensors
    }

    Card(
        modifier = modifier
            .heightIn(min = 68.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)) {
            // Left Accent Line
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(0.4f)
                    .align(Alignment.CenterStart)
                    .padding(start = 0.dp)
                    .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                    .background(accentColor)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 6: MADE FOR YOU
// -------------------------------------------------------------
@Composable
fun MadeForYouSection(
    madeForYou: List<MadeForYouItem>,
    source: HomeFeedSource = HomeFeedSource.PLEX,
    baseUrl: String,
    token: String,
    onPlayClick: (MadeForYouItem) -> Unit,
    onAddQueueClick: (MadeForYouItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = if (source == HomeFeedSource.SPOTCORE) "SpotCore discovery" else "Made For You")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(madeForYou, key = { it.id }) { mfyItem ->
                LargeRecommendationCard(
                    mfyItem = mfyItem,
                    baseUrl = baseUrl,
                    token = token,
                    onPlayClick = { onPlayClick(mfyItem) },
                    onAddQueueClick = { onAddQueueClick(mfyItem) }
                )
            }
        }
    }
}

@Composable
fun LargeRecommendationCard(
    mfyItem: MadeForYouItem,
    baseUrl: String,
    token: String,
    onPlayClick: () -> Unit,
    onAddQueueClick: () -> Unit
) {
    val context = LocalContext.current
    val imageUrl = if (mfyItem.thumb.isNotEmpty()) resolveArtworkUrl(baseUrl, mfyItem.thumb) else null
    val playActionLabel = stringResource(R.string.home_play_recommendation_accessibility, mfyItem.title)
    val addQueueActionLabel = stringResource(R.string.home_add_recommendation_to_queue_accessibility, mfyItem.title)

    Card(
        modifier = Modifier
            .width(280.dp)
            .height(160.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Album art cover left
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(120.dp)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = authenticatedArtworkRequest(context, imageUrl, token),
                        contentDescription = mfyItem.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            // Description info right
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = mfyItem.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mfyItem.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayClick,
                        colors = ButtonDefaults.buttonColors(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = playActionLabel }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.common_action_play), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onAddQueueClick,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = addQueueActionLabel }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Queue,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 7: MORE FROM [GENRE / COLLECTION]
// -------------------------------------------------------------
@Composable
fun MoreFromSectionLayout(
    section: MoreFromSection,
    baseUrl: String,
    token: String,
    onTrackClick: (TrackItem) -> Unit,
    onMoreClick: (TrackItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = section.title)
        section.reason?.let { reason ->
            Text(reason, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(section.tracks, key = { it.ratingKey }) { track ->
                TrackCard(
                    track = track,
                    baseUrl = baseUrl,
                    token = token,
                    onClick = { onTrackClick(track) },
                    onMoreClick = { onMoreClick(track) }
                )
            }
        }
    }
}

@Composable
fun TrackCard(
    track: TrackItem,
    baseUrl: String,
    token: String,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (track.thumb.isNotEmpty()) resolveArtworkUrl(baseUrl, track.thumb) else null

    Column(
        modifier = Modifier
            .width(120.dp)
            .homeTileGesture(
                onClickLabel = stringResource(R.string.home_tile_play_accessibility, track.title),
                onLongClickLabel = stringResource(R.string.details_more_options_accessibility, track.title),
                onClick = onClick,
                onLongClick = onMoreClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

        }

        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = localizedArtistPresentation(track.artist),
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------
// SECTION 8: MOST PLAYED THIS MONTH
// -------------------------------------------------------------
@Composable
fun MostPlayedSection(
    items: List<MostPlayedItem>,
    baseUrl: String,
    token: String,
    onTrackClick: (TrackItem) -> Unit,
    onMoreClick: (TrackItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = stringResource(R.string.home_section_most_played))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.track.ratingKey }) { item ->
                Box(modifier = Modifier.width(140.dp)) {
                    TrackCard(
                        track = item.track,
                        baseUrl = baseUrl,
                        token = token,
                        onClick = { onTrackClick(item.track) },
                        onMoreClick = { onMoreClick(item.track) }
                    )

                    // play counts indicator overlay badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color(0xFF818CF8), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = localizedPlayCountLabel(item.playCount),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 9: ON THIS DAY
// -------------------------------------------------------------
@Composable
fun OnThisDaySection(
    onThisDay: OnThisDayItem,
    baseUrl: String,
    token: String,
    onPlayClick: () -> Unit
) {
    val context = LocalContext.current
    val imageUrl = if (onThisDay.thumb.isNotEmpty()) resolveArtworkUrl(baseUrl, onThisDay.thumb) else null
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(title = stringResource(R.string.home_section_on_this_day))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Drawing subtle low-opacity throwback gold/brass mesh
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF78350F).copy(alpha = 0.25f), Color(0xFF1E1B4B).copy(alpha = 0.1f))
                            )
                        )
                    }
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Album Art
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(130.dp)
                    ) {
                        if (imageUrl != null) {
                            AsyncImage(
                                model = authenticatedArtworkRequest(context, imageUrl, token),
                                contentDescription = onThisDay.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFFB45309), Color(0xFF78350F))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        // Years tag badge overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .background(Color(0xFFF59E0B), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = onThisDay.timeAgo,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }
                    }

                    // Release Information details
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = onThisDay.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = colors.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = localizedArtistPresentation(onThisDay.artist),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = onThisDay.matchReason,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                ),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = onPlayClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.home_on_this_day_play_album),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 10: NEW RELEASES FOR YOU
// -------------------------------------------------------------
@Composable
fun NewReleasesSection(
    releases: List<NewReleaseItem>,
    baseUrl: String,
    token: String,
    onReleaseClick: (NewReleaseItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = stringResource(R.string.home_section_new_releases))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(releases, key = { it.title + it.artist }) { release ->
                NewReleaseCard(release = release, baseUrl = baseUrl, token = token, onClick = { onReleaseClick(release) })
            }
        }
    }
}

@Composable
fun NewReleaseCard(
    release: NewReleaseItem,
    baseUrl: String,
    token: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (release.thumb.isNotEmpty()) resolveArtworkUrl(baseUrl, release.thumb) else null

    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .padding(bottom = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = authenticatedArtworkRequest(context, imageUrl, token),
                        contentDescription = release.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF047857), Color(0xFF065F46))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FiberNew,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color(0xFF047857), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_new_badge),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }
        }

        Text(
            text = release.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = localizedArtistPresentation(release.artist),
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------
// SECTION 11: HISTORY
// -------------------------------------------------------------
@Composable
fun HistorySection(
    history: List<RecentTrack>,
    baseUrl: String,
    token: String,
    currentTrack: com.example.playback.TrackItem?,
    onTrackClick: (RecentTrack) -> Unit,
    onMoreClick: (RecentTrack) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(title = stringResource(R.string.home_section_history))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            history.take(6).forEach { recent ->
                RecentHistoryRow(
                    recent = recent,
                    baseUrl = baseUrl,
                    token = token,
                    isPlaying = currentTrack?.ratingKey == recent.ratingKey,
                    onClick = { onTrackClick(recent) },
                    onMoreClick = { onMoreClick(recent) }
                )
            }
        }
    }
}

@Composable
fun RecentHistoryRow(
    recent: RecentTrack,
    baseUrl: String,
    token: String,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (recent.thumb.isNotEmpty()) resolveArtworkUrl(baseUrl, recent.thumb) else null
    val activeColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .homeTileGesture(
                onClickLabel = stringResource(R.string.home_tile_play_accessibility, recent.title),
                onLongClickLabel = stringResource(R.string.details_more_options_accessibility, recent.title),
                onClick = onClick,
                onLongClick = onMoreClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) activeColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPlaying) activeColor.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(10.dp)
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
                            .background(Color(0xFF1F2937)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recent.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isPlaying) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (isPlaying) activeColor else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = localizedArtistPresentation(recent.artist),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isPlaying) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

        }
    }
}

// -------------------------------------------------------------
// SKELETON LOADER
// -------------------------------------------------------------
@Composable
fun HomeSkeletonLoader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                )
            }
        }
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(140.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                )
            }
        }
    }
}

@Composable
fun AlbumGridItem(
    album: PlexMetadata,
    baseUrl: String,
    token: String,
    isDownloaded: Boolean = false,
    onMoreClick: () -> Unit,
    onClick: () -> Unit
) {
    AlbumCard(
        album = album,
        baseUrl = baseUrl,
        token = token,
        isDownloaded = isDownloaded,
        onClick = onClick,
        onMoreClick = onMoreClick
    )
}
