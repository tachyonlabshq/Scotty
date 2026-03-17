package org.localsend.localsend_app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val darkMode by viewModel.isDarkMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── DEVICE section ────────────────────────────────────────────
        SectionHeader("Device")

        ListItem(
            headlineContent = { Text("Device Name") },
            supportingContent = {
                OutlinedTextField(
                    value = settings.alias,
                    onValueChange = { viewModel.updateAlias(it) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    label = { Text("Alias") }
                )
            }
        )

        ListItem(
            headlineContent = { Text("Model") },
            trailingContent = {
                Text(
                    settings.deviceModel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── APPEARANCE section ────────────────────────────────────────
        SectionHeader("Appearance")

        ListItem(
            headlineContent = { Text("Dark Mode") },
            supportingContent = { Text("Override system theme") },
            trailingContent = {
                Switch(
                    checked = darkMode,
                    onCheckedChange = { viewModel.updateDarkMode(it) }
                )
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── ABOUT section ─────────────────────────────────────────────
        SectionHeader("About")

        ListItem(
            headlineContent = { Text("Scotty") },
            supportingContent = { Text("Version 1.0.0") },
            leadingContent = {
                Icon(Icons.Default.Info, contentDescription = null)
            }
        )

        ListItem(
            headlineContent = { Text("Built with") },
            supportingContent = { Text("NFC + Google Nearby Connections") },
            leadingContent = {
                Icon(Icons.Default.Nfc, contentDescription = null)
            }
        )

        ListItem(
            headlineContent = { Text("GitHub") },
            supportingContent = { Text("View source code") },
            leadingContent = {
                Icon(Icons.Default.Code, contentDescription = null)
            },
            trailingContent = {
                Icon(Icons.Default.OpenInNew, contentDescription = "Open in browser")
            }
        )

        Spacer(modifier = Modifier.height(88.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
