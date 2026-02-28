package org.localsend.localsend_app.ui

import org.localsend.localsend_app.model.Device

/**
 * Represents the state of an NFC beam session.
 */
sealed class NfcBeamStatus {
    /** NFC beam is not active (no files selected or tab not visible). */
    object Idle : NfcBeamStatus()

    /** NFC reader is active; waiting for the user to tap devices together. */
    object Ready : NfcBeamStatus()

    /** An NFC tag was read; initiating connection to the discovered device. */
    data class Connecting(val device: Device) : NfcBeamStatus()

    /** An error occurred during beam. */
    data class Error(val message: String) : NfcBeamStatus()
}
