package com.bomeber.homestream.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

private const val TAG = "HomeStreamDownload"

/**
 * Runs one download to completion (or failure/cancellation), survives
 * process death via WorkManager's own persistence (Step 5.1H). [isStopped]
 * (set true when [DownloadRepository] calls `cancelUniqueWork`) is checked
 * both inside the engines' byte-read loops and here via
 * [CancellationException], since pause/cancel both go through the same
 * `cancelUniqueWork` call.
 */
class DownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val dao = DownloadDatabase.get(applicationContext).downloadDao()
        val entity = dao.getById(downloadId) ?: return Result.failure()

        if (entity.state == DownloadState.CANCELLED) return Result.success()

        val maxConnections = DownloadPrefs(applicationContext).getMaxConnections()
        dao.updateState(downloadId, DownloadState.DOWNLOADING)

        return try {
            when (entity.type) {
                DownloadType.DIRECT_FILE ->
                    DirectFileDownloadEngine(applicationContext, dao).run(entity, maxConnections) { isStopped }
                DownloadType.HLS -> throw IllegalStateException("HLS uses Media3 DownloadService")
            }
            Result.success()
        } catch (e: CancellationException) {
            // Paused or cancelled via DownloadRepository — state already set there.
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "DownloadWorker failed for id=$downloadId (attempt=$runAttemptCount)", e)
            if (runAttemptCount < MAX_ATTEMPTS - 1) {
                Result.retry()
            } else {
                dao.updateFailure(downloadId, DownloadState.FAILED, e.message ?: "ดาวน์โหลดล้มเหลว")
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val MAX_ATTEMPTS = 3
    }
}