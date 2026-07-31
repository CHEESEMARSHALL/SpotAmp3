package com.example.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.MusicDatabase
import com.example.data.PlexSettingsManager
import com.example.data.PlexClientManager
import com.example.data.CachedTrack
import com.example.data.SyncStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import com.example.R

class LibrarySyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        setForeground(getForegroundInfo())
        val sectionId = inputData.getString("sectionId") ?: "spotcore"
        Log.d("LibrarySyncWorker", "Starting sync for section: $sectionId")
        val musicDao = MusicDatabase.getDatabase(applicationContext).musicDao()
        val settings = PlexSettingsManager(applicationContext)
        val useCompanion = settings.isCompanionConfigured
        Log.d("LibrarySyncWorker", "Plex configured: ${settings.isConfigured}; SpotCore configured: $useCompanion")

        if (!settings.isConfigured && !useCompanion) return@withContext Result.failure()

        val syncId = System.currentTimeMillis()
        val restart = inputData.getBoolean("restart", false)
        var syncState = musicDao.getSyncState(sectionId) ?: SyncStateEntity(sectionId = sectionId)
        val initialOffset = if (restart) 0 else syncState.currentOffset
        syncState = syncState.copy(status = "running", lastSyncId = syncId, currentOffset = initialOffset)
        musicDao.insertSyncState(syncState)

        val pageSize = 500
        var offset = initialOffset
        var previousPageSignature: String? = null
        
        try {
            val service = if (!useCompanion) PlexClientManager.getApiService(settings.baseUrl) else null
            val companion = if (useCompanion) BackendClientManager.getApiService(settings.companionBackendUrl) else null

            while (true) {
                // Fetch page
                val plexPage = service?.getLibraryItems(sectionId, "10", offset, pageSize, settings.token)
                val companionPage = companion?.getLibraryTracks(pageSize, offset, "Bearer ${settings.companionBackendToken}")
                val page = plexPage?.mediaContainer?.metadata.orEmpty()
                val backendTracks = companionPage?.tracks.orEmpty()

                if (page.isEmpty() && backendTracks.isEmpty()) break

                val receivedCount = if (useCompanion) backendTracks.size else page.size
                val pageSignature = if (useCompanion) {
                    backendTracks.take(10).joinToString("|") { it.id }
                } else {
                    page.take(10).joinToString("|") { it.ratingKey }
                }
                // A faulty/paginated backend must not leave WorkManager in an
                // endless loop (and keep the app looking permanently frozen).
                if (receivedCount == 0 || pageSignature == previousPageSignature) {
                    throw IllegalStateException("Library sync made no paging progress at offset $offset")
                }
                previousPageSignature = pageSignature

                val totalTracks = plexPage?.mediaContainer?.totalSize ?: 0

                val entities = if (useCompanion) backendTracks.map { track ->
                    CachedTrack(track.id, track.title, track.artist, track.album, track.streamUrl, track.coverUrl,
                        track.duration, track.year, null, 0, null, track.genre.orEmpty(), "", syncId)
                } else page.mapNotNull { track ->
                    val trackKey = track.media?.firstOrNull()?.part?.firstOrNull()?.key
                    trackKey?.let { CachedTrack(track.ratingKey, track.title, track.grandparentTitle ?: track.parentTitle ?: "Unknown Artist",
                        track.parentTitle ?: "Unknown Album", it, track.thumb ?: "", track.duration ?: 0L, track.year,
                        track.addedAt, track.viewCount ?: 0, track.lastViewedAt,
                        track.genres.orEmpty().joinToString("|") { tag -> tag.tag },
                        track.collections.orEmpty().joinToString("|") { tag -> tag.tag }, syncId) }
                }

                musicDao.insertCachedTracks(entities)
                
                offset += receivedCount
                syncState = syncState.copy(currentOffset = offset, totalTracks = totalTracks)
                musicDao.insertSyncState(syncState)
                
                if (receivedCount < pageSize) break
            }
            
            musicDao.deleteStaleTracks(syncId)
            musicDao.insertSyncState(syncState.copy(status = "completed", currentOffset = 0))
            Result.success()
        } catch (e: Exception) {
            Log.e("LibrarySyncWorker", "Sync failed at offset $offset", e)
            musicDao.insertSyncState(syncState.copy(status = "failed", lastError = e.message))
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "sync_channel",
                "Sync Music Library",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = applicationContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, "sync_channel")
            .setContentTitle("Syncing Music Library")
            .setContentText("Syncing tracks...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) 
            .build()
        return androidx.work.ForegroundInfo(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
