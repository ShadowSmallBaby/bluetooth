package net.ankio.bluetooth.utils

/** Selects the advertising API; packets stay legacy so any scanner can see the module. */
data class BleAdvertisingMode(
    val useLegacyApi: Boolean,
    val maxDataLength: Int = LEGACY_MAX_DATA_LENGTH
) {
    companion object {
        const val LEGACY_MAX_DATA_LENGTH = 31

        fun isCompatibilityRequired(sdk: Int) = sdk < 26

        fun resolve(sdk: Int, compatibilityEnabled: Boolean): BleAdvertisingMode {
            // Android 7 lacks startAdvertisingSet, and the compatibility switch forces the old API
            // too. Either way the payload is sent as a 31-byte legacy packet: BLE5 extended
            // advertising PDUs are invisible to the ordinary legacy scanners this module must be
            // recognised by, so extended advertising is never used.
            val legacyApi = isCompatibilityRequired(sdk) || compatibilityEnabled
            return BleAdvertisingMode(legacyApi, LEGACY_MAX_DATA_LENGTH)
        }
    }
}
