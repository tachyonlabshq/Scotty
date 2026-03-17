package app.scotty

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for MainViewModel logic.
 *
 * These tests exercise the pure-logic parts of the ViewModel that don't require
 * Android context (e.g., NfcBeamStatus state machine, tab selection).
 *
 * Full ViewModel tests (DataStore, NearbyTransferService) require Robolectric
 * or an instrumented test environment and are deferred to iter 7.11.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    // ── NfcBeamStatus sealed class tests ─────────────────────────────

    @Test
    fun `NfcBeamStatus Idle is distinct from Ready`() {
        val idle = app.scotty.ui.NfcBeamStatus.Idle
        val ready = app.scotty.ui.NfcBeamStatus.Ready
        assertTrue(idle != ready)
    }

    @Test
    fun `NfcBeamStatus Connecting holds device name`() {
        val connecting = app.scotty.ui.NfcBeamStatus.Connecting("Pixel 9")
        assertEquals("Pixel 9", connecting.deviceName)
    }

    @Test
    fun `NfcBeamStatus Advertising holds endpoint token`() {
        val token = "abc123"
        val advertising = app.scotty.ui.NfcBeamStatus.Advertising(token)
        assertEquals(token, advertising.endpointToken)
    }

    @Test
    fun `NfcBeamStatus Error holds message`() {
        val err = app.scotty.ui.NfcBeamStatus.Error("NFC not enabled")
        assertEquals("NFC not enabled", err.message)
    }

    // ── AppSettings model tests ───────────────────────────────────────

    @Test
    fun `AppSettings copy preserves other fields`() {
        val settings = app.scotty.model.AppSettings(
            alias = "My Phone",
            deviceModel = "Pixel 9",
            deviceType = "mobile",
            fingerprint = "fp-123"
        )
        val updated = settings.copy(alias = "Work Phone")
        assertEquals("Work Phone", updated.alias)
        assertEquals("Pixel 9", updated.deviceModel)
        assertEquals("fp-123", updated.fingerprint)
    }

    @Test
    fun `AppSettings default port is 53317`() {
        val settings = app.scotty.model.AppSettings(
            alias = "Test",
            deviceModel = "Model",
            deviceType = "mobile"
        )
        assertEquals(53317, settings.port)
    }

    @Test
    fun `AppSettings default fingerprint is empty string`() {
        val settings = app.scotty.model.AppSettings(
            alias = "Test",
            deviceModel = "Model",
            deviceType = "mobile"
        )
        assertEquals("", settings.fingerprint)
    }

    // ── ReceivedFile model tests ──────────────────────────────────────

    @Test
    fun `ReceivedFile has correct default mimeType`() {
        val file = app.scotty.service.ReceivedFile(
            fileName = "photo.jpg",
            filePath = "/sdcard/photo.jpg",
            sizeBytes = 1024L
        )
        assertEquals("*/*", file.mimeType)
    }

    @Test
    fun `ReceivedFile receivedAtMs is set on creation`() {
        val before = System.currentTimeMillis()
        val file = app.scotty.service.ReceivedFile(
            fileName = "doc.pdf",
            filePath = "/sdcard/doc.pdf",
            sizeBytes = 2048L
        )
        val after = System.currentTimeMillis()
        assertTrue(file.receivedAtMs in before..after)
    }
}
