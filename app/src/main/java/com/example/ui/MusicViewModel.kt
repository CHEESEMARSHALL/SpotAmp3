package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MusicRepository
import com.example.data.PlexDirectory
import com.example.data.PlexMetadata
import com.example.data.CachedTrack
import com.example.data.SyncStateEntity
import com.example.data.RadioRequest
import com.example.data.RadioService
import com.example.data.DiscoveryLevel
import com.example.data.RecentTrack
import com.example.data.LibrarySyncState
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem
import com.example.data.HomeFeedState
import com.example.data.BackendClientManager
import com.example.data.BackendHomeMapper
import com.example.sync.SyncScheduler
import com.example.data.HomeRecommendationEngine
import com.example.data.DailyMix
import com.example.data.RecommendedStation
import com.example.data.RadioType
import com.example.data.SmartSearchService
import com.example.data.AppCommand
import com.example.data.PlaybackCommandExecutor
import com.example.data.LastFmScrobbler
import android.content.Intent
import android.net.Uri
import com.example.data.toTrackItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.TrackDownloadWorker
import java.util.concurrent.TimeUnit

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    val repository = MusicRepository(application)
    val playbackManager = PlaybackManager.getInstance(application)

    // Recommendation Engine
    private val recommendationEngine = HomeRecommendationEngine(application)
    private val radioService = RadioService()
    private var searchJob: Job? = null

    // Configuration states
    private val _isConfigured = MutableStateFlow(repository.settings.isConfigured)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _libraries = MutableStateFlow<List<PlexDirectory>>(emptyList())
    val libraries: StateFlow<List<PlexDirectory>> = _libraries.asStateFlow()

    private val _selectedSectionId = MutableStateFlow(repository.settings.sectionId)
    val selectedSectionId: StateFlow<String> = _selectedSectionId.asStateFlow()

    private val _selectedLibraryName = MutableStateFlow(repository.settings.libraryName)
    val selectedLibraryName: StateFlow<String> = _selectedLibraryName.asStateFlow()

    // Library items
    private val _artists = MutableStateFlow<List<PlexMetadata>>(emptyList())
    val artists: StateFlow<List<PlexMetadata>> = _artists.asStateFlow()

    private val _albums = MutableStateFlow<List<PlexMetadata>>(emptyList())
    val albums: StateFlow<List<PlexMetadata>> = _albums.asStateFlow()

    private val _recentlyAddedAlbums = MutableStateFlow<List<PlexMetadata>>(emptyList())
    val recentlyAddedAlbums: StateFlow<List<PlexMetadata>> = _recentlyAddedAlbums.asStateFlow()

    // Detail states
    private val _currentArtistAlbums = MutableStateFlow<List<PlexMetadata>>(emptyList())
    val currentArtistAlbums: StateFlow<List<PlexMetadata>> = _currentArtistAlbums.asStateFlow()

    private val _currentArtistMetadata = MutableStateFlow<PlexMetadata?>(null)
    val currentArtistMetadata: StateFlow<PlexMetadata?> = _currentArtistMetadata.asStateFlow()

    private val _currentArtistTracks = MutableStateFlow<List<PlexMetadata>>(emptyList())
    val currentArtistTracks: StateFlow<List<PlexMetadata>> = _currentArtistTracks.asStateFlow()

    private val _artistProfile = MutableStateFlow<ArtistProfile?>(null)
    val artistProfile: StateFlow<ArtistProfile?> = _artistProfile.asStateFlow()

    private val _currentAlbumTracks = MutableStateFlow<List<PlexMetadata>>(emptyList())
    val currentAlbumTracks: StateFlow<List<PlexMetadata>> = _currentAlbumTracks.asStateFlow()

    // Search results
    private val _searchResults = MutableStateFlow<List<PlexMetadata>>(emptyList())
    val searchResults: StateFlow<List<PlexMetadata>> = _searchResults.asStateFlow()
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    // Local DB flows
    val recentlyPlayed: StateFlow<List<RecentTrack>> = repository.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cachedTracks: StateFlow<List<CachedTrack>> = repository.cachedTracksCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _cachedCount = MutableStateFlow(0)
    val cachedCount: StateFlow<Int> = _cachedCount.asStateFlow()

    // Status states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _syncState = MutableStateFlow<LibrarySyncState>(LibrarySyncState.Idle)
    val syncState: StateFlow<LibrarySyncState> = _syncState.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _companionRevision = MutableStateFlow(0)

    // Combined Reactive Home Feed State Flow
    val homeFeedState: StateFlow<HomeFeedState> = combine(
        recentlyAddedAlbums,
        recentlyPlayed,
        cachedTracks,
        _companionRevision
    ) { recentAdded, history, _, _ ->
        recommendationEngine.generateHomeFeed(recentAdded, history)
    }.map { plexFeed ->
        val settings = repository.settings
        if (!settings.isCompanionConfigured) {
            plexFeed
        } else {
            runCatching {
                val response = BackendClientManager
                    .getApiService(settings.companionBackendUrl)
                    .getHomeFeed("Bearer ${settings.companionBackendToken}")
                BackendHomeMapper.merge(response, plexFeed)
            }.onFailure { error ->
                Log.w("MusicViewModel", "SpotCore Home unavailable; using Plex fallback", error)
            }.getOrDefault(plexFeed)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeFeedState())

    // Playlist and Download states
    val playlists: StateFlow<List<com.example.data.PlaylistEntity>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedTracks: StateFlow<List<com.example.data.DownloadedTrackEntity>> = repository.downloadedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedTracks: StateFlow<List<com.example.data.LikedTrackEntity>> = repository.likedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _downloadProgresses = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgresses: StateFlow<Map<String, Float>> = _downloadProgresses.asStateFlow()

    // AI generated playlist results
    private val _aiGeneratedTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val aiGeneratedTracks: StateFlow<List<TrackItem>> = _aiGeneratedTracks.asStateFlow()

    private val _aiExplanation = MutableStateFlow<String?>(null)
    val aiExplanation: StateFlow<String?> = _aiExplanation.asStateFlow()

    // Settings flows
    private val _activeThemeFlow = MutableStateFlow(repository.settings.activeTheme)
    val activeThemeFlow: StateFlow<String> = _activeThemeFlow.asStateFlow()

    private val _aiProviderFlow = MutableStateFlow(repository.settings.aiProvider)
    val aiProviderFlow: StateFlow<String> = _aiProviderFlow.asStateFlow()

    private val _equalizerEnabledFlow = MutableStateFlow(repository.settings.equalizerEnabled)
    val equalizerEnabledFlow: StateFlow<Boolean> = _equalizerEnabledFlow.asStateFlow()

    private val _equalizerPresetFlow = MutableStateFlow(repository.settings.equalizerPreset)
    val equalizerPresetFlow: StateFlow<String> = _equalizerPresetFlow.asStateFlow()

    private val _normalizationEnabledFlow = MutableStateFlow(repository.settings.normalizationEnabled)
    val normalizationEnabledFlow: StateFlow<Boolean> = _normalizationEnabledFlow.asStateFlow()

    private val _gaplessEnabledFlow = MutableStateFlow(repository.settings.gaplessEnabled)
    val gaplessEnabledFlow: StateFlow<Boolean> = _gaplessEnabledFlow.asStateFlow()

    private val _lastFmEnabledFlow = MutableStateFlow(repository.settings.lastFmEnabled)
    val lastFmEnabledFlow: StateFlow<Boolean> = _lastFmEnabledFlow.asStateFlow()

    private val _lastFmUsernameFlow = MutableStateFlow(repository.settings.lastFmUsername)
    val lastFmUsernameFlow: StateFlow<String> = _lastFmUsernameFlow.asStateFlow()

    private val _lastFmSessionKeyFlow = MutableStateFlow(repository.settings.lastFmSessionKey)
    val lastFmSessionKeyFlow: StateFlow<String> = _lastFmSessionKeyFlow.asStateFlow()
    private val _lastFmApiKeyFlow = MutableStateFlow(repository.settings.lastFmApiKey)
    val lastFmApiKeyFlow: StateFlow<String> = _lastFmApiKeyFlow.asStateFlow()
    private val _lastFmApiSecretFlow = MutableStateFlow(repository.settings.lastFmApiSecret)
    val lastFmApiSecretFlow: StateFlow<String> = _lastFmApiSecretFlow.asStateFlow()
    private val _lastFmNowPlayingFlow = MutableStateFlow(repository.settings.lastFmNowPlayingEnabled)
    val lastFmNowPlayingFlow: StateFlow<Boolean> = _lastFmNowPlayingFlow.asStateFlow()
    private val _lastFmScrobbleFlow = MutableStateFlow(repository.settings.lastFmScrobbleEnabled)
    val lastFmScrobbleFlow: StateFlow<Boolean> = _lastFmScrobbleFlow.asStateFlow()
    private val _lastFmPrivateFlow = MutableStateFlow(repository.settings.lastFmPrivateSession)
    val lastFmPrivateFlow: StateFlow<Boolean> = _lastFmPrivateFlow.asStateFlow()
    private val _lastFmAuthMessage = MutableStateFlow("")
    val lastFmAuthMessage: StateFlow<String> = _lastFmAuthMessage.asStateFlow()

    private val _syncIntervalHoursFlow = MutableStateFlow(repository.settings.syncIntervalHours)
    val syncIntervalHoursFlow: StateFlow<Long> = _syncIntervalHoursFlow.asStateFlow()

    private val _ggufModelSizeFlow = MutableStateFlow(repository.settings.ggufModelSize)
    val ggufModelSizeFlow: StateFlow<String> = _ggufModelSizeFlow.asStateFlow()

    fun updateGgufModelSize(size: String) {
        repository.settings.ggufModelSize = size
        _ggufModelSizeFlow.value = size
    }

    fun updateTheme(themeName: String) {
        repository.settings.activeTheme = themeName
        _activeThemeFlow.value = themeName
    }

    fun updateAiProvider(providerName: String) {
        repository.settings.aiProvider = providerName
        _aiProviderFlow.value = providerName
    }

    fun updateEqualizerEnabled(enabled: Boolean) {
        repository.settings.equalizerEnabled = enabled
        _equalizerEnabledFlow.value = enabled
        playbackManager.refreshAudioSettings()
    }

    fun updateEqualizerPreset(presetName: String) {
        repository.settings.equalizerPreset = presetName
        _equalizerPresetFlow.value = presetName
        playbackManager.refreshAudioSettings()
    }

    fun updateNormalizationEnabled(enabled: Boolean) {
        repository.settings.normalizationEnabled = enabled
        _normalizationEnabledFlow.value = enabled
        playbackManager.refreshAudioSettings()
    }

    fun updateLastFmEnabled(enabled: Boolean) {
        repository.settings.lastFmEnabled = enabled
        _lastFmEnabledFlow.value = enabled
    }

    fun updateLastFmUsername(username: String) {
        repository.settings.lastFmUsername = username
        _lastFmUsernameFlow.value = username
    }

    fun updateLastFmSessionKey(sessionKey: String) {
        repository.settings.lastFmSessionKey = sessionKey
        _lastFmSessionKeyFlow.value = sessionKey
    }

    fun updateSyncInterval(hours: Long) {
        repository.settings.syncIntervalHours = hours
        _syncIntervalHoursFlow.value = hours
        if (hours > 0) {
            SyncScheduler.scheduleSync(getApplication(), hours)
        } else {
            SyncScheduler.cancelSync(getApplication())
        }
    }

    fun playTrackFromCache(track: CachedTrack) {
        viewModelScope.launch {
            val trackItem = com.example.playback.TrackItem(
                ratingKey = track.ratingKey,
                title = track.title,
                artist = track.artist,
                album = track.album,
                key = track.key,
                thumb = track.thumb,
                duration = track.duration
            )
            playbackManager.playTrack(trackItem, emptyList())
        }
    }

    fun toggleLikeTrack(track: TrackItem) {
        viewModelScope.launch {
            if (repository.isTrackLiked(track.ratingKey)) {
                repository.deleteLikedTrack(track.ratingKey)
            } else {
                repository.addLikedTrack(track)
            }
        }
    }

    fun isTrackLikedFlow(ratingKey: String): Flow<Boolean> {
        return repository.isTrackLikedFlow(ratingKey)
    }

    fun savePlaylistWithTracks(name: String, tracks: List<TrackItem>, onSaved: (() -> Unit)? = null) {
        viewModelScope.launch {
            val id = repository.createPlaylist(name)
            tracks.forEach { track ->
                repository.addTrackToPlaylist(id.toInt(), track)
            }
            onSaved?.invoke()
        }
    }

    fun downloadTracksList(tracks: List<TrackItem>) {
        tracks.forEach { track ->
            startDownload(track)
        }
    }

    fun createPlaylist(name: String, onCreated: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = repository.createPlaylist(name)
            onCreated?.invoke(id)
        }
    }

    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: Int, track: TrackItem) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, track)
        }
    }

    fun addTrackToPlaylist(playlistId: Int, meta: PlexMetadata) {
        val trackItem = mapMetadataToTrackItem(meta) ?: return
        addTrackToPlaylist(playlistId, trackItem)
    }

    fun addAlbumToPlaylist(playlistId: Int, albumId: String) {
        viewModelScope.launch {
            val tracks = repository.getAlbumTracks(albumId)
            tracks.forEach { track ->
                val trackItem = mapMetadataToTrackItem(track)
                if (trackItem != null) {
                    repository.addTrackToPlaylist(playlistId, trackItem)
                }
            }
        }
    }

    fun addArtistToPlaylist(playlistId: Int, artistId: String) {
        viewModelScope.launch {
            val albums = repository.getArtistAlbums(artistId)
            albums.forEach { album ->
                val tracks = repository.getAlbumTracks(album.ratingKey)
                tracks.forEach { track ->
                    val trackItem = mapMetadataToTrackItem(track)
                    if (trackItem != null) {
                        repository.addTrackToPlaylist(playlistId, trackItem)
                    }
                }
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: Int, ratingKey: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, ratingKey)
        }
    }

    fun startDownload(track: TrackItem) {
        viewModelScope.launch {
            if (repository.isTrackDownloaded(track.ratingKey)) return@launch
            repository.queueDownload(track)
            val request = OneTimeWorkRequestBuilder<TrackDownloadWorker>()
                .addTag("spotamp_download")
                .setInputData(workDataOf(
                    TrackDownloadWorker.KEY_RATING_KEY to track.ratingKey,
                    TrackDownloadWorker.KEY_TITLE to track.title,
                    TrackDownloadWorker.KEY_ARTIST to track.artist,
                    TrackDownloadWorker.KEY_ALBUM to track.album,
                    TrackDownloadWorker.KEY_KEY to track.key,
                    TrackDownloadWorker.KEY_THUMB to track.thumb,
                    TrackDownloadWorker.KEY_DURATION to track.duration
                ))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                "download_${track.ratingKey}",
                androidx.work.ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    fun startDownload(meta: PlexMetadata) {
        val trackItem = mapMetadataToTrackItem(meta) ?: return
        startDownload(trackItem)
    }

    fun downloadAlbum(albumId: String) {
        viewModelScope.launch {
            val tracks = repository.getAlbumTracks(albumId)
            tracks.forEach { track ->
                val trackItem = mapMetadataToTrackItem(track)
                if (trackItem != null) {
                    startDownload(trackItem)
                }
            }
        }
    }

    fun downloadArtist(artistId: String) {
        viewModelScope.launch {
            val albums = repository.getArtistAlbums(artistId)
            albums.forEach { album ->
                val tracks = repository.getAlbumTracks(album.ratingKey)
                tracks.forEach { track ->
                    val trackItem = mapMetadataToTrackItem(track)
                    if (trackItem != null) {
                        startDownload(trackItem)
                    }
                }
            }
        }
    }

    fun deleteDownload(ratingKey: String) {
        viewModelScope.launch {
            WorkManager.getInstance(getApplication()).cancelUniqueWork("download_$ratingKey")
            repository.deleteDownloadedTrack(ratingKey)
        }
    }

    fun retryDownload(track: com.example.data.DownloadedTrackEntity) {
        startDownload(track.toTrackItem())
    }

    fun deleteAllDownloads() {
        viewModelScope.launch {
            WorkManager.getInstance(getApplication()).cancelAllWorkByTag("spotamp_download")
            repository.deleteAllDownloads()
        }
    }

    init {
        playbackManager.setMusicDao(com.example.data.MusicDatabase.getDatabase(application).musicDao())
        updatePlaybackCredentials()
        loadCachedCount()
        
        // Schedule background sync if enabled
        val interval = repository.settings.syncIntervalHours
        if (interval > 0) {
            SyncScheduler.scheduleSync(application, interval)
        }
        
        if (repository.settings.isConfigured) {
            loadLibraries()
        }
        if (repository.settings.sectionId.isNotEmpty() || repository.settings.isCompanionConfigured) {
            loadInitialLibraryData()
        }

        viewModelScope.launch {
            _selectedSectionId
                .flatMapLatest { sectionId ->
                    if (sectionId.isEmpty()) flowOf(null)
                    else repository.getSyncStateFlow(sectionId)
                }
                .collect { entity ->
                    _syncState.value = when (entity?.status) {
                        "running" -> LibrarySyncState.Processing(entity.currentOffset, entity.totalTracks, 0)
                        "completed" -> {
                            loadInitialLibraryData()
                            LibrarySyncState.Complete(entity.totalTracks, 0, 0)
                        }
                        "failed" -> LibrarySyncState.Failed("Sync failed", entity.lastError ?: "Unknown", "Indexing")
                        else -> LibrarySyncState.Idle
                    }
                }
        }
    }

    private fun updatePlaybackCredentials() {
        playbackManager.setCredentials(repository.settings.baseUrl, repository.settings.token)
        playbackManager.setCompanionToken(repository.settings.companionBackendToken)
    }

    fun saveCredentials(baseUrl: String, token: String, lyricsDirectory: String = "") {
        repository.settings.baseUrl = baseUrl.trim()
        repository.settings.token = token.trim()
        repository.settings.lyricsDirectory = lyricsDirectory.trim()
        _isConfigured.value = repository.settings.isConfigured
        updatePlaybackCredentials()
        if (repository.settings.isConfigured) {
            loadLibraries()
        }
    }

    fun updateLastFmApiKey(value: String) { repository.settings.lastFmApiKey = value; _lastFmApiKeyFlow.value = value }
    fun updateLastFmApiSecret(value: String) { repository.settings.lastFmApiSecret = value; _lastFmApiSecretFlow.value = value }
    fun updateLastFmNowPlaying(value: Boolean) { repository.settings.lastFmNowPlayingEnabled = value; _lastFmNowPlayingFlow.value = value }
    fun updateLastFmScrobble(value: Boolean) { repository.settings.lastFmScrobbleEnabled = value; _lastFmScrobbleFlow.value = value }
    fun updateLastFmPrivate(value: Boolean) { repository.settings.lastFmPrivateSession = value; _lastFmPrivateFlow.value = value }
    fun disconnectLastFm() {
        repository.settings.lastFmEnabled = false; repository.settings.lastFmUsername = ""; repository.settings.lastFmSessionKey = ""
        _lastFmEnabledFlow.value = false; _lastFmUsernameFlow.value = ""; _lastFmSessionKeyFlow.value = ""
        _lastFmAuthMessage.value = "Disconnected"
    }
    fun startLastFmAuthorization() {
        viewModelScope.launch {
            LastFmScrobbler.requestToken(repository.settings).onSuccess {
                LastFmScrobbler.authorizationUrl(repository.settings)?.let { getApplication<Application>().startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                _lastFmAuthMessage.value = "Approve SpotAmp in your browser, then tap Complete auth."
            }.onFailure { _lastFmAuthMessage.value = it.message ?: "Authorization failed" }
        }
    }
    fun completeLastFmAuthorization() {
        viewModelScope.launch {
            LastFmScrobbler.completeAuthorization(repository.settings).onSuccess { name ->
                _lastFmEnabledFlow.value = true; _lastFmUsernameFlow.value = name; _lastFmSessionKeyFlow.value = repository.settings.lastFmSessionKey
                _lastFmAuthMessage.value = "Connected as $name"
            }.onFailure { _lastFmAuthMessage.value = it.message ?: "Authorization failed" }
        }
    }

    fun saveCompanionBackend(url: String, token: String) {
        val normalizedUrl = url.trim().trimEnd('/')
        val normalizedToken = token.trim()
        if ((normalizedUrl.isBlank()) != (normalizedToken.isBlank())) {
            postErrorMessage("Enter both the SpotCore URL and token, or clear both fields.")
            return
        }
        repository.settings.companionBackendUrl = normalizedUrl
        repository.settings.companionBackendToken = normalizedToken
        playbackManager.setCompanionToken(normalizedToken)
        _companionRevision.value += 1
        clearErrorMessage()
    }

    fun selectLibrary(sectionId: String, name: String) {
        repository.settings.sectionId = sectionId
        repository.settings.libraryName = name
        _selectedSectionId.value = sectionId
        _selectedLibraryName.value = name
        loadInitialLibraryData()
    }

    private fun loadInitialLibraryData() {
        loadArtists()
        loadAlbums()
        loadRecentlyAdded()
        loadCachedCount()
    }

    fun loadCachedCount() {
        viewModelScope.launch {
            _cachedCount.value = repository.getCachedCount()
        }
    }

    fun loadLibraries() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val list = repository.getLibraries()
                _libraries.value = list
                if (list.isEmpty() && repository.settings.isConfigured) {
                    _errorMessage.value = "No music libraries found on this Plex server."
                } else if (list.isNotEmpty() && repository.settings.sectionId.isEmpty()) {
                    // Auto-select the first available music library section
                    val firstLib = list.first()
                    selectLibrary(firstLib.key, firstLib.title)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Could not reach Plex server: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadArtists() {
        val section = _selectedSectionId.value
        if (section.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _artists.value = repository.getArtists(section)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error artists", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadAlbums() {
        val section = _selectedSectionId.value
        if (section.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _albums.value = repository.getAlbums(section)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error albums", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadRecentlyAdded() {
        val section = _selectedSectionId.value
        if (section.isEmpty()) return
        viewModelScope.launch {
            try {
                _recentlyAddedAlbums.value = repository.getRecentlyAddedAlbums(section)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error recently added", e)
            }
        }
    }

    fun loadArtistDetail(artistId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _currentArtistMetadata.value = null
            _currentArtistTracks.value = emptyList()
            _artistProfile.value = null
            try {
                // Get artist metadata
                val artistMeta = repository.getMetadataDetail(artistId)
                _currentArtistMetadata.value = artistMeta
                
                // Get albums
                val albums = repository.getArtistAlbums(artistId)
                _currentArtistAlbums.value = albums
                
                // Fetch tracks for all albums
                val allTracks = mutableListOf<PlexMetadata>()
                albums.forEach { album ->
                    val tracks = repository.getAlbumTracks(album.ratingKey)
                    allTracks.addAll(tracks)
                }
                _currentArtistTracks.value = allTracks
                
                // Load bio/profile
                loadArtistProfile(artistId, artistMeta?.title ?: "")
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error artist detail", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playArtist(artistId: String) {
        viewModelScope.launch {
            val albums = repository.getArtistAlbums(artistId)
            val allTracks = mutableListOf<PlexMetadata>()
            albums.forEach { album ->
                allTracks.addAll(repository.getAlbumTracks(album.ratingKey))
            }
            if (allTracks.isNotEmpty()) {
                playAllTracks(allTracks, 0)
            }
        }
    }

    private fun loadArtistProfile(artistId: String, artistName: String) {
        viewModelScope.launch {
            // Cloud profile enrichment is opt-in through the selected provider.
            val geminiApiKey = com.example.BuildConfig.GEMINI_API_KEY
            val cloudEnabled = repository.settings.aiProvider == "CloudAIProvider" ||
                repository.settings.aiProvider == "HybridAIProvider"
            if (cloudEnabled && geminiApiKey.isNotEmpty() && !geminiApiKey.contains("GEMINI_API_KEY")) {
                try {
                    val systemInstructionText = """
                        You are an expert music bio and metadata assistant.
                        Your task is to return a JSON object containing information for the music artist requested.
                        
                        CRITICAL DIRECTIVES:
                        1. Return ONLY a valid JSON object. No explanation, no markdown blocks, no conversational filler.
                        2. The JSON object MUST strictly follow this schema:
                        {
                            "bio": "A 1-2 sentence compelling summary of the artist's history and style.",
                            "similarArtists": [
                                {"name": "Similar Artist Name", "style": "Genre/Style"}
                            ],
                            "styles": [
                                "Genre Name"
                            ]
                        }
                        3. Limit similarArtists to max 5 items, styles to max 3 items.
                    """.trimIndent()

                    val userPrompt = "Artist: $artistName"
                    val request = com.example.data.GeminiRequest(
                        contents = listOf(com.example.data.GeminiContent(parts = listOf(com.example.data.GeminiPart(text = userPrompt)))),
                        systemInstruction = com.example.data.GeminiContent(parts = listOf(com.example.data.GeminiPart(text = systemInstructionText))),
                        generationConfig = com.example.data.GeminiGenerationConfig(
                            responseMimeType = "application/json",
                            temperature = 0.5f
                        )
                    )
                    val response = com.example.data.GeminiClient.service.generateContent(geminiApiKey, request)
                    val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                    
                    val profile = parseArtistProfileFromJson(jsonText)
                    if (profile != null) {
                        _artistProfile.value = profile
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Error loading artist profile from Gemini", e)
                }
            }

            // Keep the local path honest when no cloud profile was requested or
            // when the provider has no verified profile result.
            _artistProfile.value = ArtistProfile(
                bio = "No artist profile metadata is available in the indexed Plex library.",
                similarArtists = emptyList(),
                styles = emptyList()
            )
        }
    }

    private fun parseArtistProfileFromJson(jsonText: String): ArtistProfile? {
        try {
            val clean = jsonText.trim()
                .removeSurrounding("```json", "```")
                .removeSurrounding("```", "```")
                .trim()
            val json = org.json.JSONObject(clean)
            val bio = json.optString("bio", "")
            
            val similarList = mutableListOf<SimilarArtist>()
            val similarArray = json.optJSONArray("similarArtists")
            if (similarArray != null) {
                for (i in 0 until similarArray.length()) {
                    val item = similarArray.getJSONObject(i)
                    similarList.add(
                        SimilarArtist(
                            name = item.optString("name", ""),
                            style = item.optString("style", "")
                        )
                    )
                }
            }
            
            val stylesList = mutableListOf<String>()
            val stylesArray = json.optJSONArray("styles")
            if (stylesArray != null) {
                for (i in 0 until stylesArray.length()) {
                    stylesList.add(stylesArray.getString(i))
                }
            }
            
            return ArtistProfile(bio, similarList, stylesList)
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Error parsing artist profile JSON", e)
            return null
        }
    }

    fun loadAlbumDetail(albumId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _currentAlbumTracks.value = repository.getAlbumTracks(albumId)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error album detail", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isBlank()) {
                _searchResults.value = emptyList()
                _searchError.value = null
                _isLoading.value = false
                return@launch
            }
            _searchResults.value = emptyList()
            _searchError.value = null
            _isLoading.value = true
            delay(150)
            try {
                val smartResults = repository.smartSearch(query)
                _searchResults.value = if (smartResults.isNotEmpty()) {
                    smartResults
                } else if (com.example.data.SmartSearchService().isStructuredQuery(query) && repository.getCachedCount() > 0) {
                    emptyList()
                } else if (repository.settings.isCompanionConfigured) {
                    runCatching { repository.searchCompanion(query) }
                        .getOrElse { repository.searchPlex(query) }
                } else {
                    repository.searchPlex(query)
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error search", e)
                _searchError.value = if (repository.getCachedCount() > 0) {
                    "Plex search failed. Try again or search your cached library offline."
                } else {
                    "Search failed because no local library cache is available. Sync your library first."
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun syncLibraryCache() {
        val section = _selectedSectionId.value
        if (section.isEmpty() && !repository.settings.isCompanionConfigured) return
        viewModelScope.launch {
            repository.startSync(if (section.isEmpty()) "spotcore" else section, restart = true)
        }
    }

    fun generateAiPlaylist(prompt: String, trackCount: Int = 30) {
        viewModelScope.launch {
            _aiLoading.value = true
            _aiError.value = null
            _aiGeneratedTracks.value = emptyList()
            _aiExplanation.value = null
            try {
                val cachedTracks = repository.getCachedTracksList()
                if (cachedTracks.isEmpty()) {
                    _aiError.value = "Your library cache is empty. Please run sync first!"
                    return@launch
                }

                val provider = when (repository.settings.aiProvider) {
                    "CloudAIProvider" -> com.example.data.CloudAIProvider()
                    "LocalAIProvider" -> com.example.data.LocalAIProvider()
                    "LlamaCppAIProvider" -> com.example.data.LlamaCppAIProvider(
                        modelPath = repository.settings.aiModelPath,
                        contextSize = 2048,
                        temperature = 0.7f,
                        modelPresetSize = repository.settings.ggufModelSize
                    )
                    "HybridAIProvider" -> com.example.data.HybridAIProvider()
                    else -> com.example.data.NoAIProvider()
                }

                Log.d("MusicViewModel", "Generating AI playlist using provider: ${provider.name}")
                var explanationPrefix: String? = null
                val rawIntent = try {
                    provider.generatePlaylistIntent(prompt, cachedTracks)
                } catch (e: Exception) {
                    if (provider is com.example.data.CloudAIProvider) {
                        Log.w("MusicViewModel", "Cloud AI failed; falling back to local on-device provider", e)
                        explanationPrefix = "Cloud AI is not configured or offline. Fell back to fast local on-device parser.\n\n"
                        com.example.data.LocalAIProvider().generatePlaylistIntent(prompt, cachedTracks)
                    } else {
                        throw e
                    }
                }
                val validation = com.example.data.AIOutputValidator.playlistIntent(rawIntent)
                val intent = validation.value ?: com.example.data.PlaylistIntent(explanation = "AI output was invalid; using deterministic fallback.")
                
                _aiExplanation.value = if (explanationPrefix != null) {
                    explanationPrefix + (intent.explanation ?: "")
                } else {
                    intent.explanation
                }

                val engine = com.example.data.RecommendationEngine(getApplication())
                val tracks = engine.buildRecommendationQueue(intent, cachedTracks).take(trackCount.coerceAtLeast(1))

                if (tracks.isNotEmpty()) {
                    _aiGeneratedTracks.value = tracks
                    playbackManager.playQueue(tracks, 0)
                } else {
                    _aiError.value = "The AI was unable to find matching tracks in your local library."
                }
            } catch (e: Exception) {
                _aiError.value = e.localizedMessage ?: "Failed to generate AI playlist"
                Log.e("MusicViewModel", "Error in generateAiPlaylist", e)
            } finally {
                _aiLoading.value = false
            }
        }
    }

    fun playTrackFromMetadata(track: PlexMetadata, queueList: List<PlexMetadata>) {
        val trackItem = mapMetadataToTrackItem(track) ?: return
        val queueItems = queueList.mapNotNull { mapMetadataToTrackItem(it) }
        playbackManager.playTrack(trackItem, queueItems)
    }

    fun playAllTracks(queueList: List<PlexMetadata>, startIndex: Int = 0) {
        val queueItems = queueList.mapNotNull { mapMetadataToTrackItem(it) }
        playbackManager.playQueue(queueItems, startIndex)
    }

    fun playRecentTrack(recent: RecentTrack) {
        val trackItem = TrackItem(
            ratingKey = recent.ratingKey,
            title = recent.title,
            artist = recent.artist,
            album = recent.album,
            key = recent.key,
            thumb = recent.thumb,
            duration = 0L
        )
        playbackManager.playTrack(trackItem, listOf(trackItem))
    }

    private fun mapMetadataToTrackItem(meta: PlexMetadata): TrackItem? {
        val key = meta.media?.firstOrNull()?.part?.firstOrNull()?.key ?: return null
        return TrackItem(
            ratingKey = meta.ratingKey,
            title = meta.title,
            artist = meta.grandparentTitle ?: meta.parentTitle ?: "Unknown Artist",
            album = meta.parentTitle ?: "Unknown Album",
            key = key,
            thumb = meta.thumb ?: "",
            duration = meta.duration ?: 0L
            ,albumRatingKey = meta.parentRatingKey
        )
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    suspend fun generateRadioTracks(request: RadioRequest): List<TrackItem> {
        val cachedTracks = repository.getCachedTracksList()
        return radioService.generate(request, cachedTracks, recentlyPlayed.value)
    }

    fun postErrorMessage(message: String?) {
        _errorMessage.value = message
    }

    fun clearAiError() {
        _aiError.value = null
    }

    fun playDailyMix(mix: DailyMix) {
        if (mix.tracks.isNotEmpty()) {
            playbackManager.playQueue(mix.tracks, 0)
        }
    }

    fun playTrackItem(track: TrackItem, queue: List<TrackItem> = listOf(track)) {
        playbackManager.playTrack(track, queue)
    }

    fun playStation(station: RecommendedStation, request: RadioRequest = RadioRequest(type = station.type)) {
        viewModelScope.launch {
            playRadioRequest(request, station.type)
        }
    }

    fun playSeededRadio(request: RadioRequest) {
        viewModelScope.launch {
            playRadioRequest(request, request.type)
        }
    }

    private suspend fun playRadioRequest(request: RadioRequest, type: RadioType) {
            val cachedTracks = repository.getCachedTracksList()
            if (cachedTracks.isEmpty()) {
                _errorMessage.value = "This radio needs an indexed Plex library. Sync your music first."
                return
            }

            val tracksToPlay = radioService.generate(request, cachedTracks, recentlyPlayed.value)

            if (tracksToPlay.isNotEmpty()) {
                playbackManager.playQueue(tracksToPlay, 0)
                _errorMessage.value = null
            } else {
                _errorMessage.value = when (type) {
                    RadioType.ARTIST_RADIO -> "No cached tracks matched that artist."
                    RadioType.ALBUM_RADIO -> "No cached tracks matched that album."
                    RadioType.GENRE_RADIO, RadioType.MOOD_RADIO, RadioType.STYLE_RADIO -> "No cached tracks matched that genre or style."
                    RadioType.COLLECTION_RADIO -> "No cached tracks matched that collection."
                    RadioType.DECADE_RADIO, RadioType.TIME_TRAVEL -> "No cached tracks matched that decade."
                    RadioType.SOUNDTRACK_RADIO -> "No soundtrack metadata is available in the indexed library."
                    RadioType.FORGOTTEN_FAVORITES -> "There are no forgotten favorites in the cached history yet."
                    else -> "This radio has no matching tracks in the cached Plex library."
                }
            }
    }

    fun executeAppCommand(command: AppCommand): Boolean {
        val executor = PlaybackCommandExecutor(
            onPlay = { playbackManager.play() },
            onPause = { playbackManager.pause() },
            onNext = { playbackManager.next() },
            onPrevious = { playbackManager.prev() },
            onSearch = ::search,
            onStartRadio = { query ->
                viewModelScope.launch {
                    val cachedTracks = repository.getCachedTracksList()
                    if (cachedTracks.isEmpty()) return@launch
                    val artist = query?.trim()?.takeIf { value ->
                        cachedTracks.any { it.artist.equals(value, ignoreCase = true) }
                    }
                    val request = if (artist != null) {
                        RadioRequest(type = RadioType.ARTIST_RADIO, seedArtist = artist)
                    } else {
                        RadioRequest(type = RadioType.LIBRARY_RADIO)
                    }
                    val tracks = radioService.generate(request, cachedTracks, recentlyPlayed.value)
                    if (tracks.isNotEmpty()) playbackManager.playQueue(tracks, 0)
                }
            }
        )
        return executor.execute(command)
    }
}

data class ArtistProfile(
    val bio: String,
    val similarArtists: List<SimilarArtist>,
    val styles: List<String>
)

data class SimilarArtist(
    val name: String,
    val style: String
)

