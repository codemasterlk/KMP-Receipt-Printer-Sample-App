package dev.escpos.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextMeasurer
import dev.escpos.core.CutMode
import dev.escpos.core.EscPos
import dev.escpos.core.PrinterProfile
import dev.escpos.core.PrinterStatus
import dev.escpos.core.RasterMode
import dev.escpos.core.Receipt
import dev.escpos.fonts.NotoFontLookup
import dev.escpos.fonts.diagnosticReceipt
import dev.escpos.render.DEFAULT_FEED_DOTS_AFTER_CONTENT
import dev.escpos.render.ReceiptRenderer
import dev.escpos.render.RenderConfig
import dev.escpos.session.PacingPolicy
import dev.escpos.session.PrintResult
import dev.escpos.session.PrintSession
import dev.escpos.session.RetryPolicy
import dev.escpos.session.StatusPoller
import dev.escpos.transport.api.PrinterTransport
import dev.escpos.transport.api.StatusQueryTransport
import dev.escpos.transport.bt.BluetoothDeviceInfo
import dev.escpos.transport.bt.BluetoothSppTransport
import dev.escpos.transport.bt.MissingBluetoothPermissionException
import dev.escpos.transport.bt.pairedBluetoothDevices
import dev.escpos.transport.serial.SerialPortInfo
import dev.escpos.transport.serial.SerialPortTransport
import dev.escpos.transport.serial.serialPorts
import dev.escpos.transport.spooler.SpoolerPrinterInfo
import dev.escpos.transport.spooler.SpoolerTransport
import dev.escpos.transport.spooler.spoolerPrinters
import dev.escpos.transport.tcp.TcpTransport
import dev.escpos.transport.usb.UsbDeviceInfo
import dev.escpos.transport.usb.UsbPrinterTransport
import dev.escpos.transport.usb.usbPrinterDevices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One row in [SampleAppState.printLog] — what was sent, how many items it had (`null` for the
 *  fixed diagnostic pattern, which has no item count), and how it turned out. */
public data class PrintLogEntry(
    val timestamp: String,
    val label: String,
    val itemCount: Int?,
    val succeeded: Boolean,
    val detail: String,
)

/**
 * All of the sample app's mutable state, plus what it actually does — refresh a device list,
 * check printer status (FR-7.5), and print either the fixed-content [customInvoiceReceipt]
 * ([printReceipt] — see [SampleInvoiceReceipt.kt]) or the fixed [diagnosticReceipt]
 * ([printDiagnostic], FR-8.6) over whichever transport/device is currently selected. Plain Compose
 * state ([mutableStateOf]/[mutableStateListOf]), not an androidx `ViewModel` — one screen with
 * nothing worth surviving process death for is simpler this way.
 *
 * Shared verbatim by every platform's sample app — [availableTransportKinds] is the only thing
 * that varies per platform about what this class *offers*; every device list/selection field
 * below (TCP through print spooler) exists unconditionally because the underlying
 * `escpos-transport-*` types are themselves KMP and safe to hold on any target, even where the
 * real transport stubs Unavailable (see build.gradle.kts's KDoc) — a platform simply never
 * populates the lists [availableTransportKinds] excludes.
 *
 * Per the confirmed "no user input, hardcode the receipt content" request, [itemCount] and
 * [contentFontId] are the *only* things about the receipt's content a user still controls —
 * everything else [customInvoiceReceipt] prints (store details, invoice number, the 100-item
 * catalog itself) is fixed in [SampleInvoiceReceipt.kt]. [contentFontId] was added afterwards,
 * on an explicit follow-up request to let the receipt's font be picked from
 * [NotoFontLookup.CONTENT_FONTS].
 *
 * [context] is only ever used to build the [TextMeasurer]'s font resolver right before rendering
 * (via [platformTextMeasurer], FR-3.6/NFR-3.2) — the actual Bluetooth/USB platform object capture
 * happens inside `escpos-transport-bt`/`-usb` themselves, so this class never touches those APIs
 * directly.
 */
public class SampleAppState(private val context: PlatformContext) {
    /** Which of the three panes [SampleApp] currently shows — see [FormTab]'s KDoc. */
    public var formTab: FormTab by mutableStateOf(FormTab.PrinterSettings)

    public var transportKind: TransportKind by mutableStateOf(availableTransportKinds().first())

    // TCP (FR-6.3)
    public var host: String by mutableStateOf("192.168.1.100")
    public var port: String by mutableStateOf("9100")

    // Bluetooth SPP (FR-6.4, FR-9.1)
    public val bluetoothDevices: MutableList<BluetoothDeviceInfo> = mutableStateListOf()
    public var selectedBluetoothDevice: BluetoothDeviceInfo? by mutableStateOf(null)
    public var bluetoothStatus: String? by mutableStateOf(null)

    // USB (FR-6.6, FR-9.3)
    public val usbDevices: MutableList<UsbDeviceInfo> = mutableStateListOf()
    public var selectedUsbDevice: UsbDeviceInfo? by mutableStateOf(null)

    // Serial / COM (FR-6.7) — real on Windows/JVM only; always empty elsewhere.
    public val serialPortList: MutableList<SerialPortInfo> = mutableStateListOf()
    public var selectedSerialPort: SerialPortInfo? by mutableStateOf(null)
    public var serialBaudRate: String by mutableStateOf("9600")

    // Print spooler (RAW) — real on Windows/JVM only; always empty elsewhere.
    public val spoolerPrinterList: MutableList<SpoolerPrinterInfo> = mutableStateListOf()
    public var selectedSpoolerPrinter: SpoolerPrinterInfo? by mutableStateOf(null)

    // Profile (FR-8.1, FR-8.2) — every PrinterProfile field, kept as text/enum state so the
    // fields round-trip through PrinterProfile's own `init` validation rather than this screen
    // re-implementing it.
    public var profileName: String by mutableStateOf("Counter 1")
    public var dotWidth: String by mutableStateOf("576")
    public var rasterMode: RasterMode by mutableStateOf(RasterMode.EscAsterisk)
    public var bandHeight: String by mutableStateOf("24")
    public var cut: CutMode by mutableStateOf(CutMode.Partial)
    public var drawerPin: Int by mutableIntStateOf(0)

    // Render-only, no PrinterProfile equivalent (see RenderConfig.feedDotsAfterContent's KDoc) —
    // deliberately adjustable from this screen rather than baked into the profile: it's a
    // per-printer mechanical offset (print head to cutter blade) with no way to detect from
    // software, only from what actually gets cut off in practice, so a real printer cutting into
    // the tail of its own content needs this raised past whatever the default guessed.
    public var feedDotsAfterContent: String by mutableStateOf(DEFAULT_FEED_DOTS_AFTER_CONTENT.toString())

    // Receipt content (see SampleInvoiceReceipt.kt) — everything is hardcoded there now; item
    // count and the content font are the remaining controls. itemCount is clamped to
    // HARDCODED_ITEMS' size below; contentFontId is any NotoFontLookup.NOTO_SANS (default) or
    // NotoFontLookup.CONTENT_FONTS id — no validation needed since the picker UI only ever offers
    // one of those.
    public var itemCount: String by mutableStateOf(DEFAULT_ITEM_COUNT.toString())
    public var contentFontId: String by mutableStateOf(NotoFontLookup.NOTO_SANS)

    public var printStatus: String? by mutableStateOf(null)
    public var printing: Boolean by mutableStateOf(false)

    /** Print history — newest first — for the "Print log" tab. Populated by [sendJob], never
     *  cleared automatically; this is a plain in-memory session log, not persisted. */
    public val printLog: MutableList<PrintLogEntry> = mutableStateListOf()

    // Live preview (see [SampleInvoiceReceipt.kt]) — re-rendered by the caller whenever a
    // profile field above changes, via [updatePreview]. previewHeightDots/previewRasterKb mirror
    // the size readout in the preview panel's footer.
    public var previewBitmap: ImageBitmap? by mutableStateOf(null)
    public var previewHeightDots: Int by mutableIntStateOf(0)
    public var previewRasterKb: Double by mutableStateOf(0.0)
    public var previewError: String? by mutableStateOf(null)

    /** [itemCount] parsed and clamped to a valid range — never zero/negative, never past
     *  [HARDCODED_ITEMS]' size, and never blank (a mid-edit empty field falls back to the last
     *  valid count rather than crashing the preview). */
    public val resolvedItemCount: Int
        get() = itemCount.toIntOrNull()?.coerceIn(1, HARDCODED_ITEMS.size) ?: DEFAULT_ITEM_COUNT

    /** Called once the caller has already ensured Bluetooth runtime permission is granted (or
     *  isn't needed on this platform) — see [rememberBluetoothRefresh]. */
    public fun refreshBluetoothDevices() {
        bluetoothStatus = null
        bluetoothDevices.clear()
        try {
            bluetoothDevices.addAll(pairedBluetoothDevices())
        } catch (e: MissingBluetoothPermissionException) {
            bluetoothStatus = e.message
        }
    }

    public fun refreshUsbDevices() {
        usbDevices.clear()
        usbDevices.addAll(usbPrinterDevices())
    }

    public fun refreshSerialPorts() {
        serialPortList.clear()
        serialPortList.addAll(serialPorts())
    }

    public fun refreshSpoolerPrinters() {
        spoolerPrinterList.clear()
        spoolerPrinterList.addAll(spoolerPrinters())
    }

    private fun buildProfile(): PrinterProfile? {
        val width = dotWidth.toIntOrNull() ?: return null
        val height = bandHeight.toIntOrNull() ?: return null
        return runCatching {
            PrinterProfile(
                name = profileName,
                dotWidth = width,
                rasterMode = rasterMode,
                bandHeight = height,
                cut = cut,
                drawerPin = drawerPin,
            )
        }.getOrNull()
    }

    private fun buildTransport(): PrinterTransport? = when (transportKind) {
        TransportKind.Tcp -> port.toIntOrNull()?.let { TcpTransport(host, it) }
        TransportKind.Bluetooth -> selectedBluetoothDevice?.let { BluetoothSppTransport(it) }
        TransportKind.Usb -> selectedUsbDevice?.let { UsbPrinterTransport(it) }
        TransportKind.Serial -> {
            val baud = serialBaudRate.toIntOrNull()
            val selected = selectedSerialPort
            if (baud != null && selected != null) SerialPortTransport(selected, baud) else null
        }
        TransportKind.Spooler -> selectedSpoolerPrinter?.let { SpoolerTransport(it) }
    }

    /** Shared by every render call — a [TextMeasurer] must be built fresh from [context] each
     *  time (per FR-3.6/NFR-3.2, see the class KDoc), so every call path builds its own
     *  [ReceiptRenderer] through here rather than duplicating this wiring. */
    private suspend fun newRenderer(profile: PrinterProfile): ReceiptRenderer {
        val measurer = platformTextMeasurer(context)
        val config = RenderConfig.of(
            profile,
            feedDotsAfterContent = feedDotsAfterContent.toIntOrNull() ?: DEFAULT_FEED_DOTS_AFTER_CONTENT,
        )
        return ReceiptRenderer(config, measurer, NotoFontLookup.create())
    }

    /** Builds [customInvoiceReceipt] for the current profile and [resolvedItemCount] — the one
     *  place both [updatePreview] and [printReceipt] assemble it, so they can never drift apart
     *  into showing one receipt and printing another. */
    private fun buildCustomReceipt(profile: PrinterProfile) =
        customInvoiceReceipt(profile = profile, itemCount = resolvedItemCount, fontFamilyId = contentFontId)

    /**
     * Re-renders [buildCustomReceipt] for the current profile and [resolvedItemCount] and unpacks
     * the result into [previewBitmap] — the preview panel's live "what would this actually print"
     * view, always in sync with what "Print receipt" sends. Called from a `LaunchedEffect` keyed
     * on the profile fields plus [itemCount], not on every unrelated recomposition.
     */
    public suspend fun updatePreview() {
        val profile = buildProfile()
        if (profile == null) {
            previewError = "Fix dot width / band height first — both must be whole numbers"
            previewBitmap = null
            return
        }
        try {
            val rows = newRenderer(profile).renderPreviewRows(buildCustomReceipt(profile))
            previewBitmap = rowsToBitmap(profile.dotWidth, rows)
            previewHeightDots = rows.size
            previewRasterKb = rows.sumOf { it.size }.toDouble() / 1024.0
            previewError = null
        } catch (e: Exception) {
            previewError = "Preview failed: ${e.message ?: e.toString()}"
            previewBitmap = null
        }
    }

    /** Renders [receipt] and sends it through a fresh [PrintSession] over whichever
     *  transport/device [transportKind] currently selects — shared by [printReceipt] and
     *  [printDiagnostic], which differ only in which [Receipt] they build. Mirrors
     *  `tools/print-sample`'s `Main.kt` wiring. Always appends one [PrintLogEntry] to [printLog],
     *  success or failure, before returning. */
    private suspend fun sendJob(receipt: Receipt, profile: PrinterProfile, label: String, itemCountForLog: Int?) {
        val transport = buildTransport()
        if (transport == null) {
            printStatus = "Select a device (or enter a TCP host/port, or a serial baud rate) first"
            return
        }

        printing = true
        try {
            val job = newRenderer(profile).render(receipt)
            val session = PrintSession(
                transport = transport,
                // PacingPolicy.None, not TimedPacing: with chunkHeightDots = profile.bandHeight
                // (24 for ESC * mode), TimedPacing sleeps after *every single band* — for a
                // normal-length receipt that's dozens of stop/resume cycles, which is exactly
                // what showed up on real hardware as visible stepping/vibration instead of a
                // continuous paper roll (a made-up, never-measured dots/sec figure made it worse,
                // but the real problem was pacing every 24-dot band at all). Modern thermal
                // printers carry enough onboard buffer, and the transport's own backpressure
                // (Bluetooth RFCOMM credit flow, USB bulk-transfer blocking, TCP socket buffering)
                // already throttles writes to what the print head can actually consume — the same
                // approach commercial POS apps rely on. If a specific printer turns out to have no
                // buffer and no real flow control (C-5), pace in much larger chunks than one band,
                // not per-band, to avoid reintroducing this stutter.
                pacing = PacingPolicy.None,
                chunkHeightDots = profile.bandHeight,
                retryPolicy = RetryPolicy(),
            )
            // session.print iterates the job, which calls transport.connect()/write()/flush() —
            // real socket/RFCOMM/USB I/O. This is launched from SampleApp's rememberCoroutineScope(),
            // whose default dispatcher is Main, so without this the I/O runs on the UI thread and
            // Android's StrictMode kills it with NetworkOnMainThreadException.
            val result = withContext(Dispatchers.IO) { session.print(job) }
            val (succeeded, detail) = when (result) {
                PrintResult.Success -> true to "$label sent successfully."
                is PrintResult.Failure -> false to (
                    "Failed at chunk ${result.chunkIndex} after ${result.attempts} attempt(s): " +
                        describeError(result.cause)
                    )
            }
            printStatus = detail
            logPrint(label, itemCountForLog, succeeded, detail)
        } catch (e: Exception) {
            val detail = "Print failed: ${describeError(e)}"
            printStatus = detail
            logPrint(label, itemCountForLog, succeeded = false, detail = detail)
        } finally {
            withContext(Dispatchers.IO) { runCatching { transport.close() } }
            printing = false
        }
    }

    private fun logPrint(label: String, itemCountForLog: Int?, succeeded: Boolean, detail: String) {
        printLog.add(0, PrintLogEntry(logTimestampNow(), label, itemCountForLog, succeeded, detail))
    }

    /** [Throwable.message] plus the underlying [Throwable.cause]'s message where one exists and
     *  says something new — e.g. `escpos-transport-tcp`'s `TransportIOException` wraps every
     *  connect/write failure in one generic message ("failed to connect to host:port"); the OS-
     *  level reason that's actually diagnostic ("Connection refused", "No route to host", a real
     *  socket timeout) only lives in [Throwable.cause]. Showing `e.message` alone silently
     *  discards it. */
    private fun describeError(e: Throwable): String {
        val own = e.message ?: e.toString()
        val causeMessage = e.cause?.message
        return if (causeMessage != null && causeMessage != own) "$own ($causeMessage)" else own
    }

    /** Sends [buildCustomReceipt] — the same receipt currently shown in the preview panel — over
     *  the selected transport. The primary print action; see [printDiagnostic] for the separate
     *  FR-8.6 verification pattern (checkerboard/fonts/QR-scale), still available but secondary
     *  now that there's real content to print. */
    public suspend fun printReceipt() {
        printStatus = null
        val profile = buildProfile()
        if (profile == null) {
            printStatus = "Fix the profile fields first — dot width and band height must be whole numbers"
            return
        }
        val count = resolvedItemCount
        sendJob(buildCustomReceipt(profile), profile, label = "Receipt", itemCountForLog = count)
    }

    /**
     * Builds and renders [diagnosticReceipt] (FR-8.6) for the current profile fields, then sends
     * it the same way [printReceipt] sends the custom receipt. Kept as a separate action rather
     * than folded into "Print receipt": it exercises raster transposition, every bundled font,
     * and QR module scale together — a real capability sending the customer-facing receipt alone
     * wouldn't cover.
     */
    public suspend fun printDiagnostic() {
        printStatus = null
        val profile = buildProfile()
        if (profile == null) {
            printStatus = "Fix the profile fields first — dot width and band height must be whole numbers"
            return
        }
        sendJob(diagnosticReceipt(profile), profile, label = "Diagnostic receipt", itemCountForLog = null)
    }

    /**
     * FR-7.5 — connects to whichever transport/device is currently selected and polls it once for
     * paper-out/cover-open, via [StatusPoller] one-shot queries decoded with
     * [PrinterStatus.fromOfflineAndPaperBytes]. Only [StatusQueryTransport]s answer this — TCP and
     * Bluetooth SPP here (see each transport's KDoc) — so any other selection reports that
     * plainly rather than pretending to check.
     */
    public suspend fun checkPrinterStatus() {
        printStatus = null
        val transport = buildTransport()
        if (transport == null) {
            printStatus = "Select a device (or enter a TCP host/port, or a serial baud rate) first"
            return
        }
        if (transport !is StatusQueryTransport) {
            printStatus = "${transportKind.name} does not support status queries (FR-7.5) — connect and print instead."
            withContext(Dispatchers.IO) { runCatching { transport.close() } }
            return
        }

        printing = true
        try {
            // transport.connect()/StatusPoller.queryOnce() are real socket/RFCOMM I/O — see
            // sendJob's withContext(Dispatchers.IO) call for why this must not run on the
            // caller's (Main) dispatcher.
            val (offline, paper) = withContext(Dispatchers.IO) {
                transport.connect()
                val offlineBytes = StatusPoller(transport, EscPos.RealtimeStatus.OFFLINE).queryOnce()
                val paperBytes = StatusPoller(transport, EscPos.RealtimeStatus.PAPER).queryOnce()
                offlineBytes to paperBytes
            }
            printStatus = if (offline.isEmpty() || paper.isEmpty()) {
                "Printer did not answer the status query in time — it may not implement DLE EOT."
            } else {
                val status = PrinterStatus.fromOfflineAndPaperBytes(offline[0], paper[0])
                "Cover open: ${status.coverOpen} · Paper near end: ${status.paperNearEnd} · Paper out: ${status.paperOut}"
            }
        } catch (e: Exception) {
            printStatus = "Status check failed: ${describeError(e)}"
        } finally {
            withContext(Dispatchers.IO) { runCatching { transport.close() } }
            printing = false
        }
    }

    private companion object {
        const val DEFAULT_ITEM_COUNT = 10
    }
}
