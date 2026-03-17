package app.scotty.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.util.UUID

enum class TransferStatus {
    IDLE,
    ADVERTISING,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    TRANSFERRING,
    SUCCESS,
    ERROR
}

data class ProgressState(
    val fileName: String,
    val transferredBytes: Long,
    val totalBytes: Long
)

data class ReceivedFile(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val mimeType: String = "*/*",
    val receivedAtMs: Long = System.currentTimeMillis()
)

class NearbyTransferService(private val context: Context) {
    companion object {
        private const val TAG = "NearbyTransferService"
        // The service ID defines the specific "room" we operate in.
        val SERVICE_ID = "app.scotty.SERVICE_ID"
    }

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)

    private val _status = MutableStateFlow(TransferStatus.IDLE)
    val status: StateFlow<TransferStatus> = _status.asStateFlow()

    private val _connectedEndpointName = MutableStateFlow<String?>(null)
    val connectedEndpointName: StateFlow<String?> = _connectedEndpointName.asStateFlow()

    private val _receivedFiles = MutableStateFlow<List<ReceivedFile>>(emptyList())
    val receivedFiles: StateFlow<List<ReceivedFile>> = _receivedFiles.asStateFlow()

    data class ConnectionRequest(val endpointId: String, val deviceName: String)

    private val _pendingConnectionRequest = MutableStateFlow<ConnectionRequest?>(null)
    val pendingConnectionRequest: StateFlow<ConnectionRequest?> = _pendingConnectionRequest.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _transferProgress = MutableStateFlow<Map<Long, ProgressState>>(emptyMap())
    val transferProgress: StateFlow<Map<Long, ProgressState>> = _transferProgress.asStateFlow()

    // The name we show to other devices
    private var localEndpointName: String = "Device"

    // The specific endpoint ID we received from NFC (when receiving)
    private var targetEndpointId: String? = null

    // For sending files
    private var pendingUris: List<Uri> = emptyList()

    /** Use this callback for the overall connection lifecycle */
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val displayName = info.endpointName.substringBefore("||").ifEmpty { info.endpointName }
            Log.d(TAG, "Connection initiated with: $endpointId ($displayName).")
            _connectedEndpointName.value = displayName
            _status.value = TransferStatus.CONNECTING
            if (pendingUris.isNotEmpty()) {
                // Sender role: auto-accept
                connectionsClient.acceptConnection(endpointId, payloadCallback)
            } else {
                // Receiver role: show dialog
                _pendingConnectionRequest.value = ConnectionRequest(endpointId, displayName)
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connection successful with $endpointId")
                    _status.value = TransferStatus.CONNECTED
                    connectionsClient.stopAdvertising()
                    connectionsClient.stopDiscovery()

                    // If we have files to send, send them now
                    if (pendingUris.isNotEmpty()) {
                        sendPayloads(endpointId)
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.e(TAG, "Connection rejected by $endpointId")
                    handleError("Connection rejected")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.e(TAG, "Connection failed with $endpointId")
                    handleError("Connection failed")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            if (_status.value == TransferStatus.TRANSFERRING) {
                handleError("Disconnected during transfer")
            } else if (_status.value != TransferStatus.SUCCESS) {
                _status.value = TransferStatus.IDLE
            }
        }
    }

    /** Handles incoming payloads (files/metadata) and updates progress */
    private val payloadCallback = object : PayloadCallback() {
        private val incomingFileNames = mutableMapOf<Long, String>()

        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.FILE) {
                val payloadId = payload.id
                // When sending, we might send a BYTES payload first with the filename.
                // For simplicity here, we'll just track the generic transfer.
                val fileName = incomingFileNames[payloadId] ?: "File"
                _status.value = TransferStatus.TRANSFERRING

                val currentState = _transferProgress.value.toMutableMap()
                currentState[payloadId] = ProgressState(fileName, 0L, 1L) // Avoid div by 0
                _transferProgress.value = currentState
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            val currentState = _transferProgress.value.toMutableMap()
            val existing = currentState[payloadId]

            if (existing != null) {
                when (update.status) {
                    PayloadTransferUpdate.Status.IN_PROGRESS -> {
                        currentState[payloadId] = existing.copy(
                            transferredBytes = update.bytesTransferred,
                            totalBytes = update.totalBytes
                        )
                        _transferProgress.value = currentState
                        _status.value = TransferStatus.TRANSFERRING
                    }
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        currentState[payloadId] = existing.copy(
                            transferredBytes = update.totalBytes,
                            totalBytes = update.totalBytes
                        )
                        _transferProgress.value = currentState
                        // Record in received files list if we are the receiver
                        if (pendingUris.isEmpty()) {
                            val received = ReceivedFile(
                                fileName = existing.fileName,
                                filePath = existing.fileName,
                                sizeBytes = update.totalBytes
                            )
                            _receivedFiles.value = _receivedFiles.value + received
                        }
                        checkIfAllTransfersComplete()
                    }
                    PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                        handleError("Transfer failed or canceled")
                    }
                }
            }
        }
    }

    // --- SENDER FLOW ---

    /**
     * Call this when user selects files and wants to send (Sender role).
     * The app should start pulsing the NFC icon and call this.
     * Returns the endpoint ID representing THIS device, to be written to the NFC HCE.
     */
    fun startAdvertisingForSend(files: List<Uri>, deviceName: String, onEndpointIdCreated: (String) -> Unit) {
        pendingUris = files
        localEndpointName = deviceName

        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        _status.value = TransferStatus.ADVERTISING

        connectionsClient.startAdvertising(
            localEndpointName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            // Unofficial way to get local endpoint ID: we must wait for the other side to discover us,
            // or we use a custom generated ID in our NFC broadcast that the receiver then matches against the `endpointName` during discovery.
            // Since Connections API doesn't expose localEndpointId directly upon advertising,
            // a common trick is to encode a random UUID in the advertised `endpointName`.
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start advertising", e)
            handleError("Failed to start Nearby Connections")
        }

        // As a workaround for obtaining a unique ID to broadcast via NFC:
        // We will advertise our `localEndpointName` + a unique token.
        val uniqueToken = UUID.randomUUID().toString().take(6)
        val advertisedName = "$localEndpointName||$uniqueToken"

        // Restart advertising with the injected token
        connectionsClient.stopAdvertising()
        connectionsClient.startAdvertising(
            advertisedName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            onEndpointIdCreated(advertisedName)
        }.addOnFailureListener {
            handleError("Failed to start Nearby Connections")
        }
    }

    // --- RECEIVER FLOW ---

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Found endpoint: $endpointId (${info.endpointName})")

            // Is this the specific advertised name we tapped?
            if (info.endpointName == targetEndpointId) {
                Log.d(TAG, "Matched target endpoint! Connecting...")
                _status.value = TransferStatus.CONNECTING
                connectionsClient.requestConnection(
                    localEndpointName,
                    endpointId,
                    connectionLifecycleCallback
                ).addOnFailureListener { e ->
                    handleError("Failed to request connection")
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Lost endpoint: $endpointId")
        }
    }

    /**
     * Call this on the Receiving device when it reads the NFC tag containing the sender's advertised name.
     */
    fun startDiscoveryToReceive(targetAdvertisedName: String, deviceName: String) {
        targetEndpointId = targetAdvertisedName
        localEndpointName = deviceName
        _status.value = TransferStatus.DISCOVERING

        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnFailureListener { e ->
            Log.e(TAG, "Failed to start discovery", e)
            handleError("Failed to start discovery")
        }
    }

    // --- SHARED TRANSFER LOGIC ---

    private fun sendPayloads(endpointId: String) {
        if (pendingUris.isEmpty()) return

        _status.value = TransferStatus.TRANSFERRING
        val urisToSend = pendingUris.toList()
        pendingUris = emptyList()

        for (uri in urisToSend) {
            try {
                // Get file descriptor
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val payload = Payload.fromFile(pfd)
                    val payloadId = payload.id

                    // Resolve filename using ContentResolver (simplified here)
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    val fileName = cursor?.use {
                        it.moveToFirst()
                        it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                    } ?: "Unknown File"

                    // Setup progress tracking for sender
                    val currentState = _transferProgress.value.toMutableMap()
                    currentState[payloadId] = ProgressState(fileName, 0L, 0L)
                    _transferProgress.value = currentState

                    connectionsClient.sendPayload(endpointId, payload)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open file for payload: $uri", e)
                handleError("Failed to read file")
            }
        }
    }

    private fun checkIfAllTransfersComplete() {
        val currentProgress = _transferProgress.value
        val allComplete = currentProgress.values.isNotEmpty() && currentProgress.values.all {
            it.totalBytes > 0 && it.transferredBytes == it.totalBytes
        }

        if (allComplete) {
            _status.value = TransferStatus.SUCCESS
            // Disconnect and cleanup after success
            stopAll()
        }
    }

    private fun handleError(msg: String) {
        _errorMessage.value = msg
        _status.value = TransferStatus.ERROR
        stopAll()
    }

    fun acceptIncomingConnection(endpointId: String) {
        _pendingConnectionRequest.value = null
        connectionsClient.acceptConnection(endpointId, payloadCallback)
    }

    fun rejectIncomingConnection(endpointId: String) {
        _pendingConnectionRequest.value = null
        connectionsClient.rejectConnection(endpointId)
        _status.value = TransferStatus.IDLE
    }

    fun clearError() {
        _errorMessage.value = null
        if (_status.value == TransferStatus.ERROR) {
            _status.value = TransferStatus.IDLE
        }
    }

    fun reset() {
        _transferProgress.value = emptyMap()
        _status.value = TransferStatus.IDLE
        targetEndpointId = null
        pendingUris = emptyList()
        stopAll()
    }

    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
    }
}
