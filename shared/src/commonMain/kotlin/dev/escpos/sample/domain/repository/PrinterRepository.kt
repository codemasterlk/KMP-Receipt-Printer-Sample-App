package dev.escpos.sample.domain.repository

import androidx.compose.ui.text.TextMeasurer
import dev.escpos.core.PrinterProfile
import dev.escpos.render.ReceiptRenderer
import dev.escpos.sample.domain.model.ProfileSettings
import dev.escpos.sample.domain.model.TransportSelection
import dev.escpos.transport.api.PrinterTransport
import dev.escpos.transport.bt.BluetoothDeviceInfo
import dev.escpos.transport.serial.SerialPortInfo
import dev.escpos.transport.spooler.SpoolerPrinterInfo
import dev.escpos.transport.usb.UsbDeviceInfo

/**
 * The boundary every [dev.escpos.sample.domain.usecase] use case reaches through for device
 * discovery, [PrinterTransport]/[PrinterProfile] construction, and [ReceiptRenderer] construction
 * — an interface so the domain layer depends only on this contract, never on
 * [dev.escpos.sample.data.repository.PrinterRepositoryImpl] or any `escpos-transport-*`/platform
 * detail directly. The data layer supplies the one real implementation; a test can supply a fake.
 */
interface PrinterRepository {
    /** FR-9.1. Throws [dev.escpos.transport.bt.MissingBluetoothPermissionException] if the
     *  runtime permission isn't granted — the caller's job to catch and report. */
    fun discoverBluetoothDevices(): List<BluetoothDeviceInfo>

    /** FR-9.3. Never throws — empty wherever USB discovery isn't available. */
    fun discoverUsbDevices(): List<UsbDeviceInfo>

    /** Windows/JVM only — empty everywhere else. */
    fun discoverSerialPorts(): List<SerialPortInfo>

    /** Windows/JVM only — empty everywhere else. */
    fun discoverSpoolerPrinters(): List<SpoolerPrinterInfo>

    /** Builds a [PrinterProfile] from [settings], or `null` if `dotWidth`/`bandHeight` don't
     *  parse or fail [PrinterProfile]'s own validation. */
    fun buildProfile(settings: ProfileSettings): PrinterProfile?

    /** Builds a [PrinterTransport] for whichever transport/device [selection] identifies, or
     *  `null` if the selection is incomplete (no device picked yet, an unparseable port/baud). */
    fun buildTransport(selection: TransportSelection): PrinterTransport?

    /** A fresh [ReceiptRenderer] for [profile] — built new on every call because the
     *  [TextMeasurer] it needs (FR-3.6/NFR-3.2) must be. [feedDotsAfterContentRaw] mirrors
     *  presentation's `SampleAppUiState.feedDotsAfterContent`; an unparseable value falls back to
     *  [dev.escpos.render.DEFAULT_FEED_DOTS_AFTER_CONTENT]. */
    suspend fun createRenderer(profile: PrinterProfile, feedDotsAfterContentRaw: String): ReceiptRenderer
}
