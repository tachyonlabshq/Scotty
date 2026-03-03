package org.localsend.localsend_app.service

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.google.gson.Gson

class NfcHceService : HostApduService() {

    companion object {
        private const val TAG = "NfcHceService"

        // Our custom AID
        private val AID = byteArrayOf(
            0xF0.toByte(), 0x39, 0x41, 0x48, 0x14, 0x81.toByte(), 0x00
        )

        private val SELECT_APDU_HEADER = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00)
        
        // Custom APDU instruction for payload delivery
        private val DELIVER_PAYLOAD_HEADER = byteArrayOf(0x80.toByte(), 0x01, 0x00, 0x00)

        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_UNKNOWN = byteArrayOf(0x6D, 0x00)
    }

    private val gson = Gson()

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "Received APDU: ${commandApdu.toHex()}")

        if (isSelectAidApdu(commandApdu)) {
            Log.d(TAG, "Received SELECT AID — Ready.")
            return SW_OK
        } else if (isDeliverPayloadApdu(commandApdu)) {
            Log.d(TAG, "Received Payload APDU.")
            try {
                // Drop header (5 bytes: CLA INS P1 P2 Lc)
                val payloadBytes = commandApdu.drop(5).toByteArray()
                val jsonString = String(payloadBytes, Charsets.UTF_8)
                Log.d(TAG, "Parsed Payload: $jsonString")
                
                val message = gson.fromJson(jsonString, NfcBeamMessage::class.java)
                NfcMessageHub.emit(message)
                
                return SW_OK
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse payload", e)
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
    
    private fun isDeliverPayloadApdu(apdu: ByteArray): Boolean {
        if (apdu.size < DELIVER_PAYLOAD_HEADER.size + 1) return false
        for (i in DELIVER_PAYLOAD_HEADER.indices) {
            if (apdu[i] != DELIVER_PAYLOAD_HEADER[i]) return false
        }
        return true
    }

    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
}
