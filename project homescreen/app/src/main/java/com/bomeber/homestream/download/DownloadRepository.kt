package com.bomeber.homestream.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.bomeber.homestream.media.DetectedMedia
import com.bomeber.homestream.media.MediaAccessType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * App-level singleton (held by [com.bomeber.homestream.HomeStreamApplication])
 * that owns Room + WorkManager for downloads — same "keep it simple, no
 * extra DI framework" approach Step 4 used for PlayerScreen.
 */
class DownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = DownloadDatabase.get(appContext).downloadDao()
    private val workManager = WorkManager.getInstance(appContext)
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    /** Starts a new download for a Step 3 [DetectedMedia] item; same DIRECT_FILE/HLS
     *  gate BrowserScreen already uses for the Play button. Returns null (no-op)
     *  for any other access type. */
    fun startDownload(media: DetectedMedia): String? {
        val type = when (media.accessType) {
            MediaAccessType.DIRECT_FILE -> DownloadType.DIRECT_FILE
            MediaAccessType.HLS -> DownloadType.HLS
            else -> return null
        }
        val id = UUID.randomUUID().toString()
        val title = media.title?.takeIf { it.isNotBlank() }
            ?: media.url.substringAfterLast('/').substringBefore('?').ifBlank { "Download" }

        repoScope.launch {
            dao.insertDownload(DownloadEntity(id = id, sourceUrl = media.url, title = title, type = type))
            enqueue(id)
        }
        return id
    }

    fun pause(id: String) {
        workManager.cancelUniqueWork(id)
        repoScope.launch { dao.updateState(id, DownloadState.PAUSED) }
    }

    fun resume(id: String) {
        repoScope.launch {
            dao.updateState(id, DownloadState.QUEUED)
            enqueue(id)
        }
    }

    fun cancel(id: String) {
        workManager.cancelUniqueWork(id)
        repoScope.launch {
            dao.updateState(id, DownloadState.CANCELLED)
            dao.deleteUnits(id)
            runCatching { File(appContext.filesDir, "downloads/$id").deleteRecursively() }
        }
    }

    fun retry(id: String) {
        repoScope.launch {
            dao.resetForRetry(id)
            enqueue(id)
        }
    }

    /**
     * Step 5.1 fix — removes a download entirely: stops any running/queued
     * WorkManager job first (so a still-downloading item doesn't keep
     * writing to a file that's about to be deleted), then removes the
     * Room rows and the on-disk file/segment folder for it. Safe to call
     * regardless of current state (COMPLETED, FAILED, CANCELLED, or still
     * DOWNLOADING/QUEUED).
     */
    fun remove(id: String) {
        workManager.cancelUniqueWork(id)
        repoScope.launch {
            dao.deleteUnits(id)
            dao.deleteDownload(id)
            runCatching { File(appContext.filesDir, "downloads/$id").deleteRecursively() }
        }
    }

    private fun enqueue(id: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_DOWNLOAD_ID to id))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(id, ExistingWorkPolicy.REPLACE, request)
    }
}