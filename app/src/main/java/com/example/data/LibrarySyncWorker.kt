package com.example.data

import android.content.Context
import android.app.Notification
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.MusicDatabase
import com.example.data.PlexSettingsManager
import com.example.data.PlexClientManager
import com.example.data.CachedTrack
import com.example.data.SyncStateEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import com.example.R
import androidx.core.app.NotificationCompat
import retrofit2.HttpException

internal const val LIBRARY_SYNC_NOTIFICATION_CHANNEL_ID = "sync_channel"

internal fun syncOrderSampleDiagnostic(discNumber: Int?, trackNumber: Int?): String =
    "Order sample: disc=${discNumber ?: "unknown"} track=${trackNumber ?: "unknown"}"

internal fun isPrematureEmptyLibraryPage(expectedTotalTrackCount: Int?, offset: Int): Boolean =
    expectedTotalTrackCount != null && offset < expectedTotalTrackCount

internal suspend fun <T> fetchOptionalMusicIntelligencePage(
    fetch: suspend () -> T,
    onFailure: (Exception) -> Unit
): T? = try {
    fetch()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    onFailure(error)
    null
}

internal fun createLibrarySyncNotification(context: Context): Notification =
    NotificationCompat.Builder(context, LIBRARY_SYNC_NOTIFICATION_CHANNEL_ID)
        .setContentTitle(context.getString(R.string.library_sync_notification_title))
        .setContentText(context.getString(R.string.library_sync_notification_text))
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .build()

internal fun ensureLibrarySyncNotificationChannel(context: Context) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
    val channel = android.app.NotificationChannel(
        LIBRARY_SYNC_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.library_sync_notification_channel),
        android.app.NotificationManager.IMPORTANCE_LOW
    )
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    manager.createNotificationChannel(channel)
}

class LibrarySyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.w("LibrarySyncWorker", "Could not set foreground info (${e::class.simpleName})")
        }

        val sectionId = "spotcore" // Strictly use spotcore for companion sync
        Log.d("LibrarySyncWorker", "Starting sync for section: $sectionId")
        val musicDao = MusicDatabase.getDatabase(applicationContext).musicDao()
        val settings = PlexSettingsManager(applicationContext)
        val useCompanion = settings.isCompanionConfigured
        Log.d("LibrarySyncWorker", "SpotCore configured: $useCompanion")

        if (!useCompanion) return@withContext Result.failure()
        val workerAccount = SyncAccountIsolationPolicy.identity(settings.companionBackendUrl, settings.companionDeviceId)
            ?: return@withContext Result.failure()

        val syncId = System.currentTimeMillis()
        var syncState = musicDao.getSyncState(sectionId) ?: SyncStateEntity(sectionId = sectionId)
        // Each attempt represents a new library snapshot. Resuming from a saved
        // offset with a new generation would make early pages look stale and
        // prune them after a later-page retry succeeds.
        val initialOffset = 0
        syncState = syncState.copy(status = "running", lastSyncId = syncId, currentOffset = initialOffset)
        // Publish the new run before making the first network request so the
        // Settings/setup UI doesn't keep showing a stale completed state while
        // WorkManager is already processing this sync.
        musicDao.insertSyncState(syncState)
        SyncDiagnosticService.reset()
        SyncDiagnosticService.updateStatus("Running (offset $initialOffset)")

        val pageSize = 500 
        var offset = initialOffset
        var previousPageSignature: String? = null
        var pageCount = 0
        var receivedAnyTracks = false
        var terminalPageReceived = false
        var expectedTotalTrackCount: Int? = null
        var expectedLibraryRevision: Long? = null
        val receivedTrackIds = mutableSetOf<String>()

        try {
            val companion = BackendClientManager.getApiService(settings.companionBackendUrl)

            while (true) {
                pageCount++
                SyncDiagnosticService.addLog("Batch $pageCount (SpotCore offset $offset)")

                val companionPage = companion.getLibraryTracks(
                    limit = pageSize,
                    offset = offset,
                    userAuthToken = "Bearer ${settings.companionBackendToken}"
                )

                val backendTracks = companionPage.tracks.orEmpty()
                val receivedCount = backendTracks.size
                val pageRevision = companionPage.libraryRevision
                    ?: throw IllegalStateException("SpotCore did not provide a library snapshot revision")
                if (pageRevision < 0) throw IllegalStateException("Library revision cannot be negative")
                val priorRevision = expectedLibraryRevision
                if (priorRevision != null && priorRevision != pageRevision) {
                    throw IllegalStateException("Library changed during pagination; retrying a fresh snapshot")
                }
                expectedLibraryRevision = pageRevision

                val pageTotal = companionPage.total
                    ?: throw IllegalStateException("SpotCore did not declare the library total")
                if (pageTotal < 0) throw IllegalStateException("Library total cannot be negative")
                val previousTotal = expectedTotalTrackCount
                if (previousTotal != null && previousTotal != pageTotal) {
                    throw IllegalStateException("Library total changed during pagination")
                }
                expectedTotalTrackCount = pageTotal
                if (offset > pageTotal) throw IllegalStateException("Library page offset exceeded its total")
                if (backendTracks.any { it.id.isBlank() }) {
                    throw IllegalStateException("SpotCore returned a track without a stable ID")
                }
                if (backendTracks.map { it.id }.toSet().size != backendTracks.size) {
                    throw IllegalStateException("SpotCore returned duplicate track IDs in a page")
                }
                if (backendTracks.any { !receivedTrackIds.add(it.id) }) {
                    throw IllegalStateException("SpotCore repeated a track across library pages")
                }
                if (receivedTrackIds.size > pageTotal) {
                    throw IllegalStateException("SpotCore returned more unique tracks than its declared total")
                }
                if (receivedCount > 0) receivedAnyTracks = true
                
                if (receivedCount == 0) {
                    val expectedTotal = expectedTotalTrackCount
                    if (isPrematureEmptyLibraryPage(expectedTotal, offset)) {
                        throw IllegalStateException("Library returned an empty page before its declared total")
                    }
                    SyncDiagnosticService.addLog("Batch returned zero. Finishing.")
                    terminalPageReceived = true
                    break
                }

                val pageSignature = backendTracks.take(10).joinToString("|") { it.id }

                if (pageSignature == previousPageSignature) {
                    throw IllegalStateException("SpotCore repeated a library page")
                }
                previousPageSignature = pageSignature
                SyncDiagnosticService.updateProgress(pageCount, receivedCount)

                val previousTracks = musicDao.getCachedTracksByRatingKeys(backendTracks.map { it.id }).associateBy { it.ratingKey }

                val entities = backendTracks.map { track ->
                    val previousTrack = previousTracks[track.id]
                    val remoteState = mergeCompanionTrackStateSnapshot(
                        snapshotFavorite = track.favorite,
                        snapshotRating = track.rating,
                        previousFavorite = previousTrack?.companionFavorite ?: false,
                        previousRating = previousTrack?.companionRating ?: 0
                    )
                    CachedTrack(
                        ratingKey = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        key = resolveCompanionMediaReference(settings.companionBackendUrl, track.streamUrl),
                        thumb = normalizeCompanionArtworkUrl(
                            companionBaseUrl = settings.companionBackendUrl,
                            trackId = track.id,
                            coverUrl = track.coverUrl
                        ),
                        duration = track.duration.toLong(),
                        year = track.year,
                        addedAt = track.addedAt,
                        playCount = track.playCount ?: 0,
                        lastPlayedAt = track.lastPlayedAt,
                        genres = track.genre.orEmpty(),
                        collections = "",
                        syncId = syncId,
                        albumArtist = track.albumArtist?.trim().orEmpty(),
                        index = track.resolvedTrackNumber(),
                        discNumber = track.resolvedDiscNumber(),
                        musicIntelligenceJson = previousTracks[track.id]?.musicIntelligenceJson ?: "{}",
                        companionFavorite = remoteState.favorite,
                        companionRating = remoteState.rating
                    )
                }

                val currentAccount = SyncAccountIsolationPolicy.identity(settings.companionBackendUrl, settings.companionDeviceId)
                if (!SyncAccountIsolationPolicy.mayCommit(workerAccount, currentAccount)) {
                    SyncDiagnosticService.setError("Account changed during sync")
                    return@withContext Result.failure()
                }
                musicDao.insertSyncedCachedTracks(entities, workerAccount.storageKey())
                
                // Log sample indices for debugging
                if (backendTracks.isNotEmpty()) {
                    val first = backendTracks.first()
                    SyncDiagnosticService.addLog(
                        syncOrderSampleDiagnostic(first.resolvedDiscNumber(), first.resolvedTrackNumber())
                    )
                }

                offset += receivedCount
                val expectedTotal = expectedTotalTrackCount
                    ?: throw IllegalStateException("SpotCore did not declare the library total")
                if (offset > expectedTotal) throw IllegalStateException("Library returned more tracks than its declared total")
                if (offset == expectedTotal) {
                    terminalPageReceived = true
                    break
                }
                syncState = syncState.copy(currentOffset = offset)
                musicDao.insertSyncState(syncState)
            }
            
            if (!terminalPageReceived) {
                throw IllegalStateException("Library pagination stopped before a terminal page" )
            }
            val completedTotal = expectedTotalTrackCount
                ?: throw IllegalStateException("SpotCore did not declare the library total")
            if (receivedTrackIds.size != completedTotal) {
                throw IllegalStateException("Library snapshot contained ${receivedTrackIds.size} unique tracks; expected $completedTotal")
            }

            var intelligenceOffset = 0
            var intelligenceTrackCount = 0
            while (true) {
                val intelligencePage = fetchOptionalMusicIntelligencePage(
                    fetch = {
                        companion.getMusicIntelligence(
                            limit = pageSize,
                            offset = intelligenceOffset,
                            userAuthToken = "Bearer ${settings.companionBackendToken}"
                        )
                    },
                    onFailure = { error ->
                        if (error is HttpException && error.code() == 404 && intelligenceOffset == 0) {
                            SyncDiagnosticService.addLog(
                                "Music intelligence endpoint unavailable; existing local analysis retained"
                            )
                        } else {
                            SyncDiagnosticService.addLog(
                                "Music intelligence stopped (${error::class.simpleName}); existing local analysis retained"
                            )
                        }
                    }
                ) ?: break
                val intelligenceTracks = intelligencePage.tracks
                if (intelligenceTracks.isEmpty()) break
                val currentAnalysisAccount = SyncAccountIsolationPolicy.identity(settings.companionBackendUrl, settings.companionDeviceId)
                if (!SyncAccountIsolationPolicy.mayCommit(workerAccount, currentAnalysisAccount)) {
                    SyncDiagnosticService.setError("Account changed during music analysis sync")
                    return@withContext Result.failure()
                }
                intelligenceTracks.forEach { track ->
                    if (track.trackId.isNotBlank()) {
                        musicDao.updateCachedTrackMusicIntelligence(track.trackId, BackendIntelligenceJson.encode(track))
                    }
                }
                intelligenceTrackCount += intelligenceTracks.size
                intelligenceOffset += intelligenceTracks.size
                if (intelligenceTracks.size < pageSize) break
                if (intelligenceOffset > 1_000_000) throw IllegalStateException("Music analysis page limit exceeded")
            }
            if (intelligenceTrackCount > 0) {
                SyncDiagnosticService.addLog("Synced music analysis for $intelligenceTrackCount tracks")
            }
            val finalLibrarySnapshot = companion.getLibraryTracks(
                limit = 1,
                offset = 0,
                userAuthToken = "Bearer ${settings.companionBackendToken}"
            )
            if (finalLibrarySnapshot.libraryRevision != expectedLibraryRevision
                || finalLibrarySnapshot.total != expectedTotalTrackCount) {
                throw IllegalStateException("Library changed before the snapshot could be committed")
            }
            val latestState = musicDao.getSyncState(sectionId)
            val currentAccount = SyncAccountIsolationPolicy.identity(settings.companionBackendUrl, settings.companionDeviceId)
            val accountStillCurrent = SyncAccountIsolationPolicy.mayCommit(workerAccount, currentAccount)
            val ownsGeneration = latestState?.lastSyncId?.let { TrackCacheSyncPolicy.ownsGeneration(syncId, it) } == true
            if (ownsGeneration && accountStillCurrent && TrackCacheSyncPolicy.shouldPruneStaleCache(
                    receivedAnyTracks,
                    initialOffset,
                    terminalPageReceived,
                    expectedTotalTrackCount,
                    receivedTrackCount = receivedTrackIds.size,
                    snapshotRevisionStable = finalLibrarySnapshot.libraryRevision == expectedLibraryRevision
                )) {
                musicDao.deleteStaleTracks(syncId)
                musicDao.insertSyncState(syncState.copy(status = "completed", currentOffset = 0))
            } else if (ownsGeneration && accountStillCurrent) {
                musicDao.insertSyncState(syncState.copy(status = "completed", currentOffset = 0))
            }
            SyncDiagnosticService.updateStatus("Completed")
            Result.success()
        } catch (e: Exception) {
            Log.e("LibrarySyncWorker", "Sync failed at offset $offset (${e::class.simpleName})")
            SyncDiagnosticService.setError(e.message ?: "Unknown error")
            val currentAccount = SyncAccountIsolationPolicy.identity(settings.companionBackendUrl, settings.companionDeviceId)
            if (workerAccount?.let { SyncAccountIsolationPolicy.mayCommit(it, currentAccount) } == true) {
                musicDao.insertSyncState(syncState.copy(status = "failed", lastError = TokenSafety.redact(e.message)))
            }
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        ensureLibrarySyncNotificationChannel(applicationContext)
        val notification = createLibrarySyncNotification(applicationContext)
        
        val serviceType = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        
        return androidx.work.ForegroundInfo(1, notification, serviceType)
    }
}
