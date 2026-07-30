package com.example.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import com.example.data.MusicDao
import com.example.data.PlexSettingsManager
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

@Serializable
data class TrackItem(
    val ratingKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val key: String, // e.g. "/library/parts/123/..."
    val thumb: String, // e.g. "/library/metadata/456/thumb/..."
    val duration: Long,
    val localPath: String? = null,
    val genres: List<String> = emptyList()
)

class PlaybackManager private constructor(private val appContext: Context) {
    private var exoPlayer: ExoPlayer? = null
    private var httpDataSourceFactory: DefaultHttpDataSource.Factory? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _currentTrack = MutableStateFlow<TrackItem?>(null)
    val currentTrack: StateFlow<TrackItem?> = _currentTrack.asStateFlow()

    private val _queue = MutableStateFlow<List<TrackItem>>(emptyList())
    val queue: StateFlow<List<TrackItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow<Int>(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow<Boolean>(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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

    // Database helper to insert into recently played history
    private var musicDao: MusicDao? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val historyMutex = Mutex()
    @Volatile private var lastPersistAt = 0L

    // Base URL & Token reference (populated from Settings)
    var baseUrl: String = ""
        private set
    var token: String = ""
        private set

    // Last.fm Scrobbling support
    private var hasScrobbledCurrent = false
    private var currentTrackCompleted = false

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
        httpDataSourceFactory?.setDefaultRequestProperties(mapOf("X-Plex-Token" to token))
    }

    fun setMusicDao(dao: MusicDao) {
        this.musicDao = dao
        coroutineScope.launch { restorePlaybackState() }
    }

    private suspend fun restorePlaybackState() {
        val state = musicDao?.getPlaybackState() ?: return
        val restored = PlaybackStateRestoration.restore(state) ?: return
        _queue.value = restored.queue
        _currentIndex.value = restored.currentIndex
        val track = restored.queue[restored.currentIndex]
        _currentTrack.value = track
        _progress.value = restored.positionMs
        _shuffleModeEnabled.value = restored.shuffleEnabled
        _repeatMode.value = restored.repeatMode
        mainHandler.post {
            exoPlayer?.shuffleModeEnabled = restored.shuffleEnabled
            exoPlayer?.repeatMode = restored.repeatMode
        }
        loadLyricsForTrack(track)
    }

    private fun persistPlaybackState() {
        val now = System.currentTimeMillis()
        if (now - lastPersistAt < 2000L) return
        lastPersistAt = now
        coroutineScope.launch {
            val dao = musicDao ?: return@launch
            dao.savePlaybackState(
                PlaybackStateEntity(
                    queueJson = Json.encodeToString(_queue.value),
                    currentIndex = _currentIndex.value,
                    positionMs = _progress.value,
                    shuffleEnabled = _shuffleModeEnabled.value,
                    repeatMode = _repeatMode.value
                )
            )
        }
    }

    private fun initializePlayer() {
        if (exoPlayer != null) return

        val httpFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("X-Plex-Token" to token))
        httpDataSourceFactory = httpFactory
        exoPlayer = ExoPlayer.Builder(appContext)
            // Route local file:// downloads through FileDataSource and remote Plex media
            // through the authenticated HTTP factory.
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(appContext, httpFactory)))
            .build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    // Keep diagnostics actionable without logging authenticated URLs or tokens.
                    android.util.Log.e("PlaybackManager", "Playback failed: ${error.errorCodeName} (${error.message ?: "no message"})")
                    _isPlaying.value = false
                    _isLoading.value = false
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    applyAudioEffects()
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
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
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    _progress.value = newPosition.positionMs
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mainHandler.post {
                        mediaItem?.let { item ->
                            val ratingKey = item.mediaId
                            _currentTrack.value?.let { previous ->
                                if (previous.ratingKey != ratingKey && !currentTrackCompleted) {
                                    recordListeningEvent(previous, completed = false, skipped = true)
                                }
                            }
                            val track = _queue.value.find { it.ratingKey == ratingKey }
                            if (track != null) {
                                _currentTrack.value = track
                                val idx = _queue.value.indexOfFirst { it.ratingKey == ratingKey }
                                if (idx != -1) {
                                    _currentIndex.value = idx
                                }
                                _duration.value = track.duration
                                saveToRecentlyPlayed(track)
                                currentTrackCompleted = false
                                recordListeningEvent(track, completed = false, skipped = false)
                                
                                // Reset Last.fm triggers and Notify Now Playing
                                hasScrobbledCurrent = false
                                val settings = PlexSettingsManager(appContext)
                                LastFmScrobbler.updateNowPlaying(appContext, settings, track)
                                loadLyricsForTrack(track)
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
                    thumb = track.thumb
                )
            )
        }
    }

    @OptIn(UnstableApi::class)
    private fun updateExoPlayerQueue(queueList: List<TrackItem>, startIndex: Int) {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

        mainHandler.post {
            exoPlayer?.let { player ->
                player.stop()
                player.clearMediaItems()
                val mediaItems = queueList.map { track ->
                    val streamUrl = mediaUri(track, normalizedBaseUrl)
                    MediaItem.Builder()
                        .setUri(streamUrl)
                        .setMediaId(track.ratingKey)
                        .build()
                }
                player.addMediaItems(mediaItems)
                player.shuffleModeEnabled = _shuffleModeEnabled.value
                player.repeatMode = _repeatMode.value
                if (startIndex in mediaItems.indices) {
                    player.seekTo(startIndex, 0L)
                }
                player.prepare()
                player.playWhenReady = true
                _isPlaying.value = true
                applyAudioEffects()
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

    private fun mediaUri(track: TrackItem, normalizedBaseUrl: String = baseUrl.trimEnd('/')): String {
        val localFile = getLocalAudioFile(track)
        if (localFile != null) return localFile.toURI().toString()
        return "$normalizedBaseUrl${track.key}"
    }

    fun playTrack(track: TrackItem, queueList: List<TrackItem>) {
        _queue.value = queueList
        val index = queueList.indexOfFirst { it.ratingKey == track.ratingKey }
        _currentIndex.value = if (index != -1) index else 0
        _currentTrack.value = track
        persistPlaybackState()
        updateExoPlayerQueue(queueList, if (index != -1) index else 0)
    }

    fun playQueue(queueList: List<TrackItem>, startIndex: Int = 0) {
        if (queueList.isEmpty()) return
        _queue.value = queueList
        val index = if (startIndex in queueList.indices) startIndex else 0
        _currentIndex.value = index
        _currentTrack.value = queueList[index]
        persistPlaybackState()
        updateExoPlayerQueue(queueList, index)
    }

    fun playNext(track: TrackItem) {
        val currentQueue = _queue.value.toMutableList()
        val currentIndexVal = _currentIndex.value

        val existingIndex = currentQueue.indexOfFirst { it.ratingKey == track.ratingKey }
        if (existingIndex != -1) {
            currentQueue.removeAt(existingIndex)
            mainHandler.post {
                exoPlayer?.removeMediaItem(existingIndex)
            }
        }

        val insertIndex = if (currentIndexVal == -1) 0 else currentIndexVal + 1
        currentQueue.add(insertIndex, track)
        _queue.value = currentQueue

        mainHandler.post {
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            val streamUrl = mediaUri(track, normalizedBaseUrl)
            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(track.ratingKey)
                .build()
            exoPlayer?.addMediaItem(insertIndex, mediaItem)
            if (currentIndexVal == -1) {
                _currentIndex.value = 0
                _currentTrack.value = track
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
            }
        }
    }

    fun playTracksNext(tracks: List<TrackItem>) {
        val currentQueue = _queue.value.toMutableList()
        val currentIndexVal = _currentIndex.value

        val insertIndex = if (currentIndexVal == -1) 0 else currentIndexVal + 1
        currentQueue.addAll(insertIndex, tracks)
        _queue.value = currentQueue

        mainHandler.post {
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            val mediaItems = tracks.map { track ->
                val streamUrl = mediaUri(track, normalizedBaseUrl)
                MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaId(track.ratingKey)
                    .build()
            }
            exoPlayer?.addMediaItems(insertIndex, mediaItems)
            if (currentIndexVal == -1 && tracks.isNotEmpty()) {
                _currentIndex.value = 0
                _currentTrack.value = tracks.first()
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
            }
        }
    }

    fun addToQueue(track: TrackItem) {
        val currentQueue = _queue.value.toMutableList()
        if (currentQueue.any { it.ratingKey == track.ratingKey }) return
        currentQueue.add(track)
        _queue.value = currentQueue

        mainHandler.post {
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            val streamUrl = mediaUri(track, normalizedBaseUrl)
            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(track.ratingKey)
                .build()
            exoPlayer?.addMediaItem(mediaItem)
            if (_currentIndex.value == -1) {
                _currentIndex.value = 0
                _currentTrack.value = track
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
            }
        }
    }

    fun addTracksToQueue(tracks: List<TrackItem>) {
        val currentQueue = _queue.value.toMutableList()
        currentQueue.addAll(tracks)
        _queue.value = currentQueue

        mainHandler.post {
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            val mediaItems = tracks.map { track ->
                val streamUrl = mediaUri(track, normalizedBaseUrl)
                MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaId(track.ratingKey)
                    .build()
            }
            exoPlayer?.addMediaItems(mediaItems)
            if (_currentIndex.value == -1 && tracks.isNotEmpty()) {
                _currentIndex.value = 0
                _currentTrack.value = tracks.first()
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
            }
        }
    }

    fun removeFromQueue(index: Int) {
        if (index !in _queue.value.indices) return
        val updated = _queue.value.toMutableList().apply { removeAt(index) }
        _queue.value = updated
        mainHandler.post { exoPlayer?.removeMediaItem(index) }
        if (updated.isEmpty()) {
            _currentIndex.value = -1
            _currentTrack.value = null
        } else if (_currentIndex.value >= updated.size) {
            _currentIndex.value = updated.lastIndex
            _currentTrack.value = updated.last()
        }
        persistPlaybackState()
    }

    fun clearQueue() {
        mainHandler.post {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        }
        _queue.value = emptyList()
        _currentIndex.value = -1
        _currentTrack.value = null
        _progress.value = 0L
        persistPlaybackState()
    }

    fun play() {
        mainHandler.post {
            exoPlayer?.play()
        }
    }

    fun pause() {
        mainHandler.post {
            exoPlayer?.pause()
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
            persistPlaybackState()
        }
    }

    fun toggleShuffle() {
        val newValue = !_shuffleModeEnabled.value
        _shuffleModeEnabled.value = newValue
        persistPlaybackState()
        mainHandler.post {
            exoPlayer?.shuffleModeEnabled = newValue
        }
    }

    fun toggleRepeat() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        persistPlaybackState()
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
        lyricsJob?.cancel()
        if (track == null) {
            _currentLyrics.value = null
            _lyricsLoading.value = false
            return
        }

        _lyricsLoading.value = true
        lyricsJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                var lyricsLoaded = false

                // 1. Try to fetch lyrics from Plex Server if online/streaming
                if (baseUrl.isNotEmpty() && token.isNotEmpty() && track.ratingKey.isNotEmpty()) {
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
                                "${baseUrl.trimEnd('/')}${lyricStream.key}?X-Plex-Token=$token"
                            }
                            
                            val request = okhttp3.Request.Builder()
                                .url(streamUrl)
                                .get()
                                .build()
                            
                            val client = okhttp3.OkHttpClient.Builder()
                                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                .build()
                                
                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val content = response.body?.string()
                                    if (!content.isNullOrBlank()) {
                                        val codec = lyricStream.codec?.lowercase() ?: lyricStream.format?.lowercase() ?: "lrc"
                                        val isLrc = codec == "lrc"
                                        val lyrics = LyricsParser.parse(content, isLrc)
                                        _currentLyrics.value = lyrics
                                        lyricsLoaded = true
                                        android.util.Log.d("PlaybackManager", "Successfully fetched lyrics from Plex Server for track ${track.title}")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PlaybackManager", "Error fetching lyrics from Plex Server", e)
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
                        _currentLyrics.value = lyrics
                    } else {
                        _currentLyrics.value = null
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackManager", "Error loading lyrics for ${track.title}", e)
                _currentLyrics.value = null
            } finally {
                _lyricsLoading.value = false
            }
        }
    }

    fun release() {
        mainHandler.post {
            stopProgressUpdates()
            equalizer?.release()
            dynamicsProcessing?.release()
            exoPlayer?.release()
            exoPlayer = null
        }
        coroutineScope.cancel()
    }
}
