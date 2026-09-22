package com.bomeber.homestream.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bomeber.homestream.ui.screens.browser.BrowserScreen
import com.bomeber.homestream.ui.screens.downloads.DownloadsScreen
import com.bomeber.homestream.ui.screens.home.HomeScreen
import com.bomeber.homestream.ui.screens.library.LibraryScreen
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
        composable(Screen.Browser.route) { BrowserScreen() }
        composable(Screen.Downloads.route) { DownloadsScreen() }
        composable(Screen.Library.route) { LibraryScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }
    }
}
