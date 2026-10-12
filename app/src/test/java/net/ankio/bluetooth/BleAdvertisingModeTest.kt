package net.ankio.bluetooth

import net.ankio.bluetooth.utils.BleAdvertiserManager.BroadcastConfig
import net.ankio.bluetooth.utils.BleAdvertisingMode
import org.junit.Assert.*
import org.junit.Test

class BleAdvertisingModeTest {
    @Test
    fun android7ForcesTheLegacyApiEvenWithCompatibilityDisabled() {
        for (sdk in listOf(24, 25)) {
            assertTrue(BleAdvertisingMode.isCompatibilityRequired(sdk))
            val mode = BleAdvertisingMode.resolve(sdk, false)
            assertTrue(mode.useLegacyApi)
            assertEquals(31, mode.maxDataLength)
        }
    }

    @Test
    fun android8AndLaterUseTheNewApiWithLegacyPackets() {
        for (sdk in listOf(26, 31, 37)) {
            assertFalse(BleAdvertisingMode.isCompatibilityRequired(sdk))
            val mode = BleAdvertisingMode.resolve(sdk, false)
            assertFalse(mode.useLegacyApi)
            assertEquals(31, mode.maxDataLength)
        }
    }

    @Test
    fun compatibilitySwitchForcesTheLegacyApiOnAndroid8AndLater() {
        val mode = BleAdvertisingMode.resolve(26, true)
        assertTrue(mode.useLegacyApi)
        assertEquals(31, mode.maxDataLength)
    }

    @Test
    fun validationUsesByteCountsAndTheSelectedLimitWithoutTruncating() {
        // A complete manufacturer AD structure whose payload ends in zero.
        val data = "27FF4C00" + "AA".repeat(35) + "00"
        assertEquals(40, data.length / 2)
        assertFalse(BroadcastConfig.isValidBroadcastData(data))
        assertTrue(BroadcastConfig.isValidBroadcastData(data, 40))
        assertTrue(BroadcastConfig.isValidBroadcastData(data.lowercase(), 40))
        assertFalse(BroadcastConfig.isValidBroadcastData(data, 39))
        for (limit in listOf(31, 64, 251, 1650)) {
            assertTrue(BroadcastConfig.isValidBroadcastData("AA".repeat(limit), limit))
            assertFalse(BroadcastConfig.isValidBroadcastData("AA".repeat(limit + 1), limit))
        }
    }

    @Test
    fun largerLimitsStillRejectMalformedHex() {
        for (data in listOf("", "A", "02010", "02GG06", "02 01 06")) {
            assertFalse(data, BroadcastConfig.isValidBroadcastData(data, 1650))
        }
    }
}
