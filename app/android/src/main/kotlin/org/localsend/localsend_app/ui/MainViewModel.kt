package org.localsend.localsend_app.ui

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.localsend.localsend_app.model.AppSettings
import org.localsend.localsend_app.service.NearbyTransferService
import org.localsend.localsend_app.service.NfcBeamMessage
import org.localsend.localsend_app.service.NfcMessageHub
import org.localsend.localsend_app.service.NfcReaderManager
import org.localsend.localsend_app.service.TransferStatus
import java.util.UUID

sealed class NfcBeamStatus {
    object Idle : NfcBeamStatus()
    object Ready : NfcBeamStatus()
    // When the sender is advertising its Endpoint ID and waiting for NFC tap
    data class Advertising(val endpointToken: String) : NfcBeamStatus()
    // When the receiver is discovering because it was tapped
    object Discovering : NfcBeamStatus()
    data class Connecting(val deviceName: String) : NfcBeamStatus()
    // When an error occurs
    data class Error(val message: String) : NfcBeamStatus()
}

private val android.content.Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scotty_settings")

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        val ALIAS_KEY        = stringPreferencesKey("alias")
        val DARK_MODE_KEY    = booleanPreferencesKey("dark_mode")
        val FINGERPRINT_KEY  = stringPreferencesKey("fingerprint")
        val HISTORY_KEY      = stringPreferencesKey("received_history")
        private const val HISTORY_SEP = "\u001F" // unit separator
    }

    private val dataStore = application.dataStore

    private val nearbyTransferService = NearbyTransferService(application)

    val transferStatus = nearbyTransferService.status
    val transferProgress = nearbyTransferService.transferProgress
    val transferError = nearbyTransferService.errorMessage

    val pendingConnectionRequest = nearbyTransferService.pendingConnectionRequest

    // Merged: history from DataStore + live files from this session
    private val _receivedFiles = MutableStateFlow<List<org.localsend.localsend_app.service.ReceivedFile>>(emptyList())
    val receivedFiles: StateFlow<List<org.localsend.localsend_app.service.ReceivedFile>> = _receivedFiles.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<Pair<Uri, String>>>(emptyList())
    val selectedFiles: StateFlow<List<Pair<Uri, String>>> = _selectedFiles.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings(
        alias = getDeviceName(),
        deviceModel = Build.MODEL,
        deviceType = "mobile"
    ))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _quickSave = MutableStateFlow(false)
    val quickSave: StateFlow<Boolean> = _quickSave.asStateFlow()

    private val _nfcBeamStatus = MutableStateFlow<NfcBeamStatus>(NfcBeamStatus.Idle)
    val nfcBeamStatus: StateFlow<NfcBeamStatus> = _nfcBeamStatus.asStateFlow()

    val nfcReaderManager = NfcReaderManager(
        scope = viewModelScope,
        onReaderEnabled = {
            // Emit Ready when NFC reader mode is successfully activated
            viewModelScope.launch {
                if (_nfcBeamStatus.value !is NfcBeamStatus.Advertising) {
                    _nfcBeamStatus.value = NfcBeamStatus.Ready
                }
            }
        },
        onBeamSent = {
            viewModelScope.launch {
                val deviceName = nearbyTransferService.connectedEndpointName.value
                    ?: "Device"
                _nfcBeamStatus.value = NfcBeamStatus.Connecting(deviceName)
            }
        },
        onError = { error ->
            viewModelScope.launch {
                _nfcBeamStatus.value = NfcBeamStatus.Error(error)
                nearbyTransferService.reset()
            }
        }
    )

    init {
        // Load persisted settings from DataStore
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                val savedAlias = prefs[ALIAS_KEY]
                val savedDarkMode = prefs[DARK_MODE_KEY] ?: false
                var savedFingerprint = prefs[FINGERPRINT_KEY] ?: ""

                // Generate fingerprint on first run
                if (savedFingerprint.isEmpty()) {
                    savedFingerprint = UUID.randomUUID().toString()
                    dataStore.edit { it[FINGERPRINT_KEY] = savedFingerprint }
                }

                _settings.value = _settings.value.copy(
                    alias = savedAlias ?: getDeviceName(),
                    fingerprint = savedFingerprint
                )
                _isDarkMode.value = savedDarkMode

                // Load received file history (newline-separated "name|path|size|mime|ts")
                val historyRaw = prefs[HISTORY_KEY] ?: ""
                if (historyRaw.isNotEmpty()) {
                    val historical = historyRaw.split(HISTORY_SEP).mapNotNull { line ->
                        val parts = line.split("|")
                        if (parts.size >= 5) {
                            org.localsend.localsend_app.service.ReceivedFile(
                                fileName     = parts[0],
                                filePath     = parts[1],
                                sizeBytes    = parts[2].toLongOrNull() ?: 0L,
                                mimeType     = parts[3],
                                receivedAtMs = parts[4].toLongOrNull() ?: 0L
                            )
                        } else null
                    }
                    // Merge: historical first, then any live session files already in _receivedFiles
                    _receivedFiles.value = (historical + _receivedFiles.value)
                        .distinctBy { it.receivedAtMs }
                        .sortedByDescending { it.receivedAtMs }
                }
            }
        }

        // Observe new files from NearbyTransferService and persist them
        viewModelScope.launch {
            nearbyTransferService.receivedFiles.collect { serviceFiles ->
                val current = _receivedFiles.value
                val newFiles = serviceFiles.filter { sf -> current.none { it.receivedAtMs == sf.receivedAtMs } }
                if (newFiles.isNotEmpty()) {
                    val merged = (newFiles + current).sortedByDescending { it.receivedAtMs }
                    _receivedFiles.value = merged
                    persistHistory(merged)
                }
            }
        }

        // Observers for state events
        viewModelScope.launch {
            NfcMessageHub.incomingMessages.collect { msg ->
                if (msg.action == "discover") {
                    _nfcBeamStatus.value = NfcBeamStatus.Discovering
                    nearbyTransferService.startDiscoveryToReceive(
                        targetAdvertisedName = msg.targetEndpoint,
                        deviceName = _settings.value.alias
                    )
                }
            }
        }

        viewModelScope.launch {
            transferStatus.collect { status ->
                when (status) {
                    TransferStatus.ERROR -> {
                        _nfcBeamStatus.value = NfcBeamStatus.Error(transferError.value ?: "Unknown error")
                    }
                    TransferStatus.SUCCESS -> {
                        _nfcBeamStatus.value = NfcBeamStatus.Idle
                        if (pendingToSend) {
                            // Clear files after send completes
                            clearFiles()
                            pendingToSend = false
                        }
                    }
                    TransferStatus.IDLE -> {
                        if (_nfcBeamStatus.value !is NfcBeamStatus.Ready) {
                             _nfcBeamStatus.value = NfcBeamStatus.Idle
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private var pendingToSend = false

    /**
     * Used by the SENDER. When the user selects files and goes to the Send tab,
     * they are "ready to beam". This kicks off advertising to get an endpoint token.
     */
    fun enableNfcBeam(activity: Activity) {
        if (_selectedFiles.value.isEmpty()) {
            _nfcBeamStatus.value = NfcBeamStatus.Ready
            return
        }

        pendingToSend = true
        _nfcBeamStatus.value = NfcBeamStatus.Ready

        val uris = _selectedFiles.value.map { it.first }
        nearbyTransferService.startAdvertisingForSend(uris, _settings.value.alias) { endpointToken ->
            _nfcBeamStatus.value = NfcBeamStatus.Advertising(endpointToken)

            // Now that we have the endpoint token, we can ACTIVATE reader mode.
            val message = NfcBeamMessage(
                action = "discover",
                targetEndpoint = endpointToken,
                deviceName = _settings.value.alias
            )
            nfcReaderManager.enable(activity, message)
        }
    }

    fun disableNfcBeam(activity: Activity) {
        nfcReaderManager.disable(activity)
        nearbyTransferService.stopAll()
        pendingToSend = false
        if (_nfcBeamStatus.value !is NfcBeamStatus.Idle) {
            _nfcBeamStatus.value = NfcBeamStatus.Idle
        }
    }

    fun resetNfcBeamStatus() {
        _nfcBeamStatus.value = NfcBeamStatus.Idle
        nearbyTransferService.clearError()
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun addFiles(files: List<Pair<Uri, String>>) {
        _selectedFiles.value = _selectedFiles.value + files
    }

    fun removeFile(index: Int) {
        _selectedFiles.value = _selectedFiles.value.toMutableList().apply {
            if (index in indices) removeAt(index)
        }
    }

    fun clearFiles() {
        _selectedFiles.value = emptyList()
    }

    fun toggleQuickSave() {
        _quickSave.value = !_quickSave.value
    }

    fun updateAlias(alias: String) {
        _settings.value = _settings.value.copy(alias = alias)
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[ALIAS_KEY] = alias }
        }
    }

    fun updateDarkMode(dark: Boolean) {
        _isDarkMode.value = dark
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[DARK_MODE_KEY] = dark }
        }
    }

    fun updatePort(port: Int) {
        _settings.value = _settings.value.copy(port = port)
    }

    fun updateTheme(theme: String) {
        _settings.value = _settings.value.copy(theme = theme)
    }

    private fun getDeviceName(): String {
        return try {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            if (model.startsWith(manufacturer, ignoreCase = true)) {
                model.replaceFirstChar { it.uppercase() }
            } else {
                "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
            }
        } catch (e: Exception) {
            "Android Device"
        }
    }

    fun acceptConnection(endpointId: String) {
        nearbyTransferService.acceptIncomingConnection(endpointId)
    }

    fun rejectConnection(endpointId: String) {
        nearbyTransferService.rejectIncomingConnection(endpointId)
        _nfcBeamStatus.value = NfcBeamStatus.Idle
    }

    fun clearReceivedHistory() {
        _receivedFiles.value = emptyList()
        viewModelScope.launch {
            dataStore.edit { it.remove(HISTORY_KEY) }
        }
    }

    private fun persistHistory(files: List<org.localsend.localsend_app.service.ReceivedFile>) {
        viewModelScope.launch {
            val encoded = files.joinToString(HISTORY_SEP) { f ->
                "${f.fileName}|${f.filePath}|${f.sizeBytes}|${f.mimeType}|${f.receivedAtMs}"
            }
            dataStore.edit { it[HISTORY_KEY] = encoded }
        }
    }

    override fun onCleared() {
        super.onCleared()
        nearbyTransferService.stopAll()
    }
}
