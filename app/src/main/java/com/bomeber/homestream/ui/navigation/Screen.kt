package com.bomeber.homestream.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * รายการหน้าทั้งหมด + route + icon ที่ใช้ทั้งใน NavHost และ Bottom Navigation
 * เพิ่ม/ลดแท็บแค่แก้ตรงนี้ที่เดียว
 *
 * Step 4: added [Player]. It is deliberately NOT in [bottomNavItems] — it's
 * not a tab, it's only ever reached from BrowserScreen when the user taps
 * Play on a supported DetectedMedia item. Per the Step 4 navigation rule
 * ("don't put large objects into navigation arguments"), only the media
 * URL (URL-encoded) and the access-type name travel through the route —
 * never a serialized DetectedMedia object.
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Browser : Screen("browser", "Browser", Icons.Filled.Public)
    object Downloads : Screen("downloads", "Downloads", Icons.Filled.CloudDownload)
    object Library : Screen("library", "Library", Icons.Filled.VideoLibrary)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)

    object Player : Screen(
        route = "player/{mediaUrl}/{accessType}",
        label = "Player",
        icon = Icons.Filled.PlayArrow
    ) {
        private const val BASE = "player"

        /** Builds a navigable route to play [mediaUrl], URL-encoding it so slashes/query strings survive as one nav-arg segment. */
        fun createRoute(mediaUrl: String, accessType: String): String {
            val encodedUrl = URLEncoder.encode(mediaUrl, "UTF-8")
            return "$BASE/$encodedUrl/$accessType"
        }

        /** Reverses [createRoute]'s encoding — call on the raw "mediaUrl" nav argument before handing it to PlayerScreen. */
        fun decodeMediaUrl(encoded: String): String = URLDecoder.decode(encoded, "UTF-8")
    }

    companion object {
        // Player is intentionally excluded here — see the class doc above.
        val bottomNavItems = listOf(Home, Browser, Downloads, Library, Settings)
    }
}
