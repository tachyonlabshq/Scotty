package app.scotty.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ── Screen state enum ─────────────────────────────────────────────────
private enum class SendScreenState { EMPTY, FILES_SELECTED, BEAMING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(viewModel: MainViewModel, snackbarHostState: SnackbarHostState) {
    val selectedFiles    by viewModel.selectedFiles.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val transferStatus   by viewModel.transferStatus.collectAsState()
    val nfcBeamStatus    by viewModel.nfcBeamStatus.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val files = uris.map { uri ->
            val fileName = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )?.use { cursor ->
                cursor.moveToFirst()
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
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

    // Show snackbar on NFC error
    LaunchedEffect(nfcBeamStatus) {
        if (nfcBeamStatus is NfcBeamStatus.Error) {
            val msg = (nfcBeamStatus as NfcBeamStatus.Error).message
            val result = snackbarHostState.showSnackbar(
                message = "Beam failed: $msg",
                actionLabel = "Retry",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.resetNfcBeamStatus()
            } else {
                viewModel.resetNfcBeamStatus()
            }
        }
    }

    // Show success snackbar
    LaunchedEffect(transferStatus) {
        if (transferStatus == app.scotty.service.TransferStatus.SUCCESS) {
            snackbarHostState.showSnackbar(
                message = "Beam complete! Files sent successfully.",
                duration = SnackbarDuration.Short
            )
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
                SendScreenState.EMPTY -> EmptyState(
                    onAddFiles = { filePickerLauncher.launch(arrayOf("*/*")) }
                )
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
                    transferStatus   = transferStatus,
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
                    .padding(bottom = 120.dp)
                    .semantics { contentDescription = "Add files to beam" },
                shape = MaterialTheme.shapes.extraLarge,
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
                        contentDescription = null,
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

// ── Empty state — concentric ring design ─────────────────────────────
@Composable
private fun EmptyState(onAddFiles: () -> Unit) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background faint concentric rings — static, decorative
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.38f)
            listOf(0.15f, 0.28f, 0.42f, 0.57f).forEach { fraction ->
                drawCircle(
                    color = primaryColor.copy(alpha = 0.04f),
                    radius = size.minDimension * fraction,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        Column(
            modifier = Modifier.padding(bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // NFC icon in a concentric ring structure
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxR = size.minDimension / 2f
                    listOf(0.38f, 0.62f, 0.86f).forEachIndexed { i, frac ->
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.10f - i * 0.02f),
                            radius = maxR * frac,
                            center = center,
                            style = Stroke(width = (2 - i * 0.4f).dp.toPx())
                        )
                    }
                }
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(88.dp),
                    tonalElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Nfc,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Text(
                text = "Beam Files",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Select files · tap devices together",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
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
            .padding(top = 8.dp, bottom = 120.dp)
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
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
            AnimatedVisibility(visible = selectedFiles.isNotEmpty()) {
                IconButton(
                    onClick = onClearFiles,
                    modifier = Modifier.semantics { contentDescription = "Clear all files" }
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // File list with animated placement
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            itemsIndexed(
                items = selectedFiles,
                key   = { _, pair -> pair.first.toString() }
            ) { index, (uri, fileName) ->
                val context = LocalContext.current
                val fileSize = remember(uri) {
                    context.contentResolver.query(
                        uri, arrayOf(OpenableColumns.SIZE), null, null, null
                    )?.use { c ->
                        c.moveToFirst()
                        val col = c.getColumnIndex(OpenableColumns.SIZE)
                        if (col >= 0) c.getLong(col) else -1L
                    } ?: -1L
                }
                val sizeLabel = when {
                    fileSize < 0             -> ""
                    fileSize < 1024          -> "$fileSize B"
                    fileSize < 1024 * 1024   -> "${fileSize / 1024} KB"
                    else                     -> "${fileSize / (1024 * 1024)} MB"
                }

                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    ) { it / 2 } + fadeIn(),
                    exit  = slideOutHorizontally(spring()) + fadeOut(),
                    modifier = Modifier.animateItem(
                        placementSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = MaterialTheme.shapes.medium
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = fileName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = if (sizeLabel.isNotEmpty()) ({
                                Text(
                                    text = sizeLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }) else null,
                            leadingContent = {
                                Icon(
                                    imageVector = fileTypeIcon(fileName),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = { onRemoveFile(index) },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Remove $fileName"
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // NFC beam status card
        when (nfcBeamStatus) {
            is NfcBeamStatus.Ready, is NfcBeamStatus.Advertising -> NfcBeamReadyCard()
            else -> Unit
        }
    }
}

// ── Beaming / progress state ──────────────────────────────────────────
@Composable
private fun BeamingState(
    nfcBeamStatus    : NfcBeamStatus,
    transferProgress : Map<Long, app.scotty.service.ProgressState>,
    transferStatus   : app.scotty.service.TransferStatus,
    onCancel         : () -> Unit
) {
    val isSuccess = transferStatus == app.scotty.service.TransferStatus.SUCCESS
    val primaryColor = MaterialTheme.colorScheme.primary

    // Spring scale for success icon
    val successScale by animateFloatAsState(
        targetValue = if (isSuccess) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "success_scale"
    )

    // Infinite pulse scale for NFC icon during beaming
    val beamInfinite = rememberInfiniteTransition(label = "beam_pulse")
    val pulseScale by beamInfinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beam_icon_scale"
    )
    // 3 radiating rings
    val ring1 by beamInfinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "beam_ring1"
    )
    val ring2 by beamInfinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, delayMillis = 533, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "beam_ring2"
    )
    val ring3 by beamInfinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, delayMillis = 1066, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "beam_ring3"
    )

    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isSuccess) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Transfer complete",
                    tint = primaryColor,
                    modifier = Modifier
                        .size(96.dp)
                        .scale(successScale)
                )
            } else {
                // Pulsing NFC icon with radiating rings
                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = size.minDimension / 2f
                        listOf(ring1, ring2, ring3).forEach { p ->
                            val radius = maxRadius * 0.3f + maxRadius * 0.7f * p
                            val alpha = (1f - p).coerceIn(0f, 1f) * 0.45f
                            drawCircle(
                                color = primaryColor.copy(alpha = alpha),
                                radius = radius,
                                center = center,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Nfc,
                        contentDescription = "Beaming files",
                        tint = primaryColor,
                        modifier = Modifier
                            .size(80.dp)
                            .scale(pulseScale)
                    )
                }
            }

            Text(
                text = if (isSuccess) "Beam Complete!" else "Beaming\u2026",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (!isSuccess) {
                transferProgress.values.forEach { state ->
                    val progress = if (state.totalBytes > 0)
                        (state.transferredBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) else 0f
                    val pct = (progress * 100).toInt()
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
                                text = "$pct%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .semantics {
                                    contentDescription =
                                        "Transferring ${state.fileName}, $pct percent complete"
                                }
                        )
                    }
                }
            }

            if (isSuccess) {
                Text(
                    text = "Files sent successfully",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                FilledTonalButton(onClick = onCancel) {
                    Text("Done")
                }
            } else {
                FilledTonalButton(onClick = onCancel) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel")
                }
            }
        }
    }
}

// ── NFC Beam Ready card — M3 Expressive spring + radial gradient ─────
@Composable
private fun NfcBeamReadyCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nfc_scale"
    )

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

    val primaryColor     = MaterialTheme.colorScheme.primary
    val containerColor   = MaterialTheme.colorScheme.primaryContainer
    val onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pulseScale)
            .semantics { contentDescription = "NFC beam ready — hold devices together" },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension * 0.85f
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
                    contentDescription = null,
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
        ext.contains("pdf")                                         -> Icons.Default.Info
        else                                                         -> Icons.Default.InsertDriveFile
    }
}
