package net.ankio.bluetooth.service

import android.annotation.SuppressLint
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.Build
import androidx.annotation.RequiresApi

/** Retains the API and callback used to start, even if preferences change while running. */
internal interface AdvertisingSession {
    fun start()
    fun stop()

    interface Listener {
        fun started()
        fun failed(errorCode: Int)
        fun stopped()
    }
}

@SuppressLint("MissingPermission")
internal class LegacyAdvertisingSession(
    private val advertiser: BluetoothLeAdvertiser,
    private val data: AdvertiseData,
    private val listener: AdvertisingSession.Listener
) : AdvertisingSession {
    private var stopped = false
    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            // A stop can arrive before Android delivers the start callback.
            if (stopped) runCatching { stop() } else listener.started()
        }

        override fun onStartFailure(errorCode: Int) {
            if (!stopped) listener.failed(errorCode)
        }
    }

    override fun start() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        advertiser.startAdvertising(settings, data, callback)
    }

    override fun stop() {
        stopped = true
        advertiser.stopAdvertising(callback)
    }
}

/** Only instantiated on API 26+, keeping AdvertisingSet types out of the service on Android 7. */
@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("MissingPermission")
internal class SetAdvertisingSession(
    private val advertiser: BluetoothLeAdvertiser,
    private val data: AdvertiseData,
    private val listener: AdvertisingSession.Listener
) : AdvertisingSession {
    private var stopped = false
    private val callback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
            if (stopped) {
                if (status == ADVERTISE_SUCCESS) runCatching { stop() }
                return
            }
            if (status == ADVERTISE_SUCCESS) listener.started() else listener.failed(status)
        }

        override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
            if (!stopped) listener.stopped()
        }

        override fun onAdvertisingEnabled(advertisingSet: AdvertisingSet?, enable: Boolean, status: Int) {
            if (stopped) return
            if (status != ADVERTISE_SUCCESS) listener.failed(status)
            else if (!enable) listener.stopped()
        }
    }

    override fun start() {
        // Legacy packets are visible to every scanner, unlike BLE5 extended advertising PDUs,
        // and the module's payload always fits the 31-byte legacy limit.
        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(true)
            .setConnectable(true)
            .setScannable(true)
            .setInterval(AdvertisingSetParameters.INTERVAL_MEDIUM)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
            .build()
        advertiser.startAdvertisingSet(parameters, data, null, null, null, callback)
    }

    override fun stop() {
        stopped = true
        advertiser.stopAdvertisingSet(callback)
    }
}
