package com.bomeber.homestream.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * รายการหน้าทั้งหมด + route + icon ที่ใช้ทั้งใน NavHost และ Bottom Navigation
 * เพิ่ม/ลดแท็บแค่แก้ตรงนี้ที่เดียว
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Browser : Screen("browser", "Browser", Icons.Filled.Public)
    object Downloads : Screen("downloads", "Downloads", Icons.Filled.CloudDownload)
    object Library : Screen("library", "Library", Icons.Filled.VideoLibrary)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)

    companion object {
        val bottomNavItems = listOf(Home, Browser, Downloads, Library, Settings)
    }
}
