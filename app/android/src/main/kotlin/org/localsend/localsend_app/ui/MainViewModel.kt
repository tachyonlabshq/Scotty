package org.localsend.localsend_app.ui

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.localsend.localsend_app.model.*
import org.localsend.localsend_app.service.FileServerService
import org.localsend.localsend_app.service.FileTransferClient
import org.localsend.localsend_app.service.NetworkDiscoveryService
import org.localsend.localsend_app.service.NfcDevicePayload
import org.localsend.localsend_app.service.NfcHceService
import org.localsend.localsend_app.service.NfcReaderManager
import android.net.Uri

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val gson = Gson()
    private val networkDiscovery = NetworkDiscoveryService(application)
    private val fileServer = FileServerService(application)
    private val fileTransferClient = FileTransferClient(application)

    val nearbyDevices = networkDiscovery.nearbyDevices
    val isScanning = networkDiscovery.isScanning

    val isServerRunning = fileServer.isServerRunning
    val localIp = fileServer.localIp

    val transferProgress = fileTransferClient.transferProgress
    val isTransferring = fileTransferClient.isTransferring

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

    private val _quickSave = MutableStateFlow(false)
    val quickSave: StateFlow<Boolean> = _quickSave.asStateFlow()

    // --- NFC Beam state ---
    private val _nfcBeamStatus = MutableStateFlow<NfcBeamStatus>(NfcBeamStatus.Idle)
    val nfcBeamStatus: StateFlow<NfcBeamStatus> = _nfcBeamStatus.asStateFlow()

    val nfcReaderManager = NfcReaderManager(
        onDeviceDiscovered = { device ->
            viewModelScope.launch {
                _nfcBeamStatus.value = NfcBeamStatus.Connecting(device)
                sendToDevice(device)
            }
        },
        onError = { error ->
            viewModelScope.launch {
                _nfcBeamStatus.value = NfcBeamStatus.Error(error)
            }
        }
    )

    init {
        startServer()
        startDiscovery()
        // Sync device info to both NFC HCE and HTTP /info endpoint
        updateNfcDeviceInfo()
        updateServerDeviceInfo()
        // Refresh when localIp changes
        viewModelScope.launch {
            localIp.collect { updateNfcDeviceInfo() }
        }
        // Refresh when settings change
        viewModelScope.launch {
            _settings.collect { updateServerDeviceInfo() }
        }
    }

    private fun startServer() {
        val localIp = networkDiscovery.getLocalIp()
        fileServer.setLocalIp(localIp ?: "127.0.0.1")
        fileServer.startServer(_settings.value.port) { _ -> }
    }

    private fun updateServerDeviceInfo() {
        val s = _settings.value
        fileServer.updateDeviceInfo(
            alias = s.alias,
            model = s.deviceModel,
            fp = s.fingerprint
        )
    }

    private fun startDiscovery() {
        networkDiscovery.startDiscovery()
    }

    /** Update the NFC HCE service's device payload so tapping devices get fresh info. */
    private fun updateNfcDeviceInfo() {
        val ip = networkDiscovery.getLocalIp() ?: return
        val settings = _settings.value
        val payload = NfcDevicePayload(
            ip = ip,
            port = settings.port,
            https = false,
            fingerprint = settings.fingerprint,
            alias = settings.alias,
            deviceModel = settings.deviceModel
        )
        NfcHceService.deviceInfoJson = gson.toJson(payload)
    }

    fun enableNfcBeam(activity: Activity) {
        _nfcBeamStatus.value = NfcBeamStatus.Ready
        nfcReaderManager.enable(activity)
    }

    fun disableNfcBeam(activity: Activity) {
        nfcReaderManager.disable(activity)
        if (_nfcBeamStatus.value is NfcBeamStatus.Ready) {
            _nfcBeamStatus.value = NfcBeamStatus.Idle
        }
    }

    fun resetNfcBeamStatus() {
        _nfcBeamStatus.value = NfcBeamStatus.Idle
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
    
    fun sendToDevice(device: Device) {
        if (_selectedFiles.value.isEmpty()) return
        
        fileTransferClient.sendFiles(
            targetDevice = device,
            files = _selectedFiles.value,
            onProgress = { fileId, sent, total ->
                // Update progress UI
            }
        )
    }
    
    fun toggleQuickSave() {
        _quickSave.value = !_quickSave.value
    }
    
    fun updateAlias(alias: String) {
        _settings.value = _settings.value.copy(alias = alias)
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
    
    override fun onCleared() {
        super.onCleared()
        networkDiscovery.cleanup()
        fileServer.cleanup()
        fileTransferClient.cleanup()
    }
}
