package com.bomeber.homestream.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "HomeStreamDownload"

/**
 * Step 5.1E/F/G — parallel byte-range download for a single direct-file
 * URL, bounded by [maxConnections] (never exceeded — Step 5.1R). Falls
 * back to one sequential connection whenever Range isn't confirmed
 * supported. Each worker opens its own [RandomAccessFile] on the shared,
 * pre-allocated destination file and writes only at its own byte offset —
 * safe for concurrent writers on the same file (distinct fds, distinct
 * offsets). Never buffers a whole chunk in memory (Step 5.1R).
 */
class DirectFileDownloadEngine(
    private val context: Context,
    private val dao: DownloadDao
) {
    suspend fun run(entity: DownloadEntity, maxConnections: Int, isCancelled: () -> Boolean) {
        val destDir = File(context.filesDir, "downloads/${entity.id}").apply { mkdirs() }
        val destFile = File(destDir, "file")

        var units = dao.getUnits(entity.id)
        if (units.isEmpty()) {
            val probe = RangeSupportProbe.probe(entity.sourceUrl)
            val connections = if (probe.supportsRange && probe.contentLength > 0) {
                chooseConnectionCount(probe.contentLength, maxConnections)
            } else 1

            units = if (probe.supportsRange && probe.contentLength > 0 && connections > 1) {
                val chunkSize = probe.contentLength / connections
                (0 until connections).map { i ->
                    val start = i * chunkSize
                    val end = if (i == connections - 1) probe.contentLength - 1 else start + chunkSize - 1
                    DownloadUnitEntity(
                        downloadId = entity.id, unitIndex = i, rangeStart = start, rangeEnd = end,
                        segmentUrl = null, localPath = destFile.absolutePath, completed = false, byteCount = 0
                    )
                }
            } else {
                listOf(
                    DownloadUnitEntity(
                        downloadId = entity.id, unitIndex = 0, rangeStart = 0,
                        rangeEnd = if (probe.contentLength > 0) probe.contentLength - 1 else null,
                        segmentUrl = null, localPath = destFile.absolutePath, completed = false, byteCount = 0
                    )
                )
            }

            dao.setPlan(entity.id, totalBytes = probe.contentLength, totalUnits = units.size)
            dao.insertUnits(units)

            if (probe.contentLength > 0) {
                RandomAccessFile(destFile, "rw").use { it.setLength(probe.contentLength) }
            }
        }

        val pending = units.filter { !it.completed }
        val semaphore = Semaphore(maxConnections.coerceAtLeast(1))
        val downloaded = AtomicLong(units.filter { it.completed }.sumOf { it.byteCount })

        coroutineScope {
            pending.map { unit ->
                launch {
                    semaphore.withPermit {
                        if (!isCancelled()) downloadUnit(entity, unit, destFile, downloaded, isCancelled)
                    }
                }
            }.forEach { it.join() }
        }

        if (isCancelled()) return

        val allUnits = dao.getUnits(entity.id)
        if (allUnits.all { it.completed }) {
            dao.markCompleted(entity.id, destFile.absolutePath)
        } else {
            throw IllegalStateException("บางส่วนของไฟล์ดาวน์โหลดไม่สำเร็จ")
        }
    }

    /** Never spins up more workers than the file size reasonably supports (min ~2 MB/connection). */
    private fun chooseConnectionCount(contentLength: Long, maxConnections: Int): Int {
        val minChunkSize = 2L * 1024 * 1024
        val byLength = (contentLength / minChunkSize).toInt().coerceAtLeast(1)
        return maxConnections.coerceAtMost(byLength).coerceIn(1, DownloadPrefs.ALLOWED_CONNECTIONS.max())
    }

    private suspend fun downloadUnit(
        entity: DownloadEntity,
        unit: DownloadUnitEntity,
        destFile: File,
        downloaded: AtomicLong,
        isCancelled: () -> Boolean
    ) {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < 3) {
            attempt++
            try {
                val conn = (URL(entity.sourceUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 20_000
                    if (unit.rangeStart != null) {
                        val end = unit.rangeEnd?.toString().orEmpty()
                        setRequestProperty("Range", "bytes=${unit.rangeStart}-$end")
                    }
                }
                conn.connect()
                if (unit.rangeStart != null && unitsAreParallel(entity.id) &&
                    conn.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    conn.disconnect()
                    throw IllegalStateException("เซิร์ฟเวอร์ไม่รองรับการดาวน์โหลดหลายช่วง")
                }
                var lastPersist = 0L
                RandomAccessFile(destFile, "rw").use { raf ->
                    raf.seek(unit.rangeStart ?: 0)
                    conn.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var totalRead = 0L
                        while (true) {
                            if (isCancelled()) return
                            val read = input.read(buffer)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            totalRead += read
                            val now = downloaded.addAndGet(read.toLong())
                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastPersist > 400) {
                                lastPersist = nowMs
                                dao.updateProgressBytes(entity.id, now)
                            }
                        }
                        dao.updateProgressBytes(entity.id, downloaded.get())
                        dao.markUnitComplete(entity.id, unit.unitIndex, totalRead)
                    }
                }
                conn.disconnect()
                return
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "DirectFile unit ${unit.unitIndex} attempt $attempt failed", e)
                delay(1500L * attempt)
            }
        }
        throw lastError ?: IllegalStateException("ดาวน์โหลดล้มเหลว: unit ${unit.unitIndex}")
    }

    private suspend fun unitsAreParallel(id: String): Boolean = dao.getUnits(id).size > 1
}
