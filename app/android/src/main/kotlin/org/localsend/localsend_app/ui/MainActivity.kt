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

@Composable
fun BeamApp(viewModel: MainViewModel = viewModel()) {
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val activity = LocalContext.current as? android.app.Activity

    val hasFiles = selectedFiles.isNotEmpty()

    LaunchedEffect(hasFiles) {
        if (activity != null) {
            if (hasFiles) {
                viewModel.enableNfcBeam(activity)
            } else {
                viewModel.disableNfcBeam(activity)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (activity != null) viewModel.disableNfcBeam(activity)
        }
    }

    SendScreen(viewModel = viewModel)
}
