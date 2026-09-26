package com.bomeber.homestream.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "HomeStreamDownload"

/**
 * Step 5.1B/C/D — resolves an HLS VOD playlist (master or media), downloads
 * every segment (plus the init segment for fMP4/CMAF, if `#EXT-X-MAP` is
 * present) with bounded parallelism, then concatenates them **in order**
 * into one local file so it can be played back through PlayerScreen's
 * existing DIRECT_FILE path — no Media3 offline-download machinery, no new
 * dependency. Refuses live playlists (no `#EXT-X-ENDLIST`) and encrypted
 * playlists (`#EXT-X-KEY` other than METHOD=NONE) outright — see
 * HlsPlaylistParser doc.
 *
 * Step 5.1 fix: before downloading, does a bounded-parallel HEAD-request
 * pass over every segment to sum up an estimated total byte size (HLS
 * playlists never carry this — unlike a direct file's Content-Length). If
 * any segment's HEAD request doesn't return a usable Content-Length, the
 * total falls back to "unknown" (-1) and the UI shows segment-count
 * progress instead — this is a best-effort estimate, not a guarantee.
 */
class HlsDownloadEngine(
    private val context: Context,
    private val dao: DownloadDao
) {
    suspend fun run(entity: DownloadEntity, maxConnections: Int, isCancelled: () -> Boolean) {
        val destDir = File(context.filesDir, "downloads/${entity.id}").apply { mkdirs() }
        val destFile = File(destDir, "video.ts")

        var units = dao.getUnits(entity.id)
        if (units.isEmpty()) {
            val playlistText = fetchText(entity.sourceUrl)
            val mediaText: String
            val mediaPlaylistUrl: String
            if (HlsPlaylistParser.isMaster(playlistText)) {
                val best = HlsPlaylistParser.parseMaster(playlistText, entity.sourceUrl)
                    .maxByOrNull { it.bandwidth }
                    ?: throw IllegalStateException("ไม่พบ stream ที่เล่นได้ใน master playlist")
                mediaPlaylistUrl = best.uri
                mediaText = fetchText(mediaPlaylistUrl)
            } else {
                mediaPlaylistUrl = entity.sourceUrl
                mediaText = playlistText
            }

            val parsed = HlsPlaylistParser.parseMedia(mediaText, mediaPlaylistUrl)
            if (parsed.isEncrypted) {
                throw IllegalStateException("สตรีมนี้เข้ารหัสส่วนย่อย (AES-128 หรือใกล้เคียง) — Step 5.1 ยังไม่รองรับ")
            }
            if (!parsed.isVod) {
                throw IllegalStateException("รองรับเฉพาะ HLS แบบ VOD (มี #EXT-X-ENDLIST) เท่านั้น")
            }
            if (parsed.segments.isEmpty()) {
                throw IllegalStateException("ไม่พบ segment ใน playlist")
            }

            val newUnits = mutableListOf<DownloadUnitEntity>()
            var idx = 0
            parsed.initSegmentUri?.let { initUri ->
                newUnits += DownloadUnitEntity(
                    downloadId = entity.id, unitIndex = idx, rangeStart = null, rangeEnd = null,
                    segmentUrl = initUri, localPath = File(destDir, "unit_%05d".format(idx)).absolutePath,
                    completed = false, byteCount = 0
                )
                idx++
            }
            parsed.segments.forEach { segUrl ->
                newUnits += DownloadUnitEntity(
                    downloadId = entity.id, unitIndex = idx, rangeStart = null, rangeEnd = null,
                    segmentUrl = segUrl, localPath = File(destDir, "unit_%05d".format(idx)).absolutePath,
                    completed = false, byteCount = 0
                )
                idx++
            }

            // Step 5.1 fix — estimate total size up front via bounded-parallel
            // HEAD requests, bounded by the same connection-count setting as
            // the actual download, so we don't hammer the server with an
            // unrelated burst of requests before the real download even starts.
            val estimatedTotal = estimateTotalBytes(newUnits, maxConnections)

            dao.setPlan(entity.id, totalBytes = estimatedTotal, totalUnits = newUnits.size)
            dao.insertUnits(newUnits)
            units = newUnits
        }

        val pending = units.filter { !it.completed }
        val semaphore = Semaphore(maxConnections.coerceAtLeast(1))
        val downloaded = AtomicLong(units.filter { it.completed }.sumOf { it.byteCount })

        coroutineScope {
            pending.map { unit ->
                launch {
                    semaphore.withPermit {
                        if (!isCancelled()) downloadSegment(entity, unit, downloaded, isCancelled)
                    }
                }
            }.forEach { it.join() }
        }

        if (isCancelled()) return

        val allUnits = dao.getUnits(entity.id).sortedBy { it.unitIndex }
        if (allUnits.any { !it.completed }) {
            throw IllegalStateException("บาง segment ดาวน์โหลดไม่สำเร็จ")
        }

        FileOutputStream(destFile).use { out ->
            allUnits.forEach { unit -> File(unit.localPath).inputStream().use { it.copyTo(out) } }
        }
        dao.markCompleted(entity.id, destFile.absolutePath)

        // Clean up per-segment temp files now that the concatenated file exists.
        allUnits.forEach { runCatching { File(it.localPath).delete() } }
    }

    /**
     * Sums each segment's Content-Length via HEAD, bounded by [maxConnections]
     * in-flight at once. Returns -1 (unknown) if ANY segment doesn't report a
     * usable length — a partial sum would understate the real total and make
     * the progress bar finish "early" at less than 100%, which is worse than
     * just falling back to segment-count progress.
     */
    private suspend fun estimateTotalBytes(units: List<DownloadUnitEntity>, maxConnections: Int): Long = coroutineScope {
        val semaphore = Semaphore(maxConnections.coerceAtLeast(1))
        val sizes = units.map { unit ->
            async { semaphore.withPermit { probeContentLength(unit.segmentUrl!!) } }
        }.map { it.await() }
        if (sizes.any { it < 0 }) -1L else sizes.sum()
    }

    private fun probeContentLength(urlString: String): Long = try {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        val length = conn.contentLengthLong
        conn.disconnect()
        length
    } catch (e: Exception) {
        Log.w(TAG, "probeContentLength failed for $urlString", e)
        -1L
    }

    private suspend fun downloadSegment(
        entity: DownloadEntity,
        unit: DownloadUnitEntity,
        downloaded: AtomicLong,
        isCancelled: () -> Boolean
    ) {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < 3) {
            attempt++
            try {
                val conn = (URL(unit.segmentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 20_000
                }
                conn.connect()
                var totalRead = 0L
                var lastPersist = 0L
                File(unit.localPath).outputStream().use { out ->
                    conn.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (isCancelled()) return
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            totalRead += read
                            val now = downloaded.addAndGet(read.toLong())
                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastPersist > 400) {
                                lastPersist = nowMs
                                dao.updateProgressBytes(entity.id, now)
                            }
                        }
                    }
                }
                conn.disconnect()
                dao.updateProgressBytes(entity.id, downloaded.get())
                dao.markUnitComplete(entity.id, unit.unitIndex, totalRead)
                return
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "HLS segment ${unit.unitIndex} attempt $attempt failed", e)
                delay(1500L * attempt)
            }
        }
        throw lastError ?: IllegalStateException("ดาวน์โหลด segment ล้มเหลว: ${unit.unitIndex}")
    }

    private fun fetchText(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 20_000
        }
        conn.connect()
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}