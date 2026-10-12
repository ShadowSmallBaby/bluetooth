package net.ankio.bluetooth.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import net.ankio.bluetooth.data.AppRepositories
import net.ankio.bluetooth.BuildConfig
import net.ankio.bluetooth.R
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
    val advertising = BleAdvertiserManager.ServiceState.state
    private val receiver = BleAdvertiserManager.registerEventReceiver(context) { _, detail ->
        if (detail != null) message = detail
    }

    init {
        viewModelScope.launch {
            advertising.collectLatest { state ->
                remaining = state.remainingSeconds(SystemClock.elapsedRealtime())
                while (state.active && remaining > 0) {
                    delay(1000)
                    remaining = state.remainingSeconds(SystemClock.elapsedRealtime())
                }
            }
        }
        viewModelScope.launch { SendWebdavServer.state.collectLatest { refreshStatus() } }
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

    fun senderPermissions(): Array<String> = BluetoothPermissions.missing(context, BluetoothPermissions.Operation.SCAN)

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
                val data = AppRepositories.webdav.download()
                ensureActive()
                if (data != null && SpUtils.getBoolean(Keys.WEBDAV_ENABLED) && !isSender()) {
                    sp.edit().putString(Keys.DATA, data.data).putString(Keys.MAC, data.mac).apply()
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
        try {
            AppRepositories.history.updatePayload(mac, data, rssi, context.getString(R.string.manual_increase))
        } catch (_: net.ankio.bluetooth.data.HistoryReadException) {
            message = context.getString(R.string.history_read_error)
        }
    }

    fun startAdvertising() {
        try { BleAdvertiserManager.startAdvertising(context) }
        catch (e: Exception) { message = e.message }
    }

    fun stopAdvertising() {
        try { BleAdvertiserManager.stopAdvertising(context) }
        catch (e: Exception) { message = e.message }
    }

    override fun onCleared() {
        BleAdvertiserManager.unregisterEventReceiver(context, receiver)
        super.onCleared()
    }
}
