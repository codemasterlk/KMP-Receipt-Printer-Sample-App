package dev.escpos.sample.presentation

import androidx.compose.ui.graphics.ImageBitmap
import dev.escpos.core.CutMode
import dev.escpos.core.RasterMode
import dev.escpos.fonts.NotoFontLookup
import dev.escpos.render.DEFAULT_FEED_DOTS_AFTER_CONTENT
import dev.escpos.sample.domain.model.TransportKind
import dev.escpos.sample.domain.model.availableTransportKinds
import dev.escpos.sample.domain.receipt.HARDCODED_ITEMS
import dev.escpos.transport.bt.BluetoothDeviceInfo
import dev.escpos.transport.serial.SerialPortInfo
import dev.escpos.transport.spooler.SpoolerPrinterInfo
import dev.escpos.transport.usb.UsbDeviceInfo

/** [SampleAppUiState.itemCount]'s default, and [SampleAppUiState.resolvedItemCount]'s fallback
 *  when [SampleAppUiState.itemCount] doesn't parse to a number in range. */
internal const val DEFAULT_ITEM_COUNT: Int = 10

/** One row in [SampleAppUiState.printLog] — what was sent, how many items it had (`null` for the
 *  fixed diagnostic pattern, which has no item count), and how it turned out. */
data class PrintLogEntry(
    val timestamp: String,
    val label: String,
    val itemCount: Int?,
    val succeeded: Boolean,
    val detail: String,
)

/**
 * Everything [SampleApp] reads to render itself — one immutable snapshot [SampleAppViewModel]
 * publishes on [SampleAppViewModel.uiState] after every change. Nothing here is ever mutated in
 * place: every action that changes a field (a text edit, a transport pick, "Print receipt") goes
 * through a [SampleAppViewModel] method, which publishes a new `copy()` — that split is the whole
 * point of separating "what's on screen" (this class) from "what changes it and why"
 * ([SampleAppViewModel]).
 *
 * Field grouping and defaults mirror the printer-setup flow top to bottom: transport/connection,
 * printer profile, receipt content, then the print/preview results those actions produce.
 */
data class SampleAppUiState(
    /** Which of the three panes `FormPane` currently shows — see [FormTab]'s KDoc. */
    val formTab: FormTab = FormTab.PrinterSettings,
    val transportKind: TransportKind = availableTransportKinds().first(),

    // TCP (FR-6.3)
    val host: String = "192.168.1.100",
    val port: String = "9100",

    // Bluetooth SPP (FR-6.4, FR-9.1)
    val bluetoothDevices: List<BluetoothDeviceInfo> = emptyList(),
    val selectedBluetoothDevice: BluetoothDeviceInfo? = null,
    val bluetoothStatus: String? = null,

    // USB (FR-6.6, FR-9.3)
    val usbDevices: List<UsbDeviceInfo> = emptyList(),
    val selectedUsbDevice: UsbDeviceInfo? = null,

    // Serial / COM (FR-6.7) — real on Windows/JVM only; always empty elsewhere.
    val serialPortList: List<SerialPortInfo> = emptyList(),
    val selectedSerialPort: SerialPortInfo? = null,
    val serialBaudRate: String = "9600",

    // Print spooler (RAW) — real on Windows/JVM only; always empty elsewhere.
    val spoolerPrinterList: List<SpoolerPrinterInfo> = emptyList(),
    val selectedSpoolerPrinter: SpoolerPrinterInfo? = null,

    // Profile (FR-8.1, FR-8.2) — every PrinterProfile field, kept as text/enum state so the
    // fields round-trip through PrinterProfile's own `init` validation rather than this screen
    // re-implementing it.
    val profileName: String = "Counter 1",
    val dotWidth: String = "576",
    val rasterMode: RasterMode = RasterMode.EscAsterisk,
    val bandHeight: String = "24",
    val cut: CutMode = CutMode.Partial,
    val drawerPin: Int = 0,

    // Render-only, no PrinterProfile equivalent (see RenderConfig.feedDotsAfterContent's KDoc) —
    // a per-printer mechanical offset (print head to cutter blade) with no way to detect from
    // software, only from what actually gets cut off in practice.
    val feedDotsAfterContent: String = DEFAULT_FEED_DOTS_AFTER_CONTENT.toString(),

    // Receipt content (see dev.escpos.sample.domain.receipt.SampleInvoiceReceipt.kt) — everything
    // else is hardcoded there; item count and the content font are the remaining controls.
    val itemCount: String = DEFAULT_ITEM_COUNT.toString(),
    val contentFontId: String = NotoFontLookup.NOTO_SANS,

    val printStatus: String? = null,
    val printing: Boolean = false,

    /** Print history — newest first — for the "Print log" tab. Populated by
     *  [SampleAppViewModel]'s internal `logPrint`, never cleared automatically; a plain
     *  in-memory session log, not persisted. */
    val printLog: List<PrintLogEntry> = emptyList(),

    // Live preview — re-published by [SampleAppViewModel] whenever a profile field changes.
    // previewHeightDots/previewRasterKb mirror the size readout in the preview panel's footer.
    val previewBitmap: ImageBitmap? = null,
    val previewHeightDots: Int = 0,
    val previewRasterKb: Double = 0.0,
    val previewError: String? = null,
) {
    /** [itemCount] parsed and clamped to a valid range — never zero/negative, never past
     *  [HARDCODED_ITEMS]' size, and never blank (a mid-edit empty field falls back to the last
     *  valid count rather than crashing the preview). */
    val resolvedItemCount: Int
        get() = itemCount.toIntOrNull()?.coerceIn(1, HARDCODED_ITEMS.size) ?: DEFAULT_ITEM_COUNT
}
