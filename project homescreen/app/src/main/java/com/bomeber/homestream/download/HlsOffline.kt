package com.bomeber.homestream.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import com.bomeber.homestream.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor

/** One persistent cache and manager for both download and offline playback. */
@OptIn(UnstableApi::class)
object HlsOffline {
    private const val TAG = "HomeStreamHLS"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val provider by lazy { StandaloneDatabaseProvider(app) }
    private lateinit var app: Context
    private var cacheInstance: SimpleCache? = null
    private var managerInstance: DownloadManager? = null
    private var executor: ThreadPoolExecutor? = null

    fun refreshConnections(context: Context) {
        val count = DownloadPrefs(context).getMaxConnections()
        executor?.let { pool ->
            if (count > pool.maximumPoolSize) pool.maximumPoolSize = count
            pool.corePoolSize = count
            pool.maximumPoolSize = count
        }
        Log.d(TAG, "HLS maximum connections: $count")
    }

    @Synchronized fun cache(context: Context): SimpleCache {
        if (!::app.isInitialized) app = context.applicationContext
        return cacheInstance ?: SimpleCache(File(app.filesDir, "hls_media3"), NoOpCacheEvictor(), provider)
            .also { cacheInstance = it }
    }

    @Synchronized fun manager(context: Context): DownloadManager {
        if (!::app.isInitialized) app = context.applicationContext
        if (managerInstance != null) {
            refreshConnections(app)
            return managerInstance!!
        }
        val pool = Executors.newFixedThreadPool(DownloadPrefs(app).getMaxConnections()) as ThreadPoolExecutor
        executor = pool
        return DownloadManager(
            app, provider, cache(app), DefaultHttpDataSource.Factory(),
            pool
        ).also { manager ->
            // Limit one HLS job at a time. The fixed pool bounds segment network work.
            manager.maxParallelDownloads = 1
            manager.addListener(object : DownloadManager.Listener {
                override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
                    if (finalException != null) Log.w(TAG, "HLS failed: ${download.request.id}", finalException)
                    scope.launch { sync(download) }
                }
                override fun onInitialized(manager: DownloadManager) {
                    manager.currentDownloads.forEach { item -> scope.launch { sync(item) } }
                }
            })
            managerInstance = manager
            scope.launch {
                while (true) {
                    delay(1000)
                    manager.currentDownloads.forEach { sync(it) }
                }
            }
        }
    }

    private suspend fun sync(download: Download) {
        val dao = DownloadDatabase.get(app).downloadDao()
        val entity = dao.getById(download.request.id) ?: return
        val state = when (download.state) {
            Download.STATE_COMPLETED -> DownloadState.COMPLETED
            Download.STATE_FAILED -> DownloadState.FAILED
            Download.STATE_STOPPED -> DownloadState.PAUSED
            Download.STATE_DOWNLOADING -> DownloadState.DOWNLOADING
            Download.STATE_REMOVING, Download.STATE_RESTARTING -> return
            else -> DownloadState.QUEUED
        }
        if (entity.state == DownloadState.CANCELLED) return
        if (state == DownloadState.FAILED) {
            dao.updateFailure(entity.id, state, "ดาวน์โหลด HLS ไม่สำเร็จ กรุณาลองใหม่")
        } else {
            dao.updateState(entity.id, state)
        }
        val percent = if (state == DownloadState.COMPLETED) 100
            else download.percentDownloaded.takeIf { it.isFinite() && it >= 0f }?.toInt()
        dao.updateHlsProgress(entity.id, download.bytesDownloaded, percent ?: 0, if (percent == null) 0 else 100)
        if (state == DownloadState.COMPLETED) dao.markCompleted(entity.id, entity.selectedVariantUrl ?: entity.sourceUrl)
    }

    fun offlineDataSource(context: Context): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache(context))
        .setUpstreamDataSourceFactory(null) // Fail on a cache miss: offline play must never use the network.
        .setCacheWriteDataSinkFactory(null)
}

@OptIn(UnstableApi::class)
class HlsDownloadService : DownloadService(1051) {
    override fun getDownloadManager(): DownloadManager = HlsOffline.manager(this)
    override fun getScheduler(): androidx.media3.exoplayer.scheduler.Scheduler? = null

    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification {
        val channel = "homestream_downloads"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, "HomeStream downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val current = downloads.firstOrNull { it.state == Download.STATE_DOWNLOADING }
        val percent = current?.percentDownloaded?.takeIf { it.isFinite() && it >= 0f }?.toInt()
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("HomeStream")
            .setContentText(if (current == null) "Preparing download" else "Downloading • ${percent?.let { "$it%" } ?: "…"}")
            .setOngoing(true)
            .setProgress(100, percent ?: 0, percent == null)
            .build()
    }
}
