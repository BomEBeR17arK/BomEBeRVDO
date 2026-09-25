package com.bomeber.homestream.media

/** What kind of media element/resource this came from. */
enum class MediaKind { VIDEO, AUDIO, UNKNOWN }

/**
 * How a piece of detected media presents itself, based only on what the
 * page's DOM/API state or its own normal network requests already expose —
 * never on inspecting response content, decrypting, or bypassing anything.
 * This is a **classification**, not a downloadability verdict: a later
 * step decides what can actually be done with each type. [BLOB_MSE] and
 * [UNKNOWN] mean "detected, don't yet know how to fetch it" — not "can't
 * be downloaded".
 */
enum class MediaAccessType {
    /** URL that looks like a direct, single-file media resource (by file extension or MIME type). */
    DIRECT_FILE,
    /** URL that looks like an HLS manifest (`.m3u8`, or an `mpegurl` MIME type). */
    HLS,
    /** URL that looks like a DASH manifest (`.mpd`, or a `dash+xml` MIME type). */
    DASH,
    /** URL that looks like an individual HLS media segment (`.ts`). */
    HLS_SEGMENT,
    /** URL that looks like an individual DASH/CMAF media segment (`.m4s`). */
    DASH_SEGMENT,
    /** `currentSrc`/`src` resolved to a `blob:` URL — Media Source Extensions is in use. */
    BLOB_MSE,
    /** A normal http(s) URL that doesn't match any of the patterns above. Never discarded. */
    UNKNOWN
}

/**
 * Which detection mechanism found this item. Media discovered by both are
 * de-duplicated by URL (see BrowserViewModel) — this field reflects
 * whichever source is kept after dedup, preferring DOM (usually carries
 * richer metadata) when both sources found the same URL.
 */
enum class MediaDetectionSource { DOM, NETWORK }

/**
 * Metadata read directly from the media element's own properties/attributes
 * — never fetched, never derived from network inspection. Any field may be
 * null: most commonly because the browser hasn't resolved it yet (duration/
 * width/height are only known once enough of the stream has loaded, often
 * not until the user presses Play). Always empty for [MediaDetectionSource.NETWORK]
 * items — network observation has no access to element properties.
 */
data class MediaMetadata(
    val durationSeconds: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    val posterUrl: String? = null
)

/**
 * A single media resource discovered on the currently loaded page through
 * information the WebView already exposes normally — either via its DOM
 * ([MediaDetectionSource.DOM]) or by observing a resource request the
 * WebView itself made during ordinary page use ([MediaDetectionSource.NETWORK]).
 * Never through decrypting, bypassing, or reading response content.
 *
 * [accessType] classifies *how* the media presents itself; it does not
 * decide whether the media can be downloaded — that belongs to a later
 * step. [url] is kept for every [accessType], including [MediaAccessType.BLOB_MSE]
 * (diagnostic/UI only — a `blob:` URL is never a directly fetchable
 * resource on its own) and [MediaAccessType.UNKNOWN] (kept as-is, never
 * discarded, in case a later step can make more sense of it).
 */
data class DetectedMedia(
    val url: String,
    val accessType: MediaAccessType,
    val detectionSource: MediaDetectionSource,
    val mediaType: MediaKind = MediaKind.UNKNOWN,
    val mimeType: String? = null,
    val title: String? = null,
    val sourcePageUrl: String? = null,
    val metadata: MediaMetadata = MediaMetadata()
)
