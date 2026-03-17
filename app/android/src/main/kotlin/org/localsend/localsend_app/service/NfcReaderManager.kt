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

class NfcReaderManager(
    private val scope: CoroutineScope,
    private val onReaderEnabled: () -> Unit = {},
    private val onBeamSent: () -> Unit,
    private val onError: (String) -> Unit
) : NfcAdapter.ReaderCallback {

    companion object {
        private const val TAG = "NfcReaderManager"

        private val SELECT_AID_APDU = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xF0.toByte(), 0x39, 0x41, 0x48, 0x14, 0x81.toByte(), 0x00
        )

        // Custom APDU instruction for payload delivery
        private val DELIVER_PAYLOAD_HEADER = byteArrayOf(0x80.toByte(), 0x01, 0x00, 0x00)
    }

    private val gson = Gson()
    private var pendingMessage: NfcBeamMessage? = null

    fun enable(activity: Activity, message: NfcBeamMessage) {
        pendingMessage = message
        val nfcAdapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        if (!nfcAdapter.isEnabled) {
            onError("NFC not enabled")
            return
        }
        nfcAdapter.enableReaderMode(
            activity,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
        Log.d(TAG, "NFC reader mode enabled with pending message: $message")
        onReaderEnabled()
    }

    fun disable(activity: Activity) {
        pendingMessage = null
        NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity)
        Log.d(TAG, "NFC reader mode disabled")
    }

    override fun onTagDiscovered(tag: Tag?) {
        val isoDep = IsoDep.get(tag) ?: run {
            onError("NFC tag does not support IsoDep")
            return
        }

        val messageToSend = pendingMessage ?: run {
            Log.w(TAG, "Tag discovered but no pending message to send")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                isoDep.connect()
                isoDep.timeout = 5000

                // 1. Select AID
                val selectResponse = isoDep.transceive(SELECT_AID_APDU)
                if (!isSuccess(selectResponse)) {
                    onError("NFC Select failed")
                    return@launch
                }

                // 2. Transmit Payload
                val jsonBytes = gson.toJson(messageToSend).toByteArray(Charsets.UTF_8)
                val payloadApdu = ByteArray(5 + jsonBytes.size)
                System.arraycopy(DELIVER_PAYLOAD_HEADER, 0, payloadApdu, 0, 4)
                payloadApdu[4] = jsonBytes.size.toByte()
                System.arraycopy(jsonBytes, 0, payloadApdu, 5, jsonBytes.size)

                Log.d(TAG, "Sending Payload APDU...")
                val payloadResponse = isoDep.transceive(payloadApdu)

                if (isSuccess(payloadResponse)) {
                    Log.d(TAG, "Payload sent successfully!")
                    onBeamSent()
                } else {
                    onError("NFC Payload send failed")
                }

            } catch (e: Exception) {
                Log.e(TAG, "NFC read error", e)
                onError("NFC error: ${e.message}")
            } finally {
                try { isoDep.close() } catch (_: Exception) {}
            }
        }
    }

    private fun isSuccess(response: ByteArray): Boolean {
        if (response.size < 2) return false
        val sw1 = response[response.size - 2]
        val sw2 = response[response.size - 1]
        return sw1 == 0x90.toByte() && sw2 == 0x00.toByte()
    }
}
