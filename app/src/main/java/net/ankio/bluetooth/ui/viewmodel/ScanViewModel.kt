package net.ankio.bluetooth.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import net.ankio.bluetooth.R
import net.ankio.bluetooth.bluetooth.BleDevice
import net.ankio.bluetooth.config.PreferenceKeys as Keys
import net.ankio.bluetooth.data.BluetoothData
import net.ankio.bluetooth.data.AppRepositories
import net.ankio.bluetooth.data.HistoryReadException
import net.ankio.bluetooth.utils.*

@SuppressLint("MissingPermission")
class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var timeout: Job? = null
    val devices = mutableStateListOf<BleDevice>()
    var isScanning by mutableStateOf(false)
        private set
    var showingHistory by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    private val parser = BluetoothData(context)
    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            viewModelScope.launch {
                if (!isScanning) return@launch
                val bytes = result.scanRecord?.bytes ?: return@launch
                val name = result.scanRecord?.deviceName.orEmpty()
                val company = parser.parseManufacturerData(bytes).orEmpty()
                if (SpUtils.getBoolean(BleConstant.NULL_NAME) && name.isBlank()) return@launch
                val filter = SpUtils.getString(BleConstant.COMPANY)
                if (filter.isNotEmpty() && !company.contains(filter, ignoreCase = true)) return@launch
                if (result.rssi < -SpUtils.getInt(BleConstant.RSSI, 100)) return@launch
                val device = BleDevice(ByteUtils.advDataToHexString(bytes), company.ifEmpty { "None" },
                    result.rssi, result.device.address, name.ifEmpty { "None" })
                val index = devices.indexOfFirst { it.address == device.address }
                if (index < 0) devices.add(device) else devices[index] = device
            }
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }
        override fun onScanFailed(errorCode: Int) {
            viewModelScope.launch { stop(); message = context.getString(R.string.scan_failed, errorCode) }
        }
    }

    fun bluetoothEnabled() = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
    fun supported() = adapter != null

    fun resetToScan() {
        stop()
        showingHistory = false
        devices.clear()
    }

    fun start() {
        if (isScanning) return
        if (!BluetoothPermissions.granted(context, BluetoothPermissions.Operation.SCAN)) {
            message = context.getString(R.string.no_permission); return
        }
        if (!bluetoothEnabled()) { message = context.getString(R.string.not_open_bluetooth); return }
        try {
            val activeScanner = adapter?.bluetoothLeScanner
                ?: run { message = context.getString(R.string.unsupported_bluetooth); return }
            showingHistory = false
            devices.clear()
            scanner = activeScanner
            isScanning = true
            activeScanner.startScan(callback)
            timeout = viewModelScope.launch { delay(5 * 60 * 1000L); stop() }
        } catch (e: Exception) { stop(); message = e.message }
    }

    fun stop() {
        isScanning = false
        timeout?.cancel()
        timeout = null
        runCatching { scanner?.stopScan(callback) }
        scanner = null
    }

    fun showHistory() {
        stop()
        try {
            val saved = AppRepositories.history.list()
            showingHistory = true
            devices.clear()
            devices.addAll(saved)
        } catch (_: HistoryReadException) { message = context.getString(R.string.history_read_error) }
    }

    fun select(device: BleDevice): Boolean {
        stop()
        try {
            AppRepositories.history.upsert(device)
            sp.edit().putString(Keys.MAC, device.address).putString(Keys.DATA, device.data)
                .putString(Keys.RSSI, device.rssi.toString()).apply()
            return true
        } catch (_: HistoryReadException) {
            message = context.getString(R.string.history_read_error)
            return false
        }
    }

    fun delete(device: BleDevice) {
        try {
            AppRepositories.history.remove(device.address)
            devices.remove(device)
        } catch (_: HistoryReadException) { message = context.getString(R.string.history_read_error) }
    }

    override fun onCleared() { stop(); super.onCleared() }
}
