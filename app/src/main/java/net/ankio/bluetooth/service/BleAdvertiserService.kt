package net.ankio.bluetooth.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import net.ankio.bluetooth.R
import net.ankio.bluetooth.config.PreferenceKeys
import net.ankio.bluetooth.ui.MainActivity
import net.ankio.bluetooth.utils.BleAdvertiserManager
import net.ankio.bluetooth.utils.BluetoothPermissions
import net.ankio.bluetooth.utils.ByteUtils
import net.ankio.bluetooth.utils.SpUtils
import java.util.UUID

/** Owns advertising, its timeout, and cleanup regardless of the app screen or launch source. */
class BleAdvertiserService : Service() {
    companion object {
        private const val TAG = "BleAdvertiserService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "ble_advertiser_channel"
        private const val DURATION_MS = 6 * 60 * 1000L
        const val ACTION_START_ADVERTISING = "net.ankio.bluetooth.START_ADVERTISING"
        const val ACTION_STOP_ADVERTISING = "net.ankio.bluetooth.STOP_ADVERTISING"
        const val ACTION_UPDATE_CONFIG = "net.ankio.bluetooth.UPDATE_CONFIG"
    }

    enum class AdvertisingEvent {
        STARTED, STOPPED, FAILED, BLUETOOTH_DISABLED, DEVICE_NOT_SUPPORTED, PERMISSION_DENIED, EXCEPTION
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timeout: Job? = null
    private var advertisingSession: AdvertisingSession? = null
    private val bluetoothState = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                finish(AdvertisingEvent.BLUETOOTH_DISABLED)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.ble_advertiser_title), NotificationManager.IMPORTANCE_LOW))
        }
        ContextCompat.registerReceiver(this, bluetoothState,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ADVERTISING, ACTION_UPDATE_CONFIG -> try {
                val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(getString(R.string.ble_advertiser_title))
                    .setContentText(getString(R.string.ble_advertiser_active))
                    .setSmallIcon(R.drawable.ic_bluetooth).setContentIntent(open).setOngoing(true).setSilent(true).build())
                if (intent.action == ACTION_UPDATE_CONFIG) releaseAdvertising()
                startAdvertising()
            } catch (error: Exception) { finish(AdvertisingEvent.EXCEPTION, error.message) }
            else -> finish(AdvertisingEvent.STOPPED)
        }
        // A killed process cannot recover its in-memory Bluetooth session or deadline.
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (advertisingSession != null) return // Includes a pending start.
        if (!BluetoothPermissions.granted(this, BluetoothPermissions.Operation.ADVERTISE)) {
            finish(AdvertisingEvent.PERMISSION_DENIED, getString(R.string.no_permission)); return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter?.isEnabled != true) {
            finish(AdvertisingEvent.BLUETOOTH_DISABLED, getString(R.string.not_open_bluetooth)); return
        }
        val activeAdvertiser = adapter.bluetoothLeAdvertiser
        if (activeAdvertiser == null) {
            finish(AdvertisingEvent.DEVICE_NOT_SUPPORTED, getString(R.string.unsupported_bluetooth)); return
        }
        val mode = BleAdvertiserManager.advertisingMode()
        val validation = BleAdvertiserManager.ServiceState.validateConfiguration(this, mode)
        if (!validation.isValid) { finish(AdvertisingEvent.FAILED, validation.message); return }
        val bytes = ByteUtils.hexStringToBytes(SpUtils.getString(PreferenceKeys.DATA).trim())
        val data = createAdvertiseData(bytes)
        Log.d(TAG, "Advertising mode: $mode")
        lateinit var session: AdvertisingSession
        val listener = object : AdvertisingSession.Listener {
            override fun started() {
                if (advertisingSession !== session) return
                val deadline = SystemClock.elapsedRealtime() + DURATION_MS
                BleAdvertiserManager.ServiceState.setEnabled(this@BleAdvertiserService, true, deadline)
                timeout?.cancel()
                timeout = serviceScope.launch { delay(DURATION_MS); finish(AdvertisingEvent.STOPPED) }
                notifyManager(AdvertisingEvent.STARTED)
            }
            override fun failed(errorCode: Int) {
                if (advertisingSession === session) finish(AdvertisingEvent.FAILED, getAdvertiseErrorMessage(errorCode))
            }
            override fun stopped() {
                if (advertisingSession === session) finish(AdvertisingEvent.STOPPED)
            }
        }
        session = if (!mode.useLegacyApi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SetAdvertisingSession(activeAdvertiser, data, listener)
        } else {
            LegacyAdvertisingSession(activeAdvertiser, data, listener)
        }
        advertisingSession = session
        session.start()
    }

    /** Stop a radio session without stopping the service, also used for configuration changes. */
    @SuppressLint("MissingPermission")
    private fun releaseAdvertising() {
        timeout?.cancel()
        timeout = null
        val session = advertisingSession
        advertisingSession = null // Late callbacks cannot reactivate this session.
        runCatching { session?.stop() }
            .onFailure { Log.w(TAG, "Could not stop advertising", it) }
        BleAdvertiserManager.ServiceState.setEnabled(this, false)
    }

    private fun finish(event: AdvertisingEvent, message: String? = null) {
        releaseAdvertising()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifyManager(event, message)
        stopSelf()
    }

    override fun onDestroy() {
        val wasActive = advertisingSession != null
        releaseAdvertising()
        serviceScope.cancel()
        unregisterReceiver(bluetoothState)
        if (wasActive) notifyManager(AdvertisingEvent.STOPPED)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notifyManager(event: AdvertisingEvent, message: String? = null) {
        sendBroadcast(Intent("net.ankio.bluetooth.ADVERTISING_EVENT").apply {
            setPackage(packageName)
            putExtra("event", event.name)
            putExtra("message", message)
        })
    }

    private fun createAdvertiseData(data: ByteArray): AdvertiseData {
        val builder = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)

        // 解析广播数据包中的各个字段
        parseAdvertisingData(builder, data)

        return builder.build()
    }

    /**
     * 解析广播数据包
     */
    private fun parseAdvertisingData(builder: AdvertiseData.Builder, data: ByteArray) {
        var index = 0

        while (index < data.size) {
            val length = (data[index++].toInt() and 0xFF)
            if (length == 0 || index + length > data.size) break

            val type = (data[index].toInt() and 0xFF)
            val fieldData = if (index + 1 < data.size && length > 1) {
                data.copyOfRange(index + 1, index + length)
            } else {
                byteArrayOf()
            }

            when (type) {
                0x01 -> {
                    // 标志位
                    Log.d(TAG, "标志位: ${fieldData.joinToString("") { String.format("%02X", it) }}")
                }
                0x03 -> {
                    // 16位服务UUID完整列表
                    if (fieldData.size >= 2 && fieldData.size % 2 == 0) {
                        val serviceUuids = mutableListOf<ParcelUuid>()
                        for (i in fieldData.indices step 2) {
                            val uuidBytes = fieldData.sliceArray(i until i + 2)
                            val uuid = UUID.fromString(String.format("%04X-0000-1000-8000-00805F9B34FB",
                                (uuidBytes[1].toInt() and 0xFF shl 8) or (uuidBytes[0].toInt() and 0xFF)))
                            serviceUuids.add(ParcelUuid(uuid))
                            Log.d(TAG, "添加16位服务UUID: 0x${String.format("%04X",
                                (uuidBytes[1].toInt() and 0xFF shl 8) or (uuidBytes[0].toInt() and 0xFF))}")
                        }
                        // 使用正确的方法添加服务UUID
                        for (serviceUuid in serviceUuids) {
                            builder.addServiceUuid(serviceUuid)
                        }
                    }
                }
                0xFF -> {
                    // 厂商特定数据
                    if (fieldData.size >= 2) {
                        val companyIdLow = (fieldData[0].toInt() and 0xFF)
                        val companyIdHigh = (fieldData[1].toInt() and 0xFF)
                        val companyId = (companyIdHigh shl 8) or companyIdLow

                        val manufacturerData = if (fieldData.size > 2) {
                            fieldData.sliceArray(2 until fieldData.size)
                        } else {
                            byteArrayOf()
                        }

                        builder.addManufacturerData(companyId, manufacturerData)
                        Log.d(TAG, "添加厂商数据: ID=0x${String.format("%04X", companyId)}, 数据长度=${manufacturerData.size}")
                        Log.d(TAG, "厂商数据内容: ${manufacturerData.joinToString("") { String.format("%02X", it) }}")
                    }
                }
                else -> {
                    Log.d(TAG, "未知数据类型: 0x${String.format("%02X", type)}, 长度: ${fieldData.size}")
                }
            }
            index += length
        }
    }

    private fun getAdvertiseErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "设备不支持BLE广播"
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "广播实例数量已达上限"
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "广播已经在运行"
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "广播数据过大"
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "内部错误"
            else -> "未知错误代码: $errorCode"
        }
    }
}
