package com.example.playback

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import android.media.session.MediaSession
import android.media.session.PlaybackState
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class MusicPlaybackService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var playbackManager: PlaybackManager
    private lateinit var mediaSession: MediaSession
    private var isServiceRunning = false

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
        playbackManager = PlaybackManager.getInstance(this)
        createNotificationChannel()
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
        if (!isServiceRunning) {
            isServiceRunning = true
            startInitialForeground()
        }
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        stopForeground(true)
                    }
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
            .setContentTitle("Plex Music Player")
            .setContentText("Preparing playback...")
            .setSmallIcon(android.R.drawable.ic_media_play)
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
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and info for currently playing music"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun observePlaybackState() {
        serviceScope.launch {
            // Observe the track changes
            playbackManager.currentTrack.collectLatest { track ->
                if (track != null) {
                    showNotification(track, playbackManager.isPlaying.value)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        stopForeground(true)
                    }
                }
            }
        }

        serviceScope.launch {
            // Observe the playing status changes
            playbackManager.isPlaying.collectLatest { isPlaying ->
                val track = playbackManager.currentTrack.value
                if (track != null) {
                    showNotification(track, isPlaying)
                }
            }
        }
    }

    private var lastLoadedThumbUrl: String? = null
    private var lastLoadedBitmap: Bitmap? = null

    private suspend fun showNotification(track: TrackItem, isPlaying: Boolean) {
        var largeIcon: Bitmap? = null
        val baseUrlVal = playbackManager.baseUrl
        val tokenVal = playbackManager.token

        if (track.thumb.isNotEmpty() && baseUrlVal.isNotEmpty() && tokenVal.isNotEmpty()) {
            val normalizedBaseUrl = if (baseUrlVal.endsWith("/")) baseUrlVal.dropLast(1) else baseUrlVal
            val imageUrl = "$normalizedBaseUrl${track.thumb}"

            if (imageUrl == lastLoadedThumbUrl && lastLoadedBitmap != null) {
                largeIcon = lastLoadedBitmap
            } else {
                try {
                    val imageLoader = Coil.imageLoader(this)
                    val request = ImageRequest.Builder(this)
                        .data(imageUrl)
                        .addHeader("X-Plex-Token", tokenVal)
                        .allowHardware(false) // Safe for Notification large icons
                        .build()
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

        val playPauseAction = if (isPlaying) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Notification.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    getPendingIntent(ACTION_PAUSE)
                ).build()
            } else {
                null
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Notification.Action.Builder(
                    android.R.drawable.ic_media_play,
                    "Play",
                    getPendingIntent(ACTION_PLAY)
                ).build()
            } else {
                null
            }
        }

        val prevAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Notification.Action.Builder(
                android.R.drawable.ic_media_previous,
                "Previous",
                getPendingIntent(ACTION_PREV)
            ).build()
        } else {
            null
        }

        val nextAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Notification.Action.Builder(
                android.R.drawable.ic_media_next,
                "Next",
                getPendingIntent(ACTION_NEXT)
            ).build()
        } else {
            null
        }

        val stopAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                getPendingIntent(ACTION_STOP)
            ).build()
        } else {
            null
        }

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
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOngoing(isPlaying)

        if (largeIcon != null) {
            notificationBuilder.setLargeIcon(largeIcon)
        }

        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_STOP
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

        if (prevAction != null) notificationBuilder.addAction(prevAction)
        if (playPauseAction != null) notificationBuilder.addAction(playPauseAction)
        if (nextAction != null) notificationBuilder.addAction(nextAction)
        if (stopAction != null) notificationBuilder.addAction(stopAction)

        // Set MediaStyle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val mediaStyle = Notification.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
            notificationBuilder.setStyle(mediaStyle)
        }

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            this.action = action
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    override fun onDestroy() {
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
