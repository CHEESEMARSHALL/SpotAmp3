package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.MusicRepository

class PlaylistSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            MusicRepository(applicationContext).syncSharedPlaylists()
            Result.success()
        } catch (error: Exception) {
            Log.w("PlaylistSyncWorker", "Shared playlist sync failed", error)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

object PlaylistSyncScheduler {
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<PlaylistSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "spotcore_playlist_sync",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
