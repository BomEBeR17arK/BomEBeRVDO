package com.bomeber.homestream.ui.model

/**
 * ข้อมูล mock ทั้งหมดสำหรับ UI-only step นี้
 * Step ถัดไปค่อยเปลี่ยนมาดึงจาก Room / WorkManager จริง
 */

data class ContinueWatchingItem(
    val id: String,
    val title: String,
    val progressPercent: Int,
    val durationLabel: String
)

data class DownloadItem(
    val id: String,
    val title: String,
    val status: DownloadStatus,
    val progressPercent: Int,
    val sizeLabel: String
)

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class LibraryVideoItem(
    val id: String,
    val title: String,
    val fileSizeLabel: String,
    val durationLabel: String?
)

object SampleData {
    val continueWatching = listOf(
        ContinueWatchingItem("1", "Sample Video 1", 45, "12:30"),
        ContinueWatchingItem("2", "Sample Video 2", 80, "05:10"),
        ContinueWatchingItem("3", "Sample Video 3", 20, "34:00")
    )

    val recentDownloads = listOf(
        DownloadItem("1", "Sample Download 1", DownloadStatus.COMPLETED, 100, "245 MB"),
        DownloadItem("2", "Sample Download 2", DownloadStatus.DOWNLOADING, 62, "1.1 GB"),
        DownloadItem("3", "Sample Download 3", DownloadStatus.QUEUED, 0, "580 MB")
    )

    val allDownloads = recentDownloads + listOf(
        DownloadItem("4", "Sample Download 4", DownloadStatus.PAUSED, 30, "780 MB"),
        DownloadItem("5", "Sample Download 5", DownloadStatus.FAILED, 15, "150 MB")
    )

    val recentlyWatched = listOf(
        LibraryVideoItem("1", "Watched Video 1", "320 MB", "22:14"),
        LibraryVideoItem("2", "Watched Video 2", "1.4 GB", "58:03")
    )

    val libraryVideos = recentlyWatched + listOf(
        LibraryVideoItem("3", "Library Video 3", "890 MB", "40:12"),
        LibraryVideoItem("4", "Library Video 4", "210 MB", null),
        LibraryVideoItem("5", "Library Video 5", "3.2 GB", "1:32:00"),
        LibraryVideoItem("6", "Library Video 6", "670 MB", "18:45")
    )
}
