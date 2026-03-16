package org.localsend.localsend_app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
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
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val selectedTab  by viewModel.selectedTab.collectAsState()
    val nfcBeamStatus by viewModel.nfcBeamStatus.collectAsState()
    val activity = LocalContext.current as? android.app.Activity

    LaunchedEffect(selectedFiles.isNotEmpty()) {
        if (activity != null) {
            if (selectedFiles.isNotEmpty()) viewModel.enableNfcBeam(activity)
            else viewModel.disableNfcBeam(activity)
        }
    }
    DisposableEffect(Unit) {
        onDispose { activity?.let { viewModel.disableNfcBeam(it) } }
    }

    val nfcDotColor = when (nfcBeamStatus) {
        is NfcBeamStatus.Advertising, is NfcBeamStatus.Ready ->
            MaterialTheme.colorScheme.tertiary
        is NfcBeamStatus.Connecting, is NfcBeamStatus.Discovering ->
            MaterialTheme.colorScheme.secondary
        is NfcBeamStatus.Error ->
            MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    val tabs = listOf(
        Triple("Send",     Icons.Default.Send,         selectedFiles.size),
        Triple("Receive",  Icons.Default.CallReceived,  0),
        Triple("Settings", Icons.Default.Settings,      0),
    )

    Scaffold(
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
                    // NFC status dot
                    Box(modifier = Modifier.padding(start = 16.dp)) {
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = CircleShape,
                            color = nfcDotColor
                        ) {}
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEachIndexed { index, (label, icon, badge) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick  = { viewModel.selectTab(index) },
                        icon = {
                            if (badge > 0) {
                                BadgedBox(badge = { Badge { Text(badge.toString()) } }) {
                                    Icon(icon, contentDescription = label)
                                }
                            } else {
                                Icon(icon, contentDescription = label)
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0    -> SendScreen(viewModel = viewModel)
                1    -> ReceiveScreen(viewModel = viewModel)
                else -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
