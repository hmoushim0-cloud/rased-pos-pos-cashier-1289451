package sa.factory.rasedposposcashier.hardware

import sa.factory.rasedposposcashier.hardware.barcode.*
import sa.factory.rasedposposcashier.domain.cashier.EscPosCommandBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale
import kotlin.random.Random

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver {
    private val peripheral = "ESC/POS Thermal Receipt Printer + Barcode Scanner"
    private val barcodeEngine = Gs1BarcodeScannerEngine()

    fun getBarcodeEngine(): Gs1BarcodeScannerEngine = barcodeEngine

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "POS Peripheral Interface: Initializing USB HID Scanner & ESC/POS Printer..."))
        delay(300)

        // Sample ESC/POS Drawer Kick command
        val drawerKick = EscPosCommandBuilder.KICK_CASH_DRAWER_PIN2
        val kickHex = drawerKick.joinToString("") { String.format("%02X", it) }

        emit(HardwareTelemetryFrame(
            peripheral,
            "ONLINE",
            "ESC/POS Ready: Thermal 80mm Head OK | Cash Drawer Relay [ESC p]: $kickHex | GS1-128 Scanner Online"
        ))

        var frameCounter = 1
        val sampleBarcodes = listOf(
            "6281001000128", // Standard EAN-13 Milk / Juice
            "2110450014503", // Saudi Supermarket Scale (PLU 1045, 1.450 kg)
            "(01)06281001000123(17)261231(10)LOT9901" // GS1-128 Batch
        )

        while (true) {
            delay(1500)
            val jitter = Random.nextInt(1, 10)
            val raw = sampleBarcodes[frameCounter % sampleBarcodes.size]
            val symbology = barcodeEngine.identifySymbology(raw)

            val payload = String.format(
                Locale.US,
                "Scan Event #%d: [%s] Symbology: %s | Drawer Status: LOCKED | Paper Feed: OK (+%dms)",
                frameCounter,
                raw,
                symbology.name,
                jitter
            )

            emit(HardwareTelemetryFrame(peripheral, "ACTIVE_STREAMING", payload))
            frameCounter++
        }
    }
}
