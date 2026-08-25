package dev.escpos.sample

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.escpos.transport.bt.BluetoothPermissions

/**
 * Android 12+ (API 31+) gates paired-device enumeration behind the runtime `BLUETOOTH_CONNECT`
 * permission (`escpos-transport-bt`'s own manifest declares it, along with `BLUETOOTH_SCAN`
 * requested alongside it — see [BluetoothPermissions.ANDROID_12_PLUS]'s KDoc). Below API 31
 * there is nothing to request: the legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` permissions are
 * install-time-granted.
 */
@Composable
public actual fun rememberBluetoothRefresh(state: SampleAppState): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[android.Manifest.permission.BLUETOOTH_CONNECT] == true) {
            state.refreshBluetoothDevices()
        }
    }
    return remember(context) {
        {
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                state.refreshBluetoothDevices()
            } else {
                launcher.launch(BluetoothPermissions.ANDROID_12_PLUS)
            }
        }
    }
}
