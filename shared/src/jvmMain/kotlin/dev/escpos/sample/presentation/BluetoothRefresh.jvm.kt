package dev.escpos.sample.presentation

import androidx.compose.runtime.Composable

/** No runtime permission model on desktop — refresh directly. */
@Composable
actual fun rememberBluetoothRefresh(viewModel: SampleAppViewModel): () -> Unit =
    { viewModel.onEvent(SampleAppEvent.RefreshBluetoothDevices) }
