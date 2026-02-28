package org.localsend.localsend_app.service

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.localsend.localsend_app.model.Device
import org.localsend.localsend_app.model.DeviceType

/**
 * Manages NFC Reader Mode for the sending device.
 *
 * When the user has selected files and holds the device near another phone
 * running LocalSend in receive mode, this reads the HCE APDU response
 * containing the receiver's connection details and returns a Device object.
 */
class NfcReaderManager(
    private val onDeviceDiscovered: (Device) -> Unit,
    private val onError: (String) -> Unit
) : NfcAdapter.ReaderCallback {

    companion object {
        private const val TAG = "NfcReaderManager"

        // SELECT AID APDU: CLA INS P1 P2 Lc AID
        private val SELECT_AID_APDU = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xF0.toByte(), 0x39, 0x41, 0x48, 0x14, 0x81.toByte(), 0x00
        )
    }

    private val gson = Gson()

    fun enable(activity: Activity) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        if (!nfcAdapter.isEnabled) {
            Log.w(TAG, "NFC not enabled")
            return
        }
        nfcAdapter.enableReaderMode(
            activity,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
        Log.d(TAG, "NFC reader mode enabled")
    }

    fun disable(activity: Activity) {
        NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity)
        Log.d(TAG, "NFC reader mode disabled")
    }

    override fun onTagDiscovered(tag: Tag?) {
        val isoDep = IsoDep.get(tag) ?: run {
            onError("NFC tag does not support IsoDep")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                isoDep.connect()
                isoDep.timeout = 5000

                val response = isoDep.transceive(SELECT_AID_APDU)
                Log.d(TAG, "APDU response length: ${response.size}")

                // Expect: [2 bytes length] [JSON payload] [SW 90 00]
                if (response.size < 4) {
                    onError("NFC response too short")
                    return@launch
                }

                val sw1 = response[response.size - 2]
                val sw2 = response[response.size - 1]
                if (sw1 != 0x90.toByte() || sw2 != 0x00.toByte()) {
                    onError("NFC response error: ${sw1.toInt() and 0xFF} ${sw2.toInt() and 0xFF}")
                    return@launch
                }

                val payloadLength = ((response[0].toInt() and 0xFF) shl 8) or (response[1].toInt() and 0xFF)
                if (response.size < 2 + payloadLength + 2) {
                    onError("NFC payload length mismatch")
                    return@launch
                }

                val jsonBytes = response.copyOfRange(2, 2 + payloadLength)
                val jsonString = String(jsonBytes, Charsets.UTF_8)
                Log.d(TAG, "Received device JSON: $jsonString")

                val nfcPayload = gson.fromJson(jsonString, NfcDevicePayload::class.java)
                val device = Device(
                    ip = nfcPayload.ip,
                    port = nfcPayload.port,
                    https = nfcPayload.https,
                    fingerprint = nfcPayload.fingerprint,
                    alias = nfcPayload.alias,
                    deviceModel = nfcPayload.deviceModel,
                    deviceType = DeviceType.MOBILE,
                    discoveryMethods = setOf("nfc")
                )

                onDeviceDiscovered(device)

            } catch (e: Exception) {
                Log.e(TAG, "NFC read error", e)
                onError("NFC error: ${e.message}")
            } finally {
                try { isoDep.close() } catch (_: Exception) {}
            }
        }
    }
}

/**
 * Compact payload for NFC device info exchange.
 */
data class NfcDevicePayload(
    val ip: String,
    val port: Int = 53317,
    val https: Boolean = false,
    val fingerprint: String = "",
    val alias: String = "",
    val deviceModel: String? = null
)
