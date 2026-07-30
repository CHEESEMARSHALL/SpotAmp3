package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.example.ui.theme.MyApplicationTheme

sealed class Screen {
    object Home : Screen()
    object Library : Screen()
    object Search : Screen()
    object Downloads : Screen()
    object Settings : Screen()
    data class ArtistDetail(val id: String, val name: String) : Screen()
    data class AlbumDetail(val id: String, val name: String) : Screen()
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

@Composable
fun MainAppScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val navigationStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val currentScreen = navigationStack.last()

    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token

    var isNowPlayingExpanded by remember { mutableStateOf(false) }

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

        Row(modifier = Modifier.fillMaxSize()) {
            // Adaptive Navigation Sidebar for wide screens (tablets, desktops)
            if (isWideScreen) {
                NavigationRail(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color(0xFF818CF8),
                    header = {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = "Plex Player",
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
                        selected = currentScreen is Screen.Home,
                        onClick = { 
                            navigationStack.clear()
                            navigationStack.add(Screen.Home)
                        },
                        icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                        label = { Text("Home", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF818CF8),
                            selectedTextColor = Color(0xFF818CF8),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B),
                            indicatorColor = Color.Transparent
                        )
                    )

                    NavigationRailItem(
                        selected = currentScreen is Screen.Library || currentScreen is Screen.ArtistDetail || currentScreen is Screen.AlbumDetail || currentScreen is Screen.PlaylistDetail,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(Screen.Library)
                        },
                        icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = "Library") },
                        label = { Text("Library", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF818CF8),
                            selectedTextColor = Color(0xFF818CF8),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B),
                            indicatorColor = Color.Transparent
                        )
                    )

                    NavigationRailItem(
                        selected = currentScreen is Screen.Search,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(Screen.Search)
                        },
                        icon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                        label = { Text("Search", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF818CF8),
                            selectedTextColor = Color(0xFF818CF8),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B),
                            indicatorColor = Color.Transparent
                        )
                    )

                    NavigationRailItem(
                        selected = currentScreen is Screen.Downloads,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(Screen.Downloads)
                        },
                        icon = { Icon(Icons.Rounded.Download, contentDescription = "Downloads") },
                        label = { Text("Downloads", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF818CF8),
                            selectedTextColor = Color(0xFF818CF8),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B),
                            indicatorColor = Color.Transparent
                        )
                    )

                    NavigationRailItem(
                        selected = currentScreen is Screen.Settings,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(Screen.Settings)
                        },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontSize = 11.sp) },
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
                                    selected = currentScreen is Screen.Home,
                                    onClick = { 
                                        navigationStack.clear()
                                        navigationStack.add(Screen.Home)
                                    },
                                    icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                                    label = { Text("Home", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF818CF8),
                                        selectedTextColor = Color(0xFF818CF8),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B),
                                        indicatorColor = Color.Transparent
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentScreen is Screen.Library || currentScreen is Screen.ArtistDetail || currentScreen is Screen.AlbumDetail || currentScreen is Screen.PlaylistDetail,
                                    onClick = {
                                        navigationStack.clear()
                                        navigationStack.add(Screen.Library)
                                    },
                                    icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = "Library") },
                                    label = { Text("Library", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF818CF8),
                                        selectedTextColor = Color(0xFF818CF8),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B),
                                        indicatorColor = Color.Transparent
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentScreen is Screen.Search,
                                    onClick = {
                                        navigationStack.clear()
                                        navigationStack.add(Screen.Search)
                                    },
                                    icon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                                    label = { Text("Search", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF818CF8),
                                        selectedTextColor = Color(0xFF818CF8),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B),
                                        indicatorColor = Color.Transparent
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentScreen is Screen.Downloads,
                                    onClick = {
                                        navigationStack.clear()
                                        navigationStack.add(Screen.Downloads)
                                    },
                                    icon = { Icon(Icons.Rounded.Download, contentDescription = "Downloads") },
                                    label = { Text("Downloads", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF818CF8),
                                        selectedTextColor = Color(0xFF818CF8),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B),
                                        indicatorColor = Color.Transparent
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentScreen is Screen.Settings,
                                    onClick = {
                                        navigationStack.clear()
                                        navigationStack.add(Screen.Settings)
                                    },
                                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings", fontSize = 11.sp) },
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
                        .padding(innerPadding)
                ) {
                    // Content Layer with Crossfade Animations
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentScreen) {
                            is Screen.Home -> HomeScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = { navigateTo(Screen.Settings) },
                                onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) },
                                onNavigateToAlbum = { id, name -> navigateTo(Screen.AlbumDetail(id, name)) },
                                onNavigateToCustomPlaylist = { type, id, title, desc, tracks, colors ->
                                    navigateTo(Screen.CustomPlaylistDetail(type, id, title, desc, tracks, colors))
                                }
                            )
                            is Screen.Library -> LibraryScreen(
                                viewModel = viewModel,
                                onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) },
                                onNavigateToAlbum = { id, name -> navigateTo(Screen.AlbumDetail(id, name)) },
                                onNavigateToPlaylist = { id, name -> navigateTo(Screen.PlaylistDetail(id, name)) },
                                onNavigateToCustomPlaylist = { type, id, title, desc, tracks, colors ->
                                    navigateTo(Screen.CustomPlaylistDetail(type, id, title, desc, tracks, colors))
                                }
                            )
                            is Screen.Search -> SearchScreen(
                                viewModel = viewModel,
                                onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) },
                                onNavigateToAlbum = { id, name -> navigateTo(Screen.AlbumDetail(id, name)) }
                            )
                            is Screen.Downloads -> DownloadsScreen(
                                viewModel = viewModel
                            )
                            is Screen.Settings -> SettingsScreen(
                                viewModel = viewModel
                            )
                            is Screen.ArtistDetail -> ArtistDetailScreen(
                                artistId = currentScreen.id,
                                artistName = currentScreen.name,
                                viewModel = viewModel,
                                onNavigateToAlbum = { id, name -> navigateTo(Screen.AlbumDetail(id, name)) },
                                onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) },
                                onBack = { navigateBack() }
                            )
                            is Screen.AlbumDetail -> AlbumDetailScreen(
                                albumId = currentScreen.id,
                                albumName = currentScreen.name,
                                viewModel = viewModel,
                                onBack = { navigateBack() },
                                onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) }
                            )
                            is Screen.PlaylistDetail -> PlaylistDetailScreen(
                                playlistId = currentScreen.id,
                                playlistName = currentScreen.name,
                                viewModel = viewModel,
                                onBack = { navigateBack() }
                            )
                            is Screen.CustomPlaylistDetail -> CustomPlaylistDetailScreen(
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

        // Animated Immersive Now Playing Overlay (Slides up from the bottom!)
        AnimatedVisibility(
            visible = isNowPlayingExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            NowPlayingScreen(
                viewModel = viewModel,
                baseUrl = baseUrl,
                token = token,
                onCollapse = { isNowPlayingExpanded = false },
                onNavigateToArtist = { id, name -> navigateTo(Screen.ArtistDetail(id, name)) },
                onNavigateToAlbum = { id, name -> navigateTo(Screen.AlbumDetail(id, name)) }
            )
        }
    }
}
}
