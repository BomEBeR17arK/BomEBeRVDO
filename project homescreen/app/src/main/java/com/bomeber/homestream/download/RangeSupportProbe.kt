package com.bomeber.homestream.download

import java.net.HttpURLConnection
import java.net.URL

/**
 * Determines whether [urlString] supports HTTP Range requests, without
 * assuming any server behavior (Step 5.1E rule #1–3). First tries a HEAD
 * request for Accept-Ranges/Content-Length; if that's inconclusive, does a
 * real "bytes=0-0" GET and checks for a 206 response — some servers don't
 * advertise Accept-Ranges but honor Range anyway, and vice versa.
 */
object RangeSupportProbe {

    data class ProbeResult(val supportsRange: Boolean, val contentLength: Long)

    fun probe(urlString: String): ProbeResult = try {
        val head = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val length = head.contentLengthLong
        val acceptRanges = head.getHeaderField("Accept-Ranges")
        head.disconnect()

        when {
            acceptRanges?.equals("bytes", ignoreCase = true) == true && length > 0 ->
                ProbeResult(true, length)
            length > 0 -> confirmWithRangeRequest(urlString, length)
            else -> ProbeResult(false, -1)
        }
    } catch (e: Exception) {
        ProbeResult(false, -1)
    }

    private fun confirmWithRangeRequest(urlString: String, knownLength: Long): ProbeResult = try {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=0-0")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val supports = conn.responseCode == HttpURLConnection.HTTP_PARTIAL
        conn.disconnect()
        ProbeResult(supports, knownLength)
    } catch (e: Exception) {
        ProbeResult(false, knownLength)
    }
}
