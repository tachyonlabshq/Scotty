package org.localsend.localsend_app.ui

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.Build
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val nearbyTransferService = NearbyTransferService(application)

    val transferStatus = nearbyTransferService.status
    val transferProgress = nearbyTransferService.transferProgress
    val transferError = nearbyTransferService.errorMessage

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

    private val _nfcBeamStatus = MutableStateFlow<NfcBeamStatus>(NfcBeamStatus.Idle)
    val nfcBeamStatus: StateFlow<NfcBeamStatus> = _nfcBeamStatus.asStateFlow()

    val nfcReaderManager = NfcReaderManager(
        onBeamSent = {
            viewModelScope.launch {
                _nfcBeamStatus.value = NfcBeamStatus.Connecting("Target Device")
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
            // When the SENDER (us) taps the RECEIVER, the NfcReaderManager will send this message.
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
        nearbyTransferService.stopAll()
    }
}
