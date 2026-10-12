package net.ankio.bluetooth.utils
import net.ankio.bluetooth.config.PreferenceKeys

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.regex.Pattern
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.ankio.bluetooth.service.AdvertisingState

/**
 * BLE广播器管理器
 * 提供BLE广告功能的兼容性检查、权限管理和服务控制
 */
object BleAdvertiserManager {

    private const val TAG = "BleAdvertiserManager"

    const val PERMISSION_REQUEST_CODE = 1001
    const val BLUETOOTH_ENABLE_REQUEST_CODE = 1002

    object BroadcastConfig {
        const val DEFAULT_DATA = "02011A17FF0002317D89030000000F0295699D011000000003FE3C"
        const val DEFAULT_RSSI = -50
        const val MAX_DATA_LENGTH = BleAdvertisingMode.LEGACY_MAX_DATA_LENGTH

        private val MAC_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
        private val HEX_PATTERN = Pattern.compile("^[0-9A-Fa-f]+$")

        /**
         * 验证MAC地址格式
         */
        fun isValidMacAddress(mac: String): Boolean {
            return MAC_PATTERN.matcher(mac).matches()
        }

        /**
         * 验证广播数据格式
         */
        fun isValidBroadcastData(data: String, maxDataLength: Int = MAX_DATA_LENGTH): Boolean {
            return HEX_PATTERN.matcher(data).matches() &&
                   data.length % 2 == 0 &&
                   data.length / 2 <= maxDataLength
        }

        /**
         * 验证RSSI范围
         */
        fun isValidRssi(rssi: Int): Boolean {
            return rssi in -100..20
        }
    }

    object ServiceState {

        private val current = MutableStateFlow(AdvertisingState())
        val state = current.asStateFlow()

        fun setEnabled(context: Context, enabled: Boolean, deadlineElapsed: Long = 0) {
            current.value = AdvertisingState(enabled, if (enabled) deadlineElapsed else 0)
            SpUtils.putBoolean(PreferenceKeys.BLE_ADVERTISER_ENABLED, enabled)
        }

        fun isEnabled(context: Context): Boolean = state.value.active

        fun validateConfiguration(
            context: Context,
            mode: BleAdvertisingMode = advertisingMode()
        ): ConfigValidationResult {
            val mac = SpUtils.getString(PreferenceKeys.MAC, "").trim()
            val data = SpUtils.getString(PreferenceKeys.DATA, "").trim()
            val rssiStr = SpUtils.getString(PreferenceKeys.RSSI, BroadcastConfig.DEFAULT_RSSI.toString()).trim()

            return when {
                mac.isEmpty() -> ConfigValidationResult(false, "MAC地址不能为空")
                !BroadcastConfig.isValidMacAddress(mac) -> ConfigValidationResult(false, "MAC地址格式无效，应为: XX:XX:XX:XX:XX:XX")
                data.isEmpty() -> ConfigValidationResult(false, "广播数据不能为空")
                !BroadcastConfig.isValidBroadcastData(data, mode.maxDataLength) -> ConfigValidationResult(false, "广播数据格式无效，应为偶数位十六进制字符，且不超过${mode.maxDataLength}字节（${mode.maxDataLength * 2}位）")
                rssiStr.isEmpty() -> ConfigValidationResult(false, "信号强度不能为空")
                !BroadcastConfig.isValidRssi(rssiStr.toIntOrNull() ?: -999) -> ConfigValidationResult(false, "信号强度应在-100到20dBm之间")
                else -> ConfigValidationResult(true, "配置验证通过")
            }
        }
    }

    data class ConfigValidationResult(
        val isValid: Boolean,
        val message: String
    )

    fun isCompatibilityModeEnabled(): Boolean =
        BleAdvertisingMode.isCompatibilityRequired(Build.VERSION.SDK_INT) ||
            SpUtils.getBoolean(PreferenceKeys.BLE_ADVERTISER_COMPATIBILITY, false)

    fun advertisingMode(): BleAdvertisingMode =
        BleAdvertisingMode.resolve(Build.VERSION.SDK_INT, isCompatibilityModeEnabled())

    /**
     * 检查设备是否支持BLE广播
     */
    fun isBleAdvertisingSupported(context: Context): Boolean {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter
            val bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser

            val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
            val hasAdvertiser = bluetoothLeAdvertiser != null

            Log.d(TAG, "BLE支持检查: 系统特性=$hasFeature, 广播器=$hasAdvertiser")

            hasFeature && hasAdvertiser
        } catch (e: Exception) {
            Log.e(TAG, "检查BLE广播支持时发生错误", e)
            false
        }
    }

    /**
     * 检查兼容性
     */
    fun checkCompatibility(context: Context): CompatibilityResult {
        try {
            if (!isBleAdvertisingSupported(context)) {
                return CompatibilityResult(false, "设备不支持BLE广播功能")
            }

            return CompatibilityResult(true, "设备兼容BLE广播功能")

        } catch (e: Exception) {
            Log.e(TAG, "兼容性检查时发生错误", e)
            return CompatibilityResult(false, "兼容性检查失败: ${e.message}")
        }
    }

    /**
     * 检查是否有必要权限
     */
    fun hasRequiredPermissions(context: Context): Boolean =
        BluetoothPermissions.granted(context, BluetoothPermissions.Operation.ADVERTISE)

    fun getMissingPermissions(context: Context): Array<String> =
        BluetoothPermissions.missing(context, BluetoothPermissions.Operation.ADVERTISE)

    /**
     * 检查蓝牙是否开启
     */
    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter
            bluetoothAdapter?.isEnabled == true
        } catch (e: Exception) {
            Log.e(TAG, "检查蓝牙状态时发生错误", e)
            false
        }
    }

    /**
     * 请求开启蓝牙
     */
    @SuppressLint("MissingPermission")
    fun requestEnableBluetooth(activity: Activity) {
        try {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(intent, BLUETOOTH_ENABLE_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "请求开启蓝牙时发生错误", e)
        }
    }

    /**
     * 启动BLE广播服务
     */
    fun startAdvertising(context: Context) {
        try {
            check(hasRequiredPermissions(context)) { context.getString(net.ankio.bluetooth.R.string.no_permission) }
            val compatibility = checkCompatibility(context)
            if (!compatibility.isCompatible) {
                Log.e(TAG, "设备不兼容: ${compatibility.message}")
                throw RuntimeException(compatibility.message)
            }

            val configValidation = ServiceState.validateConfiguration(context)
            if (!configValidation.isValid) {
                Log.e(TAG, "配置验证失败: ${configValidation.message}")
                throw RuntimeException(configValidation.message)
            }

            val intent = Intent(context, net.ankio.bluetooth.service.BleAdvertiserService::class.java).apply {
                action = net.ankio.bluetooth.service.BleAdvertiserService.ACTION_START_ADVERTISING
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }

            Log.i(TAG, "BLE广播启动请求已发送")

        } catch (e: Exception) {
            Log.e(TAG, "启动BLE广播失败", e)
            throw e
        }
    }

    /**
     * 停止BLE广播服务
     */
    fun stopAdvertising(context: Context) {
        try {
            context.stopService(Intent(context, net.ankio.bluetooth.service.BleAdvertiserService::class.java))

            Log.i(TAG, "BLE广播停止请求已发送")

        } catch (e: Exception) {
            Log.e(TAG, "停止BLE广播失败", e)
            throw e
        }
    }

    /**
     * 注册广播接收器监听Service事件
     */
    fun registerEventReceiver(context: Context, callback: (String, String?) -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val event = intent?.getStringExtra("event") ?: return
                val message = intent.getStringExtra("message")
                Log.d(TAG, "收到Service事件: $event, message: $message")
                callback(event, message)
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter("net.ankio.bluetooth.ADVERTISING_EVENT"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        return receiver
    }

    /**
     * 取消注册广播接收器
     */
    fun unregisterEventReceiver(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "取消注册广播接收器失败", e)
        }
    }

    /**
     * 获取权限请求代码
     */
    fun getPermissionRequestCode(): Int = PERMISSION_REQUEST_CODE

    /**
     * 获取蓝牙开启请求代码
     */
    fun getBluetoothEnableRequestCode(): Int = BLUETOOTH_ENABLE_REQUEST_CODE

    /**
     * 兼容性检查结果
     */
    data class CompatibilityResult(
        val isCompatible: Boolean,
        val message: String
    )
}
