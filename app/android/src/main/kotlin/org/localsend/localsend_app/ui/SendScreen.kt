package org.localsend.localsend_app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.localsend.localsend_app.model.Device

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(viewModel: MainViewModel) {
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val nearbyDevices by viewModel.nearbyDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isTransferring by viewModel.isTransferring.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val nfcBeamStatus by viewModel.nfcBeamStatus.collectAsState()

    val context = LocalContext.current

    var selectedDevice by remember { mutableStateOf<Device?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val files = uris.map { uri ->
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "Unknown"
            uri to fileName
        }
        viewModel.addFiles(files)
    }

    // Snackbar for beam errors
    LaunchedEffect(nfcBeamStatus) {
        if (nfcBeamStatus is NfcBeamStatus.Error) {
            viewModel.resetNfcBeamStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // File selection section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Files", style = MaterialTheme.typography.titleMedium)
                    if (selectedFiles.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearFiles() }) { Text("Clear") }
                    }
                }

                if (selectedFiles.isEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        OutlinedButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Files")
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(selectedFiles.size) { index ->
                            val (uri, fileName) = selectedFiles[index]
                            ListItem(
                                headlineContent = {
                                    Text(text = fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingContent = {
                                    Icon(Icons.Default.InsertDriveFile, contentDescription = null)
                                },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.removeFile(index) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove")
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add More Files")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── NFC BEAM BANNER ──────────────────────────────────────────────────
        // Show beam UI when files are selected and NFC is active
        if (selectedFiles.isNotEmpty()) {
            when (val status = nfcBeamStatus) {
                is NfcBeamStatus.Ready -> {
                    NfcBeamReadyCard()
                }
                is NfcBeamStatus.Connecting -> {
                    NfcBeamConnectingCard(deviceAlias = status.device.alias)
                }
                is NfcBeamStatus.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Beam failed: ${status.message}",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                else -> Unit
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        // ────────────────────────────────────────────────────────────────────

        // Device selection section (Wi-Fi discovery fallback)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Send to", style = MaterialTheme.typography.titleMedium)
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }

                if (nearbyDevices.isEmpty()) {
                    Text(
                        text = "No devices found. Make sure other devices are on the same network.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(nearbyDevices.values.toList()) { device ->
                            ListItem(
                                headlineContent = { Text(device.alias) },
                                supportingContent = { Text(device.ip ?: "Unknown IP") },
                                leadingContent = {
                                    Icon(
                                        imageVector = when (device.deviceType.name) {
                                            "MOBILE" -> Icons.Default.PhoneAndroid
                                            "DESKTOP" -> Icons.Default.Computer
                                            else -> Icons.Default.Devices
                                        },
                                        contentDescription = null
                                    )
                                },
                                trailingContent = {
                                    RadioButton(
                                        selected = selectedDevice?.ip == device.ip,
                                        onClick = { selectedDevice = device }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDevice = device }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Send button (manual device selection fallback)
        Button(
            onClick = { selectedDevice?.let { device -> viewModel.sendToDevice(device) } },
            enabled = selectedFiles.isNotEmpty() && selectedDevice != null && !isTransferring,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (isTransferring) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sending...")
            } else {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send")
            }
        }

        // Transfer progress
        if (transferProgress.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    transferProgress.values.forEach { state ->
                        val progress = if (state.totalBytes > 0) {
                            (state.transferredBytes.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val percent = (progress * 100).toInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = state.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/** Pulsing NFC card shown when beam is active and waiting for tap. */
@Composable
private fun NfcBeamReadyCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nfc_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nfc_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = "NFC",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Touch to Beam",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Hold the back of the devices together",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Card shown while connecting after a tap. */
@Composable
private fun NfcBeamConnectingCard(deviceAlias: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Column {
                Text(
                    text = "Beaming to $deviceAlias…",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Transfer starting",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
