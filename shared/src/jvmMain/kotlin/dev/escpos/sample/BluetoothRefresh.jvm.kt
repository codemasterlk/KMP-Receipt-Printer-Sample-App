package dev.escpos.sample

import androidx.compose.runtime.Composable

/** No runtime permission model on desktop — refresh directly. */
@Composable
public actual fun rememberBluetoothRefresh(state: SampleAppState): () -> Unit =
    { state.refreshBluetoothDevices() }
