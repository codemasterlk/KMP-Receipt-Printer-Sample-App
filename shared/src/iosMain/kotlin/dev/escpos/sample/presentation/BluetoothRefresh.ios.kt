package dev.escpos.sample.presentation

import androidx.compose.runtime.Composable

/** `escpos-transport-bt`'s iOS actual is permanently unavailable (C-1, SPP needs MFi
 *  certification) — refreshing just comes back empty; nothing to request here. */
@Composable
actual fun rememberBluetoothRefresh(viewModel: SampleAppViewModel): () -> Unit =
    { viewModel.onEvent(SampleAppEvent.RefreshBluetoothDevices) }
