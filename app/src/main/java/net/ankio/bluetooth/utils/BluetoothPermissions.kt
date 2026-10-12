package net.ankio.bluetooth.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** The same policy is used before requesting permissions and before calling Bluetooth APIs. */
object BluetoothPermissions {
    enum class Operation { SCAN, ADVERTISE }

    fun required(operation: Operation, sdk: Int): List<String> = buildList {
        if (sdk >= 31) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(if (operation == Operation.SCAN) Manifest.permission.BLUETOOTH_SCAN
                else Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        // Keep unfiltered scan results: this app does not declare neverForLocation.
        if (operation == Operation.SCAN) {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun missing(operation: Operation, sdk: Int, granted: (String) -> Boolean): Array<String> {
        val missing = required(operation, sdk).filterNot(granted).toMutableSet()
        // Android 12 requires coarse alongside fine, even after an approximate-only grant.
        if (Manifest.permission.ACCESS_FINE_LOCATION in missing) {
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        return missing.toTypedArray()
    }

    fun missing(context: Context, operation: Operation): Array<String> =
        missing(operation, Build.VERSION.SDK_INT) {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun granted(context: Context, operation: Operation): Boolean = missing(context, operation).isEmpty()
}
