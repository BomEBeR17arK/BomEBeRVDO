package com.bomeber.homestream.download

/**
 * Which download engine to use for a source URL. Mirrors the same subset
 * of [com.bomeber.homestream.media.MediaAccessType] that PlayerScreen
 * already knows how to play (see BrowserScreen.isPlayable) — Step 5.1
 * deliberately does not try to download anything Step 4 can't already
 * play, so a completed download is always immediately playable offline
 * through the existing DIRECT_FILE path.
 */
enum class DownloadType { DIRECT_FILE, HLS }

/** Lifecycle state of one download, persisted in Room ([DownloadEntity]). */
enum class DownloadState { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }