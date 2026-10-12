package net.ankio.bluetooth.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import net.ankio.bluetooth.bluetooth.BleDevice

interface HistoryStore {
    fun read(): String
    fun write(json: String)
}

class HistoryReadException(cause: Exception) : Exception("Cannot read saved Bluetooth history", cause)

/** Keeps the existing JSON format. A failed read must never turn into an empty-list write. */
class HistoryRepository(private val store: HistoryStore) {
    private val gson = Gson()

    @Synchronized
    fun list(): List<BleDevice> {
        val json = store.read()
        if (json.isBlank()) return emptyList()
        try {
            val array = JsonParser.parseString(json).asJsonArray
            array.forEach { element ->
                val row = element.asJsonObject
                for (key in listOf("address", "data")) {
                    val value = row.get(key)
                    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
                }
                val rssi = row.get("rssi")
                require(rssi != null && rssi.isJsonPrimitive && rssi.asJsonPrimitive.isNumber)
            }
            return gson.fromJson(array, Array<BleDevice>::class.java).toList().distinctBy { it.address }
        } catch (error: Exception) {
            throw HistoryReadException(error)
        }
    }

    @Synchronized
    fun upsert(device: BleDevice) {
        val entries = list().filterNot { it.address == device.address } + device
        store.write(gson.toJson(entries))
    }

    @Synchronized
    fun updatePayload(mac: String, data: String, rssi: Int, manualLabel: String) {
        val entries = list()
        val existing = entries.firstOrNull { it.address == mac }
        val device = existing?.copy(data = data, rssi = rssi)
            ?: BleDevice(data, manualLabel, rssi, mac, "")
        store.write(gson.toJson(entries.filterNot { it.address == mac } + device))
    }

    @Synchronized
    fun remove(address: String) {
        store.write(gson.toJson(list().filterNot { it.address == address }))
    }
}
