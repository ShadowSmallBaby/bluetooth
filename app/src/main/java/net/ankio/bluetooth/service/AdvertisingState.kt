package net.ankio.bluetooth.service

data class AdvertisingState(val active: Boolean = false, val deadlineElapsed: Long = 0) {
    fun remainingSeconds(nowElapsed: Long): Int =
        if (!active) 0 else ((deadlineElapsed - nowElapsed).coerceAtLeast(0) + 999).div(1000).toInt()
}
