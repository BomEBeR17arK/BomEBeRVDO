package com.bomeber.homestream.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.bomeber.homestream.media.DetectedMedia
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
                        Screen.Player.createRoute(
                            mediaUrl = media.url,
                            accessType = media.accessType.name
                        )
                    )
                }
            )
        }
        composable(Screen.Downloads.route) { DownloadsScreen() }
        composable(Screen.Library.route) { LibraryScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }

        // Step 4 — Player destination. Reached only from BrowserScreen's
        // "Play" action on a DIRECT_FILE or HLS DetectedMedia item. Only
        // the URL + access-type name travel through navigation as plain
        // strings (see Screen.Player).
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
