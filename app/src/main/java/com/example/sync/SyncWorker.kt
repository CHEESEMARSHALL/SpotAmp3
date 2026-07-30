package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val repository = MusicRepository(applicationContext)
            val settings = repository.settings
            val sectionId = settings.sectionId

            if (!settings.isConfigured || sectionId.isEmpty()) {
                Log.w("SyncWorker", "Sync skipped: Plex not configured or sectionId empty")
                return@withContext Result.failure()
            }
            
            Log.d("SyncWorker", "Starting background sync for section: $sectionId")
            repository.startSync(sectionId)
            Log.d("SyncWorker", "Background sync triggered successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background sync failed", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
