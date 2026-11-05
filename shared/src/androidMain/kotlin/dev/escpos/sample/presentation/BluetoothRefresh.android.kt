package dev.escpos.sample.presentation

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import dev.escpos.transport.bt.BluetoothPermissions

/**
 * Android 12+ (API 31+) gates paired-device enumeration behind runtime Bluetooth permissions
 * (`escpos-transport-bt`'s own manifest declares them; see [BluetoothPermissions.ANDROID_12_PLUS]).
 * The prompt is launched every time — if everything is already granted it returns immediately with
 * no dialog — and only a fully granted result refreshes the list; a denial reports
 * [SampleAppEvent.BluetoothPermissionDenied] so the user sees why the list stays empty. Below API 31
 * the legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` permissions are install-time-granted, so there is
 * nothing to request and this enumerates straight away.
 */
@Composable
actual fun rememberBluetoothRefresh(viewModel: SampleAppViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        viewModel.onEvent(
            if (granted.values.all { it }) {
                SampleAppEvent.RefreshBluetoothDevices
            } else {
                SampleAppEvent.BluetoothPermissionDenied
            },
        )
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            launcher.launch(BluetoothPermissions.ANDROID_12_PLUS)
        } else {
            viewModel.onEvent(SampleAppEvent.RefreshBluetoothDevices)
        }
    }
}
