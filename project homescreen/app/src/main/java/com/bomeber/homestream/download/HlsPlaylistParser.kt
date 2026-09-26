package com.bomeber.homestream.download

import java.net.URI

/**
 * Minimal, dependency-free M3U8 parser — enough to resolve a VOD media
 * playlist's segment list, and (Step 5.1 revision) enough quality metadata
 * from a master playlist for the user to make an informed choice in the
 * Quality Selection sheet. Does NOT attempt DRM/key handling: an encrypted
 * media playlist is reported via [MediaPlaylist.isEncrypted] and the
 * caller must refuse it — no decryption is ever attempted, per rule #14.
 *
 * Step 5.1 change: [Variant] no longer carries just bandwidth — quality
 * selection is now a real user choice (see project.md), so every field a
 * quality-picker UI could reasonably want is parsed here. Automatic
 * "pick highest bandwidth" selection has been removed entirely — it used
 * to live in HlsDownloadEngine as `.maxByOrNull { it.bandwidth }`, which
 * no longer exists.
 */
object HlsPlaylistParser {

    /**
     * One quality variant from a master playlist.
     * [width]/[height] are null when RESOLUTION wasn't present (some
     * master playlists only differentiate by bitrate, e.g. audio-only
     * variants or non-standard encoders) — [displayLabel] falls back to
     * bitrate-only display in that case, per the spec's display rules.
     */
    data class Variant(
        val uri: String,
        val bandwidth: Long,
        val averageBandwidth: Long? = null,
        val width: Int? = null,
        val height: Int? = null,
        val codecs: String? = null,
        val frameRate: Double? = null
    ) {
        /** "1080p • 5.8 Mbps" / "1080p" (no bandwidth) / "5.8 Mbps" (no resolution). */
        fun displayLabel(): String {
            val resPart = height?.let { "${it}p" }
            val bwSource = averageBandwidth ?: bandwidth
            val bwPart = if (bwSource > 0) formatBitrate(bwSource) else null
            return listOfNotNull(resPart, bwPart).joinToString(" • ").ifBlank { uri.substringAfterLast('/') }
        }

        private fun formatBitrate(bitsPerSecond: Long): String = when {
            bitsPerSecond >= 1_000_000 -> "%.1f Mbps".format(bitsPerSecond / 1_000_000.0)
            bitsPerSecond >= 1_000 -> "%d Kbps".format(bitsPerSecond / 1_000)
            else -> "$bitsPerSecond bps"
        }
    }

    data class MediaPlaylist(
        val segments: List<String>,
        val initSegmentUri: String?,
        val isVod: Boolean,
        val isEncrypted: Boolean
    )

    fun isMaster(playlistText: String): Boolean = playlistText.contains("#EXT-X-STREAM-INF")

    /**
     * Parses every variant in a master playlist, sorted highest quality
     * first (by height when known, falling back to bandwidth) — display
     * order only; Step 5.1's quality sheet must NOT auto-select from this
     * list, the caller just renders it and waits for the user.
     */
    fun parseMaster(playlistText: String, baseUrl: String): List<Variant> {
        val variants = mutableListOf<Variant>()
        var pending: PendingStreamInf? = null

        for (rawLine in playlistText.lines()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF") -> {
                    pending = PendingStreamInf(
                        bandwidth = Regex("[^-]BANDWIDTH=(\\d+)").find(" $line")
                            ?.groupValues?.get(1)?.toLongOrNull() ?: 0L,
                        averageBandwidth = Regex("AVERAGE-BANDWIDTH=(\\d+)").find(line)
                            ?.groupValues?.get(1)?.toLongOrNull(),
                        resolution = Regex("RESOLUTION=(\\d+)x(\\d+)").find(line)?.let {
                            it.groupValues[1].toIntOrNull() to it.groupValues[2].toIntOrNull()
                        },
                        codecs = Regex("CODECS=\"([^\"]*)\"").find(line)?.groupValues?.get(1),
                        frameRate = Regex("FRAME-RATE=([\\d.]+)").find(line)
                            ?.groupValues?.get(1)?.toDoubleOrNull()
                    )
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    pending?.let { p ->
                        variants += Variant(
                            uri = resolveUrl(baseUrl, line),
                            bandwidth = p.bandwidth,
                            averageBandwidth = p.averageBandwidth,
                            width = p.resolution?.first,
                            height = p.resolution?.second,
                            codecs = p.codecs,
                            frameRate = p.frameRate
                        )
                    }
                    pending = null
                }
            }
        }
        return variants.sortedWith(
            compareByDescending<Variant> { it.height ?: -1 }.thenByDescending { it.bandwidth }
        )
    }

    fun parseMedia(playlistText: String, baseUrl: String): MediaPlaylist {
        val segments = mutableListOf<String>()
        var initUri: String? = null
        var isVod = false
        var isEncrypted = false
        for (rawLine in playlistText.lines()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-ENDLIST") -> isVod = true
                line.startsWith("#EXT-X-KEY") && !line.contains("METHOD=NONE") -> isEncrypted = true
                line.startsWith("#EXT-X-MAP") -> {
                    Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)?.let {
                        initUri = resolveUrl(baseUrl, it)
                    }
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    segments += resolveUrl(baseUrl, line)
                }
            }
        }
        return MediaPlaylist(segments, initUri, isVod, isEncrypted)
    }

    private data class PendingStreamInf(
        val bandwidth: Long,
        val averageBandwidth: Long?,
        val resolution: Pair<Int?, Int?>?,
        val codecs: String?,
        val frameRate: Double?
    )

    private fun resolveUrl(base: String, ref: String): String =
        if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(ref)) ref
        else URI(base).resolve(ref).toString()
}