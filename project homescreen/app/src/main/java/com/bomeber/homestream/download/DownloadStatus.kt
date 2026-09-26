package com.bomeber.homestream.download

/**
 * Status of one download job.
 *
 * PAUSED is only ever set when the app itself stopped a download the user
 * asked to pause (see DownloadRepository.pause) — never faked. If the
 * server hosting the file doesn't honor a `Range` request (no HTTP 206),
 * DownloadRepository/DownloadWorker restart the download from byte 0
 * instead of pretending to resume.
 */
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}