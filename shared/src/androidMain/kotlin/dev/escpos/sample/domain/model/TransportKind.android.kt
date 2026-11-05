package dev.escpos.sample.domain.model

/** `escpos-transport-serial`/`-spooler` stub Unavailable on Android (Windows/JVM-only) — TCP,
 *  Bluetooth SPP, and USB are the three real transports here. Order matches the reference
 *  sample's Android app: Bluetooth first, which also makes it the transport selected by default
 *  (the first entry is `SampleAppUiState`'s initial `transportKind`). */
actual fun availableTransportKinds(): List<TransportKind> =
    listOf(TransportKind.Bluetooth, TransportKind.Tcp, TransportKind.Usb)
