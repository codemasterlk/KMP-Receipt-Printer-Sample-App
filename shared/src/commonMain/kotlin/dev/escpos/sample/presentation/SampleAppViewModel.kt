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
import dev.escpos.sample.data.platform.createKeyValueStorage
import dev.escpos.sample.data.platform.logTimestampNow
import dev.escpos.sample.data.repository.PrinterRepositoryImpl
import dev.escpos.sample.data.repository.SetupRepositoryImpl
import dev.escpos.sample.domain.model.PrintMode
import dev.escpos.sample.domain.model.ProfileSettings
import dev.escpos.sample.domain.model.ReceiptOptions
import dev.escpos.sample.domain.model.SavedSetup
import dev.escpos.sample.domain.model.SetupTuning
import dev.escpos.sample.domain.model.TransportKind
import dev.escpos.sample.domain.model.TransportSelection
import dev.escpos.sample.domain.model.TuningFields
import dev.escpos.sample.domain.model.TuningOutcome
import dev.escpos.sample.domain.model.WizardAnswer
import dev.escpos.sample.domain.model.WizardStep
import dev.escpos.sample.domain.model.availableTransportKinds
import dev.escpos.sample.domain.receipt.customInvoiceReceipt
import dev.escpos.sample.domain.receipt.wizardConnectionTestReceipt
import dev.escpos.sample.domain.receipt.wizardCutFeedTestReceipt
import dev.escpos.sample.domain.receipt.wizardSmoothnessTestReceipt
import dev.escpos.sample.domain.receipt.wizardWidthTestReceipt
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
 * Three things happen reactively, in [init], rather than as explicit events the UI has to trigger:
 * the preview re-renders whenever a field that changes it changes (not on every unrelated
 * keystroke, e.g. typing a TCP host), a device-based transport's device list re-populates the
 * moment its tab is selected, and the printer setup is saved (debounced) whenever it changes.
 * Driving these off [uiState] itself — not a UI-side `LaunchedEffect` — keeps [SampleApp] a pure
 * "dispatch events, render state" view with no timing decisions of its own to get wrong.
 *
 * The saved setup (`SetupRepository`) is loaded once at construction and applied over the
 * [SampleAppUiState] defaults. Devices are remembered by identity — a Bluetooth device is restored
 * directly, USB/serial/spooler devices are re-selected once a scan finds the matching one. The
 * diagnostic toggles under "Developer tools" are deliberately never saved.
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
    // The one place this app picks concrete repositories — everything below this line (use cases,
    // and this class itself) only ever depends on the domain-layer interfaces.
    private val repository = PrinterRepositoryImpl(context)
    private val setupRepository = SetupRepositoryImpl(createKeyValueStorage(context))
    private val renderPreview = RenderPreviewUseCase(repository)
    private val sendReceipt = SendReceiptUseCase(repository)
    private val checkPrinterStatus = CheckPrinterStatusUseCase(repository)
    private val feedAndCut = FeedAndCutUseCase(repository)
    private val replayReferenceJob = ReplayReferenceJobUseCase(repository)

    /** What was saved last launch, read once and synchronously so the very first frame already
     *  shows the remembered setup instead of flashing the defaults. Also what [toSavedSetup] falls
     *  back to for a USB/serial/spooler device that hasn't been found again yet — without that,
     *  saving any unrelated field before the device list refreshes would erase the remembered
     *  device. */
    private val savedAtLaunch: SavedSetup = setupRepository.load()

    /** A genuine first run — nothing saved and the wizard never finished — opens the wizard by
     *  itself. Someone who already has a saved setup is never interrupted by it. */
    private val firstRun: Boolean = savedAtLaunch.setupWizardCompleted != true && savedAtLaunch.transportKind == null

    private val _uiState = MutableStateFlow(
        SampleAppUiState().withSavedSetup(savedAtLaunch).let {
            if (firstRun) it.copy(wizard = WizardState(open = true)) else it
        },
    )

    /** The state the wizard opened on, so Cancel can put every setting back. Null when closed. */
    private var wizardBaseline: SampleAppUiState? = if (firstRun) _uiState.value else null

    /** The current, immutable snapshot [SampleApp] renders — collect with `collectAsState()`. */
    val uiState: StateFlow<SampleAppUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            uiState.map(::PreviewKey).distinctUntilChanged().collectLatest { runRenderPreview() }
        }
        scope.launch {
            uiState.map { it.transportKind }.distinctUntilChanged().collectLatest { kind ->
                when (kind) {
                    TransportKind.Usb -> refreshUsbDevices()
                    TransportKind.Serial -> refreshSerialPorts()
                    TransportKind.Spooler -> refreshSpoolerPrinters()
                    else -> Unit
                }
            }
        }
        // Saves the setup whenever it changes. `drop(1)` skips the initial value (nothing changed
        // yet — and on a first run there's nothing worth writing); `collectLatest` + `delay` is a
        // debounce without the experimental `debounce` operator: each keystroke cancels the
        // pending write, so typing an IP address saves once, after the last digit.
        scope.launch {
            uiState.map { it.toSavedSetup(savedAtLaunch) }.distinctUntilChanged().drop(1).collectLatest { setup ->
                delay(SAVE_DEBOUNCE_MILLIS)
                setupRepository.save(setup)
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

    /** Lays [saved] over this state's own defaults — only what was actually saved is applied, and
     *  a saved transport this platform doesn't offer (a COM port remembered on a build that has no
     *  serial support) is ignored rather than selected. USB/serial/spooler selections are *not*
     *  applied here: they need the live device list, so [refreshUsbDevices] and friends re-match
     *  them once it's been scanned. A Bluetooth device is restored directly — it's just a name and
     *  an address, which is all the transport needs, so printing works before any scan. */
    private fun SampleAppUiState.withSavedSetup(saved: SavedSetup) = copy(
        transportKind = saved.transportKind?.takeIf { it in availableTransportKinds() } ?: transportKind,
        host = saved.host ?: host,
        port = saved.port ?: port,
        selectedBluetoothDevice = saved.bluetoothDevice ?: selectedBluetoothDevice,
        serialBaudRate = saved.serialBaudRate ?: serialBaudRate,
        profileName = saved.profileName ?: profileName,
        dotWidth = saved.dotWidth ?: dotWidth,
        rasterMode = saved.rasterMode ?: rasterMode,
        bandHeight = saved.bandHeight ?: bandHeight,
        cut = saved.cut ?: cut,
        drawerPin = saved.drawerPin ?: drawerPin,
        feedDotsAfterContent = saved.feedDotsAfterContent ?: feedDotsAfterContent,
        printMode = saved.printMode ?: printMode,
        columnsOverride = saved.columnsOverride ?: columnsOverride,
        itemCount = saved.itemCount ?: itemCount,
        contentFontId = saved.contentFontId ?: contentFontId,
        wizardCompleted = saved.setupWizardCompleted ?: wizardCompleted,
    )

    /** The part of this state worth remembering — see [SavedSetup] for what's deliberately left
     *  out. [fallback] supplies a USB/serial/spooler device that was remembered but hasn't been
     *  found again yet this session, so it isn't forgotten just because nothing has re-selected it. */
    private fun SampleAppUiState.toSavedSetup(fallback: SavedSetup) = SavedSetup(
        transportKind = transportKind,
        host = host,
        port = port,
        bluetoothDevice = selectedBluetoothDevice ?: fallback.bluetoothDevice,
        usbVendorId = selectedUsbDevice?.vendorId ?: fallback.usbVendorId,
        usbProductId = selectedUsbDevice?.productId ?: fallback.usbProductId,
        serialPortName = selectedSerialPort?.systemPortName ?: fallback.serialPortName,
        serialBaudRate = serialBaudRate,
        spoolerPrinterName = selectedSpoolerPrinter?.name ?: fallback.spoolerPrinterName,
        profileName = profileName,
        dotWidth = dotWidth,
        rasterMode = rasterMode,
        bandHeight = bandHeight,
        cut = cut,
        drawerPin = drawerPin,
        feedDotsAfterContent = feedDotsAfterContent,
        printMode = printMode,
        columnsOverride = columnsOverride,
        itemCount = itemCount,
        contentFontId = contentFontId,
        setupWizardCompleted = wizardCompleted,
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
            is SampleAppEvent.DeveloperToolsExpandedChanged -> setState { it.copy(developerToolsExpanded = event.expanded) }
            is SampleAppEvent.TransportKindChanged -> setState { it.copy(transportKind = event.kind) }

            is SampleAppEvent.HostChanged -> setState { it.copy(host = event.value) }
            is SampleAppEvent.PortChanged -> setState { it.copy(port = event.value) }

            is SampleAppEvent.BluetoothDeviceSelected -> setState { it.copy(selectedBluetoothDevice = event.device) }
            is SampleAppEvent.BluetoothPacedChanged -> setState { it.copy(bluetoothPaced = event.paced) }
            SampleAppEvent.RefreshBluetoothDevices -> refreshBluetoothDevices()
            SampleAppEvent.BluetoothPermissionDenied -> setState { it.copy(bluetoothStatus = BLUETOOTH_PERMISSION_DENIED_MESSAGE) }

            is SampleAppEvent.UsbDeviceSelected -> setState { it.copy(selectedUsbDevice = event.device) }
            SampleAppEvent.RefreshUsbDevices -> refreshUsbDevices()

            is SampleAppEvent.SerialPortSelected -> setState { it.copy(selectedSerialPort = event.port) }
            is SampleAppEvent.SerialBaudRateChanged -> setState { it.copy(serialBaudRate = event.value) }
            SampleAppEvent.RefreshSerialPorts -> refreshSerialPorts()

            is SampleAppEvent.SpoolerPrinterSelected -> setState { it.copy(selectedSpoolerPrinter = event.printer) }
            SampleAppEvent.RefreshSpoolerPrinters -> refreshSpoolerPrinters()

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

            SampleAppEvent.WizardStart -> wizardStart()
            SampleAppEvent.WizardCancel -> wizardCancel()
            SampleAppEvent.WizardNext -> enterStep(nextWizardStep(_uiState.value.wizard))
            SampleAppEvent.WizardBack -> goToStep(previousWizardStep(_uiState.value.wizard))
            SampleAppEvent.WizardPrintTest -> scope.launch { wizardPrintTest() }
            is SampleAppEvent.WizardAnswered -> wizardAnswered(event.answer)
            SampleAppEvent.WizardUndo -> wizardUndo()
            SampleAppEvent.WizardFinish -> wizardFinish(formTab = null)
            SampleAppEvent.WizardOpenFullForm -> wizardFinish(formTab = FormTab.PrinterSettings)
        }
    }

    // ---- Setup wizard ----------------------------------------------------------------------
    //
    // The wizard edits the same SampleAppUiState fields the full form does, so the two never
    // disagree and the debounced auto-save above persists wizard changes with no extra code. What
    // it adds is a way back: the state it opened on is kept in [wizardBaseline], and Cancel puts
    // every setting back. The printer never answers, so the user's taps ([WizardAnswer]) are the
    // only input that moves the flow; [SetupTuning] decides what each one changes.

    private fun wizardStart() {
        wizardBaseline = _uiState.value
        setState { it.copy(wizard = WizardState(open = true)) }
    }

    /** Close without keeping anything: every setting returns to what it was when the wizard
     *  opened. Marked completed so a cancelled first run is not nagged about again. */
    private fun wizardCancel() {
        val base = wizardBaseline
        wizardBaseline = null
        setState { s ->
            val restored = if (base != null) s.restoredFrom(base) else s
            restored.copy(wizard = WizardState(), wizardCompleted = true)
        }
    }

    private fun wizardFinish(formTab: FormTab?) {
        wizardBaseline = null
        setState { s ->
            s.copy(
                wizard = WizardState(),
                wizardCompleted = true,
                formTab = formTab ?: s.formTab,
            )
        }
    }

    private fun SampleAppUiState.restoredFrom(b: SampleAppUiState) = copy(
        transportKind = b.transportKind,
        host = b.host,
        port = b.port,
        selectedBluetoothDevice = b.selectedBluetoothDevice,
        selectedUsbDevice = b.selectedUsbDevice,
        selectedSerialPort = b.selectedSerialPort,
        serialBaudRate = b.serialBaudRate,
        selectedSpoolerPrinter = b.selectedSpoolerPrinter,
        profileName = b.profileName,
        dotWidth = b.dotWidth,
        rasterMode = b.rasterMode,
        bandHeight = b.bandHeight,
        cut = b.cut,
        drawerPin = b.drawerPin,
        feedDotsAfterContent = b.feedDotsAfterContent,
        printMode = b.printMode,
        columnsOverride = b.columnsOverride,
    )

    private fun SampleAppUiState.toTuningFields() =
        TuningFields(rasterMode, bandHeight, printMode, dotWidth, feedDotsAfterContent, cut)

    private fun SampleAppUiState.withTuning(f: TuningFields) = copy(
        rasterMode = f.rasterMode,
        bandHeight = f.bandHeight,
        printMode = f.printMode,
        dotWidth = f.dotWidth,
        feedDotsAfterContent = f.feedDotsAfterContent,
        cut = f.cut,
    )

    /** The page after [w]'s. */
    private fun nextWizardStep(w: WizardState): WizardStep = when (w.step) {
        WizardStep.Transport -> WizardStep.Device
        WizardStep.Device -> WizardStep.ConnectionCheck
        WizardStep.ConnectionCheck -> WizardStep.Readability
        WizardStep.Readability -> WizardStep.Width
        WizardStep.Width -> WizardStep.CutFeed
        WizardStep.CutFeed, WizardStep.Done -> WizardStep.Done
    }

    private fun previousWizardStep(w: WizardState): WizardStep = when (w.step) {
        WizardStep.Transport, WizardStep.Device -> WizardStep.Transport
        WizardStep.ConnectionCheck -> WizardStep.Device
        WizardStep.Readability -> WizardStep.ConnectionCheck
        WizardStep.Width -> WizardStep.Readability
        WizardStep.CutFeed -> WizardStep.Width
        WizardStep.Done -> WizardStep.CutFeed
    }

    private fun goToStep(step: WizardStep, message: String? = null) = setState {
        it.copy(wizard = it.wizard.copy(step = step, testsPrintedInStep = 0, message = message, exhausted = false))
    }

    /** Moves to [step]. The first arrival at [WizardStep.Readability] also selects the
     *  highest-priority print method, so the smoothness check always starts there. */
    private fun enterStep(step: WizardStep) {
        if (step == WizardStep.Readability && _uiState.value.wizard.triedReadability.isEmpty()) {
            startReadabilityCheck(currentFailed = false, prefix = "")
        } else {
            goToStep(step)
        }
    }

    /**
     * Opens [WizardStep.Readability] on the top-priority method ([SetupTuning.startReadability]).
     * [currentFailed] skips the method already in use when it just produced bad output.
     */
    private fun startReadabilityCheck(currentFailed: Boolean, prefix: String) {
        val s = _uiState.value
        val before = s.toTuningFields()
        val now = SetupTuning.currentCandidate(before)
        val outcome = SetupTuning.startReadability(before, currentFailed)
        val tried = (if (currentFailed) setOf(now) else emptySet()) + SetupTuning.currentCandidate(outcome.fields)
        setState { it.copy(wizard = it.wizard.copy(triedReadability = tried)) }
        applyOutcome(outcome, moveTo = WizardStep.Readability, prefix = prefix)
    }

    /** Sends the current step's test print and records the outcome on the wizard. A job that could
     *  not even be sent gets transport-specific help instead of a tuning question — that is a
     *  connection problem, not a setting. */
    private suspend fun wizardPrintTest() = runExclusive {
        val state = _uiState.value
        val profile = repository.buildProfile(state.toProfileSettings())
        if (profile == null) {
            setState { it.copy(wizard = it.wizard.copy(message = FIX_PROFILE_MESSAGE)) }
            return@runExclusive
        }
        val step = state.wizard.step
        val receipt = when (step) {
            WizardStep.Readability -> wizardSmoothnessTestReceipt(profile, state.printMode)
            WizardStep.Width -> wizardWidthTestReceipt(profile)
            WizardStep.CutFeed -> wizardCutFeedTestReceipt(profile)
            else -> wizardConnectionTestReceipt(profile)
        }
        when (val result = send(receipt, profile, state, label = "Setup wizard: ${step.title}", itemCountForLog = null)) {
            is SendResult.Success -> setState {
                it.copy(wizard = it.wizard.copy(testsPrintedInStep = it.wizard.testsPrintedInStep + 1, message = null))
            }
            is SendResult.Failure -> setState {
                it.copy(
                    wizard = it.wizard.copy(
                        message = result.message + "\n" + SetupTuning.connectionHint(it.transportKind),
                    ),
                )
            }
        }
    }

    private fun wizardAnswered(answer: WizardAnswer) {
        val s = _uiState.value
        val w = s.wizard
        when (answer) {
            WizardAnswer.ConnReadable -> enterStep(WizardStep.Readability)
            // The paper moved, so the printer is reachable — the output method is what failed, so
            // the smoothness check starts on the best method that was not just the one in use.
            WizardAnswer.ConnBadOutput -> startReadabilityCheck(
                currentFailed = true,
                prefix = "Good news — the printer is reachable, since it moved the paper. ",
            )
            WizardAnswer.ConnNothing -> setWizardMessage(SetupTuning.connectionHint(s.transportKind))
            WizardAnswer.ConnAttention -> setWizardMessage(SetupTuning.ATTENTION_HINT)

            WizardAnswer.ReadClear -> goToStep(WizardStep.Width)
            WizardAnswer.ReadBad -> tuneReadability()
            WizardAnswer.ReadNothing -> goToStep(
                WizardStep.ConnectionCheck,
                "Nothing printing at all points to the connection, not a setting. " +
                    SetupTuning.connectionHint(s.transportKind),
            )

            WizardAnswer.WidthGood -> goToStep(WizardStep.CutFeed)
            WizardAnswer.WidthCutOff, WizardAnswer.WidthTooNarrow ->
                applyOutcome(SetupTuning.adjustWidth(s.toTuningFields(), answer))

            WizardAnswer.CutGood -> goToStep(WizardStep.Done)
            WizardAnswer.CutThroughContent, WizardAnswer.CutTooMuchPaper ->
                applyOutcome(SetupTuning.adjustFeed(s.toTuningFields(), answer))
            WizardAnswer.CutDidNotCut -> {
                val tried = w.triedCuts + s.cut
                val outcome = SetupTuning.nextCut(s.toTuningFields(), tried)
                setState { it.copy(wizard = it.wizard.copy(triedCuts = tried + outcome.fields.cut)) }
                applyOutcome(outcome)
            }
        }
    }

    /** Steps the print-method ladder ([SetupTuning.nextReadability]) and remembers what was tried,
     *  so the same method is never offered twice. */
    private fun tuneReadability() {
        val s = _uiState.value
        val before = s.toTuningFields()
        val tried = s.wizard.triedReadability + SetupTuning.currentCandidate(before)
        val outcome = SetupTuning.nextReadability(before, tried)
        val newTried = if (outcome.change != null) tried + SetupTuning.currentCandidate(outcome.fields) else tried
        setState { it.copy(wizard = it.wizard.copy(triedReadability = newTried)) }
        // Falling back to text mode ends the smoothness check — carry on to the width step.
        applyOutcome(outcome, moveTo = if (outcome.proceed) WizardStep.Width else null)
    }

    /** Applies [outcome]'s new settings (recording the old ones for Undo) and shows its message.
     *  After a change the user is asked to print again, so the answer buttons hide until they do. */
    private fun applyOutcome(outcome: TuningOutcome, moveTo: WizardStep? = null, prefix: String = "") = setState { s ->
        val w = s.wizard
        val changes = if (outcome.change != null) {
            w.changes + WizardChange(outcome.change, s.toTuningFields())
        } else {
            w.changes
        }
        s.withTuning(outcome.fields).copy(
            wizard = w.copy(
                step = moveTo ?: w.step,
                changes = changes,
                message = prefix + outcome.message,
                exhausted = outcome.exhausted,
                testsPrintedInStep = if (outcome.change != null || moveTo != null) 0 else w.testsPrintedInStep,
            ),
        )
    }

    private fun setWizardMessage(message: String) =
        setState { it.copy(wizard = it.wizard.copy(message = message)) }

    private fun wizardUndo() = setState { s ->
        val last = s.wizard.changes.lastOrNull() ?: return@setState s
        s.withTuning(last.before).copy(
            wizard = s.wizard.copy(
                changes = s.wizard.changes.dropLast(1),
                message = "Reverted — ${last.description}",
                exhausted = false,
                testsPrintedInStep = 0,
            ),
        )
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

    // USB, serial, and spooler selections can't be restored directly (their platform handles aren't
    // stable across launches — see SavedSetup), so each refresh re-matches the remembered device
    // against the freshly scanned list. Only when nothing is selected yet: a user's own choice,
    // made after the scan, is never overridden.

    private fun refreshUsbDevices() {
        val found = repository.discoverUsbDevices()
        setState { s ->
            s.copy(
                usbDevices = found,
                selectedUsbDevice = s.selectedUsbDevice ?: found.firstOrNull {
                    it.vendorId == savedAtLaunch.usbVendorId && it.productId == savedAtLaunch.usbProductId
                },
            )
        }
    }

    private fun refreshSerialPorts() {
        val found = repository.discoverSerialPorts()
        setState { s ->
            s.copy(
                serialPortList = found,
                selectedSerialPort = s.selectedSerialPort ?: found.firstOrNull {
                    it.systemPortName == savedAtLaunch.serialPortName
                },
            )
        }
    }

    private fun refreshSpoolerPrinters() {
        val found = repository.discoverSpoolerPrinters()
        setState { s ->
            s.copy(
                spoolerPrinterList = found,
                selectedSpoolerPrinter = s.selectedSpoolerPrinter ?: found.firstOrNull {
                    it.name == savedAtLaunch.spoolerPrinterName
                },
            )
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
    ): SendResult {
        val result = sendReceipt(
            receipt, profile, state.feedDotsAfterContent, state.toTransportSelection(), label, state.printMode,
        )
        publish(result, label, itemCountForLog)
        return result
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
        /** How long a setup change waits for a further change before it's written — long enough
         *  that typing an IP address is one write, short enough that a quick quit loses nothing. */
        const val SAVE_DEBOUNCE_MILLIS = 300L

        const val FIX_PROFILE_MESSAGE =
            "Fix the profile fields first — dot width and band height must be whole numbers"
        const val BLUETOOTH_PERMISSION_DENIED_MESSAGE =
            "Bluetooth permission denied — grant Nearby devices to list paired printers"
    }
}
