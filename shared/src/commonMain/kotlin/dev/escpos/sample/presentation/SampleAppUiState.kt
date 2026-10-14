package dev.escpos.sample.presentation

import androidx.compose.ui.graphics.ImageBitmap
import dev.escpos.core.CutMode
import dev.escpos.core.DEFAULT_TEXT_MODE_CHAR_WIDTH_DOTS
import dev.escpos.core.RasterMode
import dev.escpos.core.RuleStyle
import dev.escpos.fonts.NotoFontLookup
import dev.escpos.render.DEFAULT_FEED_DOTS_AFTER_CONTENT
import dev.escpos.sample.domain.model.PrintMode
import dev.escpos.sample.domain.model.ReceiptOptions
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

/** 80mm paper — the more common of the two documented paper widths, used only while
 *  [SampleAppUiState.dotWidth] is mid-edit and unparseable. */
private const val FALLBACK_DOT_WIDTH: Int = 576

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
    val host: String = "10.20.0.3",
    val port: String = "9100",

    // Bluetooth SPP (FR-6.4, FR-9.1)
    val bluetoothDevices: List<BluetoothDeviceInfo> = emptyList(),
    val selectedBluetoothDevice: BluetoothDeviceInfo? = null,
    val bluetoothStatus: String? = null,

    /** Diagnostic-only toggle for `BluetoothSppTransport`'s `paced` constructor parameter. `true`
     *  (the default) is the normal, real-hardware-confirmed pacing; turning it off blasts writes
     *  unpaced, matching another app's zero-pacing send path, to isolate whether this app's own
     *  deliberate write pacing is behind a stuttering feed. */
    val bluetoothPaced: Boolean = true,

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

    /** Which pipeline "Print receipt"/"Print diagnostic" send through — see [PrintMode]. */
    val printMode: PrintMode = PrintMode.Graphic,

    /** Text mode's characters-per-line (FR-8.7). Blank means "use `PrinterProfile`'s own computed
     *  default" ([defaultColumns]) — a printer whose default font isn't Font A is a one-field
     *  override here, same as the profile fields above. */
    val columnsOverride: String = "",

    // Render-only, no PrinterProfile equivalent (see RenderConfig.feedDotsAfterContent's KDoc) —
    // a per-printer mechanical offset (print head to cutter blade) with no way to detect from
    // software, only from what actually gets cut off in practice.
    val feedDotsAfterContent: String = DEFAULT_FEED_DOTS_AFTER_CONTENT.toString(),

    // Diagnostic-only receipt toggles — see ReceiptOptions's KDoc. Defaults are the receipt's
    // original, unchanged look.
    val ruleStyle: RuleStyle = RuleStyle.Solid,
    val includeLogoQrBarcode: Boolean = true,
    val boldHeadings: Boolean = true,
    /** Free text, since the right value isn't known yet — see [ReceiptOptions.itemGapDots]. */
    val itemGapDots: String = ReceiptOptions.DEFAULT_ITEM_GAP_DOTS.toString(),

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
    // previewHeightDots/previewRasterKb mirror the size readout in the preview panel's footer;
    // in text mode they carry a line count and the real job's byte size instead, so one footer
    // works for both modes.
    val previewBitmap: ImageBitmap? = null,
    val previewHeightDots: Int = 0,
    val previewRasterKb: Double = 0.0,
    val previewError: String? = null,

    /** Text mode's preview — populated instead of [previewBitmap] when [printMode] is
     *  [PrintMode.Text]. */
    val previewTextLines: List<String> = emptyList(),
) {
    /** [itemCount] parsed and clamped to a valid range — never zero/negative, never past
     *  [HARDCODED_ITEMS]' size, and never blank (a mid-edit empty field falls back to the last
     *  valid count rather than crashing the preview). */
    val resolvedItemCount: Int
        get() = itemCount.toIntOrNull()?.coerceIn(1, HARDCODED_ITEMS.size) ?: DEFAULT_ITEM_COUNT

    /** Text mode's default line width (FR-8.7) — [dotWidth] divided by Font A's assumed cell
     *  width, the same computation `PrinterProfile`'s own `columns` default uses. Shown as the
     *  "Columns" field's placeholder while [columnsOverride] is blank. */
    val defaultColumns: Int
        get() = (dotWidth.toIntOrNull() ?: FALLBACK_DOT_WIDTH) / DEFAULT_TEXT_MODE_CHAR_WIDTH_DOTS

    /** Text mode's actual line width right now — [columnsOverride] if set and parseable,
     *  otherwise [defaultColumns]. What the preview footer reports. */
    val resolvedColumns: Int
        get() = columnsOverride.toIntOrNull() ?: defaultColumns

    /** [itemGapDots] parsed, never negative; unparseable falls back to the original gap. */
    val resolvedItemGapDots: Int
        get() = itemGapDots.toIntOrNull()?.coerceAtLeast(0) ?: ReceiptOptions.DEFAULT_ITEM_GAP_DOTS
}
