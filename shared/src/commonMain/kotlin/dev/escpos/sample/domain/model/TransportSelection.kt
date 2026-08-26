package dev.escpos.sample.domain.model

import dev.escpos.transport.api.PrinterTransport
import dev.escpos.transport.bt.BluetoothDeviceInfo
import dev.escpos.transport.serial.SerialPortInfo
import dev.escpos.transport.spooler.SpoolerPrinterInfo
import dev.escpos.transport.usb.UsbDeviceInfo

/**
 * Everything [dev.escpos.sample.domain.repository.PrinterRepository.buildTransport] needs to build
 * a [PrinterTransport] — the domain-level equivalent of presentation state's transport/device
 * fields, kept separate so [dev.escpos.sample.domain.usecase.SendReceiptUseCase] and
 * [dev.escpos.sample.domain.usecase.CheckPrinterStatusUseCase] depend on exactly the five fields
 * they need, not the whole `SampleAppUiState`. Only the field matching [transportKind] is ever
 * actually read; the rest are simply whichever were last selected while another transport was
 * active.
 */
data class TransportSelection(
    val transportKind: TransportKind,
    val host: String,
    val port: String,
    val bluetoothDevice: BluetoothDeviceInfo?,
    val usbDevice: UsbDeviceInfo?,
    val serialPort: SerialPortInfo?,
    val serialBaudRate: String,
    val spoolerPrinter: SpoolerPrinterInfo?,
)
