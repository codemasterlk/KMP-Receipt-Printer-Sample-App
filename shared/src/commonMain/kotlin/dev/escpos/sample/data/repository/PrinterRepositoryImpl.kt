package dev.escpos.sample.data.repository

import androidx.compose.ui.text.TextMeasurer
import dev.escpos.core.PrinterProfile
import dev.escpos.render.DEFAULT_FEED_DOTS_AFTER_CONTENT
import dev.escpos.render.FontLookup
import dev.escpos.render.ReceiptRenderer
import dev.escpos.render.RenderConfig
import dev.escpos.sample.data.platform.PlatformContext
import dev.escpos.sample.data.platform.loadFontLookup
import dev.escpos.sample.data.platform.loadReferenceJobPieces
import dev.escpos.sample.data.platform.platformTextMeasurer
import dev.escpos.sample.domain.model.ProfileSettings
import dev.escpos.sample.domain.model.TransportKind
import dev.escpos.sample.domain.model.TransportSelection
import dev.escpos.sample.domain.repository.PrinterRepository
import dev.escpos.transport.api.PrinterTransport
import dev.escpos.transport.bt.BluetoothDeviceInfo
import dev.escpos.transport.bt.BluetoothSppTransport
import dev.escpos.transport.bt.paceBytesPerSecondForDotWidth
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

/**
 * The real [PrinterRepository] — the one place this app reaches into `escpos-transport-*` device
 * discovery and [PrinterTransport] construction, and into `escpos-render` plus the bundled fonts
 * to build a [ReceiptRenderer]. Method names are `discoverX`, not the library's own top-level
 * function names (`usbPrinterDevices`, `serialPorts`, …) — matching them exactly would shadow the
 * imports inside this very class body.
 *
 * [context] is only ever used to build the [TextMeasurer] a [ReceiptRenderer] needs (FR-3.6/
 * NFR-3.2) — see [platformTextMeasurer]'s KDoc for why that has to be built fresh, off the UI
 * thread, from a platform-specific object, rather than cached here.
 */
class PrinterRepositoryImpl(private val context: PlatformContext) : PrinterRepository {

    // Unlike the TextMeasurer, the font bytes never change — and reading them is a ~11 MB
    // resource read across eleven files, not something to repeat on every keystroke-triggered
    // preview. Built lazily on first render so a screen that never renders never pays for it.
    private var fontLookup: FontLookup? = null

    private suspend fun fontLookup(): FontLookup = fontLookup ?: loadFontLookup().also { fontLookup = it }

    override fun discoverBluetoothDevices(): List<BluetoothDeviceInfo> = pairedBluetoothDevices()

    override fun discoverUsbDevices(): List<UsbDeviceInfo> = usbPrinterDevices()

    override fun discoverSerialPorts(): List<SerialPortInfo> = serialPorts()

    override fun discoverSpoolerPrinters(): List<SpoolerPrinterInfo> = spoolerPrinters()

    override fun buildProfile(settings: ProfileSettings): PrinterProfile? {
        val width = settings.dotWidth.toIntOrNull() ?: return null
        val height = settings.bandHeight.toIntOrNull() ?: return null
        val columns = settings.columnsOverride.toIntOrNull() // null -> PrinterProfile's own default
        return runCatching {
            if (columns != null) {
                PrinterProfile(
                    name = settings.name,
                    dotWidth = width,
                    rasterMode = settings.rasterMode,
                    bandHeight = height,
                    cut = settings.cut,
                    drawerPin = settings.drawerPin,
                    columns = columns,
                )
            } else {
                PrinterProfile(
                    name = settings.name,
                    dotWidth = width,
                    rasterMode = settings.rasterMode,
                    bandHeight = height,
                    cut = settings.cut,
                    drawerPin = settings.drawerPin,
                )
            }
        }.getOrNull()
    }

    override fun buildTransport(selection: TransportSelection): PrinterTransport? = when (selection.transportKind) {
        TransportKind.Tcp -> selection.port.toIntOrNull()?.let { TcpTransport(selection.host, it) }
        // Width-scaled pace rate, not the flat DEFAULT_BLUETOOTH_PACE_BYTES_PER_SECOND — see
        // paceBytesPerSecondForDotWidth's KDoc: the flat default paces a wide (576-dot/80mm)
        // printer slower than necessary, which a real CK-710's visibly stuttering feed confirmed.
        TransportKind.Bluetooth -> selection.bluetoothDevice?.let {
            val width = selection.dotWidth.toIntOrNull() ?: DEFAULT_DOT_WIDTH
            BluetoothSppTransport(it, paceBytesPerSecondForDotWidth(width), paced = selection.bluetoothPaced)
        }
        TransportKind.Usb -> selection.usbDevice?.let { UsbPrinterTransport(it) }
        TransportKind.Serial -> {
            val baud = selection.serialBaudRate.toIntOrNull()
            val port = selection.serialPort
            if (baud != null && port != null) SerialPortTransport(port, baud) else null
        }
        TransportKind.Spooler -> selection.spoolerPrinter?.let { SpoolerTransport(it) }
    }

    override suspend fun createRenderer(profile: PrinterProfile, feedDotsAfterContentRaw: String): ReceiptRenderer {
        val measurer = platformTextMeasurer(context)
        val config = RenderConfig.of(
            profile,
            feedDotsAfterContent = feedDotsAfterContentRaw.toIntOrNull() ?: DEFAULT_FEED_DOTS_AFTER_CONTENT,
        )
        return ReceiptRenderer(config, measurer, fontLookup())
    }

    override suspend fun loadReferenceJob(): List<ByteArray> = loadReferenceJobPieces()

    private companion object {
        /** 80mm paper — the more common of the two documented widths, used only while the
         *  dot-width field is mid-edit and unparseable. */
        const val DEFAULT_DOT_WIDTH = 576
    }
}
