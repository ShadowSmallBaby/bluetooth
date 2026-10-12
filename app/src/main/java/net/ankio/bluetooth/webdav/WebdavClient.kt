package net.ankio.bluetooth.webdav

import com.google.gson.Gson
import net.ankio.bluetooth.bluetooth.BluetoothData
import java.io.InputStream

interface DavTransport {
    fun exists(path: String): Boolean
    fun createDirectory(path: String)
    fun get(path: String): InputStream
    fun put(path: String, data: ByteArray)
}

/** Blocking transport, called on Dispatchers.IO. Construction never contacts the server. */
class WebdavClient(server: String, private val transport: DavTransport) {
    private val directory = server.trim().trimEnd('/') + "/bluetooth"
    private val file = "$directory/bluetooth.json"
    private val gson = Gson()

    fun upload(data: BluetoothData) {
        if (!transport.exists(directory)) transport.createDirectory(directory)
        // PUT replaces a resource; DELETE first loses the last valid copy if the upload fails.
        transport.put(file, gson.toJson(data).toByteArray(Charsets.UTF_8))
    }

    fun download(): BluetoothData? {
        if (!transport.exists(file)) return null
        return transport.get(file).bufferedReader(Charsets.UTF_8).use { reader ->
            val data = gson.fromJson(reader, BluetoothData::class.java)
            requireNotNull(data) { "Empty Bluetooth configuration" }
            require(!data.mac.isNullOrBlank() && !data.data.isNullOrBlank()) { "Invalid Bluetooth configuration" }
            data
        }
    }
}
