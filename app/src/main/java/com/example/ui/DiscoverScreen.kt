package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.DiscoveryTuning
import com.example.data.RadioType
import com.example.data.RecommendedStation
import com.example.data.filterDiscoveryTrackItems
import com.example.playback.TrackItem
import kotlinx.coroutines.launch

@Composable
fun DiscoverScreen(
    viewModel: MusicViewModel,
    onNavigateToSearch: (String) -> Unit,
    onNavigateToAlbum: (String, String, List<TrackItem>) -> Unit,
    onNavigateToCustomPlaylist: (String, String, String, String, List<TrackItem>, List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val homeFeed by viewModel.homeFeedState.collectAsStateWithLifecycle()
    val cachedTracks by viewModel.cachedTracks.collectAsStateWithLifecycle()
    val tuning by viewModel.discoveryTuning.collectAsStateWithLifecycle()
    val savedRecipes by viewModel.savedStationRecipes.collectAsStateWithLifecycle()
    val recentHistory by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrlFlow.collectAsStateWithLifecycle()
    val token by viewModel.tokenFlow.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedStation by remember { mutableStateOf<RecommendedStation?>(null) }
    var activePremiumScreen by remember { mutableStateOf<RecommendedStation?>(null) }
    val cachedByRatingKey = remember(cachedTracks) { cachedTracks.associateBy { it.ratingKey } }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("discover_feed"),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "discover_header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.discover_title),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    DiscoverSearchControls(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = onNavigateToSearch
                    )
                }
            }

            item(key = "discover_tuning") {
                DiscoveryTuningControls(
                    tuning = tuning,
                    onTuningChange = viewModel::updateDiscoveryTuning
                )
            }

            item(key = "discover_stations") {
                RecommendedStationsSection(
                    stations = homeFeed.stations,
                    onStationClick = { station ->
                        if (station.type in discoverBuilderRadioTypes) activePremiumScreen = station
                        else selectedStation = station
                    }
                )
            }

            item(key = "discover_saved_stations") {
                SavedStationRecipesSection(
                    recipes = savedRecipes,
                    onPlay = viewModel::playStationRecipe,
                    onRename = viewModel::renameStationRecipe,
                    onDelete = viewModel::deleteStationRecipe
                )
            }

            item(key = "discover_recommendations_header") {
                Text(
                    stringResource(R.string.discover_recommendations_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            val visibleDailyMixes = homeFeed.dailyMixes.mapNotNull { mix ->
                mix.copy(tracks = filterDiscoveryTrackItems(mix.tracks, cachedByRatingKey, tuning))
                    .takeIf { it.tracks.isNotEmpty() }
            }
            items(visibleDailyMixes, key = { "discover_mix_${it.id}" }) { mix ->
                DiscoverReasonCard(
                    title = mix.title,
                    reason = mix.reason,
                    detail = mix.tracks.take(3).joinToString(" • ") { it.title },
                    onPlay = { viewModel.playDailyMix(mix) },
                    onQueue = { viewModel.playbackManager.addTracksToQueue(mix.tracks) }
                )
            }

            val visibleMadeForYou = homeFeed.madeForYou.mapNotNull { item ->
                item.copy(tracks = filterDiscoveryTrackItems(item.tracks, cachedByRatingKey, tuning))
                    .takeIf { it.tracks.isNotEmpty() }
            }
            items(visibleMadeForYou, key = { "discover_mfy_${it.id}" }) { item ->
                DiscoverReasonCard(
                    title = item.title,
                    reason = item.description,
                    detail = item.artists.joinToString(" • ").ifBlank { "${item.tracks.size} tracks" },
                    onPlay = { viewModel.playbackManager.playQueue(item.tracks, 0) },
                    onQueue = { viewModel.playbackManager.addTracksToQueue(item.tracks) }
                )
            }

            val visibleAlbumShelves = homeFeed.albumShelves.mapNotNull { shelf ->
                shelf.copy(albums = shelf.albums.mapNotNull { album ->
                    album.copy(tracks = filterDiscoveryTrackItems(album.tracks, cachedByRatingKey, tuning))
                        .takeIf { it.tracks.isNotEmpty() }
                }).takeIf { it.albums.isNotEmpty() }
            }
            items(visibleAlbumShelves, key = { "discover_album_shelf_${it.id}" }) { shelf ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(shelf.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(shelf.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    shelf.albums.take(4).forEach { album ->
                        TextButton(
                            onClick = { onNavigateToAlbum(album.id, album.title, album.tracks) },
                            modifier = Modifier.fillMaxWidth().testTag("discover_album_${album.id}")
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(album.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            val visibleMoreFrom = homeFeed.moreFromSections.mapNotNull { section ->
                section.copy(tracks = filterDiscoveryTrackItems(section.tracks, cachedByRatingKey, tuning))
                    .takeIf { it.tracks.isNotEmpty() }
            }
            items(visibleMoreFrom, key = { "discover_more_${it.title}" }) { section ->
                DiscoverReasonCard(
                    title = section.title,
                    reason = section.reason.orEmpty(),
                    detail = section.tracks.take(3).joinToString(" • ") { it.title },
                    onPlay = { viewModel.playbackManager.playQueue(section.tracks, 0) },
                    onQueue = { viewModel.playbackManager.addTracksToQueue(section.tracks) }
                )
            }

            if (visibleDailyMixes.isEmpty() && visibleMadeForYou.isEmpty() && visibleAlbumShelves.isEmpty() && visibleMoreFrom.isEmpty()) {
                item(key = "discover_recommendations_empty") {
                    Text(
                        stringResource(R.string.discover_recommendations_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("discover_recommendations_empty")
                    )
                }
            }
        }

        selectedStation?.let { station ->
            StationDetailsDialog(
                station = station,
                viewModel = viewModel,
                onDismiss = { selectedStation = null },
                onPlay = { request ->
                    selectedStation = null
                    coroutineScope.launch {
                        val tracks = viewModel.generateRadioTracks(request, useDiscoveryTuning = true)
                        if (tracks.isNotEmpty()) {
                            onNavigateToCustomPlaylist(
                                "radio",
                                "discover_radio_${request.type.name}_${System.currentTimeMillis()}",
                                station.title,
                                station.subtitle,
                                tracks,
                                station.gradientColors
                            )
                        } else {
                            viewModel.postErrorMessage(context.getString(radioNoMatchesString(station.type)))
                        }
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = activePremiumScreen != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            activePremiumScreen?.let { station ->
                val onGenerated: (String, String, String, String, List<TrackItem>, List<Long>) -> Unit =
                    { type, id, title, description, tracks, colors ->
                        activePremiumScreen = null
                        onNavigateToCustomPlaylist(type, id, title, description, tracks, colors)
                    }
                when (station.type) {
                    RadioType.ARTIST_RADIO -> ArtistMixBuilderScreen(
                        viewModel = viewModel,
                        baseUrl = baseUrl,
                        token = token,
                        onBack = { activePremiumScreen = null },
                        onNavigateToCustomPlaylist = onGenerated
                    )
                    RadioType.ALBUM_RADIO -> AlbumMixBuilderScreen(
                        viewModel = viewModel,
                        baseUrl = baseUrl,
                        token = token,
                        onBack = { activePremiumScreen = null },
                        onNavigateToCustomPlaylist = onGenerated
                    )
                    RadioType.STYLE_RADIO, RadioType.MOOD_RADIO, RadioType.GENRE_RADIO,
                    RadioType.DECADE_RADIO, RadioType.TIME_TRAVEL -> GenericRadioSelectorScreen(
                        station = station,
                        viewModel = viewModel,
                        onBack = { activePremiumScreen = null },
                        onNavigateToCustomPlaylist = onGenerated
                    )
                    else -> Unit
                }
            }
        }
    }
}

@Composable
internal fun DiscoverSearchControls(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    fun submit() = onSearch(query.trim())
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().testTag("discover_search_input"),
            label = { Text(stringResource(R.string.discover_search_label)) },
            placeholder = { Text(stringResource(R.string.discover_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() })
        )
        Button(
            onClick = ::submit,
            modifier = Modifier.fillMaxWidth().testTag("discover_open_search")
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.discover_search_action))
        }
    }
}

@Composable
private fun DiscoverReasonCard(
    title: String,
    reason: String,
    detail: String,
    onPlay: () -> Unit,
    onQueue: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            if (reason.isNotBlank()) {
                Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPlay, modifier = Modifier.testTag("discover_play_${title.hashCode()}")) {
                    Text(stringResource(R.string.common_action_play))
                }
                TextButton(onClick = onQueue, modifier = Modifier.testTag("discover_queue_${title.hashCode()}")) {
                    Text(stringResource(R.string.discover_add_to_queue))
                }
            }
        }
    }
}

private val discoverBuilderRadioTypes = setOf(
    RadioType.ARTIST_RADIO,
    RadioType.ALBUM_RADIO,
    RadioType.STYLE_RADIO,
    RadioType.MOOD_RADIO,
    RadioType.GENRE_RADIO,
    RadioType.DECADE_RADIO,
    RadioType.TIME_TRAVEL
)
