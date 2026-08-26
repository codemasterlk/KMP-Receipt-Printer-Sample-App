package dev.escpos.sample.presentation

import dev.escpos.core.CutMode
import dev.escpos.core.RasterMode
import dev.escpos.sample.domain.model.TransportKind
import dev.escpos.transport.bt.BluetoothDeviceInfo
import dev.escpos.transport.serial.SerialPortInfo
import dev.escpos.transport.spooler.SpoolerPrinterInfo
import dev.escpos.transport.usb.UsbDeviceInfo

/**
 * Every action [SampleApp] can trigger, dispatched through [SampleAppViewModel.onEvent] — the
 * single entry point the UI drives the view model through. No composable in [SampleApp] ever
 * reads a [SampleAppUiState] field and writes it back mutated; each one only ever describes *what
 * happened* (a field was edited, a device was picked, "Print receipt" was tapped) and lets
 * [SampleAppViewModel] decide what that means for the next [SampleAppUiState].
 */
sealed interface SampleAppEvent {
    data class FormTabChanged(val tab: FormTab) : SampleAppEvent
    data class TransportKindChanged(val kind: TransportKind) : SampleAppEvent

    // TCP (FR-6.3)
    data class HostChanged(val value: String) : SampleAppEvent
    data class PortChanged(val value: String) : SampleAppEvent

    // Bluetooth SPP (FR-6.4, FR-9.1)
    data class BluetoothDeviceSelected(val device: BluetoothDeviceInfo) : SampleAppEvent

    /** Requests a re-list of paired devices — see `rememberBluetoothRefresh`'s KDoc for why this
     *  may trigger a runtime permission prompt before [SampleAppViewModel] actually acts on it. */
    data object RefreshBluetoothDevices : SampleAppEvent

    // USB (FR-6.6, FR-9.3)
    data class UsbDeviceSelected(val device: UsbDeviceInfo) : SampleAppEvent
    data object RefreshUsbDevices : SampleAppEvent

    // Serial / COM (FR-6.7) — real on Windows/JVM only.
    data class SerialPortSelected(val port: SerialPortInfo) : SampleAppEvent
    data class SerialBaudRateChanged(val value: String) : SampleAppEvent
    data object RefreshSerialPorts : SampleAppEvent

    // Print spooler (RAW) — real on Windows/JVM only.
    data class SpoolerPrinterSelected(val printer: SpoolerPrinterInfo) : SampleAppEvent
    data object RefreshSpoolerPrinters : SampleAppEvent

    // Printer profile (FR-8.1, FR-8.2)
    data class ProfileNameChanged(val value: String) : SampleAppEvent
    data class DotWidthChanged(val value: String) : SampleAppEvent
    data class RasterModeChanged(val mode: RasterMode) : SampleAppEvent
    data class BandHeightChanged(val value: String) : SampleAppEvent
    data class CutChanged(val mode: CutMode) : SampleAppEvent
    data class DrawerPinChanged(val pin: Int) : SampleAppEvent
    data class FeedDotsAfterContentChanged(val value: String) : SampleAppEvent

    // Receipt content (see dev.escpos.sample.domain.receipt.SampleInvoiceReceipt.kt)
    data class ItemCountChanged(val value: String) : SampleAppEvent
    data class ContentFontIdChanged(val id: String) : SampleAppEvent

    // Actions — each delegated to a use case; see SampleAppViewModel's KDoc.
    data object PrintReceipt : SampleAppEvent
    data object PrintDiagnostic : SampleAppEvent
    data object CheckPrinterStatus : SampleAppEvent
}
