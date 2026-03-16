package org.localsend.localsend_app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ── Screen state enum ─────────────────────────────────────────────────
private enum class SendScreenState { EMPTY, FILES_SELECTED, BEAMING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(viewModel: MainViewModel) {
    val selectedFiles   by viewModel.selectedFiles.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val nfcBeamStatus   by viewModel.nfcBeamStatus.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val files = uris.map { uri ->
            val fileName = context.contentResolver.query(uri, null, null, null, null)
                ?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst(); cursor.getString(idx)
                } ?: "Unknown"
            uri to fileName
        }
        viewModel.addFiles(files)
    }

    val screenState = when {
        transferProgress.isNotEmpty() ||
        nfcBeamStatus is NfcBeamStatus.Connecting ||
        nfcBeamStatus is NfcBeamStatus.Discovering -> SendScreenState.BEAMING
        selectedFiles.isNotEmpty() -> SendScreenState.FILES_SELECTED
        else -> SendScreenState.EMPTY
    }

    // Dismiss errors with snackbar-style auto-clear
    LaunchedEffect(nfcBeamStatus) {
        if (nfcBeamStatus is NfcBeamStatus.Error) {
            kotlinx.coroutines.delay(3000)
            viewModel.resetNfcBeamStatus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screenState,
            transitionSpec = {
                (slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) { it / 4 } + fadeIn()) togetherWith
                (slideOutVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) { -it / 4 } + fadeOut())
            },
            label = "send_screen_state"
        ) { state ->
            when (state) {
                SendScreenState.EMPTY         -> EmptyState(onAddFiles = { filePickerLauncher.launch(arrayOf("*/*")) })
                SendScreenState.FILES_SELECTED -> FilesSelectedState(
                    selectedFiles = selectedFiles,
                    nfcBeamStatus = nfcBeamStatus,
                    onAddFiles    = { filePickerLauncher.launch(arrayOf("*/*")) },
                    onRemoveFile  = { viewModel.removeFile(it) },
                    onClearFiles  = { viewModel.clearFiles() }
                )
                SendScreenState.BEAMING -> BeamingState(
                    nfcBeamStatus    = nfcBeamStatus,
                    transferProgress = transferProgress,
                    onCancel         = { viewModel.clearFiles() }
                )
            }
        }

        // FAB always visible on empty + files selected states
        if (screenState != SendScreenState.BEAMING) {
            LargeFloatingActionButton(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                shape = FloatingActionButtonDefaults.largeShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = "Add files to beam",
                        modifier = Modifier.size(28.dp)
                    )
                    AnimatedVisibility(
                        visible = screenState == SendScreenState.EMPTY,
                        enter = fadeIn() + expandHorizontally(),
                        exit  = fadeOut() + shrinkHorizontally()
                    ) {
                        Text(
                            text = "Add Files",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────
@Composable
private fun EmptyState(onAddFiles: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .padding(bottom = 120.dp), // FAB clearance
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Select files\nto beam",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Tap Add Files, then touch\nanother device to transfer",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Files selected state ──────────────────────────────────────────────
@Composable
private fun FilesSelectedState(
    selectedFiles : List<Pair<Uri, String>>,
    nfcBeamStatus : NfcBeamStatus,
    onAddFiles    : () -> Unit,
    onRemoveFile  : (Int) -> Unit,
    onClearFiles  : () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 120.dp) // FAB clearance
    ) {
        // Section header
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Beam Files",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            AnimatedVisibility(visible = selectedFiles.isNotEmpty()) {
                FilledTonalButton(onClick = onClearFiles) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear all")
                }
            }
        }

        // File list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(selectedFiles) { index, (_, fileName) ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = fileName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = fileTypeIcon(fileName),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onRemoveFile(index) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove $fileName from beam list"
                                )
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // NFC beam status card
        when (val s = nfcBeamStatus) {
            is NfcBeamStatus.Ready, is NfcBeamStatus.Advertising -> NfcBeamReadyCard()
            is NfcBeamStatus.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            text = "Beam failed: ${(s as NfcBeamStatus.Error).message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            else -> Unit
        }
    }
}

// ── Beaming / progress state ──────────────────────────────────────────
@Composable
private fun BeamingState(
    nfcBeamStatus    : NfcBeamStatus,
    transferProgress : Map<Long, org.localsend.localsend_app.service.ProgressState>,
    onCancel         : () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(120.dp),
                strokeWidth = 8.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Beaming…",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            transferProgress.values.forEach { state ->
                val progress = if (state.totalBytes > 0)
                    (state.transferredBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) else 0f
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
            FilledTonalButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancel")
            }
        }
    }
}

// ── NFC Beam Ready card — M3 Expressive spring + radial gradient ─────
@Composable
private fun NfcBeamReadyCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_pulse")

    // Gentle card scale pulse (tween — springs can't be infinite)
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nfc_scale"
    )

    // Three staggered ring radii expanding outward
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ring1"
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ring2"
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ring3"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = Modifier.fillMaxWidth().scale(pulseScale),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            // Radial gradient background overlay
            Canvas(modifier = Modifier.matchParentSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension * 0.85f
                // Radial gradient: primary center → transparent edge
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.15f),
                            primaryColor.copy(alpha = 0.0f)
                        ),
                        center = center,
                        radius = maxRadius
                    ),
                    radius = maxRadius,
                    center = center
                )
                // Three pulsing rings
                listOf(ring1, ring2, ring3).forEach { progress ->
                    val radius = maxRadius * 0.3f + maxRadius * 0.7f * progress
                    val alpha = (1f - progress).coerceIn(0f, 1f) * 0.4f
                    drawCircle(
                        color = primaryColor.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Nfc,
                    contentDescription = "NFC beam ready, touch devices together",
                    tint = primaryColor,
                    modifier = Modifier.size(128.dp)
                )
                Text(
                    text = "Touch to Beam",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = onContainerColor,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Hold the back of the devices together",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainerColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────
private fun fileTypeIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        ext in listOf("jpg","jpeg","png","gif","webp","heic","bmp") -> Icons.Default.Image
        ext in listOf("mp4","mov","avi","mkv","webm")               -> Icons.Default.Videocam
        ext in listOf("mp3","aac","wav","flac","ogg","m4a")         -> Icons.Default.AudioFile
        else                                                         -> Icons.Default.InsertDriveFile
    }
}
