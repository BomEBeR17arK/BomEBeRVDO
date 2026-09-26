package com.bomeber.homestream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.bomeber.homestream.ui.components.HomeStreamBottomBar
import com.bomeber.homestream.ui.navigation.HomeStreamNavHost
import com.bomeber.homestream.ui.navigation.Screen
import com.bomeber.homestream.ui.theme.HomeStreamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeStreamTheme {
                HomeStreamApp()
            }
        }
    }
}

@Composable
fun HomeStreamApp() {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { HomeStreamBottomBar(navController) }
    ) { innerPadding ->
        HomeStreamNavHost(
            navController = navController,
            innerPadding = innerPadding,
            onNavigateToTab = { route ->
                navController.navigate(route) {
                    popUpTo(Screen.Home.route)
                    launchSingleTop = true
                }
            }
        )
    }
}
