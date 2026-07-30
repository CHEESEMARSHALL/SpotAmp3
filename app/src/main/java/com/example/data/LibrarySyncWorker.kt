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
        val sectionId = inputData.getString("sectionId") ?: return@withContext Result.failure()
        Log.d("LibrarySyncWorker", "Starting sync for section: $sectionId")
        val musicDao = MusicDatabase.getDatabase(applicationContext).musicDao()
        val settings = PlexSettingsManager(applicationContext)
        Log.d("LibrarySyncWorker", "Settings configured: ${settings.isConfigured}")

        if (!settings.isConfigured) return@withContext Result.failure()

        val syncId = System.currentTimeMillis()
        val restart = inputData.getBoolean("restart", false)
        var syncState = musicDao.getSyncState(sectionId) ?: SyncStateEntity(sectionId = sectionId)
        val initialOffset = if (restart) 0 else syncState.currentOffset
        syncState = syncState.copy(status = "running", lastSyncId = syncId, currentOffset = initialOffset)
        musicDao.insertSyncState(syncState)

        val pageSize = 500
        var offset = initialOffset
        
        try {
            val service = PlexClientManager.getApiService(settings.baseUrl)
            
            while (true) {
                // Fetch page
                val container = service.getLibraryItems(sectionId, "10", offset, pageSize, settings.token)
                    .mediaContainer
                val page = container.metadata.orEmpty()
                
                if (page.isEmpty()) break

                val totalTracks = container.totalSize ?: 0

                val entities = page.mapNotNull { track ->
                    val trackKey = track.media?.firstOrNull()?.part?.firstOrNull()?.key
                    if (trackKey.isNullOrEmpty()) {
                        null
                    } else {
                        CachedTrack(
                            ratingKey = track.ratingKey,
                            title = track.title,
                            artist = track.grandparentTitle ?: track.parentTitle ?: "Unknown Artist",
                            album = track.parentTitle ?: "Unknown Album",
                            key = trackKey,
                            thumb = track.thumb ?: "",
                            duration = track.duration ?: 0L,
                            year = track.year,
                            addedAt = track.addedAt,
                            playCount = track.viewCount ?: 0,
                            lastPlayedAt = track.lastViewedAt,
                            genres = track.genres.orEmpty().joinToString("|") { it.tag },
                            collections = track.collections.orEmpty().joinToString("|") { it.tag },
                            syncId = syncId
                        )
                    }
                }

                musicDao.insertCachedTracks(entities)
                
                offset += page.size
                syncState = syncState.copy(currentOffset = offset, totalTracks = totalTracks)
                musicDao.insertSyncState(syncState)
                
                if (page.size < pageSize) break
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
