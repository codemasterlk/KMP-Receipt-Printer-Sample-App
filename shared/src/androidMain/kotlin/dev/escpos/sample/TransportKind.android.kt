package dev.escpos.sample

/** `escpos-transport-serial`/`-spooler` stub Unavailable on Android (Windows/JVM-only, per
 *  [SampleAppState]'s KDoc) — TCP, Bluetooth SPP, and USB are the three real transports here. */
public actual fun availableTransportKinds(): List<TransportKind> =
    listOf(TransportKind.Tcp, TransportKind.Bluetooth, TransportKind.Usb)
