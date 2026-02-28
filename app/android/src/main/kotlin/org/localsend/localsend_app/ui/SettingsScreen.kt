package org.localsend.localsend_app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    
    var alias by remember { mutableStateOf(settings.alias) }
    var port by remember { mutableStateOf(settings.port.toString()) }
    var showAliasDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Device section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Device",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                ListItem(
                    headlineContent = { Text("Device Name") },
                    supportingContent = { Text(alias) },
                    leadingContent = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                HorizontalDivider()
                
                ListItem(
                    headlineContent = { Text("Device Model") },
                    supportingContent = { Text(settings.deviceModel) },
                    leadingContent = {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Network section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Network",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                ListItem(
                    headlineContent = { Text("Port") },
                    supportingContent = { Text(port) },
                    leadingContent = {
                        Icon(Icons.Default.Router, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                HorizontalDivider()
                
                ListItem(
                    headlineContent = { Text("Protocol") },
                    supportingContent = { Text(if (settings.https) "HTTPS" else "HTTP") },
                    leadingContent = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.https,
                            onCheckedChange = { viewModel.updateTheme(if (it) "https" else "http") }
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Receive section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Receive",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                ListItem(
                    headlineContent = { Text("Save to Gallery") },
                    supportingContent = { Text("Automatically save received images/videos") },
                    leadingContent = {
                        Icon(Icons.Default.Photo, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.saveToGallery,
                            onCheckedChange = { /* Toggle save to gallery */ }
                        )
                    }
                )
                
                HorizontalDivider()
                
                ListItem(
                    headlineContent = { Text("Save History") },
                    supportingContent = { Text("Keep history of received files") },
                    leadingContent = {
                        Icon(Icons.Default.History, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.saveToHistory,
                            onCheckedChange = { /* Toggle save history */ }
                        )
                    }
                )
                
                HorizontalDivider()
                
                ListItem(
                    headlineContent = { Text("Auto Accept") },
                    supportingContent = { Text("Automatically accept incoming files") },
                    leadingContent = {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.autoFinish,
                            onCheckedChange = { /* Toggle auto accept */ }
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Appearance section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = { Text(settings.theme.replaceFirstChar { it.uppercase() }) },
                    leadingContent = {
                        Icon(Icons.Default.Palette, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                HorizontalDivider()
                
                ListItem(
                    headlineContent = { Text("Animations") },
                    supportingContent = { Text("Enable animations") },
                    leadingContent = {
                        Icon(Icons.Default.Animation, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.enableAnimations,
                            onCheckedChange = { /* Toggle animations */ }
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // About section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                ListItem(
                    headlineContent = { Text("LocalSend") },
                    supportingContent = { Text("Version 1.17.0") },
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null)
                    }
                )
                
                HorizontalDivider()
                
                ListItem(
                    headlineContent = { Text("Protocol Version") },
                    supportingContent = { Text("2.1") },
                    leadingContent = {
                        Icon(Icons.Default.Code, contentDescription = null)
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
    
    // Alias dialog
    if (showAliasDialog) {
        AlertDialog(
            onDismissRequest = { showAliasDialog = false },
            title = { Text("Device Name") },
            text = {
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateAlias(alias)
                        showAliasDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAliasDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Port dialog
    if (showPortDialog) {
        AlertDialog(
            onDismissRequest = { showPortDialog = false },
            title = { Text("Port") },
            text = {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Port Number") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        port.toIntOrNull()?.let { viewModel.updatePort(it) }
                        showPortDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPortDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
