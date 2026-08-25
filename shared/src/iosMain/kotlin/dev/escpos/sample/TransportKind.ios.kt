package dev.escpos.sample

/** iOS has no third-party Bluetooth SPP (C-1), USB host (C-2), or serial/spooler access — TCP is
 *  the only real transport here. */
public actual fun availableTransportKinds(): List<TransportKind> = listOf(TransportKind.Tcp)
