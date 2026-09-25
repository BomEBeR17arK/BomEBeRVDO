package com.bomeber.homestream.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.bomeber.homestream.download.DownloadEntity
import com.bomeber.homestream.media.DetectedMedia
import com.bomeber.homestream.media.MediaAccessType
import com.bomeber.homestream.ui.screens.browser.BrowserScreen
import com.bomeber.homestream.ui.screens.downloads.DownloadsScreen
import com.bomeber.homestream.ui.screens.downloads.DownloadsViewModel
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
    // Step 5: created here (not inside a composable{} destination block) so
    // it's scoped to the Activity and shared between BrowserScreen's
    // Download action and DownloadsScreen's list — a single source of truth.
    val downloadsViewModel: DownloadsViewModel = viewModel()

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
                onDownloadMedia = { media: DetectedMedia ->
                    downloadsViewModel.onDownloadMedia(media)
                    onNavigateToTab(Screen.Downloads.route)
                }
            )
        }
        composable(Screen.Downloads.route) {
            DownloadsScreen(
                viewModel = downloadsViewModel,
                onOpenCompleted = { entity: DownloadEntity ->
                    // Reuses Step 4's PlayerScreen for the local file — no
                    // FileProvider/manifest changes needed since Media3 can
                    // read a file:// Uri directly in-process.
                    navController.navigate(
                        Screen.Player.createRoute(
                            mediaUrl = "file://${entity.filePath}",
                            accessType = MediaAccessType.DIRECT_FILE.name
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