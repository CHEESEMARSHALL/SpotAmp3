package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowInsetsControllerCompat
import com.example.R

import com.example.ui.theme.MyApplicationTheme

sealed class Screen {
    object Home : Screen()
    object Library : Screen()
    object Discover : Screen()
    data class Search(val initialQuery: String = "") : Screen()
    data class SearchResults(val category: String, val items: List<com.example.data.PlexMetadata>) : Screen()
    object Downloads : Screen()
    object Settings : Screen()
    object QaLab : Screen()
    data class DownloadedAlbumDetail(val artist: String, val album: String) : Screen()
    data class ArtistDetail(val id: String, val name: String) : Screen()
    data class AlbumDetail(
        val id: String,
        val name: String,
        val seedTracks: List<com.example.playback.TrackItem> = emptyList()
    ) : Screen()
    data class PlaylistDetail(val id: Int, val name: String) : Screen()
    data class CustomPlaylistDetail(
        val type: String,
        val id: String,
        val title: String,
        val description: String,
        val seedTracks: List<com.example.playback.TrackItem>,
        val colors: List<Long> = emptyList()
    ) : Screen()
}

internal enum class PhoneDestination { HOME, LIBRARY, DISCOVER, DOWNLOADS, SETTINGS }

internal fun phoneDestinationForScreen(screen: Screen): PhoneDestination = when (screen) {
    Screen.Home -> PhoneDestination.HOME
    Screen.Library -> PhoneDestination.LIBRARY
    Screen.Discover, is Screen.Search, is Screen.SearchResults -> PhoneDestination.DISCOVER
    Screen.Downloads, is Screen.DownloadedAlbumDetail -> PhoneDestination.DOWNLOADS
    Screen.Settings, Screen.QaLab -> PhoneDestination.SETTINGS
    is Screen.ArtistDetail, is Screen.AlbumDetail, is Screen.PlaylistDetail,
    is Screen.CustomPlaylistDetail -> PhoneDestination.LIBRARY
}

internal fun selectedPhoneDestination(stack: List<Screen>): PhoneDestination {
    val root = stack.lastOrNull {
        it is Screen.Home || it is Screen.Library || it is Screen.Discover ||
            it is Screen.Downloads || it is Screen.Settings
    } ?: stack.lastOrNull() ?: Screen.Home
    return phoneDestinationForScreen(root)
}

internal fun firstRunSetupRoute(currentScreen: Screen, showingFirstRunSetup: Boolean): Screen =
    if (showingFirstRunSetup && currentScreen !is Screen.Settings) Screen.Settings else currentScreen

internal fun navigateSearchToConnectionSettings(navigationStack: MutableList<Screen>, query: String) {
    if (navigationStack.lastOrNull() is Screen.Search) {
        navigationStack[navigationStack.lastIndex] = Screen.Search(query)
    }
    navigationStack.add(Screen.Settings)
}

internal fun alwaysShowBottomNavigationLabels(fontScale: Float): Boolean = fontScale < 1.2f

internal fun bottomNavigationDownloadsLabelResource(fontScale: Float): Int =
    if (alwaysShowBottomNavigationLabels(fontScale)) R.string.nav_downloads
    else R.string.nav_downloads_compact

@Composable
fun MainAppScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    var restoredRoute by rememberSaveable { mutableStateOf("home") }
    fun screenForRestoredRoute(route: String): Screen = when {
        route == "library" -> Screen.Library
        route == "discover" -> Screen.Discover
        route == "search" -> Screen.Search()
        route == "downloads" -> Screen.Downloads
        route == "settings" -> Screen.Settings
        route.startsWith("downloaded-album|") -> route.substringAfter("downloaded-album|").split("|", limit = 2).let { parts ->
            Screen.DownloadedAlbumDetail(
                artist = Uri.decode(parts.getOrElse(0) { "" }),
                album = Uri.decode(parts.getOrElse(1) { "" })
            )
        }
        route.startsWith("artist|") -> route.substringAfter("|").split("|", limit = 2).let { parts -> Screen.ArtistDetail(parts[0], parts.getOrElse(1) { parts[0] }) }
        route.startsWith("album|") -> route.substringAfter("|").split("|", limit = 2).let { parts -> Screen.AlbumDetail(parts[0], parts.getOrElse(1) { parts[0] }) }
        else -> Screen.Home
    }
    val navigationStack = remember { mutableStateListOf<Screen>(screenForRestoredRoute(restoredRoute)) }
    val currentScreen = navigationStack.last()
    val selectedDestination = selectedPhoneDestination(navigationStack)
    val screenStateHolder = rememberSaveableStateHolder()

    LaunchedEffect(currentScreen) {
        restoredRoute = when (val screen = currentScreen) {
            Screen.Library -> "library"
            Screen.Discover -> "discover"
            is Screen.Search -> "search"
            Screen.Downloads -> "downloads"
            is Screen.DownloadedAlbumDetail -> "downloaded-album|${Uri.encode(screen.artist)}|${Uri.encode(screen.album)}"
            Screen.Settings -> "settings"
            is Screen.ArtistDetail -> "artist|${screen.id}|${screen.name}"
            is Screen.AlbumDetail -> "album|${screen.id}|${screen.name}"
            else -> "home"
        }
    }

    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token

    var isNowPlayingExpanded by rememberSaveable { mutableStateOf(false) }
    var showPairingDialog by rememberSaveable { mutableStateOf(false) }
    var showingFirstRunSetup by rememberSaveable { mutableStateOf(false) }
    var pairingStartedForFirstRun by rememberSaveable { mutableStateOf(false) }
    val settingsScrollState = rememberScrollState()

    LaunchedEffect(showingFirstRunSetup, currentScreen) {
        if (showingFirstRunSetup) {
            val setupScreen = firstRunSetupRoute(currentScreen, showingFirstRunSetup)
            if (setupScreen != currentScreen) {
                navigationStack.clear()
                navigationStack.add(setupScreen)
            }
            settingsScrollState.scrollTo(0)
        }
    }

    // Navigation Helper
    fun navigateTo(screen: Screen) {
        navigationStack.add(screen)
    }

    fun navigateBack() {
        if (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.lastIndex)
        }
    }

    // Handle system back press
    BackHandler(enabled = true) {
        if (isNowPlayingExpanded) {
            isNowPlayingExpanded = false
        } else if (navigationStack.size > 1) {
            navigateBack()
        }
    }

    val activeTheme by viewModel.activeThemeFlow.collectAsStateWithLifecycle()
    val currentTrack by viewModel.playbackManager.currentTrack.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lightThemeActive = activeTheme == "Warm Light"
    val applyLightStatusBarAppearance = lightThemeActive && !isNowPlayingExpanded

    SideEffect {
        val activity = context.findActivity()
        if (activity != null) {
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = applyLightStatusBarAppearance
                isAppearanceLightNavigationBars = lightThemeActive
            }
        }
    }

    MyApplicationTheme(activeTheme = activeTheme) {
        val background = MaterialTheme.colorScheme.background
        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.secondary

        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(color = background)
                    
                    // Top-left atmospheric blend
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primary.copy(alpha = 0.15f), Color.Transparent),
                            center = Offset(-100f, -100f),
                            radius = size.minDimension * 0.9f
                        ),
                        center = Offset(-100f, -100f),
                        radius = size.minDimension * 0.9f
                    )
                    
                    // Top-right ambient glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(secondary.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(size.width + 100f, size.height * 0.25f),
                            radius = size.minDimension * 0.7f
                        ),
                        center = Offset(size.width + 100f, size.height * 0.25f),
                        radius = size.minDimension * 0.7f
                    )
                }
        ) {
        val isWideScreen = maxWidth >= 600.dp
        val fontScale = LocalDensity.current.fontScale
        val showBottomNavigationLabels = alwaysShowBottomNavigationLabels(fontScale)
        val downloadsNavigationLabel = bottomNavigationDownloadsLabelResource(fontScale)

        Row(modifier = Modifier.fillMaxSize()) {
            // Adaptive Navigation Sidebar for wide screens (tablets, desktops)
            if (isWideScreen) {
                NavigationRail(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color(0xFF818CF8),
                    header = {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier
                                .size(48.dp)
                                .padding(vertical = 12.dp)
                        )
                    },
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    NavigationRailItem(
                        selected = selectedDestination == PhoneDestination.HOME,
                        onClick = { 
                            navigationStack.clear()
                            navigationStack.add(Screen.Home)
                        },
                        icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_home), fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF818CF8),
                            selectedTextColor = Color(0xFF818CF8),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B),
                            indicatorColor = Color.Transparent
                        )
                    )

                    NavigationRailItem(
                        selected = selectedDestination == PhoneDestination.LIBRARY,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(Screen.Library)
                        },
                        icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_library), fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF818CF8),
                            selectedTextColor = Color(0xFF818CF8),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B),
                            indicatorColor = Color.Transparent
                        )
                    )

                    NavigationRailItem(
                        selected = selectedDestination == PhoneDestination.DISCOVER,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(Screen.Discover)
                        },
                        icon = { Icon(Icons.Rounded.Explore, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_discover), fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF818CF8),
                            selectedTextColor = Color(0xFF818CF8),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B),
                            indicatorColor = Color.Transparent
                        )
                    )

                    NavigationRailItem(
                        selected = selectedDestination == PhoneDestination.DOWNLOADS,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(Screen.Downloads)
                        },
                        icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_downloads), fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF818CF8),
                            selectedTextColor = Color(0xFF818CF8),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B),
                            indicatorColor = Color.Transparent
                        )
                    )

                    NavigationRailItem(
                        selected = selectedDestination == PhoneDestination.SETTINGS,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(Screen.Settings)
                        },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings), fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF818CF8),
                            selectedTextColor = Color(0xFF818CF8),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B),
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }

            // Main Screen Panel containing active view and player
            Scaffold(
                bottomBar = {
                    if (!isWideScreen) {
                        Column {
                            if (currentTrack != null) {
                                BottomMiniPlayer(
                                    playbackManager = viewModel.playbackManager,
                                    baseUrl = baseUrl,
                                    token = token,
                                    onExpand = { isNowPlayingExpanded = true }
                                )
                            }
                            
                            val surfaceColor = MaterialTheme.colorScheme.surface
                            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                            val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

                            NavigationBar(
                                containerColor = surfaceColor.copy(alpha = 0.9f),
                                contentColor = onSurfaceColor,
                                modifier = Modifier
                                    .drawBehind {
                                        drawLine(
                                            color = outlineVariantColor.copy(alpha = 0.5f),
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, 0f),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                            ) {
                                NavigationBarItem(
                                    selected = selectedDestination == PhoneDestination.HOME,
                                    onClick = { 
                                        navigationStack.clear()
                                        navigationStack.add(Screen.Home)
                                    },
                                    icon = { Icon(Icons.Rounded.Home, contentDescription = stringResource(R.string.nav_home)) },
                                    label = { Text(stringResource(R.string.nav_home), fontSize = 11.sp) },
                                    alwaysShowLabel = showBottomNavigationLabels,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF818CF8),
                                        selectedTextColor = Color(0xFF818CF8),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B),
                                        indicatorColor = Color.Transparent
                                    )
                                )

                                NavigationBarItem(
                                    selected = selectedDestination == PhoneDestination.LIBRARY,
                                    onClick = {
                                        navigationStack.clear()
                                        navigationStack.add(Screen.Library)
                                    },
                                    icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = stringResource(R.string.nav_library)) },
                                    label = { Text(stringResource(R.string.nav_library), fontSize = 11.sp) },
                                    alwaysShowLabel = showBottomNavigationLabels,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF818CF8),
                                        selectedTextColor = Color(0xFF818CF8),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B),
                                        indicatorColor = Color.Transparent
                                    )
                                )

                                NavigationBarItem(
                                    selected = selectedDestination == PhoneDestination.DISCOVER,
                                    onClick = {
                                        navigationStack.clear()
                                        navigationStack.add(Screen.Discover)
                                    },
                                    icon = { Icon(Icons.Rounded.Explore, contentDescription = stringResource(R.string.nav_discover)) },
                                    label = { Text(stringResource(R.string.nav_discover), fontSize = 11.sp) },
                                    alwaysShowLabel = showBottomNavigationLabels,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF818CF8),
                                        selectedTextColor = Color(0xFF818CF8),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B),
                                        indicatorColor = Color.Transparent
                                    )
                                )

                                NavigationBarItem(
                                    selected = selectedDestination == PhoneDestination.DOWNLOADS,
                                    onClick = {
                                        navigationStack.clear()
                                        navigationStack.add(Screen.Downloads)
                                    },
                                    icon = { Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.nav_downloads)) },
                                    label = { Text(stringResource(downloadsNavigationLabel), fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                    alwaysShowLabel = showBottomNavigationLabels,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF818CF8),
                                        selectedTextColor = Color(0xFF818CF8),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B),
                                        indicatorColor = Color.Transparent
                                    )
                                )

                                NavigationBarItem(
                                    selected = selectedDestination == PhoneDestination.SETTINGS,
                                    onClick = {
                                        navigationStack.clear()
                                        navigationStack.add(Screen.Settings)
                                    },
                                    icon = { Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.nav_settings)) },
                                    label = { Text(stringResource(R.string.nav_settings), fontSize = 11.sp) },
                                    alwaysShowLabel = showBottomNavigationLabels,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF818CF8),
                                        selectedTextColor = Color(0xFF818CF8),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B),
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                },
                containerColor = Color.Transparent,
                modifier = Modifier.weight(1f)
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // Content Layer
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentScreen) {
                            is Screen.Home -> Box(Modifier.padding(innerPadding)) {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToSettings = { navigateTo(Screen.Settings) },
                                    onStartPairing = {
                                        pairingStartedForFirstRun = !viewModel.companionConfigured.value
                                        showPairingDialog = true
                                    },
                                    onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) },
                                    onNavigateToAlbum = { id, name -> navigateTo(Screen.AlbumDetail(id, name)) },
                                    onNavigateToAlbumWithTracks = { id, name, tracks ->
                                        navigateTo(Screen.AlbumDetail(id, name, tracks))
                                    },
                                    onNavigateToCustomPlaylist = { type, id, title, desc, tracks, colors ->
                                        navigateTo(Screen.CustomPlaylistDetail(type, id, title, desc, tracks, colors))
                                    }
                                )
                            }
                            is Screen.Discover -> Box(Modifier.padding(innerPadding)) {
                                screenStateHolder.SaveableStateProvider("discover") {
                                    DiscoverScreen(
                                        viewModel = viewModel,
                                        onNavigateToSearch = { query -> navigateTo(Screen.Search(query)) },
                                        onNavigateToAlbum = { id, name, tracks ->
                                            navigateTo(Screen.AlbumDetail(id, name, tracks))
                                        },
                                        onNavigateToCustomPlaylist = { type, id, title, desc, tracks, colors ->
                                            navigateTo(Screen.CustomPlaylistDetail(type, id, title, desc, tracks, colors))
                                        }
                                    )
                                }
                            }
                            is Screen.Library -> Box(Modifier.padding(innerPadding)) {
                                screenStateHolder.SaveableStateProvider("library") {
                                    LibraryScreen(
                                        viewModel = viewModel,
                                        onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) },
                                        onNavigateToAlbum = { id, name -> navigateTo(Screen.AlbumDetail(id, name)) },
                                        onNavigateToPlaylist = { id, name -> navigateTo(Screen.PlaylistDetail(id, name)) },
                                        onStartPairing = {
                                            pairingStartedForFirstRun = !viewModel.companionConfigured.value
                                            showPairingDialog = true
                                        },
                                        onNavigateToSettings = { navigateTo(Screen.Settings) },
                                        onNavigateToCustomPlaylist = { type, id, title, desc, tracks, colors ->
                                            navigateTo(Screen.CustomPlaylistDetail(type, id, title, desc, tracks, colors))
                                        }
                                    )
                                }
                            }
                            is Screen.Search -> Box(Modifier.padding(innerPadding)) {
                                screenStateHolder.SaveableStateProvider("search") {
                                    SearchScreen(
                                        viewModel = viewModel,
                                        initialQuery = currentScreen.initialQuery,
                                        onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) },
                                        onNavigateToAlbum = { id, name -> navigateTo(Screen.AlbumDetail(id, name)) },
                                        onNavigateToResults = { category, items -> navigateTo(Screen.SearchResults(category, items)) },
                                        onNavigateToSettings = { query ->
                                            navigateSearchToConnectionSettings(navigationStack, query)
                                        },
                                        onStartPairing = {
                                            pairingStartedForFirstRun = !viewModel.companionConfigured.value
                                            showPairingDialog = true
                                        }
                                    )
                                }
                            }
                            is Screen.SearchResults -> Box(Modifier.padding(innerPadding)) {
                                SearchResultsScreen(
                                    category = currentScreen.category,
                                    results = currentScreen.items,
                                    viewModel = viewModel,
                                    onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) },
                                    onNavigateToAlbum = { id, name -> navigateTo(Screen.AlbumDetail(id, name)) }
                                )
                            }
                            is Screen.Downloads -> Box(Modifier.padding(innerPadding)) {
                                DownloadsScreen(
                                    viewModel = viewModel,
                                    onNavigateToDownloadedAlbum = { artist, album ->
                                        navigateTo(Screen.DownloadedAlbumDetail(artist, album))
                                    },
                                    onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) },
                                    onNavigateToAlbum = { id, name -> navigateTo(Screen.AlbumDetail(id, name)) },
                                    onNavigateToLibrary = {
                                        navigationStack.clear()
                                        navigationStack.add(Screen.Library)
                                    },
                                    onStartPairing = {
                                        pairingStartedForFirstRun = !viewModel.companionConfigured.value
                                        showPairingDialog = true
                                    }
                                )
                            }
                            is Screen.Settings -> Box(Modifier.padding(innerPadding)) {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onOpenQaLab = { navigateTo(Screen.QaLab) },
                                    onRequestPairing = {
                                        pairingStartedForFirstRun = !viewModel.companionConfigured.value
                                        showPairingDialog = true
                                    },
                                    settingsScrollState = settingsScrollState,
                                    showFirstRunSetup = showingFirstRunSetup,
                                    onFinishFirstRunSetup = {
                                        showingFirstRunSetup = false
                                        navigationStack.clear()
                                        navigationStack.add(Screen.Home)
                                    },
                                    onDismissFirstRunSetup = {
                                        showingFirstRunSetup = false
                                        navigationStack.clear()
                                        navigationStack.add(Screen.Home)
                                    }
                                )
                            }
                            is Screen.QaLab -> Box(Modifier.padding(innerPadding)) {
                                QaLabScreen(viewModel = viewModel)
                            }
                            is Screen.ArtistDetail -> Box(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                                ArtistDetailScreen(
                                    artistId = currentScreen.id,
                                    artistName = currentScreen.name,
                                    viewModel = viewModel,
                                    onNavigateToAlbum = { id, name -> navigateTo(Screen.AlbumDetail(id, name)) },
                                    onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) },
                                    onNavigateToSearch = { query -> navigateTo(Screen.Search(query)) },
                                    onBack = { navigateBack() }
                                )
                            }
                            is Screen.DownloadedAlbumDetail -> Box(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                                DownloadedAlbumDetailScreen(
                                    albumArtist = currentScreen.artist,
                                    albumName = currentScreen.album,
                                    viewModel = viewModel,
                                    onBack = { navigateBack() },
                                    onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) }
                                )
                            }
                            is Screen.AlbumDetail -> Box(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                                AlbumDetailScreen(
                                    albumId = currentScreen.id,
                                    albumName = currentScreen.name,
                                    seedTracks = currentScreen.seedTracks,
                                    viewModel = viewModel,
                                    onBack = { navigateBack() },
                                    onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) }
                                )
                            }
                            is Screen.PlaylistDetail -> Box(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                                PlaylistDetailScreen(
                                    playlistId = currentScreen.id,
                                    playlistName = currentScreen.name,
                                    viewModel = viewModel,
                                    onBack = { navigateBack() }
                                )
                            }
                            is Screen.CustomPlaylistDetail -> Box(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                                CustomPlaylistDetailScreen(
                                    type = currentScreen.type,
                                    playlistId = currentScreen.id,
                                    playlistName = currentScreen.title,
                                    description = currentScreen.description,
                                    tracks = currentScreen.seedTracks,
                                    colors = currentScreen.colors,
                                    viewModel = viewModel,
                                    onBack = { navigateBack() }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Animated Immersive Now Playing Overlay (Slides up from the bottom!)
        if (showPairingDialog) {
            CompanionPairingDialog(
                viewModel = viewModel,
                onDismiss = {
                    showPairingDialog = false
                    pairingStartedForFirstRun = false
                },
                onPairingComplete = {
                    showPairingDialog = false
                    if (pairingStartedForFirstRun) {
                        showingFirstRunSetup = true
                        if (currentScreen !is Screen.Settings) navigateTo(Screen.Settings)
                    }
                    pairingStartedForFirstRun = false
                }
            )
        }

        AnimatedVisibility(
            visible = isNowPlayingExpanded,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            NowPlayingScreen(
                viewModel = viewModel,
                baseUrl = baseUrl,
                token = token,
                modifier = Modifier.fillMaxSize(),
                onCollapse = { isNowPlayingExpanded = false },
                onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) },
                onNavigateToAlbum = { id, name -> navigateTo(Screen.AlbumDetail(id, name)) },
                onNavigateToDownloads = {
                    isNowPlayingExpanded = false
                    navigationStack.clear()
                    navigationStack.add(Screen.Downloads)
                }
            )
        }
    }
}
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
