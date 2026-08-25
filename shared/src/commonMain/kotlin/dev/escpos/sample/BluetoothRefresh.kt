package dev.escpos.sample

import androidx.compose.runtime.Composable

/**
 * Returns the callback [SampleApp]'s "Scan again" button uses to refresh
 * [SampleAppState.bluetoothDevices]. On Android 12+, `BLUETOOTH_CONNECT` is a runtime permission
 * — the returned callback requests it first (if not already granted) and only calls
 * [SampleAppState.refreshBluetoothDevices] once that resolves; see the `androidMain` actual's
 * KDoc. Every other platform either needs no runtime grant or has no such prompt to drive, so the
 * callback just calls [SampleAppState.refreshBluetoothDevices] directly.
 */
@Composable
public expect fun rememberBluetoothRefresh(state: SampleAppState): () -> Unit
