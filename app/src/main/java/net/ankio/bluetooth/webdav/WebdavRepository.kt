package net.ankio.bluetooth.webdav

import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.ankio.bluetooth.bluetooth.BluetoothData
import net.ankio.bluetooth.config.PreferenceKeys as Keys
import net.ankio.bluetooth.utils.SpUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WebdavRepository(
    private val createClient: () -> WebdavClient = ::configuredClient,
    private val onSuccess: () -> Unit = ::recordSuccess,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun upload(data: BluetoothData) {
        withContext(dispatcher) { createClient().upload(data) }
        currentCoroutineContext().ensureActive()
        onSuccess()
    }

    suspend fun download(): BluetoothData? {
        val data = withContext(dispatcher) { createClient().download() }
        currentCoroutineContext().ensureActive()
        if (data != null) onSuccess()
        return data
    }
}

private fun configuredClient(): WebdavClient {
    val server = SpUtils.getString(Keys.WEBDAV_SERVER, "https://dav.jianguoyun.com/dav/")
    val sardine = OkHttpSardine().apply {
        setCredentials(SpUtils.getString(Keys.WEBDAV_USERNAME), SpUtils.getString(Keys.WEBDAV_PASSWORD))
    }
    return WebdavClient(server, object : DavTransport {
        override fun exists(path: String) = sardine.exists(path)
        override fun createDirectory(path: String) = sardine.createDirectory(path)
        override fun get(path: String) = sardine.get(path)
        override fun put(path: String, data: ByteArray) = sardine.put(path, data)
    })
}

private fun recordSuccess() {
    SpUtils.putString(Keys.WEBDAV_LAST, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
}
