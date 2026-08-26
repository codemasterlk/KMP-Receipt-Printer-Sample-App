package dev.escpos.sample.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.escpos.core.CutMode
import dev.escpos.core.RasterMode
import dev.escpos.fonts.NotoFontLookup
import dev.escpos.sample.data.platform.PlatformContext
import dev.escpos.sample.domain.model.TransportKind
import dev.escpos.sample.domain.model.availableTransportKinds
import dev.escpos.sample.domain.receipt.HARDCODED_ITEMS
import dev.escpos.sample.domain.receipt.INVOICE_NUMBER
import dev.escpos.sample.domain.receipt.STORE_NAME

/**
 * The sample app's whole UI (NFR-5.2) — one shared implementation every platform's sample runs
 * (Android's `MainActivity`, desktop's `main()`, and a future `sample/ios` entry point): a
 * [FormTab]-switched form (printer/connection settings vs. receipt content vs. print log — never
 * more than one at once), a live receipt preview always in sync with whichever content is
 * current, and print/status actions. Form pane and preview sit side by side above
 * [WIDE_LAYOUT_BREAKPOINT], stacked below it — the form/preview split is a layout convenience, not
 * a navigation state, so it stays regardless of which [FormTab] is active.
 *
 * A pure "dispatch events, render state" view: every composable below reads from one
 * [SampleAppUiState] snapshot and, on any interaction, calls [SampleAppViewModel.onEvent] with a
 * [SampleAppEvent] — never a direct field write, and never a `LaunchedEffect` deciding when to
 * re-render the preview or re-list devices, both of which [SampleAppViewModel] now does
 * reactively itself. See that class's KDoc for the full split.
 *
 * [context] is whatever platform-specific object `platformTextMeasurer` needs (an Android
 * `Context`, nothing everywhere else) — each entry point supplies it (see this file's `actual`
 * callers in androidMain/jvmMain/iosMain).
 */
@Composable
fun SampleApp(context: PlatformContext) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { SampleAppViewModel(context, scope) }
    val uiState by viewModel.uiState.collectAsState()
    val onRefreshBluetooth = rememberBluetoothRefresh(viewModel)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // enableEdgeToEdge() (see MainActivity's actual) draws this content behind the status
            // bar / navigation bar / display cutout on Android — without consuming those insets
            // here, the header and the bottom action buttons render underneath them instead of
            // above. safeContentPadding() also covers Desktop/iOS window insets (a notch, in
            // principle), so it's applied unconditionally rather than only on Android.
            BoxWithConstraints(modifier = Modifier.fillMaxSize().safeContentPadding()) {
                if (maxWidth >= WIDE_LAYOUT_BREAKPOINT) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        FormPane(
                            uiState = uiState,
                            viewModel = viewModel,
                            onRefreshBluetooth = onRefreshBluetooth,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                        )
                        PreviewPane(
                            uiState = uiState,
                            // Wide enough for the receipt's true-size width (see PreviewPane's
                            // KDoc) plus the ElevatedCard's own 20dp-per-side padding, so the
                            // 80mm/58mm preview needs no horizontal scroll — only a fixed
                            // PREVIEW_PANE_WIDTH couldn't do that for both paper widths at once.
                            modifier = Modifier
                                .width(dotsToDp(uiState.dotWidth.toIntOrNull() ?: DEFAULT_PREVIEW_DOT_WIDTH) + PREVIEW_CARD_PADDING)
                                .fillMaxHeight()
                                .padding(24.dp),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        FormPane(uiState = uiState, viewModel = viewModel, onRefreshBluetooth = onRefreshBluetooth)
                        PreviewPane(
                            uiState = uiState,
                            modifier = Modifier.fillMaxWidth().height(PREVIEW_PANE_STACKED_HEIGHT),
                        )
                    }
                }
            }
        }
    }
}

private val WIDE_LAYOUT_BREAKPOINT = 720.dp
private val PREVIEW_PANE_STACKED_HEIGHT = 520.dp

/** ElevatedCard's own padding around [PreviewPane]'s content (20dp left + 20dp right) — added on
 *  top of the true-size image width so the wide-layout pane fits it with no horizontal scroll. */
private val PREVIEW_CARD_PADDING = 40.dp

/** Fallback used only while [SampleAppUiState.dotWidth] is mid-edit and unparseable — 80mm's 576
 *  dots, the more common of the two documented paper widths (FR-8.1). */
private const val DEFAULT_PREVIEW_DOT_WIDTH = 576

/**
 * The one form pane, showing exactly one of printer/connection settings, receipt content, or the
 * print log at a time — switched by [FormTab] tabs. The live preview panel is separate and stays
 * visible regardless of which tab is active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormPane(
    uiState: SampleAppUiState,
    viewModel: SampleAppViewModel,
    onRefreshBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        HeaderSection()
        RunningOnSection()

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            FormTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = uiState.formTab == tab,
                    onClick = { viewModel.onEvent(SampleAppEvent.FormTabChanged(tab)) },
                    shape = SegmentedButtonDefaults.itemShape(index, FormTab.entries.size),
                    label = { Text(tab.label) },
                )
            }
        }

        when (uiState.formTab) {
            FormTab.PrinterSettings -> {
                ConnectionCard(uiState, viewModel, onRefreshBluetooth)
                ProfileCard(uiState, viewModel)
            }
            FormTab.ReceiptContent -> ReceiptContentCard(uiState, viewModel)
            FormTab.PrintLog -> PrintLogCard(uiState)
        }

        Button(
            onClick = { viewModel.onEvent(SampleAppEvent.PrintReceipt) },
            enabled = !uiState.printing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (uiState.printing) "Printing…" else "Print receipt")
        }
        // Separate from "Print receipt" — FR-8.6's checkerboard/fonts/QR-scale verification
        // pattern, not the customer-facing content above. See SampleAppViewModel's KDoc.
        OutlinedButton(
            onClick = { viewModel.onEvent(SampleAppEvent.PrintDiagnostic) },
            enabled = !uiState.printing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Print diagnostic pattern")
        }
        // FR-7.5 — only TCP/Bluetooth SPP actually answer; every other selection reports that
        // plainly instead of pretending to check (see CheckPrinterStatusUseCase's KDoc).
        OutlinedButton(
            onClick = { viewModel.onEvent(SampleAppEvent.CheckPrinterStatus) },
            enabled = !uiState.printing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Check printer status (paper/cover)")
        }
        uiState.printStatus?.let { status -> Text(status, textAlign = TextAlign.Start) }
    }
}

/**
 * Receipt content is fixed in code now (see `dev.escpos.sample.domain.receipt`'s constants and
 * [HARDCODED_ITEMS]) — this card shows what's fixed, read-only, and offers the two controls the
 * confirmed requests kept: how many of the 100 hardcoded items actually print, and which font the
 * receipt renders in.
 */
@Composable
private fun ReceiptContentCard(uiState: SampleAppUiState, viewModel: SampleAppViewModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionLabel("Receipt content")
            Text(
                "Store details, invoice number, and the item catalog are fixed in code — " +
                    "the item count and font below are the only things editable here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            FixedFieldRow("Store", STORE_NAME)
            FixedFieldRow("Invoice #", INVOICE_NUMBER)
            FixedFieldRow("Item catalog", "${HARDCODED_ITEMS.size} fixed items")

            HorizontalDivider()
            OutlinedTextField(
                value = uiState.itemCount,
                onValueChange = { viewModel.onEvent(SampleAppEvent.ItemCountChanged(it)) },
                label = { Text("Items to print (1-${HARDCODED_ITEMS.size})") },
                supportingText = {
                    val typed = uiState.itemCount.toIntOrNull()
                    if (typed == null || typed !in 1..HARDCODED_ITEMS.size) {
                        Text("Using ${uiState.resolvedItemCount} — enter a whole number from 1 to ${HARDCODED_ITEMS.size}")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            DropdownField(
                label = "Font",
                valueText = contentFontLabel(uiState.contentFontId),
                modifier = Modifier.fillMaxWidth(),
            ) { collapse ->
                DropdownMenuItem(
                    text = { Text("Noto Sans (default)") },
                    onClick = { viewModel.onEvent(SampleAppEvent.ContentFontIdChanged(NotoFontLookup.NOTO_SANS)); collapse() },
                )
                NotoFontLookup.CONTENT_FONTS.forEach { font ->
                    DropdownMenuItem(
                        text = { Text(font.label) },
                        onClick = { viewModel.onEvent(SampleAppEvent.ContentFontIdChanged(font.id)); collapse() },
                    )
                }
            }
        }
    }
}

/** [DropdownField]'s display text for [SampleAppUiState.contentFontId] — [NotoFontLookup.NOTO_SANS]
 *  reads as "Noto Sans (default)" (matching its menu item above); every [NotoFontLookup.CONTENT_FONTS]
 *  id falls back to its own [NotoFontLookup.ContentFont.label]. */
private fun contentFontLabel(fontId: String): String =
    if (fontId == NotoFontLookup.NOTO_SANS) {
        "Noto Sans (default)"
    } else {
        NotoFontLookup.CONTENT_FONTS.firstOrNull { it.id == fontId }?.label ?: fontId
    }

/** One read-only "label — fixed value" row for [ReceiptContentCard]. */
@Composable
private fun FixedFieldRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The print-history tab: every [PrintLogEntry] this session, newest first — see
 *  [SampleAppUiState.printLog]'s KDoc. */
@Composable
private fun PrintLogCard(uiState: SampleAppUiState) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Print log")
            if (uiState.printLog.isEmpty()) {
                Text(
                    "Nothing printed yet this session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                uiState.printLog.forEachIndexed { index, entry ->
                    PrintLogRow(entry)
                    if (index != uiState.printLog.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

/** One [PrintLogEntry]: a status dot, what was sent, its item count (diagnostic prints have
 *  none), and when — then the full result detail underneath (success message, or the
 *  chunk/attempt failure detail `SendReceiptUseCase` builds while sending). */
@Composable
private fun PrintLogRow(entry: PrintLogEntry) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(entry.succeeded)
                Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                entry.itemCount?.let {
                    Text(
                        "· $it items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(entry.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusDot(succeeded: Boolean) {
    val color = if (succeeded) SUCCESS_COLOR else MaterialTheme.colorScheme.error
    Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
}

private val SUCCESS_COLOR = Color(0xFF2E7D32)

@Composable
private fun HeaderSection() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Printer setup", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Receipts render as images, so any language prints on any printer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A "running on" badge, not a switcher — this build only ever runs on the platform it was
 *  compiled for, so there is nothing real to switch between here; [platformLabel] just names it. */
@Composable
private fun RunningOnSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Running on")
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ) {
            Text(
                platformLabel(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionCard(uiState: SampleAppUiState, viewModel: SampleAppViewModel, onRefreshBluetooth: () -> Unit) {
    val transports = remember { availableTransportKinds() }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionLabel("Connection")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                transports.forEachIndexed { index, kind ->
                    SegmentedButton(
                        selected = uiState.transportKind == kind,
                        onClick = { viewModel.onEvent(SampleAppEvent.TransportKindChanged(kind)) },
                        shape = SegmentedButtonDefaults.itemShape(index, transports.size),
                        label = { Text(kind.label) },
                    )
                }
            }

            HorizontalDivider()
            SectionLabel(if (uiState.transportKind == TransportKind.Tcp) "Printer address" else "Devices")
            DeviceSection(uiState, viewModel, onRefreshBluetooth)
        }
    }
}

@Composable
private fun DeviceSection(uiState: SampleAppUiState, viewModel: SampleAppViewModel, onRefreshBluetooth: () -> Unit) {
    when (uiState.transportKind) {
        // No network discovery is implemented (FR-9.x scopes discovery to paired Bluetooth and
        // enumerated USB only) — this is manual entry, not a device list. Deliberately not
        // dressed up as one: an "Online" badge here would claim a liveness check this app never
        // actually performs.
        TransportKind.Tcp -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Enter the printer's IP address on your network manually — there's no network scan yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = uiState.host,
                onValueChange = { viewModel.onEvent(SampleAppEvent.HostChanged(it)) },
                label = { Text("IP address") },
                placeholder = { Text("192.168.1.2") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.port,
                onValueChange = { viewModel.onEvent(SampleAppEvent.PortChanged(it)) },
                label = { Text("Port") },
                placeholder = { Text("9100") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        TransportKind.Bluetooth -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Paired devices", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onRefreshBluetooth) { Text("Scan again") }
            }
            uiState.bluetoothStatus?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (uiState.bluetoothDevices.isEmpty()) {
                Text(
                    "No paired devices found yet — pair the printer in your OS Bluetooth settings first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            uiState.bluetoothDevices.forEach { device ->
                DeviceRow(
                    selected = uiState.selectedBluetoothDevice == device,
                    onClick = { viewModel.onEvent(SampleAppEvent.BluetoothDeviceSelected(device)) },
                    title = device.name ?: "(unnamed)",
                    subtitle = device.address,
                    statusLabel = "Paired",
                )
            }
        }

        TransportKind.Usb -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Attached devices", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { viewModel.onEvent(SampleAppEvent.RefreshUsbDevices) }) { Text("Scan again") }
            }
            if (uiState.usbDevices.isEmpty()) {
                Text(
                    "No USB devices found — connect the printer via a USB host/OTG cable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            uiState.usbDevices.forEach { device ->
                DeviceRow(
                    selected = uiState.selectedUsbDevice == device,
                    onClick = { viewModel.onEvent(SampleAppEvent.UsbDeviceSelected(device)) },
                    title = device.productName ?: device.deviceName,
                    subtitle = "${device.vendorId.toString(16)}:${device.productId.toString(16)}" +
                        if (device.looksLikePrinter) " — printer class" else "",
                    // Not "Connected": usbPrinterDevices() only enumerates what's attached —
                    // the actual open/claim (and, on Android, the per-device permission dialog)
                    // happens lazily inside UsbPrinterTransport.connect(), i.e. only once a
                    // print is sent.
                    statusLabel = "Attached",
                )
            }
        }

        TransportKind.Serial -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Serial ports", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { viewModel.onEvent(SampleAppEvent.RefreshSerialPorts) }) { Text("Scan again") }
            }
            if (uiState.serialPortList.isEmpty()) {
                Text(
                    "No serial ports found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            uiState.serialPortList.forEach { port ->
                DeviceRow(
                    selected = uiState.selectedSerialPort == port,
                    onClick = { viewModel.onEvent(SampleAppEvent.SerialPortSelected(port)) },
                    title = port.systemPortName,
                    subtitle = port.descriptiveName,
                    statusLabel = "COM",
                )
            }
            OutlinedTextField(
                value = uiState.serialBaudRate,
                onValueChange = { viewModel.onEvent(SampleAppEvent.SerialBaudRateChanged(it)) },
                label = { Text("Baud rate") },
                modifier = Modifier.width(160.dp),
            )
        }

        TransportKind.Spooler -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Print queues", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { viewModel.onEvent(SampleAppEvent.RefreshSpoolerPrinters) }) { Text("Scan again") }
            }
            if (uiState.spoolerPrinterList.isEmpty()) {
                Text(
                    "No print queues found — install the printer in the OS first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            uiState.spoolerPrinterList.forEach { printer ->
                DeviceRow(
                    selected = uiState.selectedSpoolerPrinter == printer,
                    onClick = { viewModel.onEvent(SampleAppEvent.SpoolerPrinterSelected(printer)) },
                    title = printer.name,
                    subtitle = if (printer.isDefault) "Default queue" else "",
                    statusLabel = "Queue",
                )
            }
            Text(
                "Only prints correctly on a queue whose driver passes RAW bytes through untouched " +
                    "(\"Generic / Text Only\", or a vendor ESC/POS driver).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceRow(selected: Boolean, onClick: () -> Unit, title: String, subtitle: String, statusLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileCard(uiState: SampleAppUiState, viewModel: SampleAppViewModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionLabel("Profile")
            OutlinedTextField(
                value = uiState.profileName,
                onValueChange = { viewModel.onEvent(SampleAppEvent.ProfileNameChanged(it)) },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            SectionLabel("Paper and output")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DropdownField(
                    label = "Paper width",
                    valueText = paperWidthLabel(uiState.dotWidth),
                    modifier = Modifier.weight(1f),
                ) { collapse ->
                    listOf(576 to "80 mm — 576 dots", 384 to "58 mm — 384 dots").forEach { (width, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { viewModel.onEvent(SampleAppEvent.DotWidthChanged(width.toString())); collapse() },
                        )
                    }
                }
                DropdownField(
                    label = "Raster command",
                    valueText = rasterModeLabel(uiState.rasterMode),
                    modifier = Modifier.weight(1f),
                ) { collapse ->
                    RasterMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(rasterModeLabel(mode)) },
                            onClick = { viewModel.onEvent(SampleAppEvent.RasterModeChanged(mode)); collapse() },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = uiState.dotWidth,
                onValueChange = { viewModel.onEvent(SampleAppEvent.DotWidthChanged(it)) },
                label = { Text("Dot width") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.bandHeight,
                onValueChange = { viewModel.onEvent(SampleAppEvent.BandHeightChanged(it)) },
                label = { Text("Band height (dots)") },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Cut")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                CutMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.cut == mode,
                        onClick = { viewModel.onEvent(SampleAppEvent.CutChanged(mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index, CutMode.entries.size),
                        label = { Text(mode.name) },
                    )
                }
            }
            OutlinedTextField(
                value = uiState.feedDotsAfterContent,
                onValueChange = { viewModel.onEvent(SampleAppEvent.FeedDotsAfterContentChanged(it)) },
                label = { Text("Feed before cut (dots)") },
                supportingText = {
                    Text(
                        "Blank paper fed past the print head before cutting. If the cutter slices " +
                            "into your actual content instead of past it, raise this — there's no " +
                            "way to detect the right value from software, only from what gets cut off.",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Drawer pin")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(0 to "Pin 2 (default)", 1 to "Pin 5").forEachIndexed { index, (pin, label) ->
                    SegmentedButton(
                        selected = uiState.drawerPin == pin,
                        onClick = { viewModel.onEvent(SampleAppEvent.DrawerPinChanged(pin)) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

private fun paperWidthLabel(dotWidth: String): String = when (dotWidth) {
    "576" -> "80 mm — 576 dots"
    "384" -> "58 mm — 384 dots"
    else -> "$dotWidth dots"
}

private fun rasterModeLabel(mode: RasterMode): String = when (mode) {
    RasterMode.EscAsterisk -> "ESC * — works everywhere"
    RasterMode.GsV0 -> "GS v 0 — faster, needs testing"
}

/** A dropdown styled like a text field (label above, value + chevron below) backed by a plain
 *  [DropdownMenu] — avoids `ExposedDropdownMenuBox`'s anchor-modifier API, which has changed
 *  shape across Material3 versions. [content] gets a `collapse` callback each item calls after
 *  applying its choice. */
@Composable
private fun DropdownField(
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    content: @Composable (collapse: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(valueText)
                    Text("⌄")
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                content { expanded = false }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Standard ESC/POS thermal print density — 8 dots/mm (203 dpi) — matching the 576/384 dots
 *  `PrinterProfile.dotWidth` documents for 80mm/58mm paper (FR-8.1). */
private const val DOTS_PER_MM = 8f
private const val MM_PER_INCH = 25.4f

/** [Dp] is defined as exactly 1/160 inch — a physical unit, not a pixel count — which is what
 *  makes converting a dot count straight to [Dp] (via real-world millimeters) render at true
 *  physical size regardless of the viewing device's actual screen density. */
private fun dotsToDp(dots: Int): Dp = (dots / DOTS_PER_MM / MM_PER_INCH * 160f).dp

/** `kotlin.text.String.format(...)` is JVM-only (see `dev.escpos.sample.domain.receipt`'s
 *  `money()` for the same reasoning) — one decimal place via plain arithmetic instead. */
private fun formatOneDecimal(value: Double): String {
    val tenths = kotlin.math.round(value * 10).toLong()
    return "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
}

/**
 * Renders [SampleAppUiState.previewBitmap] at its true physical size — [dotsToDp] converts the
 * receipt's dot dimensions to real-world millimeters and back into [Dp] (which is *itself*
 * defined as 1/160 inch, a physical unit, not a pixel count), so what's on screen is the same
 * size paper would come out at, text included — not `ContentScale.Fit`, which would
 * shrink/stretch the bitmap to whatever the panel happened to be, making a 100-item receipt look
 * identical in size to a 1-item one.
 *
 * Width is never scrolled — both call sites in [SampleApp] size this pane's width to exactly fit
 * the true-size image (see the wide-layout call site's KDoc), so the image and the pane always
 * match. Height is: a receipt is almost always taller than the pane, especially with a large
 * item count, so only the vertical axis scrolls.
 */
@Composable
private fun PreviewPane(uiState: SampleAppUiState, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Preview", style = MaterialTheme.typography.titleMedium)
                Text(
                    "1-bit · threshold · actual size",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                val bitmap = uiState.previewBitmap
                val error = uiState.previewError
                when {
                    bitmap != null -> Image(
                        bitmap = bitmap,
                        contentDescription = "Receipt preview",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .width(dotsToDp(uiState.dotWidth.toIntOrNull() ?: bitmap.width))
                            .height(dotsToDp(uiState.previewHeightDots)),
                    )
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    else -> CircularProgressIndicator()
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${uiState.dotWidth} × ${uiState.previewHeightDots}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${formatOneDecimal(uiState.previewRasterKb)} KB raster",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
