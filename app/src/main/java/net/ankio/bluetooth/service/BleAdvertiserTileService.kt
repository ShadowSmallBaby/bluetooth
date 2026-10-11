package net.ankio.bluetooth.service

import android.content.BroadcastReceiver
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import net.ankio.bluetooth.R
import net.ankio.bluetooth.utils.BleAdvertiserManager

/**
 * BLE外围服务快速设置磁贴
 */
class BleAdvertiserTileService : TileService() {

    private var isAdvertising = false
    private var advertisingEventReceiver: BroadcastReceiver? = null

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return

        when (tile.state) {
            Tile.STATE_INACTIVE -> {
                BleAdvertiserManager.startAdvertising(this)
            }

            Tile.STATE_ACTIVE -> {
                BleAdvertiserManager.stopAdvertising(this)
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        registerAdvertisingEventReceiver()
        isAdvertising = BleAdvertiserManager.ServiceState.isEnabled(this)
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        unregisterAdvertisingEventReceiver()
    }

    /**
     * 更新Tile状态和图标
     */
    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = if (isAdvertising) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.ble_advertiser_title)
        tile.icon = android.graphics.drawable.Icon.createWithResource(
            this,
            R.drawable.ic_ble_tile
        )
        tile.updateTile()
    }

    /**
     * 注册BLE服务事件广播接收器
     */
    private fun registerAdvertisingEventReceiver() {
        if (advertisingEventReceiver == null) {
            advertisingEventReceiver = BleAdvertiserManager.registerEventReceiver(this) { event, _ ->
                when (event) {
                    "STARTED" -> isAdvertising = true
                    "STOPPED", "FAILED", "EXCEPTION", "BLUETOOTH_DISABLED",
                    "DEVICE_NOT_SUPPORTED", "PERMISSION_DENIED" -> isAdvertising = false
                }
                updateTile()
            }
        }
    }

    /**
     * 取消注册BLE服务事件广播接收器
     */
    private fun unregisterAdvertisingEventReceiver() {
        val receiver = advertisingEventReceiver
        if (receiver != null) {
            BleAdvertiserManager.unregisterEventReceiver(this, receiver)
            advertisingEventReceiver = null
        }
    }
}
