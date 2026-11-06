package dev.escpos.sample.data.repository

import dev.escpos.core.CutMode
import dev.escpos.core.RasterMode
import dev.escpos.sample.data.platform.KeyValueStorage
import dev.escpos.sample.domain.model.PrintMode
import dev.escpos.sample.domain.model.SavedSetup
import dev.escpos.sample.domain.model.TransportKind
import dev.escpos.sample.domain.repository.SetupRepository
import dev.escpos.transport.bt.BluetoothDeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores a [SavedSetup] as flat string entries in a [KeyValueStorage].
 *
 * Reading is deliberately forgiving: a value that's missing, from an older build, or no longer a
 * valid enum name just comes back `null` ("never saved") instead of throwing — a stale preference
 * must never be able to stop the app starting. Enums are stored by `name`, and a Bluetooth device
 * as three entries (name, address, bonded).
 */
class SetupRepositoryImpl(private val storage: KeyValueStorage) : SetupRepository {

    override fun load(): SavedSetup {
        val btAddress = storage.getString(BT_ADDRESS)
        return SavedSetup(
            transportKind = enumOrNull<TransportKind>(storage.getString(TRANSPORT_KIND)),
            host = storage.getString(HOST),
            port = storage.getString(PORT),
            bluetoothDevice = btAddress?.let {
                BluetoothDeviceInfo(
                    name = storage.getString(BT_NAME),
                    address = it,
                    bonded = storage.getString(BT_BONDED)?.toBooleanStrictOrNull() ?: true,
                )
            },
            usbVendorId = storage.getString(USB_VENDOR_ID)?.toIntOrNull(),
            usbProductId = storage.getString(USB_PRODUCT_ID)?.toIntOrNull(),
            serialPortName = storage.getString(SERIAL_PORT_NAME),
            serialBaudRate = storage.getString(SERIAL_BAUD_RATE),
            spoolerPrinterName = storage.getString(SPOOLER_PRINTER_NAME),
            profileName = storage.getString(PROFILE_NAME),
            dotWidth = storage.getString(DOT_WIDTH),
            rasterMode = enumOrNull<RasterMode>(storage.getString(RASTER_MODE)),
            bandHeight = storage.getString(BAND_HEIGHT),
            cut = enumOrNull<CutMode>(storage.getString(CUT)),
            drawerPin = storage.getString(DRAWER_PIN)?.toIntOrNull(),
            feedDotsAfterContent = storage.getString(FEED_DOTS),
            printMode = enumOrNull<PrintMode>(storage.getString(PRINT_MODE)),
            columnsOverride = storage.getString(COLUMNS_OVERRIDE),
            itemCount = storage.getString(ITEM_COUNT),
            contentFontId = storage.getString(CONTENT_FONT_ID),
        )
    }

    override suspend fun save(setup: SavedSetup) {
        val values = mapOf(
            TRANSPORT_KIND to setup.transportKind?.name,
            HOST to setup.host,
            PORT to setup.port,
            BT_NAME to setup.bluetoothDevice?.name,
            BT_ADDRESS to setup.bluetoothDevice?.address,
            BT_BONDED to setup.bluetoothDevice?.bonded?.toString(),
            USB_VENDOR_ID to setup.usbVendorId?.toString(),
            USB_PRODUCT_ID to setup.usbProductId?.toString(),
            SERIAL_PORT_NAME to setup.serialPortName,
            SERIAL_BAUD_RATE to setup.serialBaudRate,
            SPOOLER_PRINTER_NAME to setup.spoolerPrinterName,
            PROFILE_NAME to setup.profileName,
            DOT_WIDTH to setup.dotWidth,
            RASTER_MODE to setup.rasterMode?.name,
            BAND_HEIGHT to setup.bandHeight,
            CUT to setup.cut?.name,
            DRAWER_PIN to setup.drawerPin?.toString(),
            FEED_DOTS to setup.feedDotsAfterContent,
            PRINT_MODE to setup.printMode?.name,
            COLUMNS_OVERRIDE to setup.columnsOverride,
            ITEM_COUNT to setup.itemCount,
            CONTENT_FONT_ID to setup.contentFontId,
        )
        // Storage can block on disk (the Windows registry in particular) — keep it off the caller.
        withContext(Dispatchers.IO) { storage.putStrings(values) }
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

    private companion object {
        const val TRANSPORT_KIND = "transportKind"
        const val HOST = "host"
        const val PORT = "port"
        const val BT_NAME = "bluetooth.name"
        const val BT_ADDRESS = "bluetooth.address"
        const val BT_BONDED = "bluetooth.bonded"
        const val USB_VENDOR_ID = "usb.vendorId"
        const val USB_PRODUCT_ID = "usb.productId"
        const val SERIAL_PORT_NAME = "serial.portName"
        const val SERIAL_BAUD_RATE = "serial.baudRate"
        const val SPOOLER_PRINTER_NAME = "spooler.printerName"
        const val PROFILE_NAME = "profile.name"
        const val DOT_WIDTH = "profile.dotWidth"
        const val RASTER_MODE = "profile.rasterMode"
        const val BAND_HEIGHT = "profile.bandHeight"
        const val CUT = "profile.cut"
        const val DRAWER_PIN = "profile.drawerPin"
        const val FEED_DOTS = "profile.feedDotsAfterContent"
        const val PRINT_MODE = "printMode"
        const val COLUMNS_OVERRIDE = "profile.columnsOverride"
        const val ITEM_COUNT = "receipt.itemCount"
        const val CONTENT_FONT_ID = "receipt.contentFontId"
    }
}
