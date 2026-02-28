package org.localsend.localsend_app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.localsend.localsend_app.model.Device

@Composable
fun ReceiveScreen(viewModel: MainViewModel) {
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val localIp by viewModel.localIp.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val quickSave by viewModel.quickSave.collectAsState()
    
    var showAdvanced by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated logo
        val infiniteTransition = rememberInfiniteTransition(label = "logo")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(15000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
        
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = "LocalSend",
            modifier = Modifier
                .size(120.dp)
                .rotate(if (isServerRunning) rotation else 0f),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Device name / alias
        Text(
            text = settings.alias,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // IP Address
        Text(
            text = if (isServerRunning) localIp ?: "..." else "Offline",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Status indicator
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isServerRunning) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isServerRunning) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (isServerRunning) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isServerRunning) "Ready to receive" else "Offline",
                    color = if (isServerRunning) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Quick save toggle
        Text(
            text = "Quick Save",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = !quickSave,
                onClick = { if (quickSave) viewModel.toggleQuickSave() },
                label = { Text("Off") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = quickSave,
                onClick = { if (!quickSave) viewModel.toggleQuickSave() },
                label = { Text("On") }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Advanced toggle
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "Hide Advanced" else "Show Advanced")
            Icon(
                imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }
        
        if (showAdvanced) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "IP Address: ${localIp ?: "Not available"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Port: ${settings.port}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Protocol: ${if (settings.https) "HTTPS" else "HTTP"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
