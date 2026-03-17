package app.scotty.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker

// Permissions required for Nearby Connections + file access
private fun requiredPermissions(): Array<String> {
    val perms = mutableListOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NEARBY_WIFI_DEVICES
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        perms += Manifest.permission.READ_MEDIA_IMAGES
        perms += Manifest.permission.READ_MEDIA_VIDEO
        perms += Manifest.permission.READ_MEDIA_AUDIO
    } else {
        perms += Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return perms.toTypedArray()
}

@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current

    fun allGranted(): Boolean = requiredPermissions().all { perm ->
        ContextCompat.checkSelfPermission(context, perm) == PermissionChecker.PERMISSION_GRANTED
    }

    var permissionsGranted by remember { mutableStateOf(allGranted()) }
    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        permissionsGranted = granted
        showRationale = !granted
    }

    if (permissionsGranted) {
        content()
    } else {
        PermissionsRationaleScreen(
            showDeniedMessage = showRationale,
            onRequestPermissions = {
                showRationale = false
                launcher.launch(requiredPermissions())
            }
        )
    }
}

@Composable
private fun PermissionsRationaleScreen(
    showDeniedMessage: Boolean,
    onRequestPermissions: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Concentric ring hero with NFC icon — static decorative rings
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxR = size.minDimension / 2f
                    listOf(0.35f, 0.57f, 0.78f, 1.0f).forEachIndexed { i, frac ->
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.10f - i * 0.02f),
                            radius = maxR * frac,
                            center = center,
                            style = Stroke(width = (2f - i * 0.3f).dp.toPx())
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp,
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Nfc,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Scotty needs your permission",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Nearby, Bluetooth, and storage access are needed for NFC file transfer",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // Permission chip list
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PermissionChipRow(Icons.Default.Bluetooth, "Bluetooth Scan & Connect")
                PermissionChipRow(Icons.Default.Wifi, "Nearby Wi-Fi Devices")
                PermissionChipRow(Icons.Default.LocationOn, "Location (for Nearby discovery)")
                PermissionChipRow(Icons.Default.Folder, "Storage / Media Access")
            }

            Spacer(Modifier.height(20.dp))

            AnimatedVisibility(visible = showDeniedMessage, enter = fadeIn(), exit = fadeOut()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = "Some permissions were denied. Scotty needs these to work.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Floating grant button above nav bar
        ExtendedFloatingActionButton(
            onClick = onRequestPermissions,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .semantics { contentDescription = "Grant required permissions" },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Icon(Icons.Default.Nfc, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Grant Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PermissionChipRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        modifier = Modifier.fillMaxWidth()
    )
}
