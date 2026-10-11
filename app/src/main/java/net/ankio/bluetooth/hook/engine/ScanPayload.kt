package net.ankio.bluetooth.hook.engine
import net.ankio.bluetooth.hook.model.*

data class ScanPayload(val mac: String, val rssi: Int, val data: ByteArray) {
    fun argument(argument: ScanArgument): Any? = when (argument) {
        is ScanArgument.Constant -> argument.value
        is ScanArgument.Field -> when (argument.field) {
            ScanField.EVENT_TYPE -> 0x1b
            ScanField.ADDRESS_TYPE, ScanField.SECONDARY_PHY, ScanField.PERIODIC_INTERVAL -> 0
            ScanField.MAC, ScanField.ORIGINAL_ADDRESS -> mac
            ScanField.PRIMARY_PHY -> 1
            ScanField.ADVERTISING_SID -> 0xff
            ScanField.TX_POWER -> 0x7f
            ScanField.RSSI -> rssi
            ScanField.DATA -> data.copyOf()
        }
    }

    companion object {
        fun parse(mac: String, rssi: String, data: String): ScanPayload {
            val address = mac.trim().replace('-', ':').uppercase()
            require(address.matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}"))) { "Invalid MAC address" }
            val signal = rssi.trim().toIntOrNull()
            require(signal != null && signal in -127..20) { "Invalid RSSI" }
            val hex = data.trim()
            require(hex.isNotEmpty() && hex.length <= 3300 && hex.length % 2 == 0 &&
                hex.all { it.digitToIntOrNull(16) != null }) { "Invalid advertisement data" }
            return ScanPayload(address, signal, ByteArray(hex.length / 2) {
                hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
            })
        }
    }
}

