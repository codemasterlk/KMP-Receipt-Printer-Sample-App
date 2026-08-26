package dev.escpos.sample.data.repository

import androidx.compose.ui.text.TextMeasurer
import dev.escpos.core.PrinterProfile
import dev.escpos.fonts.NotoFontLookup
import dev.escpos.render.DEFAULT_FEED_DOTS_AFTER_CONTENT
import dev.escpos.render.ReceiptRenderer
import dev.escpos.render.RenderConfig
import dev.escpos.sample.data.platform.PlatformContext
import dev.escpos.sample.data.platform.platformTextMeasurer
import dev.escpos.sample.domain.model.ProfileSettings
import dev.escpos.sample.domain.model.TransportKind
import dev.escpos.sample.domain.model.TransportSelection
import dev.escpos.sample.domain.repository.PrinterRepository
import dev.escpos.transport.api.PrinterTransport
import dev.escpos.transport.bt.BluetoothDeviceInfo
import dev.escpos.transport.bt.BluetoothSppTransport
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
 * discovery and [PrinterTransport] construction, and into `escpos-render`/`escpos-fonts` to build
 * a [ReceiptRenderer]. Method names are `discoverX`, not the library's own top-level function
 * names (`usbPrinterDevices`, `serialPorts`, …) — matching them exactly would shadow the imports
 * inside this very class body.
 *
 * [context] is only ever used to build the [TextMeasurer] a [ReceiptRenderer] needs (FR-3.6/
 * NFR-3.2) — see [platformTextMeasurer]'s KDoc for why that has to be built fresh, off the UI
 * thread, from a platform-specific object, rather than cached here.
 */
class PrinterRepositoryImpl(private val context: PlatformContext) : PrinterRepository {

    override fun discoverBluetoothDevices(): List<BluetoothDeviceInfo> = pairedBluetoothDevices()

    override fun discoverUsbDevices(): List<UsbDeviceInfo> = usbPrinterDevices()

    override fun discoverSerialPorts(): List<SerialPortInfo> = serialPorts()

    override fun discoverSpoolerPrinters(): List<SpoolerPrinterInfo> = spoolerPrinters()

    override fun buildProfile(settings: ProfileSettings): PrinterProfile? {
        val width = settings.dotWidth.toIntOrNull() ?: return null
        val height = settings.bandHeight.toIntOrNull() ?: return null
        return runCatching {
            PrinterProfile(
                name = settings.name,
                dotWidth = width,
                rasterMode = settings.rasterMode,
                bandHeight = height,
                cut = settings.cut,
                drawerPin = settings.drawerPin,
            )
        }.getOrNull()
    }

    override fun buildTransport(selection: TransportSelection): PrinterTransport? = when (selection.transportKind) {
        TransportKind.Tcp -> selection.port.toIntOrNull()?.let { TcpTransport(selection.host, it) }
        TransportKind.Bluetooth -> selection.bluetoothDevice?.let { BluetoothSppTransport(it) }
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
        return ReceiptRenderer(config, measurer, NotoFontLookup.create())
    }
}
