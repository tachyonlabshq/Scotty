package org.localsend.localsend_app.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class NfcBeamMessage(
    val action: String,
    val targetEndpoint: String,
    val deviceName: String
)

object NfcMessageHub {
    private val _incomingMessages = MutableSharedFlow<NfcBeamMessage>(extraBufferCapacity = 1)
    val incomingMessages = _incomingMessages.asSharedFlow()
    
    fun emit(message: NfcBeamMessage) {
        _incomingMessages.tryEmit(message)
    }
}
