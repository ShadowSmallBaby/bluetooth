package net.ankio.bluetooth.ui.viewmodel

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import net.ankio.bluetooth.BuildConfig
import net.ankio.bluetooth.R
import net.ankio.bluetooth.bluetooth.BleDevice
import net.ankio.bluetooth.config.PreferenceKeys as Keys
import net.ankio.bluetooth.service.SendWebdavServer
import net.ankio.bluetooth.utils.*

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    var status by mutableIntStateOf(R.string.active_error)
        private set
    var message by mutableStateOf<String?>(null)
    var syncing by mutableStateOf(false)
        private set
    var remaining by mutableIntStateOf(0)
        private set
    private var syncJob: Job? = null
    private var countdown: Job? = null
    private val receiver = BleAdvertiserManager.registerEventReceiver(context) { event, detail ->
        when (event) {
            "STARTED" -> startCountdown()
            "STOPPED", "FAILED", "BLUETOOTH_DISABLED", "DEVICE_NOT_SUPPORTED",
            "PERMISSION_DENIED", "EXCEPTION" -> { countdown?.cancel(); remaining = 0 }
        }
        if (detail != null) message = detail
    }

    init {
        if (BleAdvertiserManager.ServiceState.isEnabled(context)) startCountdown()
    }

    fun refreshStatus() {
        status = if (isSender()) {
            if (SendWebdavServer.isRunning) R.string.server_working else R.string.server_error
        } else if (ModuleStatus.isActive) {
            if (ModuleStatus.isSupported) {
                if (SpUtils.getInt(Keys.APP_VERSION) != BuildConfig.VERSION_CODE)
                    SpUtils.putInt(Keys.APP_VERSION, BuildConfig.VERSION_CODE)
                R.string.active_success
            } else R.string.active_version
        } else R.string.active_error
    }

    fun isSender() = SpUtils.getBoolean(Keys.WEBDAV_ENABLED) && SpUtils.getBoolean(Keys.WEBDAV_SENDER)

    fun senderPermissions(): Array<String> {
        val missing = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }.toMutableSet()
        // Android 12 requires both location permissions in a fine-location request,
        // including when coarse location was already granted on an earlier attempt.
        if (Manifest.permission.ACCESS_FINE_LOCATION in missing) {
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        return missing.toTypedArray()
    }

    fun connect(restartSender: Boolean = false) {
        syncJob?.cancel()
        syncing = false
        try {
            if (isSender()) {
                SpUtils.putBoolean(Keys.ENABLED, false)
                if (senderPermissions().isNotEmpty() || !BleAdvertiserManager.isBluetoothEnabled(context)) {
                    refreshStatus()
                    return
                }
                // The service captures its MAC/company filters on creation.
                if (restartSender && SendWebdavServer.isRunning)
                    context.stopService(Intent(context, SendWebdavServer::class.java))
                if (restartSender || !SendWebdavServer.isRunning) {
                    ContextCompat.startForegroundService(context, Intent(context, SendWebdavServer::class.java))
                }
            } else {
                context.stopService(Intent(context, SendWebdavServer::class.java))
                if (SpUtils.getBoolean(Keys.WEBDAV_ENABLED)) sync()
            }
        } catch (e: Exception) { message = e.message }
        refreshStatus()
    }

    private fun sync() {
        syncing = true
        syncJob = viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    WebdavUtils(SpUtils.getString(Keys.WEBDAV_USERNAME),
                        SpUtils.getString(Keys.WEBDAV_PASSWORD)).getFromServer()
                }
                ensureActive()
                if (data != null && SpUtils.getBoolean(Keys.WEBDAV_ENABLED) && !isSender()) {
                    SpUtils.putString(Keys.DATA, data.data)
                    SpUtils.putString(Keys.MAC, data.mac)
                } else if (data == null) message = context.getString(R.string.get_bluetooth_error)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { message = context.getString(R.string.webdav_error)
            } finally {
                if (currentCoroutineContext()[Job] == syncJob) syncing = false
            }
        }
    }

    /** Mirror manual MAC/data/RSSI edits into scan history, as the old main page did. */
    fun saveToHistory() {
        val mac = SpUtils.getString(Keys.MAC)
        val data = SpUtils.getString(Keys.DATA)
        val rssi = SpUtils.getString(Keys.RSSI, "-50").toIntOrNull() ?: -50
        val history = runCatching {
            Gson().fromJson<MutableList<BleDevice>>(SpUtils.getString(Keys.HISTORY),
                object : TypeToken<MutableList<BleDevice>>() {}.type)
        }.getOrNull() ?: mutableListOf()
        val existing = history.firstOrNull { it.address == mac }
        if (existing != null) { existing.data = data; existing.rssi = rssi }
        else history.add(BleDevice(data, context.getString(R.string.manual_increase), rssi, mac, ""))
        SpUtils.putString(Keys.HISTORY, Gson().toJson(history))
    }

    fun startAdvertising() {
        try { BleAdvertiserManager.startAdvertising(context) }
        catch (e: Exception) { message = e.message }
    }

    fun stopAdvertising() {
        try { BleAdvertiserManager.stopAdvertising(context) }
        catch (e: Exception) { message = e.message }
        countdown?.cancel()
        remaining = 0
    }

    private fun startCountdown() {
        countdown?.cancel()
        val deadline = SystemClock.elapsedRealtime() + 6 * 60 * 1000L
        countdown = viewModelScope.launch {
            while (SystemClock.elapsedRealtime() < deadline) {
                remaining = ((deadline - SystemClock.elapsedRealtime()) / 1000).toInt()
                delay(1000)
            }
            stopAdvertising()
        }
    }

    override fun onCleared() {
        BleAdvertiserManager.unregisterEventReceiver(context, receiver)
        super.onCleared()
    }
}
