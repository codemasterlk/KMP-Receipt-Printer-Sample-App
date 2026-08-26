package dev.escpos.sample.presentation

import androidx.compose.runtime.Composable

/**
 * Returns the callback [SampleApp]'s "Scan again" button uses to refresh
 * [SampleAppUiState.bluetoothDevices]. On Android 12+, `BLUETOOTH_CONNECT` is a runtime permission
 * — the returned callback requests it first (if not already granted) and only dispatches
 * [SampleAppEvent.RefreshBluetoothDevices] once that resolves; see the `androidMain` actual's
 * KDoc. Every other platform either needs no runtime grant or has no such prompt to drive, so the
 * callback just dispatches [SampleAppEvent.RefreshBluetoothDevices] directly.
 */
@Composable
expect fun rememberBluetoothRefresh(viewModel: SampleAppViewModel): () -> Unit
