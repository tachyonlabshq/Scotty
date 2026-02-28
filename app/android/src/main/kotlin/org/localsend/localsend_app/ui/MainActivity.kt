package org.localsend.localsend_app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            LocalSendTheme {
                LocalSendApp()
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Receive : Screen("receive", "Receive", Icons.Default.Wifi)
    data object Send : Screen("send", "Send", Icons.Default.Send)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSendApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val activity = LocalContext.current as? android.app.Activity

    val screens = listOf(Screen.Receive, Screen.Send, Screen.Settings)

    // Enable NFC reader mode only when on Send tab with files, disable otherwise
    val isOnSendTab = selectedTab == 1
    val hasFiles = selectedFiles.isNotEmpty()
    LaunchedEffect(isOnSendTab, hasFiles) {
        if (activity != null) {
            if (isOnSendTab && hasFiles) {
                viewModel.enableNfcBeam(activity)
            } else {
                viewModel.disableNfcBeam(activity)
            }
        }
    }

    // Cleanup on exit
    DisposableEffect(Unit) {
        onDispose {
            if (activity != null) viewModel.disableNfcBeam(activity)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                screens.forEachIndexed { index, screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = selectedTab == index,
                        onClick = {
                            viewModel.selectTab(index)
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Receive.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Receive.route) {
                ReceiveScreen(viewModel = viewModel)
            }
            composable(Screen.Send.route) {
                SendScreen(viewModel = viewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
