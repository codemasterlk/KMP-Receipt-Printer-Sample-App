package dev.escpos.sample.domain.model

/** Every transport is real on Windows/JVM: TCP, Bluetooth SPP and USB via jSerialComm/usb4java,
 *  plus the two Windows-only transports, Serial/COM and the print spooler. */
actual fun availableTransportKinds(): List<TransportKind> = TransportKind.entries.toList()
