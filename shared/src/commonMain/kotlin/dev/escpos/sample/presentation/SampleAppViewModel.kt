package dev.escpos.sample.presentation

import dev.escpos.core.CutMode
import dev.escpos.core.PrinterProfile
import dev.escpos.core.RasterMode
import dev.escpos.core.Receipt
import dev.escpos.fonts.diagnosticReceipt
import dev.escpos.sample.data.platform.PlatformContext
import dev.escpos.sample.data.platform.logTimestampNow
import dev.escpos.sample.data.repository.PrinterRepositoryImpl
import dev.escpos.sample.domain.model.ProfileSettings
import dev.escpos.sample.domain.model.TransportKind
import dev.escpos.sample.domain.model.TransportSelection
import dev.escpos.sample.domain.receipt.customInvoiceReceipt
import dev.escpos.sample.domain.usecase.CheckPrinterStatusUseCase
import dev.escpos.sample.domain.usecase.PreviewResult
import dev.escpos.sample.domain.usecase.RenderPreviewUseCase
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
 * Real business logic doesn't live here: rendering a preview, sending a receipt, and checking
 * printer status are each delegated to a use case ([RenderPreviewUseCase], [SendReceiptUseCase],
 * [CheckPrinterStatusUseCase]), all backed by one `PrinterRepository` for device discovery and
 * transport/renderer construction. This class's own job is orchestration only: receive one
 * [SampleAppEvent], update [uiState] directly for a plain field edit, or map the relevant slice of
 * [SampleAppUiState] into the domain-level [ProfileSettings]/[TransportSelection] a use case
 * actually needs and publish its result. That mapping step is what keeps the domain layer from
 * ever depending on this presentation-layer state type — see each domain model's KDoc.
 *
 * Two things happen reactively, in [init], rather than as explicit events the UI has to trigger:
 * the preview re-renders whenever a field that changes the raster changes (not on every unrelated
 * keystroke, e.g. typing a TCP host), and a device-based transport's device list re-populates the
 * moment its tab is selected. Driving these off [uiState] itself — not a UI-side `LaunchedEffect`
 * — keeps [SampleApp] a pure "dispatch events, render state" view with no timing decisions of its
 * own to get wrong.
 *
 * Deliberately a plain class, not an `androidx.lifecycle.ViewModel`: a `ViewModelStoreOwner`
 * isn't guaranteed to exist in the composition on every target this sample runs on (Desktop's and
 * iOS's entry points don't go through `ComponentActivity`, unlike Android's), and one screen with
 * nothing worth surviving process death for doesn't need that lifecycle machinery anyway.
 * [SampleApp] holds one instance per composition via `remember { SampleAppViewModel(context, scope) }`,
 * passing its own `rememberCoroutineScope()` in as [scope] — every event this class handles with a
 * coroutine (printing, status checks, the two reactive flows above) runs on it, so [SampleApp]
 * itself never has to launch one.
 */
class SampleAppViewModel(context: PlatformContext, private val scope: CoroutineScope) {
    // The one place this app picks a concrete PrinterRepository — everything above this line
    // (use cases, and this class itself) only ever depends on the domain-layer interface.
    private val repository = PrinterRepositoryImpl(context)
    private val renderPreview = RenderPreviewUseCase(repository)
    private val sendReceipt = SendReceiptUseCase(repository)
    private val checkPrinterStatus = CheckPrinterStatusUseCase(repository)

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
     *  are the only content fields that belong here alongside the profile fields. */
    private data class PreviewKey(
        val dotWidth: String,
        val rasterMode: RasterMode,
        val bandHeight: String,
        val cut: CutMode,
        val drawerPin: Int,
        val itemCount: String,
        val contentFontId: String,
    ) {
        constructor(state: SampleAppUiState) : this(
            state.dotWidth, state.rasterMode, state.bandHeight, state.cut,
            state.drawerPin, state.itemCount, state.contentFontId,
        )
    }

    private inline fun setState(transform: (SampleAppUiState) -> SampleAppUiState) {
        _uiState.update(transform)
    }

    private fun SampleAppUiState.toProfileSettings() =
        ProfileSettings(profileName, dotWidth, rasterMode, bandHeight, cut, drawerPin)

    private fun SampleAppUiState.toTransportSelection() = TransportSelection(
        transportKind = transportKind,
        host = host,
        port = port,
        bluetoothDevice = selectedBluetoothDevice,
        usbDevice = selectedUsbDevice,
        serialPort = selectedSerialPort,
        serialBaudRate = serialBaudRate,
        spoolerPrinter = selectedSpoolerPrinter,
    )

    /** The one entry point [SampleApp] drives every user action through — see this class's KDoc. */
    fun onEvent(event: SampleAppEvent) {
        when (event) {
            is SampleAppEvent.FormTabChanged -> setState { it.copy(formTab = event.tab) }
            is SampleAppEvent.TransportKindChanged -> setState { it.copy(transportKind = event.kind) }

            is SampleAppEvent.HostChanged -> setState { it.copy(host = event.value) }
            is SampleAppEvent.PortChanged -> setState { it.copy(port = event.value) }

            is SampleAppEvent.BluetoothDeviceSelected -> setState { it.copy(selectedBluetoothDevice = event.device) }
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
            is SampleAppEvent.RasterModeChanged -> setState { it.copy(rasterMode = event.mode) }
            is SampleAppEvent.BandHeightChanged -> setState { it.copy(bandHeight = event.value) }
            is SampleAppEvent.CutChanged -> setState { it.copy(cut = event.mode) }
            is SampleAppEvent.DrawerPinChanged -> setState { it.copy(drawerPin = event.pin) }
            is SampleAppEvent.FeedDotsAfterContentChanged -> setState { it.copy(feedDotsAfterContent = event.value) }

            is SampleAppEvent.ItemCountChanged -> setState { it.copy(itemCount = event.value) }
            is SampleAppEvent.ContentFontIdChanged -> setState { it.copy(contentFontId = event.id) }

            SampleAppEvent.PrintReceipt -> scope.launch { printReceipt() }
            SampleAppEvent.PrintDiagnostic -> scope.launch { printDiagnostic() }
            SampleAppEvent.CheckPrinterStatus -> scope.launch { runCheckPrinterStatus() }
        }
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
            )
        ) {
            is PreviewResult.Success -> {
                val bitmap = rowsToBitmap(result.dotWidth, result.rows)
                setState {
                    it.copy(
                        previewBitmap = bitmap,
                        previewHeightDots = result.rows.size,
                        previewRasterKb = result.rows.sumOf { row -> row.size }.toDouble() / 1024.0,
                        previewError = null,
                    )
                }
            }
            is PreviewResult.Failure -> setState { it.copy(previewError = result.message, previewBitmap = null) }
        }
    }

    /** The primary print action; see [printDiagnostic] for the separate FR-8.6 verification
     *  pattern (checkerboard/fonts/QR-scale), still available but secondary now that there's real
     *  content to print. */
    private suspend fun printReceipt() {
        setState { it.copy(printStatus = null) }
        val state = _uiState.value
        val profile = repository.buildProfile(state.toProfileSettings())
        if (profile == null) {
            setState { it.copy(printStatus = "Fix the profile fields first — dot width and band height must be whole numbers") }
            return
        }
        val receipt = customInvoiceReceipt(profile = profile, itemCount = state.resolvedItemCount, fontFamilyId = state.contentFontId)
        send(receipt, profile, state, label = "Receipt", itemCountForLog = state.resolvedItemCount)
    }

    /**
     * Builds and renders [diagnosticReceipt] (FR-8.6) for the current profile fields, then sends
     * it the same way [printReceipt] sends the custom receipt. Kept separate rather than folded
     * into "Print receipt": it exercises raster transposition, every bundled font, and QR module
     * scale together — a real capability sending the customer-facing receipt alone wouldn't cover.
     */
    private suspend fun printDiagnostic() {
        setState { it.copy(printStatus = null) }
        val state = _uiState.value
        val profile = repository.buildProfile(state.toProfileSettings())
        if (profile == null) {
            setState { it.copy(printStatus = "Fix the profile fields first — dot width and band height must be whole numbers") }
            return
        }
        send(diagnosticReceipt(profile), profile, state, label = "Diagnostic receipt", itemCountForLog = null)
    }

    private suspend fun send(
        receipt: Receipt,
        profile: PrinterProfile,
        state: SampleAppUiState,
        label: String,
        itemCountForLog: Int?,
    ) {
        setState { it.copy(printing = true) }
        val result = sendReceipt(receipt, profile, state.feedDotsAfterContent, state.toTransportSelection(), label)
        val (succeeded, message) = when (result) {
            is SendResult.Success -> true to result.message
            is SendResult.Failure -> false to result.message
        }
        setState { it.copy(printStatus = message, printing = false) }
        val entry = PrintLogEntry(logTimestampNow(), label, itemCountForLog, succeeded, message)
        setState { it.copy(printLog = listOf(entry) + it.printLog) }
    }

    private suspend fun runCheckPrinterStatus() {
        setState { it.copy(printStatus = null, printing = true) }
        val message = when (val result = checkPrinterStatus(_uiState.value.toTransportSelection())) {
            is StatusCheckResult.Success -> result.message
            is StatusCheckResult.Unsupported -> result.message
            is StatusCheckResult.Failure -> result.message
        }
        setState { it.copy(printStatus = message, printing = false) }
    }
}
