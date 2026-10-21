package com.example.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import com.example.data.MusicDao
import com.example.data.PlexSettingsManager
import com.example.data.resolveCompanionMediaReference
import com.example.data.SyncAccountIsolationPolicy
import com.example.data.RecentTrack
import com.example.data.LastFmScrobbler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.data.PlaybackStateEntity
import com.example.data.ListeningHistoryEntity
import com.example.data.PendingCompanionPlayEntity
import com.example.sync.CompanionPlaySyncScheduler
import com.example.sync.spotCoreTrackId
import com.example.data.isExactCompanionOrigin
import com.example.data.isExactOrigin
import com.example.R
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@Serializable
data class TrackItem(
    val ratingKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val key: String, // e.g. "/library/parts/123/..."
    val thumb: String, // e.g. an authenticated companion artwork URL
    val duration: Long,
    val localPath: String? = null,
    val genres: List<String> = emptyList(),
    val albumRatingKey: String? = null,
    val albumArtist: String? = null,
    val artistRatingKey: String? = null,
    val index: Int = 0,
    val discNumber: Int = 1
)

private class OfflineTrackUnavailableException : IllegalStateException()

enum class PlaybackFailureKind {
    RETRYABLE,
    LOCAL_FILE_MISSING,
    AUDIO_UNPLAYABLE
}

object PlaybackFailurePolicy {
    fun classify(errorCode: Int): PlaybackFailureKind = when (errorCode) {
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            PlaybackFailureKind.LOCAL_FILE_MISSING
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
        androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
            PlaybackFailureKind.AUDIO_UNPLAYABLE
        else -> PlaybackFailureKind.RETRYABLE
    }
}

@OptIn(UnstableApi::class)
class PlaybackManager private constructor(private val appContext: Context) {
    private var exoPlayer: ExoPlayer? = null
    private var httpDataSourceFactory: OriginAwareHttpDataSourceFactory? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _currentTrack = MutableStateFlow<TrackItem?>(null)
    val currentTrack: StateFlow<TrackItem?> = _currentTrack.asStateFlow()

    private val _queue = MutableStateFlow<List<TrackItem>>(emptyList())
    val queue: StateFlow<List<TrackItem>> = _queue.asStateFlow()

    private var originalQueue: List<TrackItem> = emptyList()

    private val _currentIndex = MutableStateFlow<Int>(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow<Boolean>(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()
    private val _playbackErrorRetryable = MutableStateFlow(false)
    val playbackErrorRetryable: StateFlow<Boolean> = _playbackErrorRetryable.asStateFlow()
    private val _playbackErrorOfflineTrackMissing = MutableStateFlow(false)
    val playbackErrorOfflineTrackMissing: StateFlow<Boolean> = _playbackErrorOfflineTrackMissing.asStateFlow()
    private val _playbackErrorDiagnostic = MutableStateFlow<String?>(null)
    val playbackErrorDiagnostic: StateFlow<String?> = _playbackErrorDiagnostic.asStateFlow()

    private val _progress = MutableStateFlow<Long>(0L)
    val progress: StateFlow<Long> = _progress.asStateFlow()

    private val _duration = MutableStateFlow<Long>(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow<Boolean>(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow<Int>(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _currentLyrics = MutableStateFlow<Lyrics?>(null)
    val currentLyrics: StateFlow<Lyrics?> = _currentLyrics.asStateFlow()

    private val _lyricsLoading = MutableStateFlow<Boolean>(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    private val _visualizerFrame = MutableStateFlow(PlaybackVisualizerFrame.silence())
    internal val visualizerFrame: StateFlow<PlaybackVisualizerFrame> = _visualizerFrame.asStateFlow()
    private val visualizerCapture = PlaybackVisualizerCapture(publishFrame = { frame ->
        _visualizerFrame.value = frame
    })
    private val visualizerPlaybackGate = PlaybackVisualizerPlaybackGate(visualizerCapture)

    // Database helper to insert into recently played history
    private var musicDao: MusicDao? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val historyMutex = Mutex()
    private val playbackStateMutex = Mutex()
    private val playbackStateWriteSequence = AtomicLong(0L)
    @Volatile private var lastPersistAt = 0L

    // Base URL & Token reference (populated from Settings)
    var baseUrl: String = ""
        private set
    var token: String = ""
        private set
    private var companionToken: String = ""

    // Last.fm Scrobbling support
    private var hasScrobbledCurrent = false
    private var currentTrackCompleted = false
    private var currentPlayEventId: String? = null
    private var currentPlayStartedAt = 0L
    private val listeningDuration = PlaybackListenDurationAccumulator()
    private var companionEventQueued = false

    // Equalizer & Audio processing
    private var equalizer: android.media.audiofx.Equalizer? = null
    private var dynamicsProcessing: android.media.audiofx.DynamicsProcessing? = null

    companion object {
        @Volatile
        private var INSTANCE: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PlaybackManager(context)
                INSTANCE = instance
                instance
            }
        }
    }

    init {
        mainHandler.post {
            initializePlayer()
        }
    }

    fun setCredentials(baseUrl: String, token: String) {
        this.baseUrl = baseUrl
        this.token = token
        updateHttpRequestHeaders()
    }

    fun setCompanionToken(token: String) {
        companionToken = token.trim()
        updateHttpRequestHeaders()
        if (PlexSettingsManager(appContext).isCompanionConfigured) {
            CompanionPlaySyncScheduler.enqueue(appContext)
        }
    }

    private fun updateHttpRequestHeaders() {
        // Credentials are resolved per URI by OriginAwareHttpDataSourceFactory.
    }

    fun setMusicDao(dao: MusicDao) {
        this.musicDao = dao
        coroutineScope.launch { restorePlaybackState() }
        if (PlexSettingsManager(appContext).isCompanionConfigured) {
            CompanionPlaySyncScheduler.enqueue(appContext)
        }
    }

    private suspend fun restorePlaybackState() {
        // Do not overwrite an active in-memory session when an Activity or
        // service reconnects to the singleton. This commonly happens when a
        // background-playback test (or a real user) returns to the app while
        // a track is already playing; persisted state is only a startup
        // fallback, not a competing source of truth.
        if (_currentTrack.value != null || _queue.value.isNotEmpty() || _isPlaying.value) return
        val state = musicDao?.getPlaybackState() ?: return
        val restored = PlaybackStateRestoration.restore(state) { track ->
            track.localPath.isNullOrBlank() || File(track.localPath).isFile
        } ?: return
        _queue.value = restored.queue
        _currentIndex.value = restored.currentIndex
        val track = restored.queue[restored.currentIndex]
        _currentTrack.value = track
        _progress.value = restored.positionMs
        _shuffleModeEnabled.value = restored.shuffleEnabled
        _repeatMode.value = restored.repeatMode
        _isPlaying.value = restored.playWhenReady
        mainHandler.post {
            val player = exoPlayer ?: return@post
            val mediaItems = buildMediaItems(restored.queue, baseUrl.trimEnd('/'), retryableOnFailure = true)
                ?: return@post
            player.stop()
            player.clearMediaItems()
            player.addMediaItems(mediaItems)
            player.shuffleModeEnabled = false
            player.repeatMode = restored.repeatMode
            player.seekTo(restored.currentIndex, restored.positionMs)
            player.prepare()
            player.playWhenReady = restored.playWhenReady
            applyAudioEffects()
        }
        loadLyricsForTrack(track)
        persistPlaybackState(force = true)
    }

    private fun persistPlaybackState(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < 2000L) return
        lastPersistAt = now
        val stateSnapshot = PlaybackStateEntity(
            queueJson = Json.encodeToString(_queue.value),
            currentIndex = _currentIndex.value,
            positionMs = _progress.value,
            shuffleEnabled = _shuffleModeEnabled.value,
            repeatMode = _repeatMode.value,
            playWhenReady = _isPlaying.value
        )
        val writeSequence = playbackStateWriteSequence.incrementAndGet()
        coroutineScope.launch {
            playbackStateMutex.withLock {
                if (writeSequence == playbackStateWriteSequence.get()) {
                    musicDao?.savePlaybackState(stateSnapshot)
                }
            }
        }
    }

    private fun initializePlayer() {
        if (exoPlayer != null) return

        val httpFactory = OriginAwareHttpDataSourceFactory(
            plexBaseUrl = { baseUrl },
            plexToken = { token },
            companionBaseUrl = { PlexSettingsManager(appContext).companionBackendUrl },
            companionToken = { companionToken }
        )
        httpDataSourceFactory = httpFactory
        updateHttpRequestHeaders()
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(TeeAudioProcessor(visualizerCapture)))
                .build()
        }

        exoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
            // Route local file:// downloads through FileDataSource and remote Plex media
            // through the authenticated HTTP factory.
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(appContext, httpFactory)))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
            setPauseAtEndOfMediaItems(!PlexSettingsManager(appContext).gaplessEnabled)
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    // Media3 exception messages may include the source URI; keep this diagnostic
                    // to its stable code name so authenticated URLs and track data stay private.
                    android.util.Log.e("PlaybackManager", "Playback failed: ${error.errorCodeName}")
                    _currentTrack.value?.let { finalizeCompanionPlayEvent(it) }
                    visualizerCapture.setEnabled(false, clearFrameWhenDisabled = true)
                    _playbackErrorDiagnostic.value = error.errorCodeName
                    _isPlaying.value = false
                    _isLoading.value = false
                    when (PlaybackFailurePolicy.classify(error.errorCode)) {
                        PlaybackFailureKind.LOCAL_FILE_MISSING -> {
                            _playbackError.value = appContext.getString(R.string.playback_local_file_missing)
                            _playbackErrorRetryable.value = false
                            _playbackErrorOfflineTrackMissing.value = true
                        }
                        PlaybackFailureKind.AUDIO_UNPLAYABLE -> {
                            _playbackError.value = appContext.getString(R.string.playback_audio_unplayable)
                            _playbackErrorRetryable.value = false
                            _playbackErrorOfflineTrackMissing.value = false
                        }
                        PlaybackFailureKind.RETRYABLE -> {
                            _playbackError.value = appContext.getString(R.string.playback_error_generic)
                            _playbackErrorRetryable.value = true
                            _playbackErrorOfflineTrackMissing.value = false
                        }
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    applyAudioEffects()
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    updateVisualizerCaptureForPlayback(playing)
                    listeningDuration.resetSample()
                    if (playing) {
                        startProgressUpdates()
                        // Start Foreground Service to prevent background suspend and show controls
                        try {
                            val serviceIntent = android.content.Intent(appContext, MusicPlaybackService::class.java).apply {
                                action = MusicPlaybackService.ACTION_START
                            }
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                appContext.startForegroundService(serviceIntent)
                            } else {
                                appContext.startService(serviceIntent)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        stopProgressUpdates()
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    android.util.Log.d("PlaybackManager", "Playback state changed: $state")
                    _isLoading.value = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) {
                        val duration = exoPlayer?.duration ?: 0L
                        if (duration > 0) {
                            _duration.value = duration
                        }
                    }
                    if (state == Player.STATE_ENDED) {
                        visualizerCapture.setEnabled(false, clearFrameWhenDisabled = true)
                        _currentTrack.value?.let { finalizeCompanionPlayEvent(it) }
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    _progress.value = newPosition.positionMs
                    listeningDuration.resetSample()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mainHandler.post {
                        mediaItem?.let { item ->
                            val ratingKey = item.mediaId
                            _currentTrack.value?.let { previous ->
                                if (previous.ratingKey != ratingKey && !currentTrackCompleted) {
                                    recordListeningEvent(previous, completed = false, skipped = true)
                                }
                                finalizeCompanionPlayEvent(previous)
                            }
                            val playerIndex = exoPlayer?.currentMediaItemIndex ?: -1
                            val track = _queue.value.getOrNull(playerIndex)
                                ?.takeIf { it.ratingKey == ratingKey }
                                ?: _queue.value.firstOrNull { it.ratingKey == ratingKey }
                            if (track != null) {
                                _currentTrack.value = track
                                val idx = playerIndex.takeIf {
                                    it in _queue.value.indices && _queue.value[it].ratingKey == ratingKey
                                } ?: _queue.value.indexOfFirst { it.ratingKey == ratingKey }
                                if (idx != -1) {
                                    _currentIndex.value = idx
                                }
                                _duration.value = track.duration
                                saveToRecentlyPlayed(track)
                                currentTrackCompleted = false
                                currentPlayStartedAt = System.currentTimeMillis()
                                currentPlayEventId = UUID.randomUUID().toString()
                                listeningDuration.reset()
                                companionEventQueued = false
                                if (exoPlayer?.isPlaying == true) {
                                    listeningDuration.sample(android.os.SystemClock.elapsedRealtime(), isPlaying = true)
                                }
                                recordListeningEvent(track, completed = false, skipped = false)
                                
                                // Reset Last.fm triggers and Notify Now Playing
                                hasScrobbledCurrent = false
                                val settings = PlexSettingsManager(appContext)
                                LastFmScrobbler.updateNowPlaying(appContext, settings, track)
                                loadLyricsForTrack(track)
                                persistPlaybackState(force = true)
                            }
                        }
                    }
                }
            })
        }
    }

    private fun saveToRecentlyPlayed(track: TrackItem) {
        coroutineScope.launch {
            val dao = musicDao ?: return@launch
            dao.deleteRecentTrackByKey(track.ratingKey)
            dao.insertRecentTrack(
                RecentTrack(
                    ratingKey = track.ratingKey,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    key = track.key,
                    thumb = track.thumb,
                    albumArtist = track.albumArtist.orEmpty()
                )
            )
        }
    }

    @OptIn(UnstableApi::class)
    private fun updateExoPlayerQueue(queueList: List<TrackItem>, startIndex: Int) {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

        mainHandler.post {
            exoPlayer?.let { player ->
                val mediaItems = buildMediaItems(queueList, normalizedBaseUrl, retryableOnFailure = true)
                if (mediaItems == null) {
                    player.stop()
                    player.clearMediaItems()
                    visualizerCapture.setEnabled(false, clearFrameWhenDisabled = true)
                    _isPlaying.value = false
                    _isLoading.value = false
                    return@let
                }
                _playbackError.value = null
                _playbackErrorRetryable.value = false
                _playbackErrorOfflineTrackMissing.value = false
                _playbackErrorDiagnostic.value = null
                _currentTrack.value?.let { finalizeCompanionPlayEvent(it) }
                visualizerCapture.setEnabled(false, clearFrameWhenDisabled = true)
                player.stop()
                player.clearMediaItems()
                player.addMediaItems(mediaItems)
                player.shuffleModeEnabled = false // Manual shuffle handled by queue order
                player.repeatMode = _repeatMode.value
                if (startIndex in mediaItems.indices) {
                    player.seekTo(startIndex, 0L)
                }
                player.prepare()
                player.playWhenReady = true
                _isPlaying.value = true
                applyAudioEffects()
                persistPlaybackState(force = true)
            }
        }
    }

    private fun updateExoPlayerQueueOrder(queueList: List<TrackItem>) {
        val desiredMediaIds = queueList.map { it.ratingKey }
        mainHandler.post {
            val player = exoPlayer ?: return@post
            if (player.mediaItemCount != desiredMediaIds.size) return@post

            val currentMediaIds = (0 until player.mediaItemCount).map { index ->
                player.getMediaItemAt(index).mediaId
            }
            if (currentMediaIds.groupingBy { it }.eachCount() != desiredMediaIds.groupingBy { it }.eachCount()) {
                return@post
            }

            for (targetIndex in desiredMediaIds.indices) {
                val mediaId = desiredMediaIds[targetIndex]
                var sourceIndex = targetIndex
                while (sourceIndex < player.mediaItemCount && player.getMediaItemAt(sourceIndex).mediaId != mediaId) {
                    sourceIndex++
                }
                if (sourceIndex == player.mediaItemCount) return@post
                if (sourceIndex != targetIndex) {
                    player.moveMediaItem(sourceIndex, targetIndex)
                }
            }
        }
    }

    private fun recordListeningEvent(track: TrackItem, completed: Boolean, skipped: Boolean) {
        coroutineScope.launch {
            historyMutex.withLock {
                val dao = musicDao ?: return@withLock
                val existing = dao.getListeningHistory(track.ratingKey)
                val now = System.currentTimeMillis()
                dao.saveListeningHistory(
                    ListeningHistoryEntity(
                    ratingKey = track.ratingKey,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    playCount = (existing?.playCount ?: 0) + if (!completed && !skipped) 1 else 0,
                    completedCount = (existing?.completedCount ?: 0) + if (completed) 1 else 0,
                    skipCount = (existing?.skipCount ?: 0) + if (skipped) 1 else 0,
                    lastPlayedAt = if (!completed && !skipped) now else existing?.lastPlayedAt,
                    lastCompletedAt = if (completed) now else existing?.lastCompletedAt,
                        lastSkippedAt = if (skipped) now else existing?.lastSkippedAt
                    )
                )
            }
        }
    }

    private fun finalizeCompanionPlayEvent(track: TrackItem) {
        val eventId = currentPlayEventId ?: return
        if (companionEventQueued) return
        companionEventQueued = true
        val playedAt = currentPlayStartedAt
        val listenedMs = listeningDuration.totalMs
        coroutineScope.launch {
            val dao = musicDao ?: return@launch
            val settings = PlexSettingsManager(appContext)
            val accountIdentity = SyncAccountIsolationPolicy.identity(
                settings.companionBackendUrl,
                settings.companionDeviceId
            )?.let { "${it.backendOrigin}|${it.accountId}" } ?: return@launch
            track.spotCoreTrackId()?.let { trackId ->
                dao.insertPendingCompanionPlay(
                    PendingCompanionPlayEntity(
                        eventId = eventId,
                        trackId = trackId,
                        playedAt = playedAt,
                        accountIdentity = accountIdentity,
                        listenedMs = listenedMs
                    )
                )
                CompanionPlaySyncScheduler.enqueue(appContext)
            }
        }
    }

    fun getLocalAudioFile(track: TrackItem): File? {
        val localPath = track.localPath
        if (!localPath.isNullOrEmpty()) {
            val f = File(localPath)
            if (f.exists() && f.isFile) return f
        }
        val targetDir = appContext.getExternalFilesDir("music") ?: File(appContext.filesDir, "music")
        if (targetDir.exists()) {
            val possibleExtensions = listOf("mp3", "flac", "m4a", "wav", "ogg", "aac")
            for (ext in possibleExtensions) {
                val file = File(targetDir, "${track.ratingKey}.$ext")
                if (file.exists() && file.isFile) {
                    return file
                }
            }
        }
        return null
    }

    internal fun mediaUri(track: TrackItem, normalizedBaseUrl: String = baseUrl.trimEnd('/') ): String {
        val localFile = getLocalAudioFile(track)
        val pairedCompanionUrl = PlexSettingsManager(appContext).companionBackendUrl
        val mediaBaseUrl = if (track.spotCoreTrackId() != null && pairedCompanionUrl.isNotBlank()) pairedCompanionUrl else normalizedBaseUrl
        val remoteUri = resolveCompanionMediaReference(mediaBaseUrl, track.key)
        return PlaybackUriPolicy.resolve(
            localUri = localFile?.toURI()?.toString(),
            remoteUri = remoteUri,
            offlineOnly = PlexSettingsManager(appContext).offlineOnly
        ) ?: throw OfflineTrackUnavailableException()
    }

    private fun buildMediaItems(
        tracks: List<TrackItem>,
        normalizedBaseUrl: String,
        retryableOnFailure: Boolean
    ): List<MediaItem>? = try {
        tracks.map { track ->
            MediaItem.Builder()
                .setUri(mediaUri(track, normalizedBaseUrl))
                .setMediaId(track.ratingKey)
                .build()
        }.also {
            _playbackError.value = null
            _playbackErrorRetryable.value = false
            _playbackErrorOfflineTrackMissing.value = false
            _playbackErrorDiagnostic.value = null
        }
    } catch (error: OfflineTrackUnavailableException) {
        _playbackError.value = appContext.getString(R.string.playback_offline_track_missing)
        _playbackErrorRetryable.value = retryableOnFailure
        _playbackErrorOfflineTrackMissing.value = true
        _playbackErrorDiagnostic.value = null
        null
    } catch (error: Exception) {
        android.util.Log.w("PlaybackManager", "Could not prepare playback queue (${error::class.simpleName})")
        _playbackError.value = appContext.getString(R.string.playback_error_generic)
        _playbackErrorRetryable.value = retryableOnFailure
        _playbackErrorOfflineTrackMissing.value = false
        _playbackErrorDiagnostic.value = null
        null
    }

    fun clearPlaybackError() {
        _playbackError.value = null
        _playbackErrorRetryable.value = false
        _playbackErrorOfflineTrackMissing.value = false
        _playbackErrorDiagnostic.value = null
    }

    fun playTrack(track: TrackItem, queueList: List<TrackItem>) {
        originalQueue = queueList
        if (_shuffleModeEnabled.value) {
            val shuffled = queueList.filter { it.ratingKey != track.ratingKey }.shuffled()
            val newQueue = listOf(track) + shuffled
            _queue.value = newQueue
            _currentIndex.value = 0
            _currentTrack.value = track
            updateExoPlayerQueue(newQueue, 0)
        } else {
            _queue.value = queueList
            val index = queueList.indexOfFirst { it.ratingKey == track.ratingKey }
            _currentIndex.value = if (index != -1) index else 0
            _currentTrack.value = track
            updateExoPlayerQueue(queueList, if (index != -1) index else 0)
        }
        persistPlaybackState(force = true)
    }

    fun playQueue(queueList: List<TrackItem>, startIndex: Int = 0) {
        if (queueList.isEmpty()) return
        originalQueue = queueList
        val index = if (startIndex in queueList.indices) startIndex else 0
        val track = queueList[index]
        
        if (_shuffleModeEnabled.value) {
            val shuffled = queueList.filter { it.ratingKey != track.ratingKey }.shuffled()
            val newQueue = listOf(track) + shuffled
            _queue.value = newQueue
            _currentIndex.value = 0
            _currentTrack.value = track
            updateExoPlayerQueue(newQueue, 0)
        } else {
            _queue.value = queueList
            _currentIndex.value = index
            _currentTrack.value = track
            updateExoPlayerQueue(queueList, index)
        }
        persistPlaybackState(force = true)
    }

    fun playNext(track: TrackItem) {
        if (_currentTrack.value?.ratingKey == track.ratingKey) return
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val mediaItem = buildMediaItems(listOf(track), normalizedBaseUrl, retryableOnFailure = false)
            ?.singleOrNull() ?: return
        val currentQueue = _queue.value.toMutableList()
        var currentIndexVal = _currentIndex.value

        val existingIndex = currentQueue.indexOfFirst { it.ratingKey == track.ratingKey }
        if (existingIndex != -1) {
            currentQueue.removeAt(existingIndex)
            if (existingIndex < currentIndexVal) currentIndexVal--
            mainHandler.post {
                exoPlayer?.removeMediaItem(existingIndex)
            }
        }

        val insertIndex = if (currentIndexVal == -1) 0 else currentIndexVal + 1
        currentQueue.add(insertIndex, track)
        _queue.value = currentQueue
        _currentIndex.value = currentIndexVal.coerceAtLeast(0)
        if (currentIndexVal == -1) {
            _currentTrack.value = track
            _isPlaying.value = true
        }

        mainHandler.post {
            exoPlayer?.addMediaItem(insertIndex, mediaItem)
            if (currentIndexVal == -1) {
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
            }
        }
        originalQueue = _queue.value
        persistPlaybackState(force = true)
    }

    fun playTracksNext(tracks: List<TrackItem>) {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val mediaItems = buildMediaItems(tracks, normalizedBaseUrl, retryableOnFailure = false) ?: return
        val currentQueue = _queue.value.toMutableList()
        val currentIndexVal = _currentIndex.value

        val insertIndex = if (currentIndexVal == -1) 0 else currentIndexVal + 1
        currentQueue.addAll(insertIndex, tracks)
        _queue.value = currentQueue
        if (currentIndexVal == -1 && tracks.isNotEmpty()) {
            _currentIndex.value = 0
            _currentTrack.value = tracks.first()
            _isPlaying.value = true
        }

        mainHandler.post {
            exoPlayer?.addMediaItems(insertIndex, mediaItems)
            if (currentIndexVal == -1 && tracks.isNotEmpty()) {
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
            }
        }
        originalQueue = _queue.value
        persistPlaybackState(force = true)
    }

    fun addToQueue(track: TrackItem) {
        val currentQueue = _queue.value.toMutableList()
        if (currentQueue.any { it.ratingKey == track.ratingKey }) return
        val mediaItem = buildMediaItems(listOf(track), baseUrl.trimEnd('/'), retryableOnFailure = false)
            ?.singleOrNull() ?: return
        currentQueue.add(track)
        _queue.value = currentQueue
        val startsPlayback = _currentIndex.value == -1
        if (startsPlayback) {
            _currentIndex.value = 0
            _currentTrack.value = track
            _isPlaying.value = true
        }

        mainHandler.post {
            exoPlayer?.addMediaItem(mediaItem)
            if (startsPlayback) {
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
            }
        }
        originalQueue = _queue.value
        persistPlaybackState(force = true)
    }

    fun addTracksToQueue(tracks: List<TrackItem>) {
        val mediaItems = buildMediaItems(tracks, baseUrl.trimEnd('/'), retryableOnFailure = false) ?: return
        val currentQueue = _queue.value.toMutableList()
        currentQueue.addAll(tracks)
        _queue.value = currentQueue
        val startsPlayback = _currentIndex.value == -1 && tracks.isNotEmpty()
        if (startsPlayback) {
            _currentIndex.value = 0
            _currentTrack.value = tracks.first()
            _isPlaying.value = true
        }

        mainHandler.post {
            exoPlayer?.addMediaItems(mediaItems)
            if (startsPlayback) {
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
            }
        }
        originalQueue = _queue.value
        persistPlaybackState(force = true)
    }

    fun removeFromQueue(index: Int) {
        if (index !in _queue.value.indices) return
        val oldCurrentIndex = _currentIndex.value
        val wasCurrent = index == oldCurrentIndex
        if (wasCurrent) _currentTrack.value?.let { finalizeCompanionPlayEvent(it) }
        val updated = _queue.value.toMutableList().apply { removeAt(index) }
        _queue.value = updated
        mainHandler.post { exoPlayer?.removeMediaItem(index) }
        if (updated.isEmpty()) {
            _currentIndex.value = -1
            _currentTrack.value = null
            _isPlaying.value = false
            visualizerCapture.setEnabled(false, clearFrameWhenDisabled = true)
        } else if (wasCurrent) {
            _currentIndex.value = index.coerceAtMost(updated.lastIndex)
            _currentTrack.value = updated[_currentIndex.value]
            mainHandler.post {
                exoPlayer?.let { player ->
                    player.seekTo(_currentIndex.value, 0L)
                }
            }
        } else {
            val nextIndex = if (index < oldCurrentIndex) oldCurrentIndex - 1 else oldCurrentIndex
            _currentIndex.value = nextIndex.takeIf { it in updated.indices } ?: updated.lastIndex
            _currentTrack.value = updated[_currentIndex.value]
        }
        originalQueue = updated
        persistPlaybackState(force = true)
    }

    fun moveQueueItem(from: Int, to: Int) {
        if (from !in _queue.value.indices || to !in _queue.value.indices || from == to) return
        val updated = _queue.value.toMutableList().apply { add(to, removeAt(from)) }
        val current = _currentIndex.value
        _currentIndex.value = when {
            current == from -> to
            from < current && to >= current -> current - 1
            from > current && to <= current -> current + 1
            else -> current
        }
        _queue.value = updated
        mainHandler.post { exoPlayer?.moveMediaItem(from, to) }
        _currentTrack.value = updated.getOrNull(_currentIndex.value)
        originalQueue = updated
        persistPlaybackState(force = true)
    }

    /** Replaces only the observable queue state for debug/QA fixtures; no media URI is loaded. */
    fun replaceQueueForQa(tracks: List<TrackItem>, currentIndex: Int = 0) {
        val selectedIndex = if (tracks.isEmpty()) -1 else currentIndex.takeIf { it in tracks.indices } ?: 0
        mainHandler.post {
            stopProgressUpdates()
            _currentTrack.value?.let { finalizeCompanionPlayEvent(it) }
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        }
        visualizerCapture.setEnabled(false, clearFrameWhenDisabled = true)
        originalQueue = tracks
        _queue.value = tracks
        _currentIndex.value = selectedIndex
        _currentTrack.value = tracks.getOrNull(selectedIndex)
        _isPlaying.value = false
        _isLoading.value = false
        _progress.value = 0L
        persistPlaybackState(force = true)
    }

    fun clearQueue() {
        mainHandler.post {
            _currentTrack.value?.let { finalizeCompanionPlayEvent(it) }
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        }
        visualizerCapture.setEnabled(false, clearFrameWhenDisabled = true)
        _queue.value = emptyList()
        _currentIndex.value = -1
        _currentTrack.value = null
        _isPlaying.value = false
        _progress.value = 0L
        persistPlaybackState(force = true)
    }

    fun clearUpcoming() {
        val currentQueue = _queue.value
        val activeTrack = _currentTrack.value
        val currentIndex = _currentIndex.value.takeIf { index ->
            index in currentQueue.indices && currentQueue[index].ratingKey == activeTrack?.ratingKey
        } ?: currentQueue.indexOfFirst { it.ratingKey == activeTrack?.ratingKey }
        val retainedQueue = PlaybackQueuePresentation.keepCurrentAndEarlier(currentQueue, currentIndex)

        if (retainedQueue.isEmpty()) {
            clearQueue()
            return
        }
        if (retainedQueue.size == currentQueue.size) return

        _queue.value = retainedQueue
        originalQueue = retainedQueue
        mainHandler.post {
            exoPlayer?.let { player ->
                val firstRemovedIndex = retainedQueue.size
                if (player.mediaItemCount > firstRemovedIndex) {
                    player.removeMediaItems(firstRemovedIndex, player.mediaItemCount)
                }
            }
        }
        persistPlaybackState(force = true)
    }

    fun play() {
        mainHandler.post {
            if (_playbackError.value != null && _playbackErrorRetryable.value && _queue.value.isNotEmpty()) {
                updateExoPlayerQueue(_queue.value, _currentIndex.value.coerceAtLeast(0))
                return@post
            }
            clearPlaybackError()
            exoPlayer?.play()
            // Publish command intent immediately; the listener still reconciles
            // state changes caused by buffering, focus loss, or completion.
            _isPlaying.value = true
            persistPlaybackState(force = true)
        }
    }

    fun pause() {
        mainHandler.post {
            exoPlayer?.pause()
            // Do not leave callers observing a stale playing state while waiting
            // for Media3 to deliver its asynchronous callback.
            _isPlaying.value = false
            updateVisualizerCaptureForPlayback(isPlaying = false)
            persistPlaybackState(force = true)
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun next() {
        mainHandler.post {
            exoPlayer?.let { player ->
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && _queue.value.isNotEmpty()) {
                    player.seekTo(0, 0L)
                }
            }
        }
    }

    fun prev() {
        mainHandler.post {
            exoPlayer?.let { player ->
                if (player.currentPosition > 3000) {
                    player.seekTo(0L)
                } else if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && _queue.value.isNotEmpty()) {
                    player.seekTo(_queue.value.size - 1, 0L)
                } else {
                    player.seekTo(0L)
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mainHandler.post {
            exoPlayer?.seekTo(positionMs)
            _progress.value = positionMs
            persistPlaybackState(force = true)
        }
    }

    fun toggleShuffle() {
        val newValue = !_shuffleModeEnabled.value
        _shuffleModeEnabled.value = newValue

        val current = _currentTrack.value
        if (newValue && current != null) {
            // Keep the active queue entry and shuffle every other entry, including duplicate tracks.
            val queue = _queue.value
            val currentIndex = _currentIndex.value
                .takeIf { it in queue.indices && queue[it].ratingKey == current.ratingKey }
                ?: queue.indexOfFirst { it.ratingKey == current.ratingKey }
            val currentQueueTrack = queue.getOrNull(currentIndex) ?: current
            val remaining = queue.toMutableList().apply {
                if (currentIndex in indices) removeAt(currentIndex)
            }.shuffled()
            val newQueue = listOf(currentQueueTrack) + remaining
            _queue.value = newQueue
            _currentIndex.value = 0
            updateExoPlayerQueueOrder(newQueue)
        } else if (!newValue && originalQueue.isNotEmpty()) {
            // Restore original order
            val newIndex = originalQueue.indexOfFirst { it.ratingKey == current?.ratingKey }
            _queue.value = originalQueue
            _currentIndex.value = if (newIndex != -1) newIndex else 0
            updateExoPlayerQueueOrder(originalQueue)
        }

        persistPlaybackState(force = true)
    }

    /** Sets a deterministic playback failure for Compose QA without starting network playback. */
    fun setPlaybackErrorForQa(message: String, retryable: Boolean) {
        _playbackError.value = message
        _playbackErrorRetryable.value = retryable
        _playbackErrorOfflineTrackMissing.value = false
        _playbackErrorDiagnostic.value = null
        _isPlaying.value = false
        _isLoading.value = false
        visualizerCapture.setEnabled(false, clearFrameWhenDisabled = true)
    }

    /** Sets a safe, stable Media3 error identifier for deterministic Compose QA. */
    fun setPlaybackDiagnosticForQa(code: String) {
        _playbackErrorDiagnostic.value = code
    }

    /**
     * QA-only state stress helper. Fixture controls intentionally do not reload
     * Media3 for every toggle; real queue/player transitions are covered by the
     * dedicated playback instrumentation tests.
     */
    fun toggleShuffleForQa() {
        val newValue = !_shuffleModeEnabled.value
        _shuffleModeEnabled.value = newValue

        val current = _currentTrack.value
        if (newValue && current != null) {
            val remaining = _queue.value.filter { it.ratingKey != current.ratingKey }.shuffled()
            _queue.value = listOf(current) + remaining
            _currentIndex.value = 0
        } else if (!newValue && originalQueue.isNotEmpty()) {
            val newIndex = originalQueue.indexOfFirst { it.ratingKey == current?.ratingKey }
            _queue.value = originalQueue
            _currentIndex.value = if (newIndex != -1) newIndex else 0
        }
    }

    fun toggleRepeat() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        persistPlaybackState(force = true)
        mainHandler.post {
            exoPlayer?.repeatMode = nextMode
        }
    }

    private fun checkScrobbleProgress(progressMs: Long, durationMs: Long, track: TrackItem) {
        if (durationMs <= 0 || hasScrobbledCurrent) return
        val settings = PlexSettingsManager(appContext)
        if (!settings.lastFmEnabled) return

        val halfDuration = durationMs / 2
        val maxScrobbleLimit = 240000L // 4 minutes
        val targetMs = if (halfDuration < maxScrobbleLimit) halfDuration else maxScrobbleLimit

        if (progressMs >= targetMs) {
            hasScrobbledCurrent = true
            LastFmScrobbler.scrobble(appContext, settings, track)
        }
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    listeningDuration.sample(android.os.SystemClock.elapsedRealtime(), isPlaying = true)
                    val pos = player.currentPosition
                    _progress.value = pos
                    persistPlaybackState()
                    _currentTrack.value?.let { track ->
                        checkScrobbleProgress(pos, player.duration, track)
                        val completionThreshold = if (player.duration > 30_000L) player.duration - 30_000L else (player.duration * 0.9).toLong()
                        if (!currentTrackCompleted && completionThreshold > 0 && pos >= completionThreshold) {
                            currentTrackCompleted = true
                            recordListeningEvent(track, completed = true, skipped = false)
                        }
                    }
                } else {
                    listeningDuration.sample(android.os.SystemClock.elapsedRealtime(), isPlaying = false)
                }
            }
            mainHandler.postDelayed(this, 250)
        }
    }

    private fun startProgressUpdates() {
        mainHandler.removeCallbacks(progressRunnable)
        mainHandler.post(progressRunnable)
    }

    private fun stopProgressUpdates() {
        mainHandler.removeCallbacks(progressRunnable)
    }

    fun refreshAudioSettings() {
        mainHandler.post {
            applyAudioEffects()
        }
    }

    fun setVisualizerCaptureEnabled(enabled: Boolean) {
        visualizerPlaybackGate.setCaptureRequested(
            enabled = enabled,
            isPlaying = exoPlayer?.isPlaying == true
        )
    }

    private fun updateVisualizerCaptureForPlayback(isPlaying: Boolean) {
        visualizerPlaybackGate.onPlaybackChanged(isPlaying)
    }

    fun setGaplessPlaybackEnabled(enabled: Boolean) {
        mainHandler.post {
            exoPlayer?.setPauseAtEndOfMediaItems(!enabled)
        }
    }

    fun applyAudioEffects() {
        val sessionId = exoPlayer?.audioSessionId ?: return
        if (sessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) return
        val settings = PlexSettingsManager(appContext)

        // Equalizer Setup
        if (settings.equalizerEnabled) {
            try {
                if (equalizer == null || equalizer?.id != sessionId) {
                    equalizer?.release()
                    equalizer = android.media.audiofx.Equalizer(0, sessionId).apply {
                        enabled = true
                    }
                }
                val eq = equalizer
                if (eq != null) {
                    val preset = settings.equalizerPreset
                    val numPresets = eq.numberOfPresets
                    var found = false
                    for (i in 0 until numPresets) {
                        if (eq.getPresetName(i.toShort()).equals(preset, ignoreCase = true)) {
                            eq.usePreset(i.toShort())
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        val numBands = eq.numberOfBands
                        val range = eq.bandLevelRange
                        val maxLevel = range[1]
                        val midLevel = 0.toShort()
                        when (preset.lowercase()) {
                            "bass boost" -> {
                                if (numBands > 0) eq.setBandLevel(0, (maxLevel * 0.7).toInt().toShort())
                                if (numBands > 1) eq.setBandLevel(1, (maxLevel * 0.5).toInt().toShort())
                            }
                            "vocal boost" -> {
                                if (numBands > 2) eq.setBandLevel(2, (maxLevel * 0.6).toInt().toShort())
                                if (numBands > 3) eq.setBandLevel(3, (maxLevel * 0.4).toInt().toShort())
                            }
                            else -> {
                                for (b in 0 until numBands) eq.setBandLevel(b.toShort(), midLevel)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                equalizer?.enabled = false
                equalizer?.release()
                equalizer = null
            } catch (e: Exception) { e.printStackTrace() }
        }

        // Volume Normalization Setup
        if (settings.normalizationEnabled) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    if (dynamicsProcessing == null) {
                        val builder = android.media.audiofx.DynamicsProcessing.Config.Builder(
                            android.media.audiofx.DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                            1,
                            true, 1,
                            false, 0,
                            false, 0,
                            false
                        )
                        dynamicsProcessing = android.media.audiofx.DynamicsProcessing(0, sessionId, builder.build())
                    }
                    dynamicsProcessing?.let { dp ->
                        dp.enabled = true
                        val limiter = dp.getLimiterByChannelIndex(0)
                        limiter.isEnabled = true
                        limiter.linkGroup = 0
                        limiter.attackTime = 1.0f
                        limiter.releaseTime = 100.0f
                        limiter.ratio = 10.0f
                        limiter.threshold = -2.0f
                        limiter.postGain = 2.0f
                        dp.setLimiterByChannelIndex(0, limiter)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            try {
                dynamicsProcessing?.enabled = false
                dynamicsProcessing?.release()
                dynamicsProcessing = null
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private var lyricsJob: Job? = null
    private val lyricsRequestGate = LyricsRequestGate()

    private fun findFileCaseInsensitive(parentDir: File, baseNames: List<String>, extensions: List<String>): File? {
        if (!parentDir.exists() || !parentDir.isDirectory) return null
        val files = parentDir.listFiles() ?: return null
        
        val lowercaseBaseNames = baseNames.map { it.lowercase().trim() }
        val lowercaseExtensions = extensions.map { it.lowercase().trim() }
        
        // First, try exact matches (faster)
        for (base in baseNames) {
            for (ext in extensions) {
                val exactFile = File(parentDir, "$base.$ext")
                if (exactFile.exists() && exactFile.isFile) return exactFile
            }
        }
        
        // If not found, do case-insensitive match
        return files.firstOrNull { file ->
            file.isFile && 
            lowercaseBaseNames.contains(file.nameWithoutExtension.lowercase().trim()) &&
            lowercaseExtensions.contains(file.extension.lowercase().trim())
        }
    }

    fun loadLyricsForTrack(track: TrackItem?) {
        val requestGeneration = lyricsRequestGate.begin()
        lyricsJob?.cancel()
        if (track == null) {
            _currentLyrics.value = null
            _lyricsLoading.value = false
            return
        }

        _lyricsLoading.value = true
        lyricsJob = coroutineScope.launch(Dispatchers.IO) {
            fun publish(lyrics: Lyrics?) {
                if (lyricsRequestGate.isCurrent(requestGeneration)) _currentLyrics.value = lyrics
            }
            try {
                var lyricsLoaded = false

                // 1. Try to fetch lyrics from Companion Server if online/streaming
                val settings = PlexSettingsManager(appContext)
                val trackUsesCompanionStream = isExactCompanionOrigin(track.key, settings.companionBackendUrl)
                if (track.ratingKey.startsWith("companion:") || trackUsesCompanionStream) {
                    try {
                        val trackId = if (track.ratingKey.startsWith("companion:")) {
                            track.ratingKey.removePrefix("companion:")
                        } else {
                            // Extract ID from stream URL if possible, or use ratingKey as is
                            track.ratingKey
                        }
                        
                        val response = com.example.data.BackendClientManager
                            .getApiService(settings.companionBackendUrl)
                            .getLyrics(trackId, "Bearer ${settings.companionBackendToken}")
                        
                        if (response.text.isNotBlank()) {
                            publish(LyricsParser.parse(response.text, response.synced))
                            lyricsLoaded = true
                            android.util.Log.d("PlaybackManager", "Successfully fetched lyrics from SpotCore")
                        }
                    } catch (e: Exception) {
                        android.util.Log.d("PlaybackManager", "SpotCore lyrics request failed (${e::class.simpleName})")
                    }
                }
                
                // 2. Fallback to Plex Server if not loaded and not companion exclusive
                if (!lyricsLoaded && !trackUsesCompanionStream && baseUrl.isNotEmpty() && token.isNotEmpty() && track.ratingKey.isNotEmpty()) {
                    try {
                        val apiService = com.example.data.PlexClientManager.getApiService(baseUrl)
                        val detail = apiService.getMetadataDetail(track.ratingKey, token)
                        val metadata = detail.mediaContainer.metadata?.firstOrNull()
                        val mediaStreams = metadata?.media?.flatMap { it.part.orEmpty() }?.flatMap { it.streams.orEmpty() }
                        
                        // Look for a subtitle/lyric stream (streamType == 4)
                        val lyricStream = mediaStreams?.firstOrNull { it.streamType == 4 }
                        if (lyricStream != null && !lyricStream.key.isNullOrEmpty()) {
                            val streamUrl = if (lyricStream.key.startsWith("http")) {
                                lyricStream.key
                            } else {
                                "${baseUrl.trimEnd('/')}${lyricStream.key}"
                            }

                            val requestBuilder = okhttp3.Request.Builder().url(streamUrl).get()
                            if (isExactOrigin(streamUrl, baseUrl) && token.isNotBlank()) {
                                requestBuilder.header("X-Plex-Token", token)
                            }
                            val request = requestBuilder.build()
                            
                            val client = okhttp3.OkHttpClient.Builder()
                                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                .addInterceptor(com.example.data.QaNetworkInterceptor())
                                .build()
                                
                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val content = response.body?.string()
                                    if (!content.isNullOrBlank()) {
                                        val codec = lyricStream.codec?.lowercase() ?: lyricStream.format?.lowercase() ?: "lrc"
                                        val isLrc = codec == "lrc"
                                        val lyrics = LyricsParser.parse(content, isLrc)
                                        publish(lyrics)
                                        lyricsLoaded = true
                                        android.util.Log.d("PlaybackManager", "Successfully fetched lyrics from configured music service")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PlaybackManager", "Configured music service lyrics request failed (${e::class.simpleName})")
                    }
                }

                // 2. Fallback to local files if not loaded from Plex Server
                if (!lyricsLoaded) {
                    val settings = PlexSettingsManager(appContext)
                    
                    val artistCleaned = track.artist.replace("[/\\\\?%*:|\"<>]".toRegex(), "_").trim()
                    val titleCleaned = track.title.replace("[/\\\\?%*:|\"<>]".toRegex(), "_").trim()
                    
                    val baseNames = mutableListOf(
                        "$artistCleaned - $titleCleaned",
                        "${track.artist.trim()} - ${track.title.trim()}",
                        titleCleaned,
                        track.title.trim()
                    )

                    // Try to find local audio file to get its parent dir and base name
                    val audioFile = getLocalAudioFile(track)
                    val localParentDir = audioFile?.parentFile
                    if (audioFile != null) {
                        baseNames.add(audioFile.nameWithoutExtension)
                    }

                    // Collect parent directories to search in
                    val searchDirs = mutableListOf<File>()
                    
                    // A. Parent of the downloaded audio file
                    if (localParentDir != null && localParentDir.exists()) {
                        searchDirs.add(localParentDir)
                    }
                    
                    // B. Custom configurable directory
                    val customLyricsDirStr = settings.lyricsDirectory
                    if (customLyricsDirStr.isNotEmpty()) {
                        val customDir = File(customLyricsDirStr)
                        if (customDir.exists()) {
                            searchDirs.add(customDir)
                        }
                    }
                    
                    // C. Fallback default music directory
                    val defaultMusicDir = appContext.getExternalFilesDir("music") ?: File(appContext.filesDir, "music")
                    if (defaultMusicDir.exists()) {
                        searchDirs.add(defaultMusicDir)
                    }

                    var selectedFile: File? = null
                    
                    // Prioritize LRC first, then TXT
                    val extensions = listOf("lrc", "txt")
                    
                    for (ext in extensions) {
                        for (dir in searchDirs.distinct()) {
                            val found = findFileCaseInsensitive(dir, baseNames.distinct(), listOf(ext))
                            if (found != null) {
                                selectedFile = found
                                break
                            }
                        }
                        if (selectedFile != null) break
                    }

                    if (selectedFile != null && selectedFile.exists()) {
                        val isLrc = selectedFile.extension.lowercase() == "lrc"
                        val content = selectedFile.readText(Charsets.UTF_8)
                        val lyrics = LyricsParser.parse(content, isLrc)
                        publish(lyrics)
                    } else {
                        publish(null)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackManager", "Could not load lyrics (${e::class.simpleName})")
                publish(null)
            } finally {
                if (lyricsRequestGate.isCurrent(requestGeneration)) _lyricsLoading.value = false
            }
        }
    }

    fun release() {
        visualizerPlaybackGate.setCaptureRequested(enabled = false, isPlaying = false)
        mainHandler.post {
            stopProgressUpdates()
            equalizer?.release()
            dynamicsProcessing?.release()
            try {
                exoPlayer?.release()
            } catch (error: IllegalArgumentException) {
                // Media3 may attempt to unregister the noisy-audio receiver
                // during teardown even when playback never registered it.
                android.util.Log.w("PlaybackManager", "Player receiver was already unregistered during release", error)
            }
            exoPlayer = null
        }
        coroutineScope.cancel()
        // A released manager cannot be reused: the scope is cancelled and the player is gone.
        // Clear the singleton so a later activity/service/test obtains a fresh instance.
        synchronized(PlaybackManager::class.java) {
            if (INSTANCE === this) INSTANCE = null
        }
    }
}
