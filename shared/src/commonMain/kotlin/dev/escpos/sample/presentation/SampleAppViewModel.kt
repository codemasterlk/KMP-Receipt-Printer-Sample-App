package dev.escpos.sample.presentation

import dev.escpos.core.CutMode
import dev.escpos.core.DEFAULT_GS_V0_BAND_HEIGHT_ROWS
import dev.escpos.core.ESC_ASTERISK_BAND_ROWS
import dev.escpos.core.PrinterProfile
import dev.escpos.core.RasterMode
import dev.escpos.core.Receipt
import dev.escpos.core.RuleStyle
import dev.escpos.core.textDiagnosticReceipt
import dev.escpos.fonts.diagnosticReceipt
import dev.escpos.sample.data.platform.PlatformContext
import dev.escpos.sample.data.platform.logTimestampNow
import dev.escpos.sample.data.repository.PrinterRepositoryImpl
import dev.escpos.sample.domain.model.PrintMode
import dev.escpos.sample.domain.model.ProfileSettings
import dev.escpos.sample.domain.model.ReceiptOptions
import dev.escpos.sample.domain.model.TransportKind
import dev.escpos.sample.domain.model.TransportSelection
import dev.escpos.sample.domain.receipt.customInvoiceReceipt
import dev.escpos.sample.domain.usecase.CheckPrinterStatusUseCase
import dev.escpos.sample.domain.usecase.FeedAndCutUseCase
import dev.escpos.sample.domain.usecase.PreviewResult
import dev.escpos.sample.domain.usecase.RenderPreviewUseCase
import dev.escpos.sample.domain.usecase.ReplayReferenceJobUseCase
import dev.escpos.sample.domain.usecase.SendReceiptUseCase
import dev.escpos.sample.domain.usecase.SendResult
import dev.escpos.sample.domain.usecase.StatusCheckResult
import dev.escpos.transport.bt.MissingBluetoothPermissionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [SampleApp]'s state holder, and the only place [SampleAppUiState] ever changes. [onEvent] is
 * the single entry point the UI drives it through — no composable ever reads a field and writes
 * it back mutated; it only ever describes what happened (see [SampleAppEvent]'s KDoc).
 *
 * Real business logic doesn't live here: rendering a preview, sending a receipt, checking printer
 * status, and the two diagnostic jobs are each delegated to a use case, all backed by one
 * `PrinterRepository` for device discovery and transport/renderer construction. This class's own
 * job is orchestration only: receive one [SampleAppEvent], update [uiState] directly for a plain
 * field edit, or map the relevant slice of [SampleAppUiState] into the domain-level
 * [ProfileSettings]/[TransportSelection]/[ReceiptOptions] a use case actually needs and publish
 * its result. That mapping step is what keeps the domain layer from ever depending on this
 * presentation-layer state type — see each domain model's KDoc.
 *
 * Two things happen reactively, in [init], rather than as explicit events the UI has to trigger:
 * the preview re-renders whenever a field that changes it changes (not on every unrelated
 * keystroke, e.g. typing a TCP host), and a device-based transport's device list re-populates the
 * moment its tab is selected. Driving these off [uiState] itself — not a UI-side `LaunchedEffect`
 * — keeps [SampleApp] a pure "dispatch events, render state" view with no timing decisions of its
 * own to get wrong.
 *
 * At most one job (print, status check, feed+cut, replay) runs at a time — see [runExclusive].
 *
 * Deliberately a plain class, not an `androidx.lifecycle.ViewModel`: a `ViewModelStoreOwner`
 * isn't guaranteed to exist in the composition on every target this sample runs on (Desktop's and
 * iOS's entry points don't go through `ComponentActivity`, unlike Android's), and one screen with
 * nothing worth surviving process death for doesn't need that lifecycle machinery anyway.
 * [SampleApp] holds one instance per composition via `remember { SampleAppViewModel(context, scope) }`,
 * passing its own `rememberCoroutineScope()` in as [scope] — every event this class handles with a
 * coroutine (jobs, the two reactive flows above) runs on it, so [SampleApp] itself never has to
 * launch one.
 */
class SampleAppViewModel(context: PlatformContext, private val scope: CoroutineScope) {
    // The one place this app picks a concrete PrinterRepository — everything below this line
    // (use cases, and this class itself) only ever depends on the domain-layer interface.
    private val repository = PrinterRepositoryImpl(context)
    private val renderPreview = RenderPreviewUseCase(repository)
    private val sendReceipt = SendReceiptUseCase(repository)
    private val checkPrinterStatus = CheckPrinterStatusUseCase(repository)
    private val feedAndCut = FeedAndCutUseCase(repository)
    private val replayReferenceJob = ReplayReferenceJobUseCase(repository)

    private val _uiState = MutableStateFlow(SampleAppUiState())

    /** The current, immutable snapshot [SampleApp] renders — collect with `collectAsState()`. */
    val uiState: StateFlow<SampleAppUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            uiState.map(::PreviewKey).distinctUntilChanged().collectLatest { runRenderPreview() }
        }
        scope.launch {
            uiState.map { it.transportKind }.distinctUntilChanged().collectLatest { kind ->
                when (kind) {
                    TransportKind.Usb -> setState { it.copy(usbDevices = repository.discoverUsbDevices()) }
                    TransportKind.Serial -> setState { it.copy(serialPortList = repository.discoverSerialPorts()) }
                    TransportKind.Spooler -> setState { it.copy(spoolerPrinterList = repository.discoverSpoolerPrinters()) }
                    else -> Unit
                }
            }
        }
    }

    /** Exactly the fields that change what [RenderPreviewUseCase] would produce — receipt content
     *  itself is fixed (see `dev.escpos.sample.domain.receipt`), so `itemCount`/`contentFontId`
     *  and the diagnostic receipt options are the only content fields that belong here alongside
     *  the profile fields and print mode. */
    private data class PreviewKey(
        val dotWidth: String,
        val rasterMode: RasterMode,
        val bandHeight: String,
        val cut: CutMode,
        val drawerPin: Int,
        val printMode: PrintMode,
        val columnsOverride: String,
        val feedDotsAfterContent: String,
        val itemCount: String,
        val contentFontId: String,
        val ruleStyle: RuleStyle,
        val includeLogoQrBarcode: Boolean,
        val boldHeadings: Boolean,
        val itemGapDots: String,
    ) {
        constructor(state: SampleAppUiState) : this(
            state.dotWidth, state.rasterMode, state.bandHeight, state.cut, state.drawerPin,
            state.printMode, state.columnsOverride, state.feedDotsAfterContent,
            state.itemCount, state.contentFontId,
            state.ruleStyle, state.includeLogoQrBarcode, state.boldHeadings, state.itemGapDots,
        )
    }

    private inline fun setState(transform: (SampleAppUiState) -> SampleAppUiState) {
        _uiState.update(transform)
    }

    private fun SampleAppUiState.toProfileSettings() =
        ProfileSettings(profileName, dotWidth, rasterMode, bandHeight, cut, drawerPin, columnsOverride)

    private fun SampleAppUiState.toTransportSelection() = TransportSelection(
        transportKind = transportKind,
        host = host,
        port = port,
        bluetoothDevice = selectedBluetoothDevice,
        bluetoothPaced = bluetoothPaced,
        dotWidth = dotWidth,
        usbDevice = selectedUsbDevice,
        serialPort = selectedSerialPort,
        serialBaudRate = serialBaudRate,
        spoolerPrinter = selectedSpoolerPrinter,
    )

    private fun SampleAppUiState.toReceiptOptions() = ReceiptOptions(
        mode = printMode,
        ruleStyle = ruleStyle,
        includeLogoQrBarcode = includeLogoQrBarcode,
        boldHeadings = boldHeadings,
        itemGapDots = resolvedItemGapDots,
    )

    /** The one entry point [SampleApp] drives every user action through — see this class's KDoc. */
    fun onEvent(event: SampleAppEvent) {
        when (event) {
            is SampleAppEvent.FormTabChanged -> setState { it.copy(formTab = event.tab) }
            is SampleAppEvent.TransportKindChanged -> setState { it.copy(transportKind = event.kind) }

            is SampleAppEvent.HostChanged -> setState { it.copy(host = event.value) }
            is SampleAppEvent.PortChanged -> setState { it.copy(port = event.value) }

            is SampleAppEvent.BluetoothDeviceSelected -> setState { it.copy(selectedBluetoothDevice = event.device) }
            is SampleAppEvent.BluetoothPacedChanged -> setState { it.copy(bluetoothPaced = event.paced) }
            SampleAppEvent.RefreshBluetoothDevices -> refreshBluetoothDevices()

            is SampleAppEvent.UsbDeviceSelected -> setState { it.copy(selectedUsbDevice = event.device) }
            SampleAppEvent.RefreshUsbDevices -> setState { it.copy(usbDevices = repository.discoverUsbDevices()) }

            is SampleAppEvent.SerialPortSelected -> setState { it.copy(selectedSerialPort = event.port) }
            is SampleAppEvent.SerialBaudRateChanged -> setState { it.copy(serialBaudRate = event.value) }
            SampleAppEvent.RefreshSerialPorts -> setState { it.copy(serialPortList = repository.discoverSerialPorts()) }

            is SampleAppEvent.SpoolerPrinterSelected -> setState { it.copy(selectedSpoolerPrinter = event.printer) }
            SampleAppEvent.RefreshSpoolerPrinters -> setState { it.copy(spoolerPrinterList = repository.discoverSpoolerPrinters()) }

            is SampleAppEvent.ProfileNameChanged -> setState { it.copy(profileName = event.value) }
            is SampleAppEvent.DotWidthChanged -> setState { it.copy(dotWidth = event.value) }
            is SampleAppEvent.RasterModeChanged -> selectRasterMode(event.mode)
            is SampleAppEvent.BandHeightChanged -> setState { it.copy(bandHeight = event.value) }
            is SampleAppEvent.CutChanged -> setState { it.copy(cut = event.mode) }
            is SampleAppEvent.DrawerPinChanged -> setState { it.copy(drawerPin = event.pin) }
            is SampleAppEvent.FeedDotsAfterContentChanged -> setState { it.copy(feedDotsAfterContent = event.value) }

            is SampleAppEvent.PrintModeChanged -> setState { it.copy(printMode = event.mode) }
            is SampleAppEvent.ColumnsOverrideChanged -> setState { it.copy(columnsOverride = event.value) }

            is SampleAppEvent.RuleStyleChanged -> setState { it.copy(ruleStyle = event.style) }
            is SampleAppEvent.IncludeLogoQrBarcodeChanged -> setState { it.copy(includeLogoQrBarcode = event.include) }
            is SampleAppEvent.BoldHeadingsChanged -> setState { it.copy(boldHeadings = event.bold) }
            is SampleAppEvent.ItemGapDotsChanged -> setState { it.copy(itemGapDots = event.value) }

            is SampleAppEvent.ItemCountChanged -> setState { it.copy(itemCount = event.value) }
            is SampleAppEvent.ContentFontIdChanged -> setState { it.copy(contentFontId = event.id) }

            SampleAppEvent.PrintReceipt -> scope.launch { printReceipt() }
            SampleAppEvent.PrintDiagnostic -> scope.launch { printDiagnostic() }
            SampleAppEvent.CheckPrinterStatus -> scope.launch { runCheckPrinterStatus() }
            SampleAppEvent.FeedAndCut -> scope.launch { runFeedAndCut() }
            SampleAppEvent.ReplayReferenceJob -> scope.launch { runReplayReferenceJob() }
        }
    }

    /**
     * Sets the raster mode and, alongside it, resets the band height to that mode's own sensible
     * default — `PrinterProfile`'s own default, duplicated here as a literal because the band
     * height is this screen's own free-text field, not a default parameter, so switching modes
     * never silently reaches it. Without this, the dropdown alone doesn't change anything
     * mechanical about a `GsV0` job: confirmed on real hardware that selecting `GS v 0` while the
     * band height was left at its `24` text-field default produced a job structurally identical
     * in row height to `ESC *` — same number of raster commands, same rows per command — differing
     * only in which command byte-sequence wrapped each one. (The content-adaptive mode ignores
     * the band height entirely; it keeps the `ESC *` value, which `PrinterProfile` still needs to
     * be some positive number.)
     */
    private fun selectRasterMode(mode: RasterMode) {
        val bandHeight = if (mode == RasterMode.GsV0) DEFAULT_GS_V0_BAND_HEIGHT_ROWS else ESC_ASTERISK_BAND_ROWS
        setState { it.copy(rasterMode = mode, bandHeight = bandHeight.toString()) }
    }

    /** Called once the caller has already ensured Bluetooth runtime permission is granted (or
     *  isn't needed on this platform) — see `rememberBluetoothRefresh`. */
    private fun refreshBluetoothDevices() {
        setState { it.copy(bluetoothStatus = null, bluetoothDevices = emptyList()) }
        try {
            setState { it.copy(bluetoothDevices = repository.discoverBluetoothDevices()) }
        } catch (e: MissingBluetoothPermissionException) {
            setState { it.copy(bluetoothStatus = e.message) }
        }
    }

    private suspend fun runRenderPreview() {
        val state = _uiState.value
        when (
            val result = renderPreview(
                profileSettings = state.toProfileSettings(),
                feedDotsAfterContent = state.feedDotsAfterContent,
                itemCount = state.resolvedItemCount,
                contentFontId = state.contentFontId,
                options = state.toReceiptOptions(),
            )
        ) {
            is PreviewResult.Graphic -> {
                val bitmap = rowsToBitmap(result.dotWidth, result.rows)
                setState {
                    it.copy(
                        previewBitmap = bitmap,
                        previewTextLines = emptyList(),
                        previewHeightDots = result.rows.size,
                        previewRasterKb = result.rows.sumOf { row -> row.size }.toDouble() / 1024.0,
                        previewError = null,
                    )
                }
            }
            is PreviewResult.Text -> setState {
                it.copy(
                    previewBitmap = null,
                    previewTextLines = result.lines,
                    previewHeightDots = result.lines.size,
                    previewRasterKb = result.jobBytes / 1024.0,
                    previewError = null,
                )
            }
            is PreviewResult.Failure -> setState {
                it.copy(previewError = result.message, previewBitmap = null, previewTextLines = emptyList())
            }
        }
    }

    /**
     * Claims the single "a job is running" slot, runs [block], then releases it — or returns
     * without running [block] at all if a job is already in flight.
     *
     * A real bug confirmed on real Bluetooth hardware: relying on the UI buttons'
     * `enabled = !printing` alone is a UI *convention*, not a guard, and Compose recomposing the
     * disabled state isn't instant. Two taps close enough together (easy to hit when a Bluetooth
     * close can take a couple of extra seconds) both fire before the first recomposition lands,
     * launching two concurrent jobs — two RFCOMM connections to the same printer open at once,
     * which explains a "2nd/3rd print" failing to cut and coming out garbled far better than
     * anything about the printer. The check-and-claim has to be one atomic step, hence
     * `compareAndSet` rather than read-then-write; an explicit guard is correct regardless of what
     * races it — a UI timing quirk today, or any other caller tomorrow.
     */
    private suspend fun runExclusive(block: suspend () -> Unit) {
        if (!tryBeginJob()) return
        try {
            block()
        } finally {
            setState { it.copy(printing = false) }
        }
    }

    private fun tryBeginJob(): Boolean {
        while (true) {
            val current = _uiState.value
            if (current.printing) return false
            if (_uiState.compareAndSet(current, current.copy(printing = true, printStatus = null))) return true
        }
    }

    /** The primary print action; see [printDiagnostic] for the separate verification pattern,
     *  still available but secondary now that there's real content to print. */
    private suspend fun printReceipt() = runExclusive {
        val state = _uiState.value
        val profile = repository.buildProfile(state.toProfileSettings())
        if (profile == null) {
            setState { it.copy(printStatus = FIX_PROFILE_MESSAGE) }
            return@runExclusive
        }
        val receipt = customInvoiceReceipt(
            profile = profile,
            itemCount = state.resolvedItemCount,
            fontFamilyId = state.contentFontId,
            options = state.toReceiptOptions(),
        )
        send(receipt, profile, state, label = "Receipt", itemCountForLog = state.resolvedItemCount)
    }

    /**
     * Builds and sends a diagnostic pattern for the current profile fields and print mode, the
     * same way [printReceipt] sends the custom receipt. Kept separate rather than folded into
     * "Print receipt": it exercises capabilities a real customer-facing receipt alone wouldn't
     * cover. The two modes' diagnostics are genuinely different receipts, not one receipt rendered
     * two ways, because they exercise different things — graphic mode's raster path and text
     * mode's native commands don't overlap:
     * - [PrintMode.Graphic] sends `diagnosticReceipt` — checkerboard raster transposition, every
     *   bundled font, and QR module scale.
     * - [PrintMode.Text] sends `textDiagnosticReceipt` — alignment, bold/underline/size (1x-8x)
     *   native commands, and the full printable-ASCII character coverage.
     */
    private suspend fun printDiagnostic() = runExclusive {
        val state = _uiState.value
        val profile = repository.buildProfile(state.toProfileSettings())
        if (profile == null) {
            setState { it.copy(printStatus = FIX_PROFILE_MESSAGE) }
            return@runExclusive
        }
        val receipt = when (state.printMode) {
            PrintMode.Graphic -> diagnosticReceipt(profile)
            PrintMode.Text -> textDiagnosticReceipt(profile)
        }
        send(receipt, profile, state, label = "Diagnostic receipt", itemCountForLog = null)
    }

    private suspend fun send(
        receipt: Receipt,
        profile: PrinterProfile,
        state: SampleAppUiState,
        label: String,
        itemCountForLog: Int?,
    ) {
        val result = sendReceipt(
            receipt, profile, state.feedDotsAfterContent, state.toTransportSelection(), label, state.printMode,
        )
        publish(result, label, itemCountForLog)
    }

    private suspend fun runCheckPrinterStatus() = runExclusive {
        val message = when (val result = checkPrinterStatus(_uiState.value.toTransportSelection())) {
            is StatusCheckResult.Success -> result.message
            is StatusCheckResult.Unsupported -> result.message
            is StatusCheckResult.Failure -> result.message
        }
        setState { it.copy(printStatus = message) }
    }

    private suspend fun runFeedAndCut() = runExclusive {
        val state = _uiState.value
        publish(
            feedAndCut(state.toTransportSelection(), state.feedDotsAfterContent, state.cut),
            label = FeedAndCutUseCase.LABEL,
            itemCountForLog = null,
        )
    }

    private suspend fun runReplayReferenceJob() = runExclusive {
        publish(
            replayReferenceJob(_uiState.value.toTransportSelection()),
            label = ReplayReferenceJobUseCase.LABEL,
            itemCountForLog = null,
        )
    }

    /** Shows a job's outcome in the status line and appends it to the print log — success or
     *  failure, always exactly one entry per job that reached the use case. */
    private fun publish(result: SendResult, label: String, itemCountForLog: Int?) {
        val (succeeded, message) = when (result) {
            is SendResult.Success -> true to result.message
            is SendResult.Failure -> false to result.message
        }
        val entry = PrintLogEntry(logTimestampNow(), label, itemCountForLog, succeeded, message)
        setState { it.copy(printStatus = message, printLog = listOf(entry) + it.printLog) }
    }

    private companion object {
        const val FIX_PROFILE_MESSAGE =
            "Fix the profile fields first — dot width and band height must be whole numbers"
    }
}
