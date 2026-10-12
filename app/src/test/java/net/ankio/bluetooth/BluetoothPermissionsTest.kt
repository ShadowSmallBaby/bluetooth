package net.ankio.bluetooth

import android.Manifest.permission.*
import net.ankio.bluetooth.utils.BluetoothPermissions
import net.ankio.bluetooth.utils.BluetoothPermissions.Operation.*
import org.junit.Assert.*
import org.junit.Test

class BluetoothPermissionsTest {
    @Test fun oldAndroidNeverRequiresNearbyDevicePermissions() {
        for (sdk in listOf(24, 29, 30)) {
            assertEquals(setOf(BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION),
                BluetoothPermissions.required(SCAN, sdk).toSet())
            assertEquals(setOf(BLUETOOTH, BLUETOOTH_ADMIN), BluetoothPermissions.required(ADVERTISE, sdk).toSet())
        }
    }

    @Test fun advertisingDoesNotAskForLocationOrScan() {
        for (sdk in listOf(31, 37)) {
            assertEquals(setOf(BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT),
                BluetoothPermissions.required(ADVERTISE, sdk).toSet())
        }
    }

    @Test fun retryAfterApproximateGrantStillRequestsBothLocations() {
        assertEquals(setOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION),
            BluetoothPermissions.missing(SCAN, 31) { it != ACCESS_FINE_LOCATION }.toSet())
    }

    @Test fun alreadyGrantedPermissionsDoNotCauseAnotherRequest() {
        assertTrue(BluetoothPermissions.missing(SCAN, 31) { true }.isEmpty())
        assertEquals(listOf(BLUETOOTH_SCAN),
            BluetoothPermissions.missing(SCAN, 31) { it != BLUETOOTH_SCAN }.toList())
    }
}
