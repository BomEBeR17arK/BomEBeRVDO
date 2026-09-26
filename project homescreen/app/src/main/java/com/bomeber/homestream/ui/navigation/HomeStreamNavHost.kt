package com.bomeber.homestream.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.bomeber.homestream.HomeStreamApplication
import com.bomeber.homestream.download.DownloadType
import com.bomeber.homestream.media.DetectedMedia
import com.bomeber.homestream.media.MediaAccessType
import com.bomeber.homestream.ui.screens.browser.BrowserScreen
import com.bomeber.homestream.ui.screens.downloads.DownloadsScreen
import com.bomeber.homestream.ui.screens.home.HomeScreen
import com.bomeber.homestream.ui.screens.library.LibraryScreen
import com.bomeber.homestream.ui.screens.player.PlayerScreen
import com.bomeber.homestream.ui.screens.settings.SettingsScreen

@Composable
fun HomeStreamNavHost(
    navController: NavHostController,
    innerPadding: PaddingValues,
    onNavigateToTab: (String) -> Unit
) {
    val context = LocalContext.current
    val downloadRepository = (context.applicationContext as HomeStreamApplication).downloadRepository

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.padding(innerPadding)
    ) {
        composable(Screen.Home.route) {
            HomeScreen(onNavigateToTab = onNavigateToTab)
        }
        composable(Screen.Browser.route) {
            BrowserScreen(
                onPlayMedia = { media: DetectedMedia ->
                    navController.navigate(
                        Screen.Player.createRoute(mediaUrl = media.url, accessType = media.accessType.name)
                    )
                },
                // Step 5.1 — kicks off DownloadRepository via Room + WorkManager;
                // progress is observed on the Downloads tab, not here.
                onDownloadMedia = { media: DetectedMedia, variant ->
                    downloadRepository.startDownload(media, variant)
                }
            )
        }
        composable(Screen.Downloads.route) {
            DownloadsScreen(
                // Step 5.1 — reuses Step 4's PlayerScreen for offline playback of a
                // completed download: local file, same DIRECT_FILE access type.
                onPlayLocal = { item ->
                    navController.navigate(
                        Screen.Player.createRoute(
                            mediaUrl = if (item.type == DownloadType.HLS && item.localFilePath!!.startsWith("http")) item.localFilePath else "file://${item.localFilePath}",
                            accessType = if (item.type == DownloadType.HLS && item.localFilePath!!.startsWith("http")) "HLS_OFFLINE" else MediaAccessType.DIRECT_FILE.name
                        )
                    )
                }
            )
        }
        composable(Screen.Library.route) { LibraryScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("mediaUrl") { type = NavType.StringType },
                navArgument("accessType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("mediaUrl").orEmpty()
            val accessType = backStackEntry.arguments?.getString("accessType").orEmpty()
            PlayerScreen(
                mediaUrl = Screen.Player.decodeMediaUrl(encodedUrl),
                accessType = accessType,
                onBack = { navController.popBackStack() }
            )
        }
    }
}