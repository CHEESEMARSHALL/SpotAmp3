package com.example.data

import android.content.Context
import kotlinx.coroutines.CancellationException
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
        return try {
            repository.markDownloadStatus(ratingKey, "downloading")
            setProgressAsync(androidx.work.workDataOf(KEY_PROGRESS to -1f))
            repository.downloadTrack(track) { progress ->
                setProgressAsync(androidx.work.workDataOf(KEY_PROGRESS to progress))
            }
            setProgressAsync(androidx.work.workDataOf(KEY_PROGRESS to 1f))
            Result.success()
        } catch (error: CancellationException) {
            // WorkManager cancellation is cooperative: preserve cancellation semantics so the
            // repository finally block removes only the temporary .part file and the worker is
            // not misclassified as a retryable product failure.
            throw error
        } catch (error: Exception) {
            when (DownloadRetryPolicy.disposition(runAttemptCount)) {
                DownloadFailureDisposition.RETRY_AUTOMATICALLY -> {
                    // Do not expose a manual Retry action while WorkManager is already
                    // scheduling another attempt. The final error is saved only when
                    // automatic retries are exhausted.
                    repository.markDownloadStatus(ratingKey, "retrying")
                    Result.retry()
                }
                DownloadFailureDisposition.FINAL_FAILURE -> {
                    repository.markDownloadStatus(ratingKey, "failed", error.localizedMessage)
                    Result.failure()
                }
            }
        }
    }

    companion object {
        const val DOWNLOAD_TAG = "spotamp_download"
        const val TRACK_TAG_PREFIX = "spotamp_download_track:"
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
