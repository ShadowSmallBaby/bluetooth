package net.ankio.bluetooth

import net.ankio.bluetooth.bluetooth.BleDevice
import net.ankio.bluetooth.data.*
import org.junit.Assert.*
import org.junit.Test

class HistoryRepositoryTest {
    private class Store(var json: String = "") : HistoryStore {
        var writes = 0
        override fun read() = json
        override fun write(json: String) { this.json = json; writes++ }
    }
    private val device = BleDevice("020106", "Vendor", -50, "AA:BB:CC:DD:EE:FF", "Device")

    @Test fun legacyHistoryCanBeReadAndUpdatedWithoutLosingMetadata() {
        val store = Store("""[{"data":"020106","company":"Vendor","rssi":-50,"address":"AA:BB:CC:DD:EE:FF","name":"Device"}]""")
        val history = HistoryRepository(store)
        assertEquals(listOf(device), history.list())
        history.updatePayload(device.address, "020105", -60, "Manual")
        assertEquals(listOf(device.copy(data = "020105", rssi = -60)), history.list())
    }

    @Test fun selectingSameAddressReplacesItAndDeletingPreservesOtherDevices() {
        val history = HistoryRepository(Store())
        val other = device.copy(address = "11:22:33:44:55:66")
        history.upsert(device)
        history.upsert(other)
        history.upsert(device.copy(rssi = -20))
        assertEquals(2, history.list().size)
        history.remove(device.address)
        assertEquals(listOf(other), history.list())
    }

    @Test fun malformedHistoryIsNeverOverwrittenByAnyMutation() {
        for (json in listOf("broken", "null", "{}", "[null]", "[{\"data\":\"020106\"}]")) {
            val store = Store(json)
            val history = HistoryRepository(store)
            assertThrows(HistoryReadException::class.java) { history.upsert(device) }
            assertThrows(HistoryReadException::class.java) { history.updatePayload(device.address, device.data, -30, "Manual") }
            assertThrows(HistoryReadException::class.java) { history.remove(device.address) }
            assertEquals(json, store.json)
            assertEquals(0, store.writes)
        }
    }

    @Test fun emptyHistoryAndNullableMetadataRemainSupported() {
        val history = HistoryRepository(Store())
        assertTrue(history.list().isEmpty())
        history.upsert(device.copy(company = null, name = null))
        assertEquals(listOf(device.copy(company = null, name = null)), history.list())
    }
}
