package org.localsend.localsend_app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            LocalSendTheme {
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

@Composable
fun BeamApp(viewModel: MainViewModel = viewModel()) {
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val activity = LocalContext.current as? android.app.Activity

    val hasFiles = selectedFiles.isNotEmpty()
    
    // Manage NFC Beam State
    LaunchedEffect(hasFiles) {
        if (activity != null) {
            if (hasFiles) {
                // If SENDER (has files), enable NFC beam logic to get Endpoint token and act as NFC Reader
                viewModel.enableNfcBeam(activity)
            } else {
                // If RECEIVER (no files), disable reader mode. HCE is still running in background.
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

    // Unified Send/Receive Screen
    SendScreen(viewModel = viewModel)
}
