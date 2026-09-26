package com.bomeber.homestream.download

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/**
 * One download job, persisted so it survives process death (per Step 5.1H).
 * [totalBytes] == -1 means "unknown" (e.g. Range-unsupported server with no
 * Content-Length, or an HLS download before segment sizes are known — HLS
 * byte totals are only ever a running sum of what's actually been fetched,
 * never a pre-known total, since playlists don't carry segment sizes).
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val sourceUrl: String,
    val title: String,
    val type: DownloadType,
    val state: DownloadState = DownloadState.QUEUED,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val totalUnits: Int = 0,
    val completedUnits: Int = 0,
    val localFilePath: String? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * One resumable unit of work inside a download:
 * - DIRECT_FILE: a byte range ([rangeStart]..[rangeEnd]) written straight
 *   into [localPath] (the shared, pre-allocated destination file) at its
 *   own offset.
 * - HLS: one playlist segment (or the init segment, unit 0, when
 *   #EXT-X-MAP is present) fetched from [segmentUrl] into its own
 *   temp file at [localPath], later concatenated in unitIndex order.
 */
@Entity(tableName = "download_units", primaryKeys = ["downloadId", "unitIndex"])
data class DownloadUnitEntity(
    val downloadId: String,
    val unitIndex: Int,
    val rangeStart: Long?,
    val rangeEnd: Long?,
    val segmentUrl: String?,
    val localPath: String,
    val completed: Boolean,
    val byteCount: Long
)

/** Room converters for the two enums above — stored as their plain name string. */
class DownloadConverters {
    @TypeConverter fun fromType(type: DownloadType): String = type.name
    @TypeConverter fun toType(value: String): DownloadType = DownloadType.valueOf(value)
    @TypeConverter fun fromState(state: DownloadState): String = state.name
    @TypeConverter fun toState(value: String): DownloadState = DownloadState.valueOf(value)
}