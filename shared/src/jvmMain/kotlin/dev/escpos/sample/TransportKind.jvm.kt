package dev.escpos.sample

/** Every transport is real on Windows/JVM (see [SampleAppState]'s KDoc): TCP, Bluetooth SPP and
 *  USB via jSerialComm/usb4java, plus the two Windows-only transports, Serial/COM and the print
 *  spooler. */
public actual fun availableTransportKinds(): List<TransportKind> = TransportKind.entries.toList()
