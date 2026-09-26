package com.bomeber.homestream.download

import android.net.Uri
import java.net.URLDecoder
import java.util.UUID

/**
 * Builds a safe, unique local filename for a download. Never uses the
 * full URL as a filename (illegal characters, unbounded length, query
 * strings).
 */
object FileNaming {

    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
    private const val FALLBACK_EXT = "mp4"

    fun buildFileName(preferredTitle: String?, url: String, existingNames: Set<String>): String {
        val ext = extensionFromUrl(url) ?: FALLBACK_EXT
        val base = sanitize(preferredTitle)
            ?: sanitize(lastUrlSegmentWithoutExtension(url))
            ?: "homestream_${UUID.randomUUID().toString().take(8)}"

        var candidate = "$base.$ext"
        var counter = 1
        while (existingNames.contains(candidate)) {
            candidate = "${base}_$counter.$ext"
            counter++
        }
        return candidate
    }

    private fun extensionFromUrl(url: String): String? {
        val path = runCatching { Uri.parse(url).path }.getOrNull() ?: return null
        val lastSegment = path.substringAfterLast('/')
        val dotIndex = lastSegment.lastIndexOf('.')
        if (dotIndex == -1 || dotIndex == lastSegment.lastIndex) return null
        val ext = lastSegment.substring(dotIndex + 1).lowercase()
        return ext.takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
    }

    private fun lastUrlSegmentWithoutExtension(url: String): String? {
        val path = runCatching { Uri.parse(url).path }.getOrNull() ?: return null
        val lastSegmentRaw = path.substringAfterLast('/')
        val lastSegment = runCatching { URLDecoder.decode(lastSegmentRaw, "UTF-8") }.getOrDefault(lastSegmentRaw)
        val dotIndex = lastSegment.lastIndexOf('.')
        val nameOnly = if (dotIndex > 0) lastSegment.substring(0, dotIndex) else lastSegment
        return nameOnly.takeIf { it.isNotBlank() }
    }

    private fun sanitize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace(ILLEGAL_CHARS, "_").trim().take(80)
        return cleaned.takeIf { it.isNotBlank() }
    }
}