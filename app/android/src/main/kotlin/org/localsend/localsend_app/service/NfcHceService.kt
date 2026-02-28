package org.localsend.localsend_app.service

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.google.gson.Gson

/**
 * NFC Host-based Card Emulation service.
 *
 * When the Pixel/OnePlus is in "receive" mode and another device taps it with
 * NFC reader mode active, this service responds with a compact JSON payload
 * containing this device's LocalSend connection details (IP, port, fingerprint, alias).
 *
 * Protocol:
 *   1. Sender sends SELECT AID: 00 A4 04 00 07 F0 39 41 48 14 81 00
 *   2. Receiver (HCE) responds with JSON: {"ip":"x.x.x.x","port":53317,...}
 *   3. Sender connects via existing LocalSend HTTP protocol and sends files.
 */
class NfcHceService : HostApduService() {

    companion object {
        private const val TAG = "NfcHceService"

        // Our custom AID: F0 39 41 48 14 81 00
        private val AID = byteArrayOf(
            0xF0.toByte(), 0x39, 0x41, 0x48, 0x14, 0x81.toByte(), 0x00
        )

        // SELECT AID APDU template: CLA INS P1 P2 Lc <AID>
        private val SELECT_APDU_HEADER = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00)

        // Status words
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_UNKNOWN = byteArrayOf(0x6D, 0x00)

        // Singleton holder so MainActivity can feed current device info to the service
        @Volatile
        var deviceInfoJson: String? = null
    }

    private val gson = Gson()

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "Received APDU: ${commandApdu.toHex()}")

        if (isSelectAidApdu(commandApdu)) {
            Log.d(TAG, "Received SELECT AID — returning device info")
            val payload = deviceInfoJson
            return if (payload != null) {
                val payloadBytes = payload.toByteArray(Charsets.UTF_8)
                // Response: [length (2 bytes big-endian)] [payload] [SW_OK]
                val response = ByteArray(2 + payloadBytes.size + 2)
                response[0] = ((payloadBytes.size shr 8) and 0xFF).toByte()
                response[1] = (payloadBytes.size and 0xFF).toByte()
                System.arraycopy(payloadBytes, 0, response, 2, payloadBytes.size)
                response[2 + payloadBytes.size] = SW_OK[0]
                response[2 + payloadBytes.size + 1] = SW_OK[1]
                response
            } else {
                Log.w(TAG, "No device info available yet")
                SW_UNKNOWN
            }
        }

        return SW_UNKNOWN
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "NFC deactivated, reason=$reason")
    }

    private fun isSelectAidApdu(apdu: ByteArray): Boolean {
        if (apdu.size < SELECT_APDU_HEADER.size + 1) return false
        for (i in SELECT_APDU_HEADER.indices) {
            if (apdu[i] != SELECT_APDU_HEADER[i]) return false
        }
        val lcIndex = SELECT_APDU_HEADER.size
        val lc = apdu[lcIndex].toInt() and 0xFF
        if (apdu.size < lcIndex + 1 + lc) return false
        val aidInApdu = apdu.copyOfRange(lcIndex + 1, lcIndex + 1 + lc)
        return AID.contentEquals(aidInApdu)
    }

    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
}
