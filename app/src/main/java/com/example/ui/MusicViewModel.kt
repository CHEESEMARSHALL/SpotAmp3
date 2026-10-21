package com.example.ui

import android.app.Application
import android.util.Log
import com.example.BuildConfig
import com.example.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MusicRepository
import com.example.data.PlexDirectory
import com.example.data.PlexMetadata
import com.example.data.CachedTrack
import com.example.data.DownloadedTrackEntity
import com.example.data.ListeningHistoryEntity
import com.example.data.RadioRequest
import com.example.data.RadioService
import com.example.data.DiscoveryLevel
import com.example.data.SavedStationRecipe
import com.example.data.SavedStationRecipeStore
import com.example.data.generateSavedStationTracks
import com.example.data.DiscoveryTuning
import com.example.data.DiscoveryTuningStore
import com.example.data.applyDiscoveryTuning
import com.example.data.filterDiscoveryTracks
import com.example.data.RecentTrack
import com.example.data.LibrarySyncState
import com.example.data.CompanionConnectionPresentation
import com.example.data.CompanionConnectionStatus
import com.example.data.CompanionReachability
import com.example.data.ProductFailurePresentation
import com.example.data.FailurePresentation
import com.example.data.LastFmAuthorizationFailurePresentation
import com.example.data.LastFmAuthorizationStep
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem
import com.example.data.HomeFeedState
import com.example.data.BackendClientManager
import com.example.data.BackendHomeFeedResponse
import com.example.data.BackendHomeMapper
import com.example.data.CompanionPairingClaimRequest
import com.example.data.CompanionPairingPayload
import com.example.data.QaNetworkPolicy
import com.example.data.normalizeCompanionArtistArtworkUrl
import com.example.data.normalizeCompanionUrl
import com.example.sync.SyncScheduler
import com.example.data.HomeRecommendationEngine
import com.example.data.DailyMix
import com.example.data.RecommendedStation
import com.example.data.RadioType
import com.example.data.SmartSearchService
import com.example.data.AppCommand
import com.example.data.PlaybackCommandExecutor
import com.example.data.LastFmScrobbler
import com.example.data.SyncDiagnosticService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.example.data.toTrackItem
import com.example.data.albumNavigationKey
import com.example.data.effectiveAlbumArtist
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.TrackDownloadWorker
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@OptIn(ExperimentalCoroutinesApi::class)
class MusicViewModel @JvmOverloads constructor(
    application: Application,
    private val fetchCompanionHomeFeed: suspend (String, String) -> BackendHomeFeedResponse = { backendUrl, authorization ->
        BackendClientManager.getApiService(backendUrl).getHomeFeed(authorization)
    },
    private val searchResultsOverride: (suspend (String) -> List<PlexMetadata>)? = null,
    private val searchDebounceMillis: Long = 150,
    private val loadLibraryArtistsOverride: (suspend (String) -> List<PlexMetadata>)? = null,
    private val loadLibraryAlbumsOverride: (suspend (String) -> List<PlexMetadata>)? = null,
    private val loadRecentlyAddedOverride: (suspend (String) -> List<PlexMetadata>)? = null,
    private val loadLibrariesOverride: (suspend () -> List<PlexDirectory>)? = null
) : AndroidViewModel(application) {
    private class CompanionCredentialSaveException : IllegalStateException()
    val repository = MusicRepository(application)
    val playbackManager = PlaybackManager.getInstance(application)
    internal var enqueueLibrarySyncForQa: (suspend (String, Boolean) -> Unit)? = null

    // Recommendation Engine
    private val recommendationEngine = HomeRecommendationEngine(application)
    private val radioService = RadioService()
    private val stationRecipeStore = SavedStationRecipeStore(application)
    private val _savedStationRecipes = MutableStateFlow(stationRecipeStore.load())
    val savedStationRecipes: StateFlow<List<SavedStationRecipe>> = _savedStationRecipes.asStateFlow()
    private val discoveryTuningStore = DiscoveryTuningStore(application)
    private val _discoveryTuning = MutableStateFlow(discoveryTuningStore.load())
    val discoveryTuning: StateFlow<DiscoveryTuning> = _discoveryTuning.asStateFlow()
    private var searchJob: Job? = null
    private var searchGeneration = 0L

    // Configuration states
    private val _isConfigured = MutableStateFlow(repository.settings.isPlexConfigured || repository.settings.isCompanionConfigured)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _baseUrlFlow = MutableStateFlow(repository.settings.baseUrl)
    val baseUrlFlow: StateFlow<String> = _baseUrlFlow.asStateFlow()

    private val _tokenFlow = MutableStateFlow(repository.settings.token)
    val tokenFlow: StateFlow<String> = _tokenFlow.asStateFlow()

    private val _companionUrlFlow = MutableStateFlow(repository.settings.companionBackendUrl)
    val companionUrlFlow: StateFlow<String> = _companionUrlFlow.asStateFlow()

    private val _companionTokenFlow = MutableStateFlow(repository.settings.companionBackendToken)
    val companionTokenFlow: StateFlow<String> = _companionTokenFlow.asStateFlow()

    private val _libraries = MutableStateFlow<List<PlexDirectory>>(emptyList())
    val libraries: StateFlow<List<PlexDirectory>> = _libraries.asStateFlow()
    private val _libraryLoadError = MutableStateFlow<String?>(null)
    val libraryLoadError: StateFlow<String?> = _libraryLoadError.asStateFlow()

    private val _selectedSectionId = MutableStateFlow(
        if (repository.settings.isCompanionConfigured) "spotcore"
        else repository.settings.sectionId
    )
    val selectedSectionId: StateFlow<String> = _selectedSectionId.asStateFlow()

    private val _selectedLibraryName = MutableStateFlow(
        if (repository.settings.isCompanionConfigured) "SpotCore Library"
        else repository.settings.libraryName
    )
    val selectedLibraryName: StateFlow<String> = _selectedLibraryName.asStateFlow()

    // Library items
    private val _artists = MutableStateFlow<List<PlexMetadata>>(emptyList())
    val artists: StateFlow<List<PlexMetadata>> = _artists.asStateFlow()

    private val _albums = MutableStateFlow<List<PlexMetadata>>(emptyList())
    val albums: StateFlow<List<PlexMetadata>> = _albums.asStateFlow()

    private val _recentlyAddedAlbums = MutableStateFlow<List<PlexMetadata>>(emptyList())
    val recentlyAddedAlbums: StateFlow<List<PlexMetadata>> = _recentlyAddedAlbums.asStateFlow()
    private val _isLoadingRecentlyAdded = MutableStateFlow(false)
    val isLoadingRecentlyAdded: StateFlow<Boolean> = _isLoadingRecentlyAdded.asStateFlow()
    private val _recentlyAddedLoadError = MutableStateFlow<String?>(null)
    val recentlyAddedLoadError: StateFlow<String?> = _recentlyAddedLoadError.asStateFlow()
    private var recentlyAddedLoadGeneration = 0L

    // Detail states
    private val _currentArtistAlbums = MutableStateFlow<List<PlexMetadata>>(emptyList())
    val currentArtistAlbums: StateFlow<List<PlexMetadata>> = _currentArtistAlbums.asStateFlow()

    private val _currentArtistMetadata = MutableStateFlow<PlexMetadata?>(null)
    val currentArtistMetadata: StateFlow<PlexMetadata?> = _currentArtistMetadata.asStateFlow()

    private val _currentArtistTracks = MutableStateFlow<List<PlexMetadata>>(emptyList())
    val currentArtistTracks: StateFlow<List<PlexMetadata>> = _currentArtistTracks.asStateFlow()

    private val _artistProfile = MutableStateFlow<ArtistProfile?>(null)
    val artistProfile: StateFlow<ArtistProfile?> = _artistProfile.asStateFlow()

    private val _artistDetailLoadError = MutableStateFlow<String?>(null)
    val artistDetailLoadError: StateFlow<String?> = _artistDetailLoadError.asStateFlow()

    private val _currentAlbumTracks = MutableStateFlow<List<PlexMetadata>>(emptyList())
    val currentAlbumTracks: StateFlow<List<PlexMetadata>> = _currentAlbumTracks.asStateFlow()
    private val _albumDetailLoadError = MutableStateFlow<String?>(null)
    val albumDetailLoadError: StateFlow<String?> = _albumDetailLoadError.asStateFlow()

    // Search results
    private val _searchResults = MutableStateFlow<List<PlexMetadata>>(emptyList())
    val searchResults: StateFlow<List<PlexMetadata>> = _searchResults.asStateFlow()
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()
    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

    // Local DB flows
    val recentlyPlayed: StateFlow<List<RecentTrack>> = repository.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cachedTracks: StateFlow<List<CachedTrack>> = repository.cachedTracksCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listeningHistory: StateFlow<List<ListeningHistoryEntity>> = repository.listeningHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _cachedCount = MutableStateFlow(0)
    val cachedCount: StateFlow<Int> = _cachedCount.asStateFlow()
    private val _cachedLibraryRevision = MutableStateFlow(0L)
    val cachedLibraryRevision: StateFlow<Long> = _cachedLibraryRevision.asStateFlow()


    // Status states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _isLoadingArtists = MutableStateFlow(false)
    val isLoadingArtists: StateFlow<Boolean> = _isLoadingArtists.asStateFlow()
    private val _isLoadingAlbums = MutableStateFlow(false)
    val isLoadingAlbums: StateFlow<Boolean> = _isLoadingAlbums.asStateFlow()
    private val _artistLoadError = MutableStateFlow<String?>(null)
    val artistLoadError: StateFlow<String?> = _artistLoadError.asStateFlow()
    private val _albumLoadError = MutableStateFlow<String?>(null)
    val albumLoadError: StateFlow<String?> = _albumLoadError.asStateFlow()

    private val _syncState = MutableStateFlow<LibrarySyncState>(LibrarySyncState.Idle)
    val syncState: StateFlow<LibrarySyncState> = _syncState.asStateFlow()
    private val _isRefreshingServerLibrary = MutableStateFlow(false)
    val isRefreshingServerLibrary: StateFlow<Boolean> = _isRefreshingServerLibrary.asStateFlow()

    val syncDiagnosticInfo = SyncDiagnosticService.diagnosticInfo

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _homeFeedFailure = MutableStateFlow<FailurePresentation?>(null)
    val homeFeedFailure: StateFlow<FailurePresentation?> = _homeFeedFailure.asStateFlow()
    private val _connectionErrorMessage = MutableStateFlow<String?>(null)
    val connectionErrorMessage: StateFlow<String?> = _connectionErrorMessage.asStateFlow()
    private val _connectionErrorTechnicalDetails = MutableStateFlow<String?>(null)
    val connectionErrorTechnicalDetails: StateFlow<String?> = _connectionErrorTechnicalDetails.asStateFlow()
    private val _companionRevision = MutableStateFlow(0)
    private val companionReachabilityGeneration = AtomicLong(0)
    private val _companionConfigured = MutableStateFlow(repository.settings.isCompanionConfigured)
    val companionConfigured: StateFlow<Boolean> = _companionConfigured.asStateFlow()
    private var companionReachabilityCheckJob: Job? = null
    private val _companionReachability = MutableStateFlow(
        if (repository.settings.isCompanionConfigured) CompanionReachability.CHECKING
        else CompanionReachability.UNKNOWN
    )
    val companionReachability: StateFlow<CompanionReachability> = _companionReachability.asStateFlow()
    internal enum class ReachabilityProbePhase { IDLE, RUNNING, SUCCEEDED, FAILED, CANCELLED }
    private val _reachabilityProbePhase = MutableStateFlow(ReachabilityProbePhase.IDLE)
    internal val reachabilityProbePhase: StateFlow<ReachabilityProbePhase> = _reachabilityProbePhase.asStateFlow()
    private val _companionConnectionStatus = MutableStateFlow(
        CompanionConnectionPresentation.resolve(
            repository.settings.isCompanionConfigured,
            _companionReachability.value,
            _cachedCount.value
        )
    )
    val companionConnectionStatus: StateFlow<CompanionConnectionStatus> = _companionConnectionStatus.asStateFlow()

    private fun updateCompanionConnectionStatus() {
        _companionConnectionStatus.value = CompanionConnectionPresentation.resolve(
            _companionConfigured.value,
            _companionReachability.value,
            _cachedCount.value
        )
    }

    /** Re-run the reactive Home feed after a transient backend failure. */
    fun retryHomeFeed() {
        clearHomeFeedFailure()
        clearConnectionFailure()
        _companionReachability.value = CompanionReachability.CHECKING
        updateCompanionConnectionStatus()
        companionReachabilityGeneration.incrementAndGet()
        _companionRevision.value++
    }

    /** Explicitly recheck a saved SpotCore connection from Settings recovery. */
    fun retryCompanionConnection() {
        if (!_companionConfigured.value) return
        _companionReachability.value = CompanionReachability.CHECKING
        updateCompanionConnectionStatus()
        clearConnectionFailure()
        val requestGeneration = companionReachabilityGeneration.incrementAndGet()
        companionReachabilityCheckJob?.cancel()
        companionReachabilityCheckJob = viewModelScope.launch(Dispatchers.IO) {
            checkCompanionReachability(requestGeneration)
        }
    }

    /**
     * Settings can be the restored entry route, in which case Home's feed is not
     * collected and cannot update the initial CHECKING state. Subscribe to that
     * shared request only until the first reachability result is known.
     */
    fun ensureCompanionReachabilityChecked() {
        if (!_companionConfigured.value || _companionReachability.value != CompanionReachability.CHECKING) return
        if (companionReachabilityCheckJob?.isActive == true) return
        val requestGeneration = companionReachabilityGeneration.incrementAndGet()
        companionReachabilityCheckJob = viewModelScope.launch(Dispatchers.IO) {
            checkCompanionReachability(requestGeneration)
        }
    }

    private suspend fun checkCompanionReachability(requestGeneration: Long) {
        if (!_companionConfigured.value) return
        _reachabilityProbePhase.value = ReachabilityProbePhase.RUNNING
        val settings = repository.settings
        try {
            val response = BackendClientManager
                .getApiService(settings.companionBackendUrl)
                .checkHomeFeedReachability(
                    userAuthToken = "Bearer ${settings.companionBackendToken}",
                    reachabilityCheck = "true"
                )
                .execute()
            if (!response.isSuccessful) {
                val failure = retrofit2.HttpException(response)
                response.errorBody()?.close()
                throw failure
            }
            if (requestGeneration == companionReachabilityGeneration.get()) {
                _reachabilityProbePhase.value = ReachabilityProbePhase.SUCCEEDED
                _companionReachability.value = CompanionReachability.AVAILABLE
                updateCompanionConnectionStatus()
                clearConnectionFailure()
            }
            response.body()?.close()
        } catch (cancellation: CancellationException) {
            _reachabilityProbePhase.value = ReachabilityProbePhase.CANCELLED
            throw cancellation
        } catch (error: Exception) {
            if (requestGeneration == companionReachabilityGeneration.get()) {
                _reachabilityProbePhase.value = ReachabilityProbePhase.FAILED
                _companionReachability.value = CompanionReachability.UNAVAILABLE
                updateCompanionConnectionStatus()
                setConnectionFailure(ProductFailurePresentation.from(
                    getApplication<Application>(),
                    error,
                    fallback = getApplication<Application>().getString(R.string.product_failure_refresh_library)
                ))
            }
        }
    }

    // Combined Reactive Home Feed State Flow. When SpotCore is configured,
    // Home is server-authoritative; Room recommendations are only a fallback
    // for an unavailable companion request.
    val homeFeedState: StateFlow<HomeFeedState> = combine(
        recentlyAddedAlbums,
        recentlyPlayed,
        cachedTracks,
        _companionRevision
    ) { recentAdded, history, _, _ ->
        val settings = repository.settings
        if (!settings.isCompanionConfigured) {
            _companionReachability.value = CompanionReachability.UNKNOWN
            updateCompanionConnectionStatus()
            recommendationEngine.generateHomeFeed(recentAdded, history)
        } else {
            val requestGeneration = companionReachabilityGeneration.incrementAndGet()
            try {
                val response = fetchCompanionHomeFeed(
                    settings.companionBackendUrl,
                    "Bearer ${settings.companionBackendToken}"
                )
                if (requestGeneration == companionReachabilityGeneration.get()) {
                    _companionReachability.value = CompanionReachability.AVAILABLE
                    updateCompanionConnectionStatus()
                    clearConnectionFailure()
                    clearHomeFeedFailure()
                }
                BackendHomeMapper.merge(
                    response,
                    HomeFeedState(),
                    artworkBaseUrl = settings.companionBackendUrl
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (requestGeneration == companionReachabilityGeneration.get()) {
                    _companionReachability.value = CompanionReachability.UNAVAILABLE
                    updateCompanionConnectionStatus()
                    val failure = ProductFailurePresentation.from(
                        getApplication<Application>(),
                        error,
                        fallback = getApplication<Application>().getString(R.string.product_failure_refresh_library)
                    )
                    setConnectionFailure(failure)
                    _homeFeedFailure.value = failure
                }
                Log.w("MusicViewModel", "SpotCore Home unavailable; using saved recommendations (${error::class.simpleName})")
                recommendationEngine.generateHomeFeed(recentAdded, history)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeFeedState())

    // Playlist and Download states
    val playlists: StateFlow<List<com.example.data.PlaylistEntity>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedTracksSnapshot: StateFlow<List<DownloadedTrackEntity>?> = repository.downloadedTracks
        .map<List<DownloadedTrackEntity>, List<DownloadedTrackEntity>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val downloadedTracks: StateFlow<List<DownloadedTrackEntity>> = downloadedTracksSnapshot
        .filterNotNull()
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
    private val _lastFmAuthorizationPendingFlow = MutableStateFlow(repository.settings.lastFmPendingToken.isNotBlank())
    val lastFmAuthorizationPendingFlow: StateFlow<Boolean> = _lastFmAuthorizationPendingFlow.asStateFlow()
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
    private val _lastFmAuthFailed = MutableStateFlow(false)
    val lastFmAuthFailed: StateFlow<Boolean> = _lastFmAuthFailed.asStateFlow()

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

    fun updateGaplessEnabled(enabled: Boolean) {
        repository.settings.gaplessEnabled = enabled
        _gaplessEnabledFlow.value = enabled
        playbackManager.setGaplessPlaybackEnabled(enabled)
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

    fun triggerServerRescan() {
        if (_isRefreshingServerLibrary.value) return
        _isRefreshingServerLibrary.value = true
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            clearConnectionFailure()
            _companionReachability.value = CompanionReachability.CHECKING
            updateCompanionConnectionStatus()
            try {
                Log.d("MusicViewModel", "Triggering SpotCore library refresh")
                repository.scanServerLibrary()
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error refreshing SpotCore library (${e::class.simpleName})")
                _companionReachability.value = CompanionReachability.UNAVAILABLE
                updateCompanionConnectionStatus()
                val failure = ProductFailurePresentation.from(
                    getApplication<Application>(),
                    e,
                    fallback = getApplication<Application>().getString(R.string.product_failure_refresh_library)
                )
                setConnectionFailure(failure)
                _errorMessage.value = failure.message
                _isLoading.value = false
                _isRefreshingServerLibrary.value = false
                return@launch
            }

            _companionReachability.value = CompanionReachability.AVAILABLE
            updateCompanionConnectionStatus()
            clearConnectionFailure()
            enqueueLibrarySyncSafely(
                "spotcore",
                restart = true,
                fallback = getApplication<Application>().getString(R.string.product_failure_sync_start_failed)
            )
            _isLoading.value = false
            _isRefreshingServerLibrary.value = false
        }
    }

    fun playTrackFromCache(track: CachedTrack) {
        viewModelScope.launch {
            val trackItem = TrackItem(
                ratingKey = track.ratingKey,
                title = track.title,
                artist = track.artist,
                album = track.album,
                key = track.key,
                thumb = track.thumb,
                duration = track.duration,
                albumRatingKey = albumNavigationKey(effectiveAlbumArtist(track.albumArtist, track.artist), track.album),
                albumArtist = effectiveAlbumArtist(track.albumArtist, track.artist)
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

    fun setCompanionFavorite(trackId: String, favorite: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setCompanionFavorite(trackId, favorite) }
                .onFailure { error ->
                    Log.w("MusicViewModel", "SpotCore favorite could not be queued (${error::class.simpleName})")
                    postErrorMessage(getApplication<Application>().getString(R.string.library_spotcore_state_save_failed))
                }
        }
    }

    fun setCompanionRating(trackId: String, rating: Int) {
        viewModelScope.launch {
            runCatching { repository.setCompanionRating(trackId, rating) }
                .onFailure { error ->
                    Log.w("MusicViewModel", "SpotCore rating could not be queued (${error::class.simpleName})")
                    postErrorMessage(getApplication<Application>().getString(R.string.library_spotcore_state_save_failed))
                }
        }
    }

    fun isTrackLikedFlow(ratingKey: String): Flow<Boolean> {
        return repository.isTrackLikedFlow(ratingKey)
    }

    fun savePlaylistWithTracks(
        name: String,
        tracks: List<TrackItem>,
        onSaved: (() -> Unit)? = null,
        onFailure: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val id = repository.createPlaylist(name)
                tracks.forEach { track ->
                    repository.addTrackToPlaylist(id.toInt(), track)
                }
                onSaved?.invoke()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("MusicViewModel", "Could not save playlist with tracks (${error::class.simpleName})")
                onFailure?.invoke()
            }
        }
    }

    fun downloadTracksList(tracks: List<TrackItem>) {
        tracks.forEach { track ->
            startDownload(track)
        }
    }

    fun downloadPlaylist(playlistId: Int) {
        viewModelScope.launch {
            repository.getPlaylistTracksList(playlistId).forEach { startDownload(it.toTrackItem()) }
        }
    }

    fun createPlaylist(
        name: String,
        onFailure: (() -> Unit)? = null,
        onCreated: ((Long) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val id = repository.createPlaylist(name)
                onCreated?.invoke(id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("MusicViewModel", "Could not save playlist (${error::class.simpleName})")
                onFailure?.invoke()
            }
        }
    }

    fun sharePlaylist(playlistId: Int) {
        viewModelScope.launch {
            if (repository.sharePlaylist(playlistId)) repository.enqueuePlaylistSync()
        }
    }

    fun refreshSharedPlaylist(playlistId: Int) {
        viewModelScope.launch {
            repository.refreshSharedPlaylist(playlistId)
        }
    }

    fun keepLocalPlaylistChangesAfterConflict(playlistId: Int) {
        viewModelScope.launch {
            repository.keepLocalPlaylistChangesAfterConflict(playlistId)
        }
    }

    fun renamePlaylist(playlistId: Int, name: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, name)
        }
    }

    fun deletePlaylist(
        playlistId: Int,
        onDeleted: (() -> Unit)? = null,
        onFailure: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                if (repository.deletePlaylist(playlistId)) onDeleted?.invoke()
                else onFailure?.invoke()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("MusicViewModel", "Could not delete playlist (${error::class.simpleName})")
                onFailure?.invoke()
            }
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

    fun reorderPlaylistTracks(tracks: List<com.example.data.PlaylistTrackEntity>) {
        viewModelScope.launch { repository.reorderPlaylistTracks(tracks) }
    }

    fun startDownload(track: TrackItem) {
        viewModelScope.launch {
            try {
                // Serialize the terminal/active status check with the queue row write. This
                // covers rapid taps and overlapping album/playlist download requests.
                if (!repository.queueDownload(track)) return@launch
                val request = OneTimeWorkRequestBuilder<TrackDownloadWorker>()
                    .addTag(TrackDownloadWorker.DOWNLOAD_TAG)
                    .addTag(TrackDownloadWorker.TRACK_TAG_PREFIX + track.ratingKey)
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
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                runCatching {
                    repository.markDownloadStatus(track.ratingKey, "failed", error.localizedMessage)
                }
                Log.w("MusicViewModel", "Could not queue download (${error::class.simpleName})")
            }
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
        viewModelScope.launch {
            // A completed DB row can outlive its file (for example after
            // storage cleanup). Remove that stale row so startDownload is not
            // blocked by the completed-record guard.
            if (track.status == "completed") {
                repository.deleteDownloadedTrack(track.ratingKey)
            }
            startDownload(track.toTrackItem())
        }
    }

    fun deleteAllDownloads() {
        viewModelScope.launch {
            WorkManager.getInstance(getApplication()).cancelAllWorkByTag("spotamp_download")
            repository.deleteAllDownloads()
        }
    }

    init {
        viewModelScope.launch {
            val workManager = runCatching { WorkManager.getInstance(getApplication()) }
                .onFailure { Log.w("MusicViewModel", "Download progress monitoring is unavailable (${it::class.simpleName})") }
                .getOrNull()
                ?: return@launch
            workManager.getWorkInfosByTagFlow(TrackDownloadWorker.DOWNLOAD_TAG)
                .collect { workInfos ->
                    _downloadProgresses.value = buildMap {
                        workInfos.forEach { workInfo ->
                            if (workInfo.state != androidx.work.WorkInfo.State.RUNNING) return@forEach
                            val ratingKey = workInfo.tags.asSequence()
                                .firstOrNull { it.startsWith(TrackDownloadWorker.TRACK_TAG_PREFIX) }
                                ?.removePrefix(TrackDownloadWorker.TRACK_TAG_PREFIX)
                                ?.takeIf(String::isNotBlank)
                                ?: return@forEach
                            put(
                                ratingKey,
                                workInfo.progress.getFloat(TrackDownloadWorker.KEY_PROGRESS, -1f)
                            )
                        }
                    }
                }
        }

        if (BuildConfig.QA_BUILD) {
            repository.settings.companionBackendUrl
                .toHttpUrlOrNull()
                ?.let(QaNetworkPolicy::allowExplicitCompanionOrigin)
        }
        updatePlaybackCredentials()
        playbackManager.setMusicDao(com.example.data.MusicDatabase.getDatabase(application).musicDao())
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

        // Older cache rows can contain relative, desktop-only, or blank
        // artwork references. Queue one refresh so the authoritative SpotCore
        // contract replaces them with authenticated companion URLs.
        if (repository.settings.isCompanionConfigured) {
            repository.enqueuePlaylistSync()
            viewModelScope.launch {
                if (repository.hasNonCanonicalCacheRows()) {
                    repository.startSync("spotcore", restart = true)
                }
            }
        }

        viewModelScope.launch {
            var activeCompanionSyncId: Long? = null
            var activeCompanionSyncGeneration: Long? = null
            _selectedSectionId
                .flatMapLatest { sectionId ->
                    if (sectionId != "spotcore") {
                        activeCompanionSyncId = null
                        activeCompanionSyncGeneration = null
                    }
                    if (sectionId.isEmpty()) flowOf(null)
                    else repository.getSyncStateFlow(sectionId)
                }
                .collect { entity ->
                    _syncState.value = when (entity?.status) {
                        "running" -> if (entity.currentOffset == 0) {
                            LibrarySyncState.Connecting
                        } else {
                            LibrarySyncState.Processing(entity.currentOffset, entity.totalTracks, 0)
                        }
                        "completed" -> {
                            loadInitialLibraryData()
                            LibrarySyncState.Complete(entity.totalTracks, 0, 0)
                        }
                        "failed" -> LibrarySyncState.Failed("Sync failed", entity.lastError ?: "Unknown", "Indexing")
                        else -> LibrarySyncState.Idle
                    }
                    when (entity?.status) {
                        "running" -> {
                            activeCompanionSyncId = entity.lastSyncId
                            activeCompanionSyncGeneration = companionReachabilityGeneration.get()
                        }
                        "completed" -> {
                            // Room replays the last sync row when Settings or Home
                            // subscribes. A historical completion is not proof that
                            // SpotCore is reachable now; only accept a completion
                            // observed after this ViewModel saw that run start.
                            val completedActiveRun = activeCompanionSyncId == entity.lastSyncId &&
                                activeCompanionSyncGeneration == companionReachabilityGeneration.get()
                            activeCompanionSyncId = null
                            activeCompanionSyncGeneration = null
                            if (completedActiveRun && repository.settings.isCompanionConfigured) {
                                _companionReachability.value = CompanionReachability.AVAILABLE
                                updateCompanionConnectionStatus()
                                clearConnectionFailure()
                            }
                        }
                        "failed" -> {
                            activeCompanionSyncId = null
                            activeCompanionSyncGeneration = null
                        }
                        else -> if (entity == null) {
                            activeCompanionSyncId = null
                            activeCompanionSyncGeneration = null
                        }
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
        _baseUrlFlow.value = baseUrl.trim()
        _tokenFlow.value = token.trim()
        _isConfigured.value = repository.settings.isPlexConfigured || repository.settings.isCompanionConfigured
        updatePlaybackCredentials()
        if (repository.settings.isPlexConfigured) {
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
        repository.settings.lastFmPendingToken = ""
        _lastFmEnabledFlow.value = false; _lastFmUsernameFlow.value = ""; _lastFmSessionKeyFlow.value = ""
        _lastFmAuthorizationPendingFlow.value = false
        _lastFmAuthFailed.value = false
        _lastFmAuthMessage.value = "Disconnected"
    }
    fun startLastFmAuthorization() {
        viewModelScope.launch {
            _lastFmAuthFailed.value = false
            _lastFmAuthMessage.value = ""
            LastFmScrobbler.requestToken(repository.settings).onSuccess {
                _lastFmAuthorizationPendingFlow.value = repository.settings.lastFmPendingToken.isNotBlank()
                LastFmScrobbler.authorizationUrl(repository.settings)?.let { getApplication<Application>().startActivity(Intent(Intent.ACTION_VIEW, it.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                _lastFmAuthMessage.value = "Approve SpotAmp in your browser, then tap Complete auth."
            }.onFailure {
                _lastFmAuthFailed.value = true
                _lastFmAuthMessage.value = LastFmAuthorizationFailurePresentation.message(
                    getApplication(),
                    LastFmAuthorizationStep.START
                )
            }
        }
    }
    fun completeLastFmAuthorization() {
        viewModelScope.launch {
            _lastFmAuthFailed.value = false
            _lastFmAuthMessage.value = ""
            LastFmScrobbler.completeAuthorization(repository.settings).onSuccess { name ->
                _lastFmEnabledFlow.value = true; _lastFmUsernameFlow.value = name; _lastFmSessionKeyFlow.value = repository.settings.lastFmSessionKey
                _lastFmAuthorizationPendingFlow.value = false
                _lastFmAuthMessage.value = "Connected as $name"
            }.onFailure {
                _lastFmAuthFailed.value = true
                _lastFmAuthMessage.value = LastFmAuthorizationFailurePresentation.message(
                    getApplication(),
                    LastFmAuthorizationStep.COMPLETE
                )
            }
        }
    }

    fun disconnectCompanion() {
        val backendUrl = repository.settings.companionBackendUrl
        val token = repository.settings.companionBackendToken
        viewModelScope.launch {
            val revoked = if (backendUrl.isNotBlank() && token.isNotBlank()) {
                runCatching {
                    BackendClientManager.getApiService(backendUrl)
                        .revokePairing("Bearer $token")
                        .revoked
                }.getOrDefault(false)
            } else {
                true
            }
            saveCompanionBackend("", "")
            if (!revoked) {
                postErrorMessage(getApplication<Application>().getString(R.string.connection_disconnect_revoke_failed))
            }
        }
    }
    fun saveCompanionBackend(url: String, token: String, deviceId: String = ""): Boolean {
        val normalizedUrl = runCatching { normalizeCompanionUrl(url, allowPrivateHttp = true) }
            .getOrElse {
                if (url.isBlank() && token.isBlank()) "" else {
                    postErrorMessage(getApplication<Application>().getString(companionUrlErrorResource(it.message)))
                    return false
                }
            }
        val normalizedToken = token.trim()
        if ((normalizedUrl.isBlank()) != (normalizedToken.isBlank())) {
            postErrorMessage(getApplication<Application>().getString(R.string.settings_spotcore_credentials_incomplete))
            return false
        }
        if (normalizedUrl.isBlank()) {
            repository.settings.clearCompanionCredential()
        } else {
            try {
                repository.settings.saveCompanionCredential(normalizedUrl, deviceId, normalizedToken)
            } catch (_: Exception) {
                postErrorMessage(getApplication<Application>().getString(R.string.settings_spotcore_credentials_save_failed))
                return false
            }
        }
        companionReachabilityGeneration.incrementAndGet()
        _companionUrlFlow.value = normalizedUrl
        _companionTokenFlow.value = normalizedToken
        playbackManager.setCompanionToken(normalizedToken)
        _companionConfigured.value = repository.settings.isCompanionConfigured
        if (repository.settings.isCompanionConfigured) repository.enqueueCompanionTrackStateSync()
        updateCompanionConnectionStatus()
        clearConnectionFailure()
        clearHomeFeedFailure()
        _libraryLoadError.value = null
        _companionReachability.value = if (repository.settings.isCompanionConfigured) {
            CompanionReachability.CHECKING
        } else {
            CompanionReachability.UNKNOWN
        }
        companionReachabilityCheckJob?.cancel()
        companionReachabilityCheckJob = null
        updateCompanionConnectionStatus()
        _isConfigured.value = repository.settings.isPlexConfigured || repository.settings.isCompanionConfigured
        // sectionId falls back to "spotcore" as soon as credentials are saved,
        // so use the ViewModel's pre-existing selection to detect first pairing.
        if (repository.settings.isCompanionConfigured && _selectedSectionId.value.isBlank()) {
            repository.settings.sectionId = "spotcore"
            repository.settings.libraryName = "SpotCore Library"
            _selectedSectionId.value = "spotcore"
            _selectedLibraryName.value = "SpotCore Library"
            loadInitialLibraryData()
        } else if (!repository.settings.isPlexConfigured && !repository.settings.isCompanionConfigured && _selectedSectionId.value == "spotcore") {
            repository.settings.sectionId = ""
            repository.settings.libraryName = ""
            _selectedSectionId.value = ""
            _selectedLibraryName.value = ""
            _libraries.value = emptyList()
            _artists.value = emptyList()
            _albums.value = emptyList()
            _recentlyAddedAlbums.value = emptyList()
            _recentlyAddedLoadError.value = null
            _isLoadingRecentlyAdded.value = false
            recentlyAddedLoadGeneration++
        }
        if (repository.settings.isCompanionConfigured) repository.enqueuePlaylistSync()
        _companionRevision.value += 1
        if (repository.settings.isCompanionConfigured) ensureCompanionReachabilityChecked()
        clearErrorMessage()
        return true
    }

    fun claimCompanionPairing(
        payload: CompanionPairingPayload,
        deviceName: String,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        val cleanName = deviceName.trim().ifBlank { "SpotAmp" }.take(80)
        viewModelScope.launch {
            val claimResult = runCatching {
                payload.baseUrl.toHttpUrlOrNull()?.let(QaNetworkPolicy::allowExplicitCompanionOrigin)
                val response = BackendClientManager
                    .getApiService(payload.baseUrl)
                    .claimPairing(
                        CompanionPairingClaimRequest(
                            pairingId = payload.pairingId,
                            secret = payload.secret,
                            deviceName = cleanName
                        )
                    )
                if (!saveCompanionBackend(payload.baseUrl, response.deviceToken, response.device.id)) {
                    throw CompanionCredentialSaveException()
                }
            }
            claimResult.onSuccess {
                // A QR claim establishes the companion identity; immediately
                // build the app's local SpotCore index so Home/search/playback
                // use the newly paired library rather than an empty cache.
                runCatching { repository.startSync("spotcore", restart = true) }
                    .onFailure { error ->
                        val message = ProductFailurePresentation.from(
                            getApplication<Application>(),
                            error,
                            fallback = getApplication<Application>().getString(R.string.product_failure_pairing_sync_start_failed)
                    ).message
                        _libraryLoadError.value = message
                        _errorMessage.value = message
                    }
                onSuccess()
            }.onFailure { error ->
                val message = if (error is CompanionCredentialSaveException) {
                    getApplication<Application>().getString(R.string.pairing_credentials_save_failed)
                } else {
                    ProductFailurePresentation.pairing(
                        getApplication<Application>(),
                        error,
                        fallback = getApplication<Application>().getString(R.string.product_failure_pairing_generic)
                    ).message
                }
                postErrorMessage(message)
                onFailure(message)
            }
        }
    }

    fun selectLibrary(sectionId: String, name: String) {
        if (_selectedSectionId.value != sectionId) {
            recentlyAddedLoadGeneration++
            _recentlyAddedAlbums.value = emptyList()
            _recentlyAddedLoadError.value = null
            _isLoadingRecentlyAdded.value = false
        }
        repository.settings.sectionId = sectionId
        repository.settings.libraryName = name
        _selectedSectionId.value = sectionId
        _selectedLibraryName.value = name
        loadInitialLibraryData()
    }

    private fun loadInitialLibraryData() {
        _recentlyAddedLoadError.value = null
        loadArtists()
        loadAlbums()
        loadRecentlyAdded()
        loadCachedCount()
    }

    fun loadCachedCount() {
        // This is also the library-review refresh signal. Track tags can change
        // during a sync without changing the number of cached tracks.
        _cachedLibraryRevision.value += 1
        viewModelScope.launch {
            _cachedCount.value = repository.getCachedCount()
            updateCompanionConnectionStatus()
        }
    }

    fun loadLibraries() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _libraryLoadError.value = null
            try {
                if (repository.settings.isCompanionConfigured) {
                    _libraries.value = listOf(PlexDirectory("spotcore", "SpotCore Library", "music"))
                    if (_selectedSectionId.value.isBlank() || _selectedSectionId.value != "spotcore") {
                        selectLibrary("spotcore", "SpotCore Library")
                    }
                    return@launch
                }
                if (!repository.settings.isPlexConfigured) {
                    val message = "Connect SpotCore in Settings to continue."
                    _libraryLoadError.value = message
                    _errorMessage.value = message
                    return@launch
                }
                val list = loadLibrariesOverride?.invoke() ?: repository.getLibraries()
                _libraries.value = list
                if (list.isNotEmpty() && repository.settings.sectionId.isEmpty()) {
                    // Auto-select the first available music library section
                    val firstLib = list.first()
                    selectLibrary(firstLib.key, firstLib.title)
                }
            } catch (e: Exception) {
                val message = ProductFailurePresentation.from(
                    getApplication<Application>(),
                    e,
                    fallback = getApplication<Application>().getString(R.string.product_failure_load_library)
                ).message
                _libraryLoadError.value = message
                _errorMessage.value = message
                if (repository.settings.isCompanionConfigured) {
                    _companionReachability.value = CompanionReachability.UNAVAILABLE
                    updateCompanionConnectionStatus()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadArtists() {
        val section = _selectedSectionId.value
        if (section.isEmpty()) return
        viewModelScope.launch {
            _isLoadingArtists.value = true
            _artistLoadError.value = null
            try {
                _artists.value = loadLibraryArtistsOverride?.invoke(section) ?: repository.getArtists(section)
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error loading artists (${e::class.simpleName})")
                _artistLoadError.value = ProductFailurePresentation.from(
                    getApplication<Application>(),
                    e,
                    fallback = getApplication<Application>().getString(R.string.product_failure_load_library)
                ).message
            } finally {
                _isLoadingArtists.value = false
            }
        }
    }

    fun loadAlbums() {
        val section = _selectedSectionId.value
        if (section.isEmpty()) return
        viewModelScope.launch {
            _isLoadingAlbums.value = true
            _albumLoadError.value = null
            try {
                _albums.value = loadLibraryAlbumsOverride?.invoke(section) ?: repository.getAlbums(section)
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error loading albums (${e::class.simpleName})")
                _albumLoadError.value = ProductFailurePresentation.from(
                    getApplication<Application>(),
                    e,
                    fallback = getApplication<Application>().getString(R.string.product_failure_load_library)
                ).message
            } finally {
                _isLoadingAlbums.value = false
            }
        }
    }

    fun loadRecentlyAdded() {
        val section = _selectedSectionId.value
        val requestGeneration = ++recentlyAddedLoadGeneration
        if (section.isEmpty()) {
            _recentlyAddedAlbums.value = emptyList()
            _recentlyAddedLoadError.value = null
            _isLoadingRecentlyAdded.value = false
            return
        }
        viewModelScope.launch {
            _isLoadingRecentlyAdded.value = true
            try {
                val recentAlbums = loadRecentlyAddedOverride?.invoke(section)
                    ?: repository.getRecentlyAddedAlbums(section)
                if (requestGeneration == recentlyAddedLoadGeneration) {
                    _recentlyAddedAlbums.value = recentAlbums
                    _recentlyAddedLoadError.value = null
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (e: Exception) {
                if (requestGeneration == recentlyAddedLoadGeneration) {
                    Log.e("MusicViewModel", "Error loading recently added music (${e::class.simpleName})")
                    _recentlyAddedLoadError.value = ProductFailurePresentation.from(
                        getApplication<Application>(),
                        e,
                        fallback = getApplication<Application>().getString(R.string.product_failure_load_library)
                    ).message
                }
            } finally {
                if (requestGeneration == recentlyAddedLoadGeneration) {
                    _isLoadingRecentlyAdded.value = false
                }
            }
        }
    }

    fun loadArtistDetail(artistId: String, fallbackName: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _artistDetailLoadError.value = null
            _currentArtistMetadata.value = null
            _currentArtistTracks.value = emptyList()
            _artistProfile.value = null
            _currentArtistAlbums.value = emptyList()
            try {
                coroutineScope {
                    // 1. Start fetching cached tracks immediately if we have a name (fastest path)
                    val cachedTracksJob = if (fallbackName != null) {
                        launch {
                            val cached = repository.getArtistTracks(fallbackName)
                            if (cached.isNotEmpty()) {
                                _currentArtistTracks.value = cached
                            }
                        }
                    } else null

                    // 2. Fetch critical metadata and album list in parallel
                    val metadataDeferred = async { repository.getMetadataDetail(artistId) }
                    val albumsDeferred = async { repository.getArtistAlbumsForDetail(artistId) }

                    var artistMeta = metadataDeferred.await()

                    // 3. Fallback resolution if ID lookup failed
                    if (artistMeta == null || (artistMeta.type != "artist" && fallbackName != null)) {
                        val searchResults = repository.searchPlex(fallbackName ?: "")
                        val found = searchResults.find { it.type == "artist" && it.title.equals(fallbackName, ignoreCase = true) }
                        if (found != null) {
                            artistMeta = repository.getMetadataDetail(found.ratingKey)
                        }
                    }

                    val finalArtistId = artistMeta?.ratingKey ?: artistId
                    val finalArtistName = artistMeta?.title ?: fallbackName ?: ""

                    // 4. Update UI with metadata and albums
                    val albums = albumsDeferred.await()
                    _currentArtistMetadata.value = artistMeta
                    _currentArtistAlbums.value = albums

                    // 5. Ensure tracks are populated (either from the early job or a new search)
                    cachedTracksJob?.join()
                    if (_currentArtistTracks.value.isEmpty() && finalArtistName.isNotBlank()) {
                        _currentArtistTracks.value = repository.getArtistTracks(finalArtistName)
                    }

                    // 6. Mark as "loaded" for perceived performance (shows core UI)
                    _isLoading.value = false

                    // 7. Background enrichment
                    launch { loadArtistProfile(finalArtistId, finalArtistName) }
                    launch {
                        try {
                            val albumsToFetch = if (albums.size > 20) albums.take(20) else albums
                            val allTracks = albumsToFetch.map { album ->
                                async { repository.getAlbumTracks(album.ratingKey) }
                            }.awaitAll().flatten()

                            if (allTracks.isNotEmpty()) {
                                _currentArtistTracks.value = allTracks
                            }
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Error loading saved tracks (${e::class.simpleName})")
                        }
                    }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error loading artist details (${e::class.simpleName})")
                _artistDetailLoadError.value = ProductFailurePresentation.from(
                    getApplication<Application>(),
                    e,
                    fallback = getApplication<Application>().getString(R.string.product_failure_load_library)
                ).message
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
            // SpotCore is authoritative for companion-backed artist metadata so
            // the desktop Artist Context and this mobile Artist Bio stay in sync.
            if (repository.settings.isCompanionConfigured && artistName.isNotBlank()) {
                try {
                    val companionProfile = BackendClientManager
                        .getApiService(repository.settings.companionBackendUrl)
                        .getArtistProfile(
                            artistName = artistName,
                            plexTokenHeader = null,
                            userAuthToken = "Bearer ${repository.settings.companionBackendToken}"
                        )
                        .artist
                    if (companionProfile != null) {
                        _artistProfile.value = ArtistProfile(
                            bio = companionProfile.bio?.trim().takeUnless { it.isNullOrBlank() }
                                ?: "No artist bio metadata is available from SpotCore.",
                            artworkUrl = normalizeCompanionArtistArtworkUrl(
                                companionBaseUrl = repository.settings.companionBackendUrl,
                                artworkReference = companionProfile.coverUrl
                            ),
                            similarArtists = companionProfile.similarArtists
                                .filter { it.name.isNotBlank() }
                                .map { SimilarArtist(it.name, it.style) },
                            styles = companionProfile.styles.filter { it.isNotBlank() }
                        )
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Error loading SpotCore artist profile (${e::class.simpleName})")
                }
            }

            // Keep the local path honest when no cloud profile was requested or
            // when the provider has no verified profile result.
            _artistProfile.value = ArtistProfile(
                bio = "No artist bio metadata is available.",
                similarArtists = emptyList(),
                styles = emptyList()
            )
        }
    }

    fun loadAlbumDetail(albumId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _albumDetailLoadError.value = null
            _currentAlbumTracks.value = emptyList()
            try {
                _currentAlbumTracks.value = repository.getAlbumTracksForDetail(albumId)
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error loading album details (${e::class.simpleName})")
                _albumDetailLoadError.value = ProductFailurePresentation.from(
                    getApplication<Application>(),
                    e,
                    fallback = getApplication<Application>().getString(R.string.product_failure_load_library)
                ).message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun search(query: String) {
        val requestGeneration = ++searchGeneration
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isBlank()) {
                _searchResults.value = emptyList()
                _searchError.value = null
                _isSearchLoading.value = false
                return@launch
            }
            _searchResults.value = emptyList()
            _searchError.value = null
            _isSearchLoading.value = true
            delay(searchDebounceMillis)
            try {
                val results = searchResultsOverride?.invoke(query) ?: run {
                    val smartResults = repository.smartSearch(query)
                    if (smartResults.isNotEmpty()) {
                        smartResults
                    } else {
                        val hasSavedLibrary = repository.getCachedCount() > 0
                        if (hasSavedLibrary && (
                                com.example.data.SmartSearchService().isStructuredQuery(query) ||
                                    repository.isKnownAnalysisLabelQuery(query)
                                )
                        ) {
                            emptyList()
                        } else {
                            repository.searchCompanion(query)
                        }
                    }
                }
                if (requestGeneration == searchGeneration) {
                    _searchResults.value = results
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (e: Exception) {
                if (requestGeneration == searchGeneration) {
                    Log.e("MusicViewModel", "Error searching music (${e::class.simpleName})")
                    _searchError.value = getApplication<Application>().getString(
                        searchFailureString(hasSavedLibrary = repository.getCachedCount() > 0)
                    )
                }
            } finally {
                if (requestGeneration == searchGeneration) {
                    _isSearchLoading.value = false
                }
            }
        }
    }

    fun syncLibraryCache() {
        val section = _selectedSectionId.value
        if (section.isEmpty() && !repository.settings.isCompanionConfigured && enqueueLibrarySyncForQa == null) return
        viewModelScope.launch {
            enqueueLibrarySyncSafely(if (section.isEmpty()) "spotcore" else section, restart = true)
        }
    }

    private suspend fun enqueueLibrarySyncSafely(
        section: String,
        restart: Boolean,
        fallback: String = getApplication<Application>().getString(R.string.product_failure_sync_start_failed)
    ) {
        _libraryLoadError.value = null
        _errorMessage.value = null
        try {
            val enqueue = enqueueLibrarySyncForQa
            if (enqueue == null) repository.startSync(section, restart = restart)
            else enqueue(section, restart)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            val message = ProductFailurePresentation.from(
                getApplication<Application>(),
                error,
                fallback = fallback
            ).message
            _libraryLoadError.value = message
            _errorMessage.value = message
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
                    _aiError.value = getApplication<Application>().getString(R.string.playlist_generation_library_not_ready)
                    return@launch
                }

                val provider = when (repository.settings.aiProvider) {
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
                val rawIntent = provider.generatePlaylistIntent(prompt, cachedTracks)
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
                    _aiError.value = getApplication<Application>().getString(R.string.playlist_generation_no_matches)
                }
            } catch (e: Exception) {
                _aiError.value = getApplication<Application>().getString(R.string.playlist_generation_failed)
                Log.e("MusicViewModel", "Error generating playlist (${e::class.simpleName})")
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
            duration = 0L,
            albumRatingKey = albumNavigationKey(effectiveAlbumArtist(recent.albumArtist, recent.artist), recent.album),
            albumArtist = effectiveAlbumArtist(recent.albumArtist, recent.artist)
        )
        playbackManager.playTrack(trackItem, listOf(trackItem))
    }

    fun mapMetadataToTrackItem(meta: PlexMetadata): TrackItem? {
        val key = meta.media?.firstOrNull()?.part?.firstOrNull()?.key ?: return null
        val unknownArtist = getApplication<Application>().getString(R.string.common_unknown_artist)
        val unknownAlbum = getApplication<Application>().getString(R.string.common_unknown_album)
        val artist = meta.grandparentTitle?.takeIf { it.isNotBlank() }
            ?: meta.albumArtist?.takeIf { it.isNotBlank() }
            ?: unknownArtist
        val album = meta.parentTitle?.takeIf { it.isNotBlank() } ?: unknownAlbum
        return TrackItem(
            ratingKey = meta.ratingKey,
            title = meta.title,
            artist = artist,
            album = album,
            key = key,
            thumb = meta.thumb ?: "",
            duration = meta.duration ?: 0L,
            albumRatingKey = meta.parentRatingKey ?: albumNavigationKey(meta.albumArtist ?: artist, album),
            albumArtist = meta.albumArtist ?: artist,
            artistRatingKey = meta.grandparentRatingKey ?: com.example.data.artistNavigationKey(artist),
            index = meta.index ?: 0,
            discNumber = meta.parentIndex?.takeIf { it > 0 } ?: 1
        )
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
        clearConnectionFailure()
        clearHomeFeedFailure()
    }

    fun clearActionErrorMessage() {
        _errorMessage.value = null
    }

    private fun setConnectionFailure(failure: FailurePresentation) {
        _connectionErrorMessage.value = failure.message
        _connectionErrorTechnicalDetails.value = failure.technicalDetails
    }

    private fun clearConnectionFailure() {
        _connectionErrorMessage.value = null
        _connectionErrorTechnicalDetails.value = null
    }

    private fun clearHomeFeedFailure() {
        _homeFeedFailure.value = null
    }

    private fun companionUrlErrorResource(message: String?): Int = when (message) {
        "SpotCore URL is empty" -> R.string.settings_spotcore_url_empty
        "SpotCore URL must not contain credentials" -> R.string.settings_spotcore_url_credentials
        "SpotCore URL must not contain a path" -> R.string.settings_spotcore_url_path
        "SpotCore URL must not contain query or fragment" -> R.string.settings_spotcore_url_query
        "SpotCore URL must use HTTPS" -> R.string.settings_spotcore_url_https
        "Public SpotCore HTTP is not allowed; pair with the HTTPS endpoint" -> R.string.settings_spotcore_url_private_http
        else -> R.string.settings_spotcore_url_invalid
    }

    suspend fun generateRadioTracks(request: RadioRequest): List<TrackItem> =
        generateRadioTracks(request, useDiscoveryTuning = false)

    suspend fun generateRadioTracks(
        request: RadioRequest,
        useDiscoveryTuning: Boolean
    ): List<TrackItem> {
        val tuning = _discoveryTuning.value
        val library = repository.getCachedTracksList()
        val candidates = if (useDiscoveryTuning) filterDiscoveryTracks(library, tuning) else library
        val tunedRequest = if (useDiscoveryTuning) applyDiscoveryTuning(request, tuning) else request
        return radioService.generate(tunedRequest, candidates, recentlyPlayed.value)
    }

    fun updateDiscoveryTuning(tuning: DiscoveryTuning) {
        _discoveryTuning.value = discoveryTuningStore.save(tuning)
    }

    fun saveStationRecipe(recipe: SavedStationRecipe) {
        _savedStationRecipes.value = stationRecipeStore.save(recipe)
    }

    fun renameStationRecipe(id: String, name: String) {
        _savedStationRecipes.value = stationRecipeStore.rename(id, name)
    }

    fun deleteStationRecipe(id: String) {
        _savedStationRecipes.value = stationRecipeStore.delete(id)
    }

    fun playStationRecipe(recipe: SavedStationRecipe) {
        viewModelScope.launch {
            try {
                val tracks = generateSavedStationTracks(
                    recipe = recipe,
                    cachedTracks = repository.getCachedTracksList(),
                    history = recentlyPlayed.value,
                    radioService = radioService,
                    tuning = _discoveryTuning.value
                )
                if (tracks.isNotEmpty()) {
                    playbackManager.playQueue(tracks, 0)
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = getApplication<Application>().getString(
                        radioNoMatchesString(runCatching { com.example.data.RadioType.valueOf(recipe.radioType) }
                            .getOrDefault(com.example.data.RadioType.LIBRARY_RADIO))
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.w("MusicViewModel", "Could not restart saved station (${error::class.simpleName})")
                _errorMessage.value = getApplication<Application>().getString(R.string.product_failure_generic)
            }
        }
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
                _errorMessage.value = getApplication<Application>().getString(R.string.radio_needs_synced_library)
                return
            }

            val tracksToPlay = radioService.generate(request, cachedTracks, recentlyPlayed.value)

            if (tracksToPlay.isNotEmpty()) {
                playbackManager.playQueue(tracksToPlay, 0)
                _errorMessage.value = null
            } else {
                _errorMessage.value = getApplication<Application>().getString(radioNoMatchesString(type))
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
    val styles: List<String>,
    val artworkUrl: String? = null
)

data class SimilarArtist(
    val name: String,
    val style: String
)
