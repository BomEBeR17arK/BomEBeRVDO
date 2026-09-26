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
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import android.net.Uri
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
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
@OptIn(UnstableApi::class)
class DownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = DownloadDatabase.get(appContext).downloadDao()
    private val workManager = WorkManager.getInstance(appContext)
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    /** Fetch only the playlist, so a master never silently chooses a quality. */
    suspend fun getAvailableQualities(url: String): List<HlsPlaylistParser.Variant> = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            if (HlsPlaylistParser.isMaster(text)) {
                require(!text.contains("#EXT-X-SESSION-KEY")) { "HLS ที่เข้ารหัสไม่รองรับการดาวน์โหลด" }
                HlsPlaylistParser.parseMaster(text, url).also {
                    require(it.isNotEmpty()) { "ไม่พบคุณภาพวิดีโอใน HLS playlist" }
                }
            } else {
                validateVod(text, url)
                emptyList()
            }
        } finally { connection.disconnect() }
    }

    private fun validateVod(text: String, url: String) {
        val playlist = HlsPlaylistParser.parseMedia(text, url)
        require(playlist.isVod) { "HLS playlist นี้ไม่ใช่ VOD" }
        require(!playlist.isEncrypted) { "HLS ที่เข้ารหัสไม่รองรับการดาวน์โหลด" }
        require(playlist.segments.isNotEmpty()) { "ไม่พบ segment ใน HLS playlist" }
    }

    /** Starts a new download for a Step 3 [DetectedMedia] item; same DIRECT_FILE/HLS
     *  gate BrowserScreen already uses for the Play button. Returns null (no-op)
     *  for any other access type. */
    fun startDownload(media: DetectedMedia, selectedVariant: HlsPlaylistParser.Variant? = null): String? {
        val type = when (media.accessType) {
            MediaAccessType.DIRECT_FILE -> DownloadType.DIRECT_FILE
            MediaAccessType.HLS -> DownloadType.HLS
            else -> return null
        }
        val id = UUID.randomUUID().toString()
        val title = media.title?.takeIf { it.isNotBlank() }
            ?: media.url.substringAfterLast('/').substringBefore('?').ifBlank { "Download" }

        repoScope.launch {
            val selectedUrl = selectedVariant?.uri ?: media.url
            val entity = DownloadEntity(
                id = id, sourceUrl = media.url, title = title, type = type,
                selectedVariantUrl = if (type == DownloadType.HLS) selectedUrl else null,
                selectedQualityLabel = selectedVariant?.displayLabel() ?: if (type == DownloadType.HLS) "HLS" else null,
                selectedWidth = selectedVariant?.width, selectedHeight = selectedVariant?.height,
                selectedBandwidth = selectedVariant?.bandwidth
            )
            try {
                if (type == DownloadType.HLS) {
                    val connection = (URL(selectedUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15_000; readTimeout = 15_000
                    }
                    try { validateVod(connection.inputStream.bufferedReader().use { it.readText() }, selectedUrl) }
                    finally { connection.disconnect() }
                }
                dao.insertDownload(entity)
                if (type == DownloadType.HLS) {
                    withContext(Dispatchers.Main) { HlsOffline.manager(appContext) }
                    val request = DownloadRequest.Builder(id, Uri.parse(selectedUrl))
                        .setMimeType(MimeTypes.APPLICATION_M3U8).build()
                    DownloadService.sendAddDownload(appContext, HlsDownloadService::class.java, request, false)
                } else enqueue(id)
            } catch (e: Exception) {
                if (dao.getById(id) == null) dao.insertDownload(entity)
                dao.updateFailure(id, DownloadState.FAILED, e.message ?: "ดาวน์โหลดไม่สำเร็จ")
            }
        }
        return id
    }

    fun pause(id: String) {
        repoScope.launch {
            if (dao.getById(id)?.type == DownloadType.HLS) {
                DownloadService.sendSetStopReason(appContext, HlsDownloadService::class.java, id, 1, false)
            } else workManager.cancelUniqueWork(id)
            dao.updateState(id, DownloadState.PAUSED)
        }
    }

    fun resume(id: String) {
        repoScope.launch {
            dao.updateState(id, DownloadState.QUEUED)
            if (dao.getById(id)?.type == DownloadType.HLS) {
                DownloadService.sendSetStopReason(appContext, HlsDownloadService::class.java, id, Download.STOP_REASON_NONE, false)
            } else enqueue(id)
        }
    }

    fun cancel(id: String) {
        repoScope.launch {
            if (dao.getById(id)?.type == DownloadType.HLS) {
                DownloadService.sendRemoveDownload(appContext, HlsDownloadService::class.java, id, false)
            } else workManager.cancelUniqueWork(id)
            dao.updateState(id, DownloadState.CANCELLED)
            dao.deleteUnits(id)
            runCatching { File(appContext.filesDir, "downloads/$id").deleteRecursively() }
        }
    }

    fun retry(id: String) {
        repoScope.launch {
            dao.resetForRetry(id)
            val entity = dao.getById(id) ?: return@launch
            if (entity.type == DownloadType.HLS) {
                val request = DownloadRequest.Builder(id, Uri.parse(entity.selectedVariantUrl ?: entity.sourceUrl))
                    .setMimeType(MimeTypes.APPLICATION_M3U8).build()
                DownloadService.sendAddDownload(appContext, HlsDownloadService::class.java, request, false)
                DownloadService.sendSetStopReason(appContext, HlsDownloadService::class.java, id, Download.STOP_REASON_NONE, false)
            } else enqueue(id)
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
        repoScope.launch {
            if (dao.getById(id)?.type == DownloadType.HLS) {
                DownloadService.sendRemoveDownload(appContext, HlsDownloadService::class.java, id, false)
            } else workManager.cancelUniqueWork(id)
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
