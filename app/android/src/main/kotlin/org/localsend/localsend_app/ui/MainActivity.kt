package org.localsend.localsend_app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScottyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BeamApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeamApp(viewModel: MainViewModel = viewModel()) {
    val selectedFiles  by viewModel.selectedFiles.collectAsState()
    val selectedTab    by viewModel.selectedTab.collectAsState()
    val nfcBeamStatus  by viewModel.nfcBeamStatus.collectAsState()
    val activity = LocalContext.current as? android.app.Activity

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(selectedFiles.isNotEmpty()) {
        if (activity != null) {
            if (selectedFiles.isNotEmpty()) viewModel.enableNfcBeam(activity)
            else viewModel.disableNfcBeam(activity)
        }
    }
    DisposableEffect(Unit) {
        onDispose { activity?.let { viewModel.disableNfcBeam(it) } }
    }

    // Animated NFC status dot color
    val nfcDotColorTarget = when (nfcBeamStatus) {
        is NfcBeamStatus.Advertising, is NfcBeamStatus.Ready ->
            MaterialTheme.colorScheme.tertiary
        is NfcBeamStatus.Connecting, is NfcBeamStatus.Discovering ->
            MaterialTheme.colorScheme.secondary
        is NfcBeamStatus.Error ->
            MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val nfcDotColor by animateColorAsState(
        targetValue = nfcDotColorTarget,
        animationSpec = spring(),
        label = "nfc_dot_color"
    )

    val nfcStatusText = when (nfcBeamStatus) {
        is NfcBeamStatus.Idle        -> "idle"
        is NfcBeamStatus.Ready       -> "ready"
        is NfcBeamStatus.Advertising -> "advertising"
        is NfcBeamStatus.Discovering -> "discovering"
        is NfcBeamStatus.Connecting  -> "connecting"
        is NfcBeamStatus.Error       -> "error"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scotty",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(10.dp)
                            .background(nfcDotColor, CircleShape)
                            .semantics {
                                contentDescription = "NFC status: $nfcStatusText"
                            }
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.selectTab(2) },
                        modifier = Modifier.semantics {
                            contentDescription = "Open settings"
                        }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                // Send tab with badge
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick  = { viewModel.selectTab(0) },
                    icon = {
                        BadgedBox(badge = {
                            if (selectedFiles.isNotEmpty()) {
                                Badge { Text(selectedFiles.size.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.Send, contentDescription = null)
                        }
                    },
                    label = { Text("Send") }
                )
                // Receive tab
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick  = { viewModel.selectTab(1) },
                    icon     = { Icon(Icons.Default.CallReceived, contentDescription = null) },
                    label    = { Text("Receive") }
                )
                // Settings tab
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick  = { viewModel.selectTab(2) },
                    icon     = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label    = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0    -> SendScreen(viewModel = viewModel, snackbarHostState = snackbarHostState)
                1    -> ReceiveScreen(viewModel = viewModel)
                else -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
