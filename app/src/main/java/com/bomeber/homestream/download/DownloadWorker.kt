package com.bomeber.homestream.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "HomeStreamDownload"

/**
 * Downloads one [DownloadEntity] (Step 5 — DIRECT_FILE only; DownloadRepository
 * rejects anything else before a worker is ever enqueued). Streams the
 * response body directly to [DownloadEntity.filePath] with a plain
 * HttpURLConnection GET to the exact detected URL — no DRM/auth/paywall
 * bypass, no header/cookie/token logging, no response-body inspection
 * beyond reading bytes to write them, no request modification.
 *
 * Resume: if a partial file already exists, sends `Range: bytes=N-`. Only
 * trusts the resume if the server replies 206 Partial Content — otherwise
 * deletes the partial file and restarts cleanly from 0, rather than risk
 * corrupting the file with an un-honored range.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()

        val dao = DownloadDatabase.getInstance(applicationContext).downloadDao()
        val entity = dao.getById(downloadId) ?: return@withContext Result.failure()

        val file = File(entity.filePath)
        val resumeFrom = if (file.exists()) file.length() else 0L

        dao.updateProgress(downloadId, DownloadStatus.DOWNLOADING, entity.progressPercent, resumeFrom, entity.totalBytes)

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(entity.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "GET"
                if (resumeFrom > 0) setRequestProperty("Range", "bytes=$resumeFrom-")
            }
            connection.connect()

            val responseCode = connection.responseCode
            val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL
            val isOk = responseCode == HttpURLConnection.HTTP_OK

            if (!isOk && !isPartial) {
                Log.w(TAG, "downloadId=$downloadId HTTP error code=$responseCode")
                dao.updateStatus(downloadId, DownloadStatus.FAILED, "HTTP $responseCode")
                return@withContext Result.failure()
            }

            val actuallyResuming = resumeFrom > 0 && isPartial
            val startByte = if (actuallyResuming) resumeFrom else 0L
            if (!actuallyResuming && file.exists()) file.delete()

            val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            val totalBytes = when {
                actuallyResuming && contentLength >= 0 -> startByte + contentLength
                contentLength >= 0 -> contentLength
                else -> -1L
            }

            file.parentFile?.mkdirs()

            var downloaded = startByte
            var lastReportedAt = 0L
            val buffer = ByteArray(64 * 1024)

            connection.inputStream.use { input ->
                RandomAccessFile(file, "rw").use { out ->
                    out.seek(startByte)
                    while (true) {
                        if (isStopped) {
                            Log.d(TAG, "downloadId=$downloadId stopped (paused/cancelled)")
                            return@withContext Result.success() // status already set by pause()/cancel()
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastReportedAt > 250) {
                            lastReportedAt = now
                            val percent = if (totalBytes > 0) {
                                ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else 0
                            dao.updateProgress(downloadId, DownloadStatus.DOWNLOADING, percent, downloaded, totalBytes)
                        }
                    }
                }
            }

            val finalPercent = if (totalBytes > 0) 100 else 0
            dao.updateProgress(downloadId, DownloadStatus.COMPLETED, finalPercent, downloaded, totalBytes)
            Log.d(TAG, "downloadId=$downloadId completed, bytes=$downloaded")
            Result.success()
        } catch (e: Exception) {
            if (isStopped) {
                Log.d(TAG, "downloadId=$downloadId interrupted by stop", e)
                Result.success()
            } else {
                Log.w(TAG, "downloadId=$downloadId failed", e)
                dao.updateStatus(downloadId, DownloadStatus.FAILED, e.message ?: "ดาวน์โหลดล้มเหลว")
                Result.failure()
            }
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
    }
}