package org.localsend.localsend_app.service

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.localsend.localsend_app.model.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Sends files to a target LocalSend device using the v2 protocol:
 *   1. POST /api/localsend/v2/prepare-upload  → get upload tokens per file
 *   2. POST /api/localsend/v2/upload?fileId=X&token=Y  → stream each file
 */
import android.util.Log // Added for logging

class FileTransferClient(private val context: Context) {

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _transferProgress = MutableStateFlow<Map<String, TransferState>>(emptyMap())
    val transferProgress: StateFlow<Map<String, TransferState>> = _transferProgress.asStateFlow()

    private val _isTransferring = MutableStateFlow(false)
    val isTransferring: StateFlow<Boolean> = _isTransferring.asStateFlow()

    private var currentJob: Job? = null

    fun sendFiles(
        targetDevice: Device,
        files: List<Pair<Uri, String>>,
        onProgress: (String, Long, Long) -> Unit = { _, _, _ -> }
    ) {
        Log.d("FileTransferClient", "sendFiles called for ${files.size} files to ${targetDevice.alias}")
        if (_isTransferring.value) {
            Log.d("FileTransferClient", "Already transferring, ignoring.")
            return
        }

        currentJob = scope.launch {
            Log.d("FileTransferClient", "Coroutine started for sending files.")
            _isTransferring.value = true
            try {
                executeTransfer(targetDevice, files, onProgress)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isTransferring.value = false
            }
        }
    }

    private suspend fun executeTransfer(
        targetDevice: Device,
        files: List<Pair<Uri, String>>,
        onProgress: (String, Long, Long) -> Unit
    ) {
        val protocol = if (targetDevice.https) "https" else "http"
        val base = "$protocol://${targetDevice.ip}:${targetDevice.port}"

        // Build file metadata for prepare-upload
        val filesMeta = mutableMapOf<String, Any>()
        val fileIdToUri = mutableMapOf<String, Pair<Uri, String>>()

        for ((uri, fileName) in files) {
            val fileId = UUID.randomUUID().toString()
            val fileSize = getFileSize(uri)
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

            filesMeta[fileId] = mapOf(
                "id" to fileId,
                "fileName" to fileName,
                "size" to fileSize,
                "fileType" to mimeType
            )
            fileIdToUri[fileId] = uri to fileName

            // Set initial state
            updateProgress(fileId, TransferState(
                fileId = fileId,
                fileName = fileName,
                totalBytes = fileSize,
                status = TransferStatus.PENDING
            ))
        }

        // Step 1: prepare-upload
        val prepareBody = gson.toJson(mapOf(
            "info" to mapOf(
                "alias" to "Scotty",
                "version" to "2.1",
                "deviceModel" to android.os.Build.MODEL,
                "deviceType" to "mobile",
                "fingerprint" to UUID.randomUUID().toString()
            ),
            "files" to filesMeta
        ))

        val tokens: Map<String, String> = try {
            val conn = openConnection("$base${Constants.ApiRoutes.PREPARE_UPLOAD_V2}", "POST")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setFixedLengthStreamingMode(prepareBody.toByteArray().size)
            conn.outputStream.use { it.write(prepareBody.toByteArray()) }
            val code = conn.responseCode
            if (code == 204) {
                // Receiver is in quick-save mode — no tokens needed, upload directly with empty tokens
                fileIdToUri.keys.associateWith { "" }
            } else if (code == 200) {
                val resp = conn.inputStream.bufferedReader().readText()
                @Suppress("UNCHECKED_CAST")
                gson.fromJson(resp, Map::class.java) as Map<String, String>
            } else {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                throw IOException("prepare-upload failed: $code $err")
            }
        } catch (e: Exception) {
            // If prepare-upload is not supported, upload directly (simple mode)
            fileIdToUri.keys.associateWith { "" }
        }

        // Step 2: upload each file
        for ((fileId, uriPair) in fileIdToUri) {
            val (uri, fileName) = uriPair
            val token = tokens[fileId] ?: ""
            uploadFile(base, fileId, token, uri, fileName, onProgress)
        }
    }

    private suspend fun uploadFile(
        base: String,
        fileId: String,
        token: String,
        uri: Uri,
        fileName: String,
        onProgress: (String, Long, Long) -> Unit
    ) {
        val fileSize = getFileSize(uri)
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        updateProgress(fileId, TransferState(
            fileId = fileId,
            fileName = fileName,
            totalBytes = fileSize,
            status = TransferStatus.IN_PROGRESS
        ))

        try {
            val urlStr = buildString {
                append("$base${Constants.ApiRoutes.UPLOAD_V2}?fileId=${encode(fileId)}")
                if (token.isNotEmpty()) append("&token=${encode(token)}")
            }
            val conn = openConnection(urlStr, "POST")
            conn.setRequestProperty("Content-Type", mimeType)
            if (fileSize > 0) {
                conn.setFixedLengthStreamingMode(fileSize)
            } else {
                conn.setChunkedStreamingMode(8192)
            }

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open: $fileName")

            var totalSent = 0L
            conn.outputStream.use { out ->
                inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        totalSent += bytesRead
                        val safeTotalBytes = if (fileSize > 0) fileSize else totalSent
                        updateProgress(fileId, TransferState(
                            fileId = fileId,
                            fileName = fileName,
                            totalBytes = safeTotalBytes,
                            transferredBytes = totalSent,
                            status = TransferStatus.IN_PROGRESS
                        ))
                        onProgress(fileId, totalSent, safeTotalBytes)
                    }
                }
            }

            val responseCode = conn.responseCode
            val finalBytes = if (fileSize > 0) fileSize else totalSent
            if (responseCode in 200..204) {
                updateProgress(fileId, TransferState(
                    fileId = fileId,
                    fileName = fileName,
                    totalBytes = finalBytes,
                    transferredBytes = finalBytes,
                    status = TransferStatus.COMPLETED
                ))
            } else {
                throw IOException("Upload returned $responseCode")
            }
            conn.disconnect()

        } catch (e: Exception) {
            e.printStackTrace()
            updateProgress(fileId, TransferState(
                fileId = fileId,
                fileName = fileName,
                totalBytes = maxOf(fileSize, 1L),
                transferredBytes = 0,
                status = TransferStatus.FAILED,
                error = e.message
            ))
        }
    }

    /** Get actual file size via ContentResolver metadata (more reliable than available()). */
    private fun getFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) cursor.getLong(idx) else 0L
                } else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun openConnection(urlStr: String, method: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        return conn
    }

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun updateProgress(fileId: String, state: TransferState) {
        _transferProgress.value = _transferProgress.value.toMutableMap().apply {
            put(fileId, state)
        }
    }

    fun cancelTransfer() {
        currentJob?.cancel()
        _isTransferring.value = false
    }

    fun clearTransfers() {
        _transferProgress.value = emptyMap()
    }

    fun cleanup() {
        currentJob?.cancel()
        scope.cancel()
    }
}
