package com.example.data

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

internal object CompanionTrackStateField {
    const val FAVORITE = "favorite"
    const val RATING = "rating"
}

internal data class CompanionTrackStateValues(val favorite: Boolean, val rating: Int)

internal fun mergeCompanionTrackStateSnapshot(
    snapshotFavorite: Boolean?,
    snapshotRating: Int?,
    previousFavorite: Boolean,
    previousRating: Int
): CompanionTrackStateValues = CompanionTrackStateValues(
    favorite = snapshotFavorite ?: previousFavorite,
    rating = (snapshotRating ?: previousRating).coerceIn(0, 5)
)

/** Stable, non-secret key used to isolate queued writes by SpotCore origin and paired device. */
internal fun SyncAccountIdentity.storageKey(): String = "$backendOrigin\u001f$accountId"

internal object CompanionTrackStateSyncScheduler {
    private const val UNIQUE_WORK_NAME = "companion_track_state_sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<CompanionTrackStateSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}

class CompanionTrackStateSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!PlexSettingsManager(applicationContext).isCompanionConfigured) {
            // Keep the account-scoped outbox; pairing again will schedule a new attempt.
            return@withContext Result.success()
        }

        try {
            if (MusicRepository(applicationContext).syncPendingCompanionTrackStates()) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("CompanionTrackStateSync", "SpotCore track state write deferred (${error::class.simpleName})")
            Result.retry()
        }
    }
}
