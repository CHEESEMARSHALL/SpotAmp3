package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.BackendClientManager
import com.example.data.BackendPlaybackEventRequest
import com.example.data.MusicDatabase
import com.example.data.PlexSettingsManager
import com.example.playback.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

private const val COMPANION_PLAY_SYNC_WORK = "companion_play_sync"
private const val COMPANION_PLAY_SYNC_TAG = "spotamp_companion_play_sync"
private const val COMPANION_PLAY_BATCH_SIZE = 50
private const val COMPANION_STREAM_MARKER = "/api/v1/media/stream/"

/**
 * Returns SpotCore's library id only for tracks that came through the
 * authenticated companion stream. Plex rating keys are intentionally not
 * treated as SpotCore ids.
 */
fun TrackItem.spotCoreTrackId(): String? {
    val ratingKey = ratingKey.trim()
    if (ratingKey.startsWith("companion:") && ratingKey.length > "companion:".length) {
        return ratingKey.removePrefix("companion:")
    }

    val streamKey = key.trim()
    val markerIndex = streamKey.indexOf(COMPANION_STREAM_MARKER)
    if (markerIndex < 0) return null

    val encodedId = streamKey
        .substring(markerIndex + COMPANION_STREAM_MARKER.length)
        .substringBefore('?')
        .substringBefore('#')
        .substringBefore('/')
        .trim()
    if (encodedId.isBlank()) return null

    return runCatching {
        URLDecoder.decode(encodedId, StandardCharsets.UTF_8.name())
    }.getOrNull()?.takeIf(String::isNotBlank)
}

object CompanionPlaySyncScheduler {
    fun enqueue(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<CompanionPlaySyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(COMPANION_PLAY_SYNC_TAG)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            COMPANION_PLAY_SYNC_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

class CompanionPlaySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val settings = PlexSettingsManager(applicationContext)
            if (!settings.isCompanionConfigured) {
                return@withContext Result.success()
            }

            val dao = MusicDatabase.getDatabase(applicationContext).musicDao()
            val service = BackendClientManager.getApiService(settings.companionBackendUrl)
            var shouldRetry = false

            while (!shouldRetry) {
                val pending = dao.getPendingCompanionPlays(COMPANION_PLAY_BATCH_SIZE)
                if (pending.isEmpty()) break

                for (event in pending) {
                    try {
                        service.recordPlaybackEvent(
                            BackendPlaybackEventRequest(
                                trackId = event.trackId,
                                playedAt = event.playedAt,
                                eventId = event.eventId
                            ),
                            userAuthToken = "Bearer ${settings.companionBackendToken}"
                        )
                        dao.deletePendingCompanionPlay(event.eventId)
                    } catch (error: HttpException) {
                        if (error.code() == 404) {
                            Log.w("CompanionPlaySync", "SpotCore rejected unknown track ${event.trackId}; dropping pending event")
                            dao.deletePendingCompanionPlay(event.eventId)
                            continue
                        }
                        Log.w("CompanionPlaySync", "SpotCore play delivery failed with HTTP ${error.code()}", error)
                        shouldRetry = true
                        break
                    } catch (error: Exception) {
                        Log.w("CompanionPlaySync", "SpotCore play delivery failed; will retry", error)
                        shouldRetry = true
                        break
                    }
                }
            }

            if (shouldRetry) Result.retry() else Result.success()
        }
    }
}
