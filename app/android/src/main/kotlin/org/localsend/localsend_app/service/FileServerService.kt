package org.localsend.localsend_app.service

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.localsend.localsend_app.model.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * HTTP server implementing LocalSend v2 protocol.
 * Handles:
 *   GET /api/localsend/v2/info     → device info JSON
 *   POST /api/localsend/v2/prepare-upload → return session tokens per file
 *   POST /api/localsend/v2/upload?fileId=X&token=Y → receive and save file
 */
class FileServerService(private val context: Context) {

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _incomingFiles = MutableStateFlow<List<TransferState>>(emptyList())
    val incomingFiles: StateFlow<List<TransferState>> = _incomingFiles.asStateFlow()

    private val _localIp = MutableStateFlow<String?>(null)
    val localIp: StateFlow<String?> = _localIp.asStateFlow()

    private var onFileReceived: ((FileInfo) -> Unit)? = null
    private var deviceAlias: String = Build.MODEL
    private var deviceModel: String = Build.MODEL
    private var fingerprint: String = ""

    // Map of fileId -> upload token (populated during prepare-upload)
    private val pendingTokens = ConcurrentHashMap<String, String>()
    // Map of fileId -> file metadata
    private val pendingFiles = ConcurrentHashMap<String, Map<String, Any>>()

    fun updateDeviceInfo(alias: String, model: String, fp: String) {
        deviceAlias = alias
        deviceModel = model
        fingerprint = fp
    }

    fun startServer(port: Int = Constants.DEFAULT_PORT, onFileReceived: (FileInfo) -> Unit) {
        if (isRunning) return
        this.onFileReceived = onFileReceived
        isRunning = true

        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                _isServerRunning.value = true

                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        scope.launch { handleClient(client) }
                    } catch (e: Exception) {
                        if (isRunning) delay(100)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isServerRunning.value = false
            }
        }
    }

    fun stopServer() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
        _isServerRunning.value = false
    }

    private suspend fun handleClient(client: Socket) {
        try {
            val inputStream = client.getInputStream()
            val outputStream = client.getOutputStream()

            // Read request line
            val rawRequest = StringBuilder()
            val buffer = ByteArray(1)
            var prevByte = 0.toByte()
            var headerEnd = false
            val headerBytes = ByteArrayOutputStream()

            // Read until double CRLF
            while (!headerEnd) {
                val n = inputStream.read(buffer)
                if (n == -1) break
                headerBytes.write(buffer, 0, n)
                val bytes = headerBytes.toByteArray()
                val len = bytes.size
                if (len >= 4) {
                    val end = bytes[len - 4] == '\r'.code.toByte() &&
                            bytes[len - 3] == '\n'.code.toByte() &&
                            bytes[len - 2] == '\r'.code.toByte() &&
                            bytes[len - 1] == '\n'.code.toByte()
                    if (end) headerEnd = true
                }
                // Safety: don't read more than 64KB of headers
                if (headerBytes.size() > 65536) break
            }

            val headerSection = headerBytes.toString(Charsets.UTF_8)
            val lines = headerSection.split("\r\n")
            if (lines.isEmpty()) return

            val requestLine = lines[0]
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val rawPath = parts[1]
            val pathAndQuery = rawPath.split("?", limit = 2)
            val path = pathAndQuery[0]
            val queryString = if (pathAndQuery.size > 1) pathAndQuery[1] else ""
            val queryParams = parseQueryString(queryString)

            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size) {
                val line = lines[i]
                val colonIdx = line.indexOf(": ")
                if (colonIdx > 0) {
                    headers[line.substring(0, colonIdx).lowercase()] = line.substring(colonIdx + 2).trim()
                }
            }

            val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L

            when {
                path == Constants.ApiRoutes.INFO_V1 || path == Constants.ApiRoutes.INFO_V2 -> {
                    handleInfoRequest(outputStream)
                }
                (path == Constants.ApiRoutes.PREPARE_UPLOAD_V1 || path == Constants.ApiRoutes.PREPARE_UPLOAD_V2) && method == "POST" -> {
                    handlePrepareUpload(inputStream, outputStream, contentLength)
                }
                (path == Constants.ApiRoutes.UPLOAD_V1 || path == Constants.ApiRoutes.UPLOAD_V2) && method == "POST" -> {
                    val fileId = queryParams["fileId"] ?: ""
                    val token = queryParams["token"] ?: ""
                    handleUpload(inputStream, outputStream, headers, fileId, token, contentLength)
                }
                else -> {
                    outputStream.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleInfoRequest(out: OutputStream) {
        val info = mapOf(
            "alias" to deviceAlias,
            "version" to Constants.PROTOCOL_VERSION,
            "deviceModel" to deviceModel,
            "deviceType" to "mobile",
            "fingerprint" to fingerprint,
            "download" to true
        )
        sendJson(out, gson.toJson(info))
    }

    private suspend fun handlePrepareUpload(inputStream: InputStream, out: OutputStream, contentLength: Long) {
        // Read body
        val bodyBytes = readBody(inputStream, contentLength)
        val body = String(bodyBytes, Charsets.UTF_8)

        // Parse files from request
        val tokens = mutableMapOf<String, String>()
        try {
            @Suppress("UNCHECKED_CAST")
            val req = gson.fromJson(body, Map::class.java)
            @Suppress("UNCHECKED_CAST")
            val files = req["files"] as? Map<String, Any> ?: emptyMap()

            for ((fileId, _) in files) {
                val token = java.util.UUID.randomUUID().toString().replace("-", "")
                tokens[fileId] = token
                pendingTokens[fileId] = token
                @Suppress("UNCHECKED_CAST")
                pendingFiles[fileId] = files[fileId] as? Map<String, Any> ?: emptyMap()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        sendJson(out, gson.toJson(tokens))
    }

    private suspend fun handleUpload(
        inputStream: InputStream,
        out: OutputStream,
        headers: Map<String, String>,
        fileId: String,
        token: String,
        contentLength: Long
    ) {
        // Optional: validate token
        // val expectedToken = pendingTokens[fileId]
        // if (expectedToken != null && expectedToken != token) { ... }

        val fileMeta = pendingFiles[fileId]
        val fileName = (fileMeta?.get("fileName") as? String)
            ?: headers["x-file-name"]
            ?: "received_${System.currentTimeMillis()}"
        val mimeType = headers["content-type"] ?: "application/octet-stream"

        try {
            val bytesRead = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveFileViaMediaStore(inputStream, fileName, mimeType, contentLength)
            } else {
                saveFileLegacy(inputStream, fileName, contentLength)
            }

            pendingTokens.remove(fileId)
            pendingFiles.remove(fileId)

            onFileReceived?.invoke(FileInfo(
                id = fileId,
                name = fileName,
                size = bytesRead,
                mimeType = mimeType
            ))

            out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nAccess-Control-Allow-Origin: *\r\n\r\n".toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
            out.write("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n".toByteArray())
        }
    }

    private fun saveFileViaMediaStore(inputStream: InputStream, fileName: String, mimeType: String, contentLength: Long): Long {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Failed to create MediaStore entry")

        var totalWritten = 0L
        resolver.openOutputStream(uri)?.use { out ->
            val buffer = ByteArray(65536)
            var remaining = if (contentLength > 0) contentLength else Long.MAX_VALUE
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val n = inputStream.read(buffer, 0, toRead)
                if (n == -1) break
                out.write(buffer, 0, n)
                totalWritten += n
                remaining -= n
            }
        }

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return totalWritten
    }

    private fun saveFileLegacy(inputStream: InputStream, fileName: String, contentLength: Long): Long {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs()
        val file = File(downloadsDir, fileName)
        var totalWritten = 0L
        FileOutputStream(file).use { out ->
            val buffer = ByteArray(65536)
            var remaining = if (contentLength > 0) contentLength else Long.MAX_VALUE
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val n = inputStream.read(buffer, 0, toRead)
                if (n == -1) break
                out.write(buffer, 0, n)
                totalWritten += n
                remaining -= n
            }
        }
        return totalWritten
    }

    private fun readBody(inputStream: InputStream, contentLength: Long): ByteArray {
        if (contentLength <= 0) return ByteArray(0)
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var remaining = contentLength
        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val n = inputStream.read(buffer, 0, toRead)
            if (n == -1) break
            baos.write(buffer, 0, n)
            remaining -= n
        }
        return baos.toByteArray()
    }

    private fun sendJson(out: OutputStream, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val response = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("\r\n")
        }
        out.write(response.toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun parseQueryString(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").associate {
            val kv = it.split("=", limit = 2)
            val key = java.net.URLDecoder.decode(kv[0], "UTF-8")
            val value = if (kv.size > 1) java.net.URLDecoder.decode(kv[1], "UTF-8") else ""
            key to value
        }
    }

    fun setLocalIp(ip: String) {
        _localIp.value = ip
    }

    fun cleanup() {
        stopServer()
        scope.cancel()
    }
}
