package com.bomeber.homestream.download

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.bomeber.homestream.media.DetectedMedia
import com.bomeber.homestream.media.MediaAccessType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

private const val TAG = "HomeStreamDownload"

/**
 * Step 5 — Download Manager MVP. Only [MediaAccessType.DIRECT_FILE] is
 * actually downloadable; HLS keeps playing fine via Step 4's PlayerScreen
 * (unchanged) but [enqueueDownload] rejects it here with
 * [UnsupportedDownloadType] instead of stitching .ts segments (explicitly
 * forbidden). Every download is a plain unauthenticated GET to the exact
 * URL Step 3 detected — no DRM/auth/paywall bypass.
 */
class DownloadRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = DownloadDatabase.getInstance(appContext).downloadDao()
    private val workManager = WorkManager.getInstance(appContext)

    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    class UnsupportedDownloadType(val accessType: MediaAccessType) :
        Exception("ยังไม่รองรับการดาวน์โหลดสำหรับประเภทนี้ (${accessType.name})")

    suspend fun enqueueDownload(media: DetectedMedia): String {
        if (media.accessType != MediaAccessType.DIRECT_FILE) {
            throw UnsupportedDownloadType(media.accessType)
        }

        val id = UUID.randomUUID().toString()
        val downloadsDir = (appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(appContext.filesDir, "downloads")).apply { mkdirs() }
        val existingNames = downloadsDir.listFiles()?.mapNotNull { it.name }?.toSet().orEmpty()
        val fileName = FileNaming.buildFileName(media.title, media.url, existingNames)
        val filePath = File(downloadsDir, fileName).absolutePath

        val entity = DownloadEntity(
            id = id,
            url = media.url,
            title = media.title?.takeIf { it.isNotBlank() } ?: fileName,
            accessType = media.accessType.name,
            fileName = fileName,
            filePath = filePath,
            status = DownloadStatus.QUEUED
        )
        dao.insert(entity)
        enqueueWorker(id)
        Log.d(TAG, "enqueueDownload: queued id=$id url=${media.url} file=$fileName")
        return id
    }

    suspend fun pause(id: String) {
        val entity = dao.getById(id) ?: return
        if (entity.status != DownloadStatus.DOWNLOADING && entity.status != DownloadStatus.QUEUED) return
        dao.updateStatus(id, DownloadStatus.PAUSED, null)
        workManager.cancelUniqueWork(uniqueWorkName(id))
        Log.d(TAG, "pause: id=$id")
    }

    suspend fun resume(id: String) {
        val entity = dao.getById(id) ?: return
        if (entity.status !in RESUMABLE_STATUSES) return
        dao.updateStatus(id, DownloadStatus.QUEUED, null)
        enqueueWorker(id)
        Log.d(TAG, "resume: id=$id (from byte ${entity.downloadedBytes})")
    }

    /** Same mechanism as [resume] — a failed/cancelled job's partial file (if any) is resumed the same way. */
    suspend fun retry(id: String) = resume(id)

    suspend fun cancel(id: String) {
        val entity = dao.getById(id) ?: return
        dao.updateStatus(id, DownloadStatus.CANCELLED, null)
        workManager.cancelUniqueWork(uniqueWorkName(id))
        runCatching { File(entity.filePath).delete() }
        Log.d(TAG, "cancel: id=$id")
    }

    suspend fun remove(id: String) {
        val entity = dao.getById(id) ?: return
        workManager.cancelUniqueWork(uniqueWorkName(id))
        if (entity.status != DownloadStatus.COMPLETED) {
            runCatching { File(entity.filePath).delete() }
        }
        dao.delete(id)
        Log.d(TAG, "remove: id=$id")
    }

    /** Safety net for Test 8 — re-enqueues any row stuck DOWNLOADING after a process restart. */
    suspend fun resumeInterruptedDownloads() {
        downloads.first().filter { it.status == DownloadStatus.DOWNLOADING }.forEach { enqueueWorker(it.id) }
    }

    private fun enqueueWorker(id: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(Data.Builder().putString(DownloadWorker.KEY_DOWNLOAD_ID, id).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(uniqueWorkName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun uniqueWorkName(id: String) = "homestream-download-$id"

    companion object {
        private val RESUMABLE_STATUSES =
            setOf(DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)

        @Volatile private var INSTANCE: DownloadRepository? = null

        fun getInstance(context: Context): DownloadRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadRepository(context).also { INSTANCE = it }
            }
    }
}