package dev.escpos.sample.domain.model

import dev.escpos.core.CutMode
import dev.escpos.core.RasterMode
import dev.escpos.transport.bt.BluetoothDeviceInfo

/**
 * The part of the form that is worth remembering between launches: which printer, how it is
 * connected, and how it is configured. **Every field is nullable, and `null` means "never saved"**
 * — so presentation applies only what is present on top of its own defaults, and this type never
 * has to duplicate them.
 *
 * Deliberately *not* here: the diagnostic toggles (dashed rules, drop logo/QR/barcode, normal
 * headings, item gap, unpaced Bluetooth). They're experiments; remembering one would silently
 * change how the next launch prints.
 *
 * A selected device is remembered by identity, not by the live object, because a device handle
 * isn't stable across launches:
 * - Bluetooth is remembered whole ([BluetoothDeviceInfo] is just name + MAC address, which is all
 *   the transport needs), so printing works before any scan.
 * - USB is remembered by vendor/product id — the platform handle (`/dev/bus/usb/...`) changes on
 *   replug — and re-matched once the device list is scanned.
 * - Serial and the print spooler are remembered by port / queue name, re-matched the same way.
 */
data class SavedSetup(
    val transportKind: TransportKind? = null,
    val host: String? = null,
    val port: String? = null,
    val bluetoothDevice: BluetoothDeviceInfo? = null,
    val usbVendorId: Int? = null,
    val usbProductId: Int? = null,
    val serialPortName: String? = null,
    val serialBaudRate: String? = null,
    val spoolerPrinterName: String? = null,
    val profileName: String? = null,
    val dotWidth: String? = null,
    val rasterMode: RasterMode? = null,
    val bandHeight: String? = null,
    val cut: CutMode? = null,
    val drawerPin: Int? = null,
    val feedDotsAfterContent: String? = null,
    val printMode: PrintMode? = null,
    val columnsOverride: String? = null,
    val itemCount: String? = null,
    val contentFontId: String? = null,
    /** Whether the setup wizard has been finished (or dismissed) once — so it only opens by itself
     *  on a genuine first run. */
    val setupWizardCompleted: Boolean? = null,
)
