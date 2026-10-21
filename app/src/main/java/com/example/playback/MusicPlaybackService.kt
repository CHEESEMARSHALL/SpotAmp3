package com.example.playback

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.media3.common.Player
import coil.Coil
import coil.request.ImageRequest
import com.example.data.PlexSettingsManager
import com.example.data.originCredentialsForUrl
import com.example.data.QaNetworkPolicy
import com.example.BuildConfig
import com.example.R
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest

private data class PlaybackNotificationState(
    val track: TrackItem?,
    val isPlaying: Boolean,
    val canSkipToNext: Boolean
)

class MusicPlaybackService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var playbackManager: PlaybackManager
    private lateinit var mediaSession: MediaSession

    companion object {
        const val CHANNEL_ID = "music_playback_channel_id"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.playback.action.START"
        const val ACTION_PLAY = "com.example.playback.action.PLAY"
        const val ACTION_PAUSE = "com.example.playback.action.PAUSE"
        const val ACTION_PREV = "com.example.playback.action.PREV"
        const val ACTION_NEXT = "com.example.playback.action.NEXT"
        const val ACTION_STOP = "com.example.playback.action.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // startForegroundService() has a short platform deadline. Publish a
        // lightweight notification before initializing Media3 and its observers.
        startInitialForeground()

        playbackManager = PlaybackManager.getInstance(this)
        mediaSession = MediaSession(this, "SpotAmpPlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = playbackManager.play()
                override fun onPause() = playbackManager.pause()
                override fun onSkipToNext() = playbackManager.next()
                override fun onSkipToPrevious() = playbackManager.prev()
                override fun onStop() = playbackManager.pause()
            })
            isActive = true
        }
        observePlaybackState()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The playback observer may detach the foreground notification while
        // keeping this started service alive. Every new start command must
        // therefore re-promote immediately rather than trusting object lifetime.
        startInitialForeground()
        intent?.action?.let { action ->
            when (action) {
                ACTION_START -> {
                    // Handled by the check above
                }
                ACTION_PLAY -> playbackManager.play()
                ACTION_PAUSE -> playbackManager.pause()
                ACTION_PREV -> playbackManager.prev()
                ACTION_NEXT -> playbackManager.next()
                ACTION_STOP -> {
                    playbackManager.pause()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInitialForeground() {
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = notificationBuilder
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_playback_preparing))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_playback_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun observePlaybackState() {
        serviceScope.launch {
            combine(
                playbackManager.currentTrack,
                playbackManager.isPlaying,
                playbackManager.queue,
                playbackManager.currentIndex,
                playbackManager.repeatMode
            ) { track, isPlaying, queue, currentIndex, repeatMode ->
                PlaybackNotificationState(
                    track = track,
                    isPlaying = isPlaying,
                    canSkipToNext = PlaybackControlAvailability.canSkipToNext(
                        queueSize = queue.size,
                        currentIndex = currentIndex,
                        repeatAll = repeatMode == Player.REPEAT_MODE_ALL
                    )
                )
            }.collectLatest { state ->
                val track = state.track
                if (track != null) {
                    showNotification(track, state.isPlaying, state.canSkipToNext)
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
    }

    private var lastLoadedThumbUrl: String? = null
    private var lastLoadedBitmap: Bitmap? = null

    private suspend fun showNotification(track: TrackItem, isPlaying: Boolean, canSkipToNext: Boolean) {
        var largeIcon: Bitmap? = null
        val baseUrlVal = playbackManager.baseUrl
        val tokenVal = playbackManager.token
        val settings = PlexSettingsManager(this)

        if (track.thumb.isNotEmpty()) {
            val normalizedBaseUrl = if (baseUrlVal.endsWith("/")) baseUrlVal.dropLast(1) else baseUrlVal
            val imageUrl = if (track.thumb.startsWith("http://") || track.thumb.startsWith("https://")) {
                track.thumb
            } else {
                "$normalizedBaseUrl${track.thumb}"
            }
            val credentials = originCredentialsForUrl(
                imageUrl,
                settings.companionBackendUrl,
                settings.companionBackendToken,
                baseUrlVal,
                tokenVal
            )
            val artworkAllowed = imageUrl.toHttpUrlOrNull()?.let { QaNetworkPolicy.isAllowed(it) } ?: !BuildConfig.QA_BUILD
            val hasArtworkCredentials = credentials.headers.isNotEmpty() && artworkAllowed

            if (hasArtworkCredentials && imageUrl == lastLoadedThumbUrl && lastLoadedBitmap != null) {
                largeIcon = lastLoadedBitmap
            } else if (hasArtworkCredentials) {
                try {
                    val imageLoader = Coil.imageLoader(this)
                    val request = ImageRequest.Builder(this).data(imageUrl).apply {
                        credentials.headers
                            .forEach { (name, value) -> addHeader(name, value) }
                    }.allowHardware(false).build()
                    val result = withContext(Dispatchers.IO) {
                        imageLoader.execute(request)
                    }
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        largeIcon = bitmap
                        lastLoadedThumbUrl = imageUrl
                        lastLoadedBitmap = bitmap
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val playPauseAction = Notification.Action.Builder(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            getString(if (isPlaying) R.string.notification_action_pause else R.string.notification_action_play),
            getPendingIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
        ).build()

        val prevAction = Notification.Action.Builder(
            android.R.drawable.ic_media_previous,
            getString(R.string.notification_action_previous),
            getPendingIntent(ACTION_PREV)
        ).build()

        val nextAction = Notification.Action.Builder(
            android.R.drawable.ic_media_next,
            getString(R.string.notification_action_next),
            getPendingIntent(ACTION_NEXT)
        ).build()

        val stopAction = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.notification_action_stop),
            getPendingIntent(ACTION_STOP)
        ).build()

        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        notificationBuilder
            .setContentTitle(track.title)
            .setContentText("${track.artist} — ${track.album}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOngoing(isPlaying)

        if (largeIcon != null) {
            notificationBuilder.setLargeIcon(largeIcon)
        }

        var actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_STOP
        if (canSkipToNext) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    playbackManager.progress.value,
                    1f
                )
                .setActiveQueueItemId(track.ratingKey.toLongOrNull() ?: 0L)
                .build()
        )

        notificationBuilder.addAction(prevAction)
        notificationBuilder.addAction(playPauseAction)
        if (canSkipToNext) notificationBuilder.addAction(nextAction)
        notificationBuilder.addAction(stopAction)

        // Set MediaStyle
        val mediaStyle = Notification.MediaStyle().setMediaSession(mediaSession.sessionToken)
        if (canSkipToNext) {
            mediaStyle.setShowActionsInCompactView(0, 1, 2)
        } else {
            mediaStyle.setShowActionsInCompactView(0, 1)
        }
        notificationBuilder.setStyle(mediaStyle)

        val notification = notificationBuilder.build()

        if (isPlaying) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            stopForeground(STOP_FOREGROUND_DETACH)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            this.action = action
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    override fun onDestroy() {
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
