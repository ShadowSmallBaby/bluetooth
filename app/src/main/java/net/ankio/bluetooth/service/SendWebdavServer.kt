package net.ankio.bluetooth.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.ankio.bluetooth.data.AppRepositories
import net.ankio.bluetooth.R
import net.ankio.bluetooth.bluetooth.BluetoothData
import net.ankio.bluetooth.config.PreferenceKeys as Keys
import net.ankio.bluetooth.utils.BluetoothPermissions
import net.ankio.bluetooth.utils.ByteUtils
import net.ankio.bluetooth.utils.SpUtils
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SendWebdavServer : Service() {
    companion object {
        private const val CHANNEL_ID = "ForegroundServiceChannel"
        private val running = MutableStateFlow(false)
        val state = running.asStateFlow()
        val isRunning: Boolean get() = state.value
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val loop = SenderSyncLoop(scope, 10 * 60 * 1000L)
    private val parser by lazy { net.ankio.bluetooth.data.BluetoothData(this) }
    private class ScanException(message: String) : Exception(message)
    private val bluetoothState = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                finish(getString(R.string.not_open_bluetooth))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.server_name), NotificationManager.IMPORTANCE_LOW))
        }
        ContextCompat.registerReceiver(this, bluetoothState,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val filter = SenderFilter(SpUtils.getString(Keys.WEBDAV_MAC), SpUtils.getString(Keys.WEBDAV_COMPANY))
        if (!filter.isConfigured) {
            finish(getString(R.string.sender_filter_required))
            return START_NOT_STICKY
        }
        if (!BluetoothPermissions.granted(this, BluetoothPermissions.Operation.SCAN)) {
            finish(getString(R.string.no_permission))
            return START_NOT_STICKY
        }
        try {
            if (getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled != true) {
                finish(getString(R.string.not_open_bluetooth))
                return START_NOT_STICKY
            }
            // Location foreground type keeps scan permission usable after the activity leaves the foreground.
            startForeground(1, NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.server_name))
                .setSmallIcon(R.drawable.ic_bluetooth_scan).setOngoing(true).build())
        } catch (error: Exception) {
            Log.e("SendWebdavServer", "Cannot start foreground scan", error)
            finish(getString(R.string.sender_start_failed))
            return START_NOT_STICKY
        }
        running.value = true
        // Reconnecting replaces the cycle and reads current filters without recreating the service.
        loop.start(
            scan = { withTimeoutOrNull(5 * 60 * 1000L) { scan(filter) } },
            upload = AppRepositories.webdav::upload,
            onError = { error ->
                Log.e("SendWebdavServer", "Scan/sync failed", error)
                if (error is SecurityException) finish(getString(R.string.no_permission))
                else Toast.makeText(this,
                    if (error is ScanException) error.message else getString(R.string.webdav_error),
                    Toast.LENGTH_LONG).show()
            })
        // Restart from a visible activity so while-in-use location access is available.
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private suspend fun scan(filter: SenderFilter): BluetoothData = suspendCancellableCoroutine { continuation ->
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            continuation.resumeWithException(ScanException(getString(R.string.not_open_bluetooth)))
            return@suspendCancellableCoroutine
        }
        val completed = AtomicBoolean(false)
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!continuation.isActive || completed.get()) return
                try {
                    val bytes = result.scanRecord?.bytes ?: return
                    val address = result.device.address
                    if (!filter.matches(address, parser.parseManufacturerData(bytes).orEmpty())) return
                    val data = BluetoothData(ByteUtils.advDataToHexString(bytes), address, result.rssi.toString())
                    if (completed.compareAndSet(false, true)) {
                        runCatching { scanner.stopScan(this) }
                        continuation.resume(data)
                    }
                } catch (error: Exception) {
                    if (completed.compareAndSet(false, true)) {
                        runCatching { scanner.stopScan(this) }
                        continuation.resumeWithException(error)
                    }
                }
            }
            override fun onScanFailed(errorCode: Int) {
                if (completed.compareAndSet(false, true)) {
                    continuation.resumeWithException(ScanException(getString(R.string.scan_failed, errorCode)))
                }
            }
        }
        continuation.invokeOnCancellation {
            completed.set(true)
            runCatching { scanner.stopScan(callback) }
        }
        try {
            scanner.startScan(callback)
        } catch (error: Exception) {
            if (completed.compareAndSet(false, true)) continuation.resumeWithException(error)
        }
    }

    private fun finish(message: String) {
        loop.stop()
        running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    override fun onDestroy() {
        loop.stop()
        scope.cancel()
        running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        unregisterReceiver(bluetoothState)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
