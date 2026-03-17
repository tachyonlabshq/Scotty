package app.scotty.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            ScottyTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionGate {
                        BeamApp(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeamApp(viewModel: MainViewModel = viewModel()) {
    val selectedFiles  by viewModel.selectedFiles.collectAsState()
    val selectedTab    by viewModel.selectedTab.collectAsState()
    val nfcBeamStatus  by viewModel.nfcBeamStatus.collectAsState()
    val receivedFiles  by viewModel.receivedFiles.collectAsState()
    val activity = LocalContext.current as? android.app.Activity

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(selectedFiles.isNotEmpty()) {
        if (activity != null) {
            if (selectedFiles.isNotEmpty()) viewModel.enableNfcBeam(activity)
            else viewModel.disableNfcBeam(activity)
        }
    }
    DisposableEffect(Unit) {
        onDispose { activity?.let { viewModel.disableNfcBeam(it) } }
    }

    // Animated NFC status dot color
    val nfcDotColorTarget = when (nfcBeamStatus) {
        is NfcBeamStatus.Advertising, is NfcBeamStatus.Ready ->
            MaterialTheme.colorScheme.tertiary
        is NfcBeamStatus.Connecting, is NfcBeamStatus.Discovering ->
            MaterialTheme.colorScheme.secondary
        is NfcBeamStatus.Error ->
            MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val nfcDotColor by animateColorAsState(
        targetValue = nfcDotColorTarget,
        animationSpec = spring(),
        label = "nfc_dot_color"
    )

    val nfcStatusText = when (nfcBeamStatus) {
        is NfcBeamStatus.Idle        -> "idle"
        is NfcBeamStatus.Ready       -> "ready"
        is NfcBeamStatus.Advertising -> "advertising"
        is NfcBeamStatus.Discovering -> "discovering"
        is NfcBeamStatus.Connecting  -> "connecting"
        is NfcBeamStatus.Error       -> "error"
    }

    val nfcStatusLabel = when (nfcStatusText) {
        "idle"        -> "NFC Ready"
        "ready"       -> "Listening"
        "advertising" -> "Advertising"
        "discovering" -> "Discovering"
        "connecting"  -> "Connecting\u2026"
        "error"       -> "Error"
        else          -> nfcStatusText
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main screen content — fills entire window edge-to-edge
        when (selectedTab) {
            0    -> SendScreen(viewModel = viewModel, snackbarHostState = snackbarHostState)
            1    -> ReceiveScreen(viewModel = viewModel)
            else -> SettingsScreen(viewModel = viewModel)
        }

        // Floating NFC status pill — top center, only when not idle
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            AnimatedVisibility(
                visible = nfcBeamStatus !is NfcBeamStatus.Idle,
                enter = fadeIn(spring()) + slideInVertically(spring()) { -it },
                exit  = fadeOut(spring()) + slideOutVertically(spring()) { -it }
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 4.dp,
                    modifier = Modifier.semantics {
                        contentDescription = "NFC status: $nfcStatusLabel"
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(nfcDotColor, CircleShape)
                        )
                        AnimatedContent(
                            targetState = nfcStatusLabel,
                            transitionSpec = { fadeIn(spring()) togetherWith fadeOut(spring()) },
                            label = "nfc_status_label"
                        ) { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = nfcDotColor
                            )
                        }
                    }
                }
            }
        }

        // Floating toolbar — bottom center
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            FloatingToolbar(
                selectedTab   = selectedTab,
                fileCount     = selectedFiles.size,
                receivedCount = receivedFiles.size,
                onTabSelected = viewModel::selectTab
            )
        }

        // Snackbar — above the floating toolbar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
        ) {
            SnackbarHost(snackbarHostState)
        }
    }
}

// ── Floating pill toolbar ────────────────────────────────────────────

@Composable
fun FloatingToolbar(
    selectedTab: Int,
    fileCount: Int,
    receivedCount: Int,
    onTabSelected: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        val pillElevation by animateFloatAsState(
            targetValue = if (selectedTab == 0) 8f else 4f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "pill_elevation"
        )
        ElevatedCard(
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = pillElevation.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabItem(
                    icon     = Icons.Default.Send,
                    label    = "Send",
                    badge    = fileCount,
                    selected = selectedTab == 0,
                    onClick  = { onTabSelected(0) }
                )
                TabItem(
                    icon     = Icons.Default.CallReceived,
                    label    = "Receive",
                    badge    = receivedCount,
                    selected = selectedTab == 1,
                    onClick  = { onTabSelected(1) }
                )
                TabItem(
                    icon     = Icons.Default.Settings,
                    label    = "Settings",
                    badge    = 0,
                    selected = selectedTab == 2,
                    onClick  = { onTabSelected(2) }
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    icon: ImageVector,
    label: String,
    badge: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tab_scale"
    )
    val containerAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tab_bg"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(MaterialTheme.shapes.large)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = containerAlpha)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .semantics { contentDescription = "$label tab${if (selected) ", selected" else ""}" },
        contentAlignment = Alignment.Center
    ) {
        BadgedBox(badge = {
            if (badge > 0) Badge { Text(badge.toString()) }
        }) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
