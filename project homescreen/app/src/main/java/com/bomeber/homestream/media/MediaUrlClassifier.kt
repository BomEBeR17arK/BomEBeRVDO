package com.bomeber.homestream.media

import android.net.Uri

/**
 * Classifies a URL (plus an optional MIME type, when known) into a
 * [MediaAccessType] and a best-guess [MediaKind] — purely from the URL's
 * scheme/path extension and any MIME type already provided. Never fetches
 * anything, never inspects response content.
 *
 * This is the Kotlin-side source of truth for the **network-observed**
 * detection path ([WebViewContainer]'s `shouldInterceptRequest`). The
 * **DOM-observed** path runs entirely inside the WebView's own JavaScript
 * context ([VideoDetector.DETECTION_SCRIPT]) and cannot call Kotlin code —
 * its classifier is therefore a parallel JavaScript implementation of the
 * same rules. If these rules change, both places need updating; each one
 * documents this dependency at the top of its classifier.
 */
object MediaUrlClassifier {

    private val DIRECT_VIDEO_EXT = listOf(".mp4", ".webm", ".mov", ".mkv", ".m4v")
    private val DIRECT_AUDIO_EXT = listOf(".mp3", ".aac", ".ogg", ".oga", ".wav", ".flac", ".m4a")
    private const val HLS_EXT = ".m3u8"
    private const val DASH_EXT = ".mpd"
    private const val HLS_SEGMENT_EXT = ".ts"
    private const val DASH_SEGMENT_EXT = ".m4s"

    private val HLS_MIME_MARKERS = listOf("mpegurl")
    private val DASH_MIME_MARKERS = listOf("dash+xml")

    /** Result of classifying one URL. */
    data class Result(val accessType: MediaAccessType, val mediaType: MediaKind)

    /**
     * Classifies [url] (optionally aided by [mimeType], when the caller has
     * one). The path is checked (never the query string) to avoid the
     * false positive of e.g. `?file=movie.mp4` in an unrelated API request.
     */
    fun classify(url: String, mimeType: String? = null): Result {
        if (url.startsWith("blob:", ignoreCase = true)) {
            return Result(MediaAccessType.BLOB_MSE, MediaKind.UNKNOWN)
        }

        val path = runCatching { Uri.parse(url).path }.getOrNull()?.lowercase().orEmpty()
        val mime = mimeType?.lowercase().orEmpty()

        return when {
            path.endsWith(HLS_SEGMENT_EXT) -> Result(MediaAccessType.HLS_SEGMENT, MediaKind.UNKNOWN)
            path.endsWith(DASH_SEGMENT_EXT) -> Result(MediaAccessType.DASH_SEGMENT, MediaKind.UNKNOWN)
            path.endsWith(HLS_EXT) || HLS_MIME_MARKERS.any { mime.contains(it) } ->
                Result(MediaAccessType.HLS, MediaKind.UNKNOWN)
            path.endsWith(DASH_EXT) || DASH_MIME_MARKERS.any { mime.contains(it) } ->
                Result(MediaAccessType.DASH, MediaKind.UNKNOWN)
            DIRECT_VIDEO_EXT.any { path.endsWith(it) } || mime.startsWith("video/") ->
                Result(MediaAccessType.DIRECT_FILE, MediaKind.VIDEO)
            DIRECT_AUDIO_EXT.any { path.endsWith(it) } || mime.startsWith("audio/") ->
                Result(MediaAccessType.DIRECT_FILE, MediaKind.AUDIO)
            else -> Result(MediaAccessType.UNKNOWN, MediaKind.UNKNOWN)
        }
    }

    /**
     * Cheap pre-filter for `shouldInterceptRequest()`, so the network path
     * doesn't classify or log every CSS/JS/image/font/analytics/ad request
     * on the page — only ones whose path extension could plausibly be
     * media. This is a filter to cut noise, not a security boundary;
     * [classify] still runs on anything that passes it to confirm/refine.
     *
     * Extension-only (no MIME check) because at *request* time the
     * response's Content-Type isn't known yet — MIME-based classification
     * only ever applies on the DOM path, where the browser already
     * resolved it onto the element/`<source>` tag. A manifest/segment
     * served from an extensionless URL will not pass this filter — see
     * project.md "Known limitations".
     *
     * `blob:` is deliberately NOT included here: blob: URLs are resolved
     * from in-memory data and never go through `shouldInterceptRequest` —
     * they can only ever be found via DOM detection.
     */
    fun looksLikeMediaCandidate(url: String): Boolean {
        val path = runCatching { Uri.parse(url).path }.getOrNull()?.lowercase() ?: return false
        val candidateExt = DIRECT_VIDEO_EXT + DIRECT_AUDIO_EXT +
                listOf(HLS_EXT, DASH_EXT, HLS_SEGMENT_EXT, DASH_SEGMENT_EXT)
        return candidateExt.any { path.endsWith(it) }
    }
}
