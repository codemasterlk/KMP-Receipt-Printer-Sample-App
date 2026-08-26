package dev.escpos.sample.domain.model

/** `escpos-transport-serial`/`-spooler` stub Unavailable on Android (Windows/JVM-only) — TCP,
 *  Bluetooth SPP, and USB are the three real transports here. */
actual fun availableTransportKinds(): List<TransportKind> =
    listOf(TransportKind.Tcp, TransportKind.Bluetooth, TransportKind.Usb)
