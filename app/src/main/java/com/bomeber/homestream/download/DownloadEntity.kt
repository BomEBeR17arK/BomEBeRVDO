package com.bomeber.homestream.download

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.bomeber.homestream.media.MediaAccessType

/**
 * Persistent record of one download job. This is the first Room database
 * in the project — Step 5 does not touch or redesign anything else.
 *
 * [filePath] always points inside this app's own app-specific external
 * storage directory (Context.getExternalFilesDir), so no storage
 * permission is required on any supported API level (26+), and the file
 * stays accessible to the app after completion.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val accessType: String, // MediaAccessType.name — only DIRECT_FILE is actually downloadable in Step 5
    val fileName: String,
    val filePath: String,
    val status: DownloadStatus,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L, // -1 = unknown (server didn't send Content-Length)
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val accessTypeEnum: MediaAccessType
        get() = runCatching { MediaAccessType.valueOf(accessType) }.getOrDefault(MediaAccessType.UNKNOWN)
}

class DownloadStatusConverters {
    @TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): DownloadStatus =
        runCatching { DownloadStatus.valueOf(value) }.getOrDefault(DownloadStatus.FAILED)
}