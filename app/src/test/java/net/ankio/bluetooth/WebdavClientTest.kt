package net.ankio.bluetooth

import kotlinx.coroutines.*
import net.ankio.bluetooth.bluetooth.BluetoothData
import net.ankio.bluetooth.webdav.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class WebdavClientTest {
    private val server = "https://example.test/dav/"
    private val file = "https://example.test/dav/bluetooth/bluetooth.json"
    private val payload = BluetoothData("020106", "AA:BB:CC:DD:EE:FF", "-50")

    private class Transport : DavTransport {
        val requests = mutableListOf<String>()
        val files = mutableMapOf<String, ByteArray>()
        var failPut = false
        var closed = false
        override fun exists(path: String): Boolean { requests.add("exists $path"); return files.containsKey(path) }
        override fun createDirectory(path: String) { requests.add("mkdir $path"); files[path] = byteArrayOf() }
        override fun get(path: String) = object : ByteArrayInputStream(files.getValue(path)) {
            override fun close() { closed = true; super.close() }
        }
        override fun put(path: String, data: ByteArray) {
            requests.add("put $path")
            if (failPut) throw IOException("offline")
            files[path] = data
        }
    }

    @Test fun constructionHasNoNetworkSideEffectsAndUploadRoundTrips() {
        val transport = Transport()
        val client = WebdavClient(server, transport)
        assertTrue(transport.requests.isEmpty())
        client.upload(payload)
        assertEquals(payload, client.download())
        assertTrue(transport.closed)
    }

    @Test fun failedUploadPreservesExistingRemoteCopyAndSyncTimestamp() = runBlocking {
        val transport = Transport()
        val client = WebdavClient(server, transport)
        client.upload(payload)
        val oldCopy = transport.files.getValue(file).copyOf()
        transport.failPut = true
        var successes = 0
        val repository = WebdavRepository({ client }, { successes++ }, Dispatchers.Unconfined)
        try { repository.upload(payload.copy(data = "020105")); fail("Expected upload failure") }
        catch (_: IOException) { }
        assertArrayEquals(oldCopy, transport.files[file])
        assertEquals(0, successes)
    }

    @Test fun successfulTransfersRecordSuccessButMissingDownloadDoesNot() = runBlocking {
        val client = WebdavClient(server, Transport())
        var successes = 0
        val repository = WebdavRepository({ client }, { successes++ }, Dispatchers.Unconfined)
        assertNull(repository.download())
        assertEquals(0, successes)
        repository.upload(payload)
        assertEquals(1, successes)
        assertEquals(payload, repository.download())
        assertEquals(2, successes)
    }

    @Test fun malformedDownloadClosesStreamWithoutRecordingSuccess() = runBlocking {
        for (json in listOf("not json", "null", "{}")) {
            val transport = Transport().apply { files[file] = json.toByteArray() }
            var successes = 0
            val repository = WebdavRepository({ WebdavClient(server, transport) }, { successes++ }, Dispatchers.Unconfined)
            try { repository.download(); fail("Expected invalid response") }
            catch (_: Exception) { }
            assertTrue(transport.closed)
            assertEquals(0, successes)
        }
    }
}
