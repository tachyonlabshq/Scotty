package org.localsend.localsend_app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ReceiveScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val nfcBeamStatus by viewModel.nfcBeamStatus.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Radar + NFC icon composite
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Decorative radar rings — no semantic meaning
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {}
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

            // Center NFC icon on solid surface
            Surface(
                modifier = Modifier.size(96.dp),
                shape  = CircleShape,
                color  = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Nfc,
                        contentDescription = "Ready to receive via NFC",
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
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Tap another device to receive files",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Device name chip
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

        Spacer(Modifier.height(32.dp))

        // Connection status overlay card
        val statusText = when (nfcBeamStatus) {
            is NfcBeamStatus.Discovering -> "Discovering…"
            is NfcBeamStatus.Connecting  -> "Connecting to ${(nfcBeamStatus as NfcBeamStatus.Connecting).deviceName}…"
            else -> null
        }
        if (statusText != null) {
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
                        text  = statusText,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
