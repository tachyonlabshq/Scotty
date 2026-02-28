package org.localsend.localsend_app.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.localsend.localsend_app.model.Constants
import org.localsend.localsend_app.model.Device
import org.localsend.localsend_app.model.InfoResponse
import java.io.IOException
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class NetworkDiscoveryService(private val context: Context) {
    
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _nearbyDevices = MutableStateFlow<Map<String, Device>>(emptyMap())
    val nearbyDevices: StateFlow<Map<String, Device>> = _nearbyDevices.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private var multicastSocket: MulticastSocket? = null
    private val executor = Executors.newFixedThreadPool(50)
    private var isRunning = false
    
    fun startDiscovery() {
        if (isRunning) return
        isRunning = true
        
        scope.launch {
            startMulticastListener()
        }
        
        scope.launch {
            scanNetwork()
        }
    }
    
    fun stopDiscovery() {
        isRunning = false
        multicastSocket?.close()
        multicastSocket = null
        scope.cancel()
    }
    
    private suspend fun startMulticastListener() {
        try {
            val multicastAddress = InetAddress.getByName(Constants.DEFAULT_MULTICAST_GROUP)
            multicastSocket = MulticastSocket(Constants.MULTICAST_PORT).apply {
                reuseAddress = true
                networkInterface = NetworkInterface.getByName("wlan0")
                joinGroup(multicastAddress)
            }
            
            val buffer = ByteArray(4096)
            while (isRunning) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    multicastSocket?.receive(packet)
                    
                    val json = String(packet.data, 0, packet.length)
                    val info = gson.fromJson(json, InfoResponse::class.java)
                    
                    val senderIp = packet.address.hostAddress
                    if (senderIp != null) {
                        val device = info.toDevice(senderIp, Constants.DEFAULT_PORT, false)
                        addDevice(device)
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        delay(100)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private suspend fun scanNetwork() {
        _isScanning.value = true
        val localIp = getLocalIp() ?: return
        
        val subnet = localIp.substringBeforeLast(".")
        val jobs = (1..254).map { i ->
            val ip = "$subnet.$i"
            scope.async {
                try {
                    discoverDevice(ip, Constants.DEFAULT_PORT, false)
                } catch (e: Exception) {
                    null
                }
            }
        }
        
        jobs.awaitAll().filterNotNull().forEach { device ->
            addDevice(device)
        }
        
        _isScanning.value = false
        
        while (isRunning) {
            delay(5000)
            val rescanJobs = (1..254).map { i ->
                val ip = "$subnet.$i"
                scope.async {
                    try {
                        discoverDevice(ip, Constants.DEFAULT_PORT, false)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            
            rescanJobs.awaitAll().filterNotNull().forEach { device ->
                addDevice(device)
            }
        }
    }
    
    private fun addDevice(device: Device) {
        _nearbyDevices.value = _nearbyDevices.value.toMutableMap().apply {
            put(device.ip ?: "", device)
        }
    }
    
    private suspend fun discoverDevice(ip: String, port: Int, https: Boolean): Device? {
        return withContext(Dispatchers.IO) {
            try {
                val protocol = if (https) "https" else "http"
                val url = URL("$protocol://$ip:$port${Constants.ApiRoutes.INFO_V1}")
                
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 500
                connection.readTimeout = 500
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val info = gson.fromJson(response, InfoResponse::class.java)
                    info.toDevice(ip, port, https)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    fun getLocalIp(): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ipAddress = wifiInfo.ipAddress
        
        if (ipAddress == 0) {
            return null
        }
        
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }
    
    fun cleanup() {
        stopDiscovery()
        executor.shutdown()
    }
    
    private fun InfoResponse.toDevice(ip: String, port: Int, https: Boolean): Device {
        return Device(
            ip = ip,
            version = version ?: "1.0",
            port = port,
            https = https,
            fingerprint = fingerprint ?: "",
            alias = alias,
            deviceModel = deviceModel,
            deviceType = deviceType ?: org.localsend.localsend_app.model.DeviceType.DESKTOP,
            download = download ?: false,
            discoveryMethods = setOf("http")
        )
    }
}