package com.bomeber.homestream.download

import android.content.Context

/**
 * Persists the user's "max download connections" setting via plain
 * SharedPreferences — no new dependency needed (DataStore would have
 * required adding one; this app has none of it configured yet).
 */
class DownloadPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("homestream_download_prefs", Context.MODE_PRIVATE)

    fun getMaxConnections(): Int {
        val stored = prefs.getInt(KEY_MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS)
        return if (stored in ALLOWED_CONNECTIONS) stored else DEFAULT_MAX_CONNECTIONS
    }

    fun setMaxConnections(value: Int) {
        require(value in ALLOWED_CONNECTIONS) { "ค่าที่อนุญาตคือ $ALLOWED_CONNECTIONS เท่านั้น" }
        prefs.edit().putInt(KEY_MAX_CONNECTIONS, value).apply()
    }

    companion object {
        private const val KEY_MAX_CONNECTIONS = "max_connections"
        const val DEFAULT_MAX_CONNECTIONS = 4
        val ALLOWED_CONNECTIONS = listOf(1, 2, 4, 8, 12, 16)
    }
}
