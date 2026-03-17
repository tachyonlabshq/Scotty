package org.localsend.localsend_app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.localsend.localsend_app.service.ReceivedFile

@Composable
fun ReceiveScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val nfcBeamStatus by viewModel.nfcBeamStatus.collectAsState()
    val receivedFiles by viewModel.receivedFiles.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    // 4 staggered radar rings
    val transitions = (0..3).map { i ->
        val t = rememberInfiniteTransition(label = "ring_$i")
        t.animateFloat(
            initialValue = 0f,
            targetValue  = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2400,
                    delayMillis    = i * 600,
                    easing         = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring_progress_$i"
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        item {
            Spacer(Modifier.height(24.dp))

            // Radar + NFC icon composite
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "Radar pulse animation" }
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension / 2f
                    transitions.forEach { progress ->
                        val p = progress.value
                        val radius = maxRadius * 0.25f + maxRadius * 0.75f * p
                        val alpha  = (1f - p).coerceIn(0f, 1f) * 0.5f
                        drawCircle(
                            color  = primaryColor.copy(alpha = alpha),
                            radius = radius,
                            center = center,
                            style  = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                Surface(
                    modifier = Modifier.size(96.dp),
                    shape  = CircleShape,
                    color  = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Nfc,
                            contentDescription = "NFC ready to receive",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Ready to Beam",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Tap another device to receive files",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            SuggestionChip(
                onClick = {},
                label   = { Text(settings.alias) },
                icon    = {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            Spacer(Modifier.height(24.dp))

            // Connection status overlay card
            val statusText = when (nfcBeamStatus) {
                is NfcBeamStatus.Discovering -> "Discovering…"
                is NfcBeamStatus.Connecting  -> "Connecting to ${(nfcBeamStatus as NfcBeamStatus.Connecting).deviceName}…"
                else -> null
            }
            AnimatedVisibility(
                visible = statusText != null,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit  = fadeOut()
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = MaterialTheme.shapes.large
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(
                            text  = statusText ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Received files section header
            AnimatedVisibility(visible = receivedFiles.isNotEmpty()) {
                Column {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Received Files",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "${receivedFiles.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (receivedFiles.isEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "No files received yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(
                items = receivedFiles,
                key   = { it.receivedAtMs }
            ) { file ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    ) { it / 2 } + fadeIn(),
                    modifier = Modifier.animateItem(
                        placementSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
                ) {
                    ReceivedFileCard(
                        file = file,
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = file.mimeType
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(file.filePath))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share ${file.fileName}"))
                        },
                        onOpen = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(file.filePath), file.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ReceivedFileCard(
    file: ReceivedFile,
    onShare: () -> Unit,
    onOpen: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.extraLarge
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = file.fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = humanReadableSize(file.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(
                    imageVector = fileTypeIcon(file.fileName),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                Row {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.semantics { contentDescription = "Share ${file.fileName}" }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                    }
                    IconButton(
                        onClick = onOpen,
                        modifier = Modifier.semantics { contentDescription = "Open ${file.fileName}" }
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                    }
                }
            }
        )
    }
}

private fun humanReadableSize(bytes: Long): String = when {
    bytes < 0          -> ""
    bytes < 1024       -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else               -> "${bytes / (1024 * 1024)} MB"
}

private fun fileTypeIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        ext in listOf("jpg","jpeg","png","gif","webp","heic","bmp") -> Icons.Default.Image
        ext in listOf("mp4","mov","avi","mkv","webm")               -> Icons.Default.Videocam
        ext in listOf("mp3","aac","wav","flac","ogg","m4a")         -> Icons.Default.AudioFile
        ext.contains("pdf")                                         -> Icons.Default.PictureAsPdf
        else                                                        -> Icons.Default.InsertDriveFile
    }
}
