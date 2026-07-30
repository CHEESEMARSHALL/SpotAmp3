package com.example.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.playback.TrackItem

class TrackDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val ratingKey = inputData.getString(KEY_RATING_KEY) ?: return Result.failure()
        val track = TrackItem(
            ratingKey = ratingKey,
            title = inputData.getString(KEY_TITLE).orEmpty(),
            artist = inputData.getString(KEY_ARTIST).orEmpty(),
            album = inputData.getString(KEY_ALBUM).orEmpty(),
            key = inputData.getString(KEY_KEY).orEmpty(),
            thumb = inputData.getString(KEY_THUMB).orEmpty(),
            duration = inputData.getLong(KEY_DURATION, 0L)
        )
        val repository = MusicRepository(applicationContext)
        repository.markDownloadStatus(ratingKey, "downloading")
        return try {
            repository.downloadTrack(track) { progress ->
                setProgressAsync(androidx.work.workDataOf(KEY_PROGRESS to progress))
            }
            Result.success()
        } catch (error: Exception) {
            repository.markDownloadStatus(ratingKey, "failed", error.localizedMessage)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_RATING_KEY = "ratingKey"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ALBUM = "album"
        const val KEY_KEY = "key"
        const val KEY_THUMB = "thumb"
        const val KEY_DURATION = "duration"
        const val KEY_PROGRESS = "progress"
    }
}
