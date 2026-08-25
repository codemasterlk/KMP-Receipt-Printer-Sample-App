package dev.escpos.sample

import androidx.compose.runtime.Composable

/** `escpos-transport-bt`'s iOS actual is permanently unavailable (C-1, SPP needs MFi
 *  certification) — [SampleAppState.refreshBluetoothDevices] just comes back empty; nothing to
 *  request here. */
@Composable
public actual fun rememberBluetoothRefresh(state: SampleAppState): () -> Unit =
    { state.refreshBluetoothDevices() }
