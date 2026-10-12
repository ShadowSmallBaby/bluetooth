package net.ankio.bluetooth

import net.ankio.bluetooth.service.AdvertisingState
import org.junit.Assert.assertEquals
import org.junit.Test

class AdvertisingStateTest {
    @Test fun countdownUsesServiceDeadlineWhenScreenReopens() {
        val state = AdvertisingState(true, 370_000)
        assertEquals(360, state.remainingSeconds(10_000))
        assertEquals(60, state.remainingSeconds(310_000))
        assertEquals(1, state.remainingSeconds(369_999))
    }

    @Test fun stoppedAndExpiredSessionsNeverShowNegativeTime() {
        assertEquals(0, AdvertisingState().remainingSeconds(10_000))
        assertEquals(0, AdvertisingState(true, 10_000).remainingSeconds(20_000))
    }
}
